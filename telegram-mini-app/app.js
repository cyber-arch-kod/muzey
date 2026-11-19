import { CONFIG } from "./config.js";
import { UZ } from "./i18n.js";
import { mountMuseum3D } from "./viewer3d.js";
import { createMuseumAudioController, resolveMuseumMusicCandidates } from "./audioPlayer.js";

const tg = window.Telegram?.WebApp;
const root = document.getElementById("app");

const state = {
  page: "home",
  userId: "guest",
  subscription: { plan: "FREE", status: "ACTIVE", expires_at: 0 },
  museums: [],
  activeMuseum: null,
  selectedPhotos: [],
  roomStyle: "CLASSIC_WHITE",
  cleanupViewer: null,
};

function applyTelegramSafeAreaAndTheme() {
  if (!tg) return;
  tg.ready();
  tg.expand();
  const applyInsets = () => {
    const sa = tg.safeAreaInset || {};
    const csa = tg.contentSafeAreaInset || {};
    document.documentElement.style.setProperty("--tg-safe-area-inset-top", `${sa.top || 0}px`);
    document.documentElement.style.setProperty("--tg-safe-area-inset-bottom", `${sa.bottom || 0}px`);
    document.documentElement.style.setProperty("--tg-content-safe-area-inset-top", `${csa.top || 0}px`);
    document.documentElement.style.setProperty("--tg-content-safe-area-inset-bottom", `${csa.bottom || 0}px`);
  };
  applyInsets();
  tg.onEvent("safeAreaChanged", applyInsets);
  tg.onEvent("contentSafeAreaChanged", applyInsets);
}

function isPaidActive() {
  const s = state.subscription;
  return s && s.plan !== "FREE" && s.status === "ACTIVE" && Number(s.expires_at) > Date.now();
}

async function sbFetch(path, opts = {}) {
  const headers = {
    apikey: CONFIG.SUPABASE_ANON_KEY,
    Authorization: `Bearer ${CONFIG.SUPABASE_ANON_KEY}`,
    ...(opts.headers || {}),
  };
  const res = await fetch(`${CONFIG.SUPABASE_URL}${path}`, { ...opts, headers });
  if (!res.ok) throw new Error(await res.text());
  return res.status === 204 ? null : res.json();
}

async function authenticateWithTelegram() {
  const urlParams = new URLSearchParams(window.location.search);
  const startParam = tg?.initDataUnsafe?.start_param || urlParams.get("startapp");
  const pageParam = urlParams.get("page");

  if (tg?.initData) {
    try {
      const res = await fetch(`${CONFIG.SUPABASE_URL}/functions/v1/telegram-auth`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ initData: tg.initData }),
      });
      const data = await res.json();
      if (data.ok) {
        state.userId = data.userId;
        state.subscription = data.subscription || state.subscription;
        state.museums = data.museums || [];
      }
    } catch (e) {
      console.warn("Telegram HMAC auth fallback", e);
    }
  }

  if (startParam) {
    await openMuseumBySlug(startParam);
    return;
  }
  if (pageParam === "create") state.page = "create";
  else if (pageParam === "my") state.page = "my";
  else if (pageParam === "pricing") state.page = "pricing";
  render();
}

async function openMuseumBySlug(slug) {
  const list = await sbFetch(`/rest/v1/museums?slug=eq.${encodeURIComponent(slug)}&select=*,museum_photos(*)&limit=1`);
  state.activeMuseum = list?.[0] || null;
  state.page = "viewer";
  render();
}

async function compressImageInBrowser(file) {
  const img = await createImageBitmap(file, { imageOrientation: "from-image" });
  const maxDim = Math.max(img.width, img.height);
  const scale = maxDim > 1600 ? 1600 / maxDim : 1;
  const w = Math.round(img.width * scale);
  const h = Math.round(img.height * scale);
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  canvas.getContext("2d").drawImage(img, 0, 0, w, h);
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/webp", 0.8));
  return { blob, width: w, height: h, previewUrl: URL.createObjectURL(blob) };
}

function render() {
  if (state.cleanupViewer) {
    state.cleanupViewer();
    state.cleanupViewer = null;
  }

  if (state.page === "home") {
    root.innerHTML = `
      <div class="card">
        <h1 class="title">${UZ.appName}</h1>
        <p class="subtitle">${UZ.subtitle}</p>
        <button class="btn" id="go-create">${UZ.createBtn}</button>
        <button class="btn btn-outline" id="go-my">${UZ.myMuseumsBtn} (${state.museums.length})</button>
        <button class="btn btn-outline" id="go-pricing">${UZ.pricingBtn}</button>
      </div>
      ${UZ.steps.map((s) => `<div class="card"><strong>${s.title}</strong><p class="subtitle" style="margin:6px 0 0">${s.desc}</p></div>`).join("")}
    `;
    document.getElementById("go-create").onclick = () => { state.page = "create"; render(); };
    document.getElementById("go-my").onclick = () => { state.page = "my"; render(); };
    document.getElementById("go-pricing").onclick = () => { state.page = "pricing"; render(); };
    return;
  }

  if (state.page === "create") {
    const maxPhotos = isPaidActive() ? CONFIG.PRICING.PAID_MAX_PHOTOS : CONFIG.PRICING.FREE_MAX_PHOTOS;
    root.innerHTML = `
      <div class="card">
        <button class="btn btn-outline" id="back-home" style="margin-bottom:12px">${UZ.backBtn}</button>
        <h2 class="title" style="font-size:22px">${UZ.createTitle}</h2>
        <input class="input" id="m-title" placeholder="${UZ.museumName}" />
        <input class="input" id="m-ded" placeholder="${UZ.dedication}" />
        <div id="wedding-fields" class="${state.roomStyle === "WEDDING" ? "" : "hidden"}">
          <input class="input" id="w-bride" placeholder="${UZ.brideName}" />
          <input class="input" id="w-groom" placeholder="${UZ.groomName}" />
          <input class="input" id="w-date" placeholder="${UZ.weddingDate}" />
        </div>
        <label class="btn btn-outline" style="margin-bottom:8px">
          ${UZ.pickPhotos} (${state.selectedPhotos.length}/${maxPhotos})
          <input type="file" id="photo-input" accept="image/*" multiple hidden />
        </label>
        <div class="thumb-grid">
          ${state.selectedPhotos.map((p) => `<img src="${p.previewUrl}" />`).join("")}
        </div>
        <p class="subtitle">${UZ.roomStyle}</p>
        ${Object.entries(UZ.styles).map(([key, info]) => {
          const locked = info.premium && !isPaidActive();
          return `<div class="style-option ${state.roomStyle === key ? "active" : ""}" data-style="${key}" data-locked="${locked}">
            <span>${info.title}</span>
            ${locked ? `<span class="badge">Pullik</span>` : ""}
          </div>`;
        }).join("")}
        <button class="btn" id="publish-btn">${UZ.publishBtn}</button>
      </div>
    `;
    document.getElementById("back-home").onclick = () => { state.page = "home"; render(); };
    document.getElementById("photo-input").onchange = async (e) => {
      const files = Array.from(e.target.files || []).slice(0, maxPhotos - state.selectedPhotos.length);
      for (const f of files) {
        state.selectedPhotos.push(await compressImageInBrowser(f));
      }
      render();
    };
    root.querySelectorAll(".style-option").forEach((el) => {
      el.onclick = () => {
        if (el.dataset.locked === "true") { state.page = "pricing"; render(); return; }
        state.roomStyle = el.dataset.style;
        render();
      };
    });
    document.getElementById("publish-btn").onclick = async () => {
      const title = document.getElementById("m-title").value.trim();
      if (!title || state.selectedPhotos.length < 3) {
        tg?.showAlert("Muzey nomi va kamida 3 ta rasm kiriting");
        return;
      }
      const museumId = crypto.randomUUID();
      const slug = `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-").slice(0, 20)}-${museumId.slice(0, 6)}`;
      const uploadedPhotos = [];
      for (let i = 0; i < state.selectedPhotos.length; i++) {
        const p = state.selectedPhotos[i];
        const path = `${museumId}/${crypto.randomUUID()}.webp`;
        await fetch(`${CONFIG.SUPABASE_URL}/storage/v1/object/museum-photos/${path}`, {
          method: "POST",
          headers: { apikey: CONFIG.SUPABASE_ANON_KEY, Authorization: `Bearer ${CONFIG.SUPABASE_ANON_KEY}`, "Content-Type": "image/webp" },
          body: p.blob,
        });
        uploadedPhotos.push({
          id: crypto.randomUUID(),
          museum_id: museumId,
          sort_order: i,
          image_url: `${CONFIG.SUPABASE_URL}/storage/v1/object/public/museum-photos/${path}`,
          width: p.width,
          height: p.height,
        });
      }
      await sbFetch("/rest/v1/museums", {
        method: "POST",
        headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
        body: JSON.stringify({
          id: museumId,
          slug,
          title,
          dedication: document.getElementById("m-ded").value.trim(),
          room_style: state.roomStyle,
          bride_name: document.getElementById("w-bride")?.value.trim() || "",
          groom_name: document.getElementById("w-groom")?.value.trim() || "",
          wedding_date: document.getElementById("w-date")?.value.trim() || "",
          owner_user_id: state.userId,
          edit_token: crypto.randomUUID(),
          created_at: Date.now(),
        }),
      });
      await sbFetch("/rest/v1/museum_photos", {
        method: "POST",
        headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
        body: JSON.stringify(uploadedPhotos),
      });
      state.selectedPhotos = [];
      state.activeMuseum = { slug, title };
      state.page = "share";
      render();
    };
    return;
  }

  if (state.page === "share") {
    const slug = state.activeMuseum?.slug;
    const shareUrl = `https://t.me/${CONFIG.BOT_USERNAME}?startapp=${encodeURIComponent(slug)}`;
    root.innerHTML = `
      <div class="card">
        <h2 class="title" style="font-size:24px">${UZ.shareTitle}</h2>
        <input class="input" value="${shareUrl}" readonly />
        <button class="btn" id="copy-link">${UZ.copyLink}</button>
        <button class="btn btn-outline" id="tg-share">${UZ.shareTelegram}</button>
        <button class="btn btn-outline" id="open-viewer">${UZ.open3d}</button>
        <button class="btn btn-outline" id="back-home">${UZ.backBtn}</button>
      </div>
    `;
    document.getElementById("copy-link").onclick = () => {
      navigator.clipboard.writeText(shareUrl);
      document.getElementById("copy-link").textContent = UZ.copied;
    };
    document.getElementById("tg-share").onclick = () => {
      const url = `https://t.me/share/url?url=${encodeURIComponent(shareUrl)}&text=${encodeURIComponent(state.activeMuseum?.title || "Muzeycha")}`;
      if (tg?.openTelegramLink) tg.openTelegramLink(url);
      else window.open(url, "_blank");
    };
    document.getElementById("open-viewer").onclick = () => openMuseumBySlug(slug);
    document.getElementById("back-home").onclick = () => { state.page = "home"; render(); };
    return;
  }

  if (state.page === "my") {
    root.innerHTML = `
      <div class="card">
        <button class="btn btn-outline" id="back-home" style="margin-bottom:12px">${UZ.backBtn}</button>
        <h2 class="title" style="font-size:22px">${UZ.myMuseumsBtn}</h2>
        ${state.museums.length === 0 ? `<p class="subtitle">Hali muzey yo'q</p>` : ""}
        ${state.museums.map((m) => `
          <div class="card">
            <strong>${m.title}</strong>
            <p class="subtitle">${m.room_style}</p>
            <button class="btn" data-open="${m.slug}">${UZ.open3d}</button>
          </div>
        `).join("")}
      </div>
    `;
    document.getElementById("back-home").onclick = () => { state.page = "home"; render(); };
    root.querySelectorAll("[data-open]").forEach((b) => {
      b.onclick = () => openMuseumBySlug(b.dataset.open);
    });
    return;
  }

  if (state.page === "pricing") {
    root.innerHTML = `
      <div class="card">
        <button class="btn btn-outline" id="back-home" style="margin-bottom:12px">${UZ.backBtn}</button>
        <h2 class="title" style="font-size:24px">${UZ.pricingTitle}</h2>
        <div class="card"><strong>${UZ.planFree}</strong><p class="subtitle">1 ta muzey • 5 rasm • 3 oddiy uslub</p></div>
        <div class="card"><strong>${UZ.plan6m}</strong><p class="subtitle">20 rasm • Romantik & To'y • Musiqa</p><button class="btn" data-plan="SIX_MONTHS">${UZ.choosePlan}</button></div>
        <div class="card"><strong>${UZ.plan1y}</strong><p class="subtitle">Cheksiz muzey • 20 rasm • Barcha uslublar</p><button class="btn" data-plan="ONE_YEAR">${UZ.choosePlan}</button></div>
      </div>
    `;
    document.getElementById("back-home").onclick = () => { state.page = "home"; render(); };
    root.querySelectorAll("[data-plan]").forEach((b) => {
      b.onclick = async () => {
        const plan = b.dataset.plan;
        const days = plan === "ONE_YEAR" ? CONFIG.PRICING.ONE_YEAR_DAYS : CONFIG.PRICING.SIX_MONTHS_DAYS;
        const now = Date.now();
        const sub = {
          id: state.subscription.id || crypto.randomUUID(),
          user_id: state.userId,
          plan,
          starts_at: now,
          expires_at: now + days * 86400000,
          status: "ACTIVE",
          reminder_3d_sent: false,
        };
        await sbFetch("/rest/v1/subscriptions?on_conflict=user_id", {
          method: "POST",
          headers: { "Content-Type": "application/json", Prefer: "resolution=merge-duplicates,return=minimal" },
          body: JSON.stringify(sub),
        });
        state.subscription = sub;
        tg?.showAlert("Test obuna faollashtirildi!");
        render();
      };
    });
    return;
  }

  if (state.page === "viewer") {
    const m = state.activeMuseum;
    if (!m) {
      root.innerHTML = `<div class="card"><p>Muzey topilmadi</p><button class="btn" id="back-home">${UZ.backBtn}</button></div>`;
      document.getElementById("back-home").onclick = () => { state.page = "home"; render(); };
      return;
    }
    const isPremiumStyle = m.room_style === "ROMANTIC" || m.room_style === "WEDDING";
    if (isPremiumStyle && !isPaidActive()) {
      root.innerHTML = `
        <div class="card">
          <h2 class="title" style="font-size:22px">${UZ.lockedTitle}</h2>
          <p class="subtitle">${UZ.lockedDesc}</p>
          <button class="btn" id="go-pricing">${UZ.pricingBtn}</button>
          <button class="btn btn-outline" id="back-home">${UZ.backBtn}</button>
        </div>
      `;
      document.getElementById("go-pricing").onclick = () => { state.page = "pricing"; render(); };
      document.getElementById("back-home").onclick = () => { state.page = "home"; render(); };
      return;
    }

    const showMusicBtn = isPaidActive() || isPremiumStyle;
    root.innerHTML = `
      <div class="viewer-root">
        <div class="viewer-hud-top">
          <button class="hud-pill" id="viewer-back">${UZ.backBtn}</button>
          <span class="hud-pill">${m.title}</span>
          ${showMusicBtn ? `<button class="hud-pill" id="music-btn">${UZ.musicOff}</button>` : ""}
        </div>
        <div id="three-host" style="position:fixed;inset:0"></div>
        <div class="joystick-zone"><div class="joystick-knob"></div></div>
        <div id="art-sheet" class="artwork-sheet hidden"></div>
        ${m.room_style === "WEDDING" ? `<button class="hud-pill" id="gb-open" style="position:fixed;right:16px;bottom:calc(30px + var(--safe-bottom));z-index:60">${UZ.guestbookBtn}</button>` : ""}
      </div>
    `;

    const musicBtn = document.getElementById("music-btn");
    const audioController = showMusicBtn
      ? createMuseumAudioController({
          candidates: resolveMuseumMusicCandidates(m),
          onStateChange: ({ isLoading, isPlaying }) => {
            if (!musicBtn) return;
            musicBtn.disabled = isLoading;
            musicBtn.textContent = isLoading
              ? UZ.musicLoading
              : isPlaying
                ? UZ.musicOn
                : UZ.musicOff;
          },
          onError: () => {
            if (tg?.showAlert) tg.showAlert(UZ.musicEmptyHint);
            else alert(UZ.musicEmptyHint);
          },
        })
      : null;

    if (musicBtn && audioController) {
      musicBtn.onclick = () => audioController.toggle();
    }

    document.getElementById("viewer-back").onclick = () => {
      audioController?.destroy();
      state.page = "home";
      render();
    };

    const sheet = document.getElementById("art-sheet");
    const disposeThree = mountMuseum3D(document.getElementById("three-host"), m, {
      onSelectArtwork: (p) => {
        sheet.classList.remove("hidden");
        sheet.innerHTML = `<strong>${p.title || "Asar"}</strong><p class="subtitle" style="margin:4px 0 0">${p.caption || ""}</p>`;
      },
      onCloseArtwork: () => sheet.classList.add("hidden"),
    });
    state.cleanupViewer = () => {
      audioController?.destroy();
      disposeThree();
    };

    const gbBtn = document.getElementById("gb-open");
    if (gbBtn) {
      gbBtn.onclick = async () => {
        const wishes = await sbFetch(`/rest/v1/wedding_wishes?museum_id=eq.${m.id}&order=created_at.desc`);
        sheet.classList.remove("hidden");
        sheet.innerHTML = `
          <strong>${UZ.guestbookTitle}</strong>
          <input class="input" id="gb-name" placeholder="${UZ.guestNameHint}" style="margin-top:8px"/>
          <input class="input" id="gb-msg" placeholder="${UZ.guestMsgHint}"/>
          <button class="btn" id="gb-send">${UZ.sendWish}</button>
          <div style="max-height:140px;overflow:auto;margin-top:8px">
            ${(wishes || []).map((w) => `<p style="margin:4px 0"><strong>${w.guest_name}:</strong> ${w.message}</p>`).join("")}
          </div>
        `;
        document.getElementById("gb-send").onclick = async () => {
          const guest_name = document.getElementById("gb-name").value.trim();
          const message = document.getElementById("gb-msg").value.trim();
          if (!guest_name || !message) return;
          await sbFetch("/rest/v1/wedding_wishes", {
            method: "POST",
            headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
            body: JSON.stringify({ id: crypto.randomUUID(), museum_id: m.id, guest_name, message, created_at: Date.now() }),
          });
          sheet.classList.add("hidden");
          tg?.showAlert("Tabrigingiz saqlandi!");
        };
      };
    }
  }
}

applyTelegramSafeAreaAndTheme();
authenticateWithTelegram();
