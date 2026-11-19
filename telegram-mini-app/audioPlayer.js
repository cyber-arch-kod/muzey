import { CONFIG } from "./config.js";

/**
 * Xona uslubi yoki muzey ma'lumotlariga ko'ra dinamik musiqa manzillarini tayyorlaydi.
 */
export function resolveMuseumMusicCandidates(museum, overrideUrl = null) {
  const candidates = [];
  if (overrideUrl) candidates.push(overrideUrl);
  if (museum?.music_url) candidates.push(museum.music_url);

  const styleTrack = CONFIG.MUSIC?.TRACKS_BY_STYLE?.[museum?.room_style];
  if (styleTrack) candidates.push(styleTrack);

  if (CONFIG.MUSIC?.DEFAULT_URL) {
    candidates.push(CONFIG.MUSIC.DEFAULT_URL);
  }

  // Agar Supabase sozlangan bo'lsa, public 'museum-audio' bucketdan ham izlaydi
  if (CONFIG.SUPABASE_URL && !CONFIG.SUPABASE_URL.includes("YOUR_PROJECT_REF")) {
    const fileByStyle =
      museum?.room_style === "ROMANTIC"
        ? "romantic.mp3"
        : museum?.room_style === "WEDDING"
          ? "wedding.mp3"
          : "museum_bg.mp3";
    candidates.push(`${CONFIG.SUPABASE_URL}/storage/v1/object/public/museum-audio/${fileByStyle}`);
    candidates.push(`${CONFIG.SUPABASE_URL}/storage/v1/object/public/museum-audio/museum_bg.mp3`);
  }

  return [...new Set(candidates.filter(Boolean))];
}

/**
 * Web Audio API (AudioContext + GainNode + AudioBuffer) asosidagi 3D viewer musiqa boshqaruvchisi.
 */
export function createMuseumAudioController({
  candidates = [],
  targetVolume = CONFIG.MUSIC?.TARGET_VOLUME ?? 0.55,
  fadeSeconds = CONFIG.MUSIC?.FADE_SECONDS ?? 0.8,
  onStateChange = () => {},
  onError = () => {},
} = {}) {
  const AudioCtx = window.AudioContext || window.webkitAudioContext;

  let audioCtx = null;
  let gainNode = null;
  let sourceNode = null;
  let decodedBuffer = null;
  let activeUrl = null;
  let currentCandidates = [...candidates];

  let isLoading = false;
  let isPlaying = false;
  let isDestroyed = false;
  let startedAt = 0;
  let pauseOffset = 0;
  let stopFadeTimer = null;

  const bufferCache = new Map();

  function emitState() {
    onStateChange({ isLoading, isPlaying, activeUrl });
  }

  function ensureContext() {
    if (!AudioCtx) {
      throw new Error("AudioContext qo'llab-quvvatlanmaydi");
    }
    if (!audioCtx) {
      audioCtx = new AudioCtx();
      gainNode = audioCtx.createGain();
      gainNode.gain.setValueAtTime(0, audioCtx.currentTime);
      gainNode.connect(audioCtx.destination);
    }
    return audioCtx;
  }

  async function fetchAndDecode(url) {
    if (bufferCache.has(url)) {
      return bufferCache.get(url);
    }
    const res = await fetch(url, { mode: "cors" });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const contentType = res.headers.get("content-type") || "";
    if (contentType.includes("text/html")) {
      throw new Error("Audio o'rniga HTML qaytdi");
    }
    const arrayBuf = await res.arrayBuffer();
    const ctx = ensureContext();
    const audioBuf = await ctx.decodeAudioData(arrayBuf);
    bufferCache.set(url, audioBuf);
    return audioBuf;
  }

  async function loadDynamicTrack(urls = currentCandidates) {
    if (decodedBuffer && activeUrl && urls.includes(activeUrl)) {
      return decodedBuffer;
    }
    isLoading = true;
    emitState();

    let lastErr = null;
    for (const url of urls) {
      try {
        const buf = await fetchAndDecode(url);
        if (isDestroyed) return null;
        decodedBuffer = buf;
        activeUrl = url;
        isLoading = false;
        emitState();
        return buf;
      } catch (err) {
        lastErr = err;
      }
    }

    isLoading = false;
    emitState();
    throw lastErr || new Error("Musiqa fayli topilmadi");
  }

  function startSourceFromOffset() {
    if (!audioCtx || !decodedBuffer || isDestroyed) return;
    if (stopFadeTimer) {
      clearTimeout(stopFadeTimer);
      stopFadeTimer = null;
    }
    if (sourceNode) {
      try { sourceNode.stop(); } catch (_) {}
      try { sourceNode.disconnect(); } catch (_) {}
    }

    const source = audioCtx.createBufferSource();
    source.buffer = decodedBuffer;
    source.loop = true;
    source.connect(gainNode);

    const safeOffset = decodedBuffer.duration > 0 ? pauseOffset % decodedBuffer.duration : 0;
    const now = audioCtx.currentTime;
    gainNode.gain.cancelScheduledValues(now);
    gainNode.gain.setValueAtTime(Math.max(0.0001, gainNode.gain.value), now);
    gainNode.gain.linearRampToValueAtTime(targetVolume, now + fadeSeconds);

    source.start(0, safeOffset);
    startedAt = now - safeOffset;
    sourceNode = source;
    isPlaying = true;
    emitState();
  }

  async function play() {
    if (isDestroyed || isLoading || isPlaying) return;
    try {
      const ctx = ensureContext();
      if (ctx.state === "suspended") {
        await ctx.resume();
      }
      if (!decodedBuffer) {
        await loadDynamicTrack(currentCandidates);
      }
      if (!isDestroyed && decodedBuffer) {
        startSourceFromOffset();
      }
    } catch (err) {
      isPlaying = false;
      isLoading = false;
      emitState();
      onError(err);
    }
  }

  function pause() {
    if (!isPlaying || !audioCtx || !gainNode) return;
    const now = audioCtx.currentTime;
    if (decodedBuffer && decodedBuffer.duration > 0) {
      pauseOffset = (now - startedAt) % decodedBuffer.duration;
    }

    gainNode.gain.cancelScheduledValues(now);
    gainNode.gain.setValueAtTime(gainNode.gain.value, now);
    gainNode.gain.linearRampToValueAtTime(0.0001, now + Math.min(fadeSeconds, 0.45));

    const stoppingSource = sourceNode;
    sourceNode = null;
    isPlaying = false;
    emitState();

    stopFadeTimer = setTimeout(() => {
      try { stoppingSource?.stop(); } catch (_) {}
      try { stoppingSource?.disconnect(); } catch (_) {}
    }, Math.min(fadeSeconds, 0.45) * 1000 + 30);
  }

  async function toggle() {
    if (isLoading || isDestroyed) return;
    if (isPlaying) {
      pause();
    } else {
      await play();
    }
  }

  async function setTrack(newUrl, autoPlay = true) {
    pause();
    decodedBuffer = null;
    activeUrl = null;
    pauseOffset = 0;
    currentCandidates = [newUrl, ...candidates].filter(Boolean);
    if (autoPlay) {
      await play();
    }
  }

  function destroy() {
    isDestroyed = true;
    if (stopFadeTimer) {
      clearTimeout(stopFadeTimer);
      stopFadeTimer = null;
    }
    if (sourceNode) {
      try { sourceNode.stop(); } catch (_) {}
      try { sourceNode.disconnect(); } catch (_) {}
      sourceNode = null;
    }
    if (gainNode) {
      try { gainNode.disconnect(); } catch (_) {}
      gainNode = null;
    }
    if (audioCtx) {
      try { audioCtx.close(); } catch (_) {}
      audioCtx = null;
    }
    isPlaying = false;
    isLoading = false;
  }

  return {
    play,
    pause,
    toggle,
    setTrack,
    loadDynamicTrack,
    destroy,
    getState: () => ({ isLoading, isPlaying, activeUrl }),
  };
}
