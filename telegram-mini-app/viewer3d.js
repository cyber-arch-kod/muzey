export function mountMuseum3D(container, museum, callbacks) {
  const THREE = window.THREE;
  const isLowPower = (navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 4) ||
                     (navigator.deviceMemory && navigator.deviceMemory <= 3);

  const THEMES = {
    CLASSIC_WHITE: { bg: 0xECE5DC, wall: 0xF7F3EC, floor: 0xE6DDD0, ceil: 0xFAF7F2, frame: 0xB08D57, spot: 0xFFF3DE, amb: 0.72 },
    WARM_WOOD: { bg: 0x2B2017, wall: 0x8B5E3C, floor: 0xCBB195, ceil: 0xE6D8C7, frame: 0x3A2517, spot: 0xFFDCA8, amb: 0.65 },
    MODERN_DARK: { bg: 0x161514, wall: 0x262422, floor: 0x2D2B29, ceil: 0x1B1A18, frame: 0xD1B88E, spot: 0xFFF2DB, amb: 0.45 },
    ROMANTIC: { bg: 0xF5DDE1, wall: 0xF8E7EA, floor: 0xF3DFD6, ceil: 0xFCF2F0, frame: 0xD4AF7A, spot: 0xFFE0C8, amb: 0.78, romantic: true },
    WEDDING: { bg: 0xF6F2EB, wall: 0xFCFBF8, floor: 0xF1E8DA, ceil: 0xFFFFFF, frame: 0xC9A25E, spot: 0xFFF4DC, amb: 0.80, wedding: true },
  };

  const theme = THEMES[museum.room_style] || THEMES.CLASSIC_WHITE;
  const renderer = new THREE.WebGLRenderer({ antialias: !isLowPower, powerPreference: "high-performance" });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.shadowMap.enabled = !isLowPower;
  container.appendChild(renderer.domElement);

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(theme.bg);
  scene.fog = theme.romantic ? new THREE.FogExp2(theme.bg, 0.045) : new THREE.Fog(theme.bg, 12, 34);

  const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 100);
  camera.rotation.order = "YXZ";

  const photos = museum.museum_photos || [];
  const count = Math.max(photos.length, 3);
  const roomW = Math.max(8.5, Math.min(18, Math.ceil(count / 3) * 3.1 + 2.4));
  const roomD = Math.max(9.0, Math.min(20, Math.ceil(count / 2.2) * 3.1 + 2.6));
  const roomH = 4.2;

  scene.add(new THREE.HemisphereLight(0xFFF9F0, theme.bg, theme.amb));

  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(roomW, roomD),
    new THREE.MeshStandardMaterial({ color: theme.floor, roughness: 0.75 })
  );
  floor.rotation.x = -Math.PI / 2;
  scene.add(floor);

  const ceil = new THREE.Mesh(
    new THREE.PlaneGeometry(roomW, roomD),
    new THREE.MeshStandardMaterial({ color: theme.ceil, roughness: 0.9 })
  );
  ceil.rotation.x = Math.PI / 2;
  ceil.position.y = roomH;
  scene.add(ceil);

  const wallMat = new THREE.MeshStandardMaterial({ color: theme.wall, roughness: 0.85 });
  const addWall = (w, x, z, ry) => {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, roomH), wallMat);
    m.position.set(x, roomH / 2, z);
    m.rotation.y = ry;
    scene.add(m);
  };
  addWall(roomW, 0, -roomD / 2, 0);
  addWall(roomW, 0, roomD / 2, Math.PI);
  addWall(roomD, -roomW / 2, 0, Math.PI / 2);
  addWall(roomD, roomW / 2, 0, -Math.PI / 2);

  // Wedding entrance sign
  if (theme.wedding) {
    const c = document.createElement("canvas");
    c.width = 768; c.height = 384;
    const ctx = c.getContext("2d");
    ctx.fillStyle = "#FCF9F3";
    ctx.fillRect(0, 0, 768, 384);
    ctx.strokeStyle = "#C9A25E";
    ctx.lineWidth = 8;
    ctx.strokeRect(16, 16, 736, 352);
    ctx.fillStyle = "#2B241D";
    ctx.font = "italic bold 52px Georgia, serif";
    ctx.textAlign = "center";
    const names = (museum.bride_name && museum.groom_name)
      ? `${museum.bride_name} & ${museum.groom_name}`
      : museum.title;
    ctx.fillText(names, 384, 195);
    if (museum.wedding_date) {
      ctx.fillStyle = "#8C5A3C";
      ctx.font = "30px Georgia, serif";
      ctx.fillText(museum.wedding_date, 384, 275);
    }
    const signTex = new THREE.CanvasTexture(c);
    const sign = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 1.1), new THREE.MeshBasicMaterial({ map: signTex }));
    sign.position.set(0, 2.95, -roomD / 2 + 0.1);
    scene.add(sign);
  }

  // Particles for Romantic / Wedding
  const particles = [];
  if (theme.romantic || theme.wedding) {
    const pCount = isLowPower ? 30 : 85;
    const pGeo = new THREE.PlaneGeometry(0.11, 0.11);
    const colors = theme.romantic ? [0xF7B7C5, 0xE88AA0, 0xFCE0E6] : [0xD4AF37, 0xFFFDF7, 0xE6C79C];
    const mats = colors.map((col) => new THREE.MeshBasicMaterial({ color: col, side: THREE.DoubleSide, transparent: true, opacity: 0.85 }));
    for (let i = 0; i < pCount; i++) {
      const m = new THREE.Mesh(pGeo, mats[i % mats.length]);
      m.position.set((Math.random() - 0.5) * (roomW - 1), 0.5 + Math.random() * 3.2, (Math.random() - 0.5) * (roomD - 1));
      m.userData = { speed: -(0.14 + Math.random() * 0.16), phase: Math.random() * 6.28 };
      scene.add(m);
      particles.push(m);
    }
  }

  // Place paintings proportionally
  const wallDefs = [
    { axis: "z", fixed: -roomD / 2 + 0.06, span: roomW - 1.6, ry: 0, normal: new THREE.Vector3(0, 0, 1) },
    { axis: "x", fixed: -roomW / 2 + 0.06, span: roomD - 1.6, ry: Math.PI / 2, normal: new THREE.Vector3(1, 0, 0) },
    { axis: "x", fixed: roomW / 2 - 0.06, span: roomD - 1.6, ry: -Math.PI / 2, normal: new THREE.Vector3(-1, 0, 0) },
  ];
  const artworkMeshes = [];
  const artworkItems = [];
  const loader = new THREE.TextureLoader();
  loader.setCrossOrigin("anonymous");
  const frameMat = new THREE.MeshStandardMaterial({ color: theme.frame, roughness: 0.35, metalness: 0.45 });

  photos.forEach((p, idx) => {
    const wIdx = idx % wallDefs.length;
    const groupIdx = Math.floor(idx / wallDefs.length);
    const totalOnWall = Math.ceil((photos.length - wIdx) / wallDefs.length);
    const def = wallDefs[wIdx];
    const step = def.span / Math.max(totalOnWall, 1);
    const offset = -def.span / 2 + step * (groupIdx + 0.5);
    const pos = def.axis === "z" ? new THREE.Vector3(offset, 1.68, def.fixed) : new THREE.Vector3(def.fixed, 1.68, offset);

    const aspect = p.height ? Math.max(0.55, Math.min(1.85, p.width / p.height)) : 1.33;
    const h = aspect >= 1 ? 1.2 : 1.45;
    const w = h * aspect;

    const grp = new THREE.Group();
    grp.position.copy(pos);
    grp.rotation.y = def.ry;

    grp.add(new THREE.Mesh(new THREE.BoxGeometry(w + 0.2, h + 0.2, 0.06), frameMat));
    const artMat = new THREE.MeshStandardMaterial({ color: 0xDDD4C8, roughness: 0.55 });
    const artMesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), artMat);
    artMesh.position.z = 0.04;
    artMesh.userData = { index: idx };
    grp.add(artMesh);

    const spot = new THREE.SpotLight(theme.spot, 1.25, 7, Math.PI / 4, 0.45);
    spot.position.copy(pos).addScaledVector(def.normal, 1.3);
    spot.position.y = roomH - 0.3;
    spot.target = artMesh;
    scene.add(spot);
    scene.add(grp);

    artworkMeshes.push(artMesh);
    artworkItems.push({ p, pos, normal: def.normal, w, h });

    // Lazy load texture
    setTimeout(() => {
      loader.load(p.image_url, (tex) => {
        artMat.map = tex;
        artMat.color.setHex(0xffffff);
        artMat.needsUpdate = true;
      });
    }, idx * 90);
  });

  camera.position.set(0, 1.65, Math.min(2.6, roomD / 2 - 1.4));
  let yaw = 0, pitch = 0, focused = -1;
  let joyVec = { x: 0, y: 0 };
  let dragId = null, lastTouch = { x: 0, y: 0 }, moved = 0;

  const joyZone = container.parentElement.querySelector(".joystick-zone");
  const joyKnob = container.parentElement.querySelector(".joystick-knob");
  if (joyZone) {
    joyZone.addEventListener("touchstart", (e) => e.stopPropagation(), { passive: true });
    joyZone.addEventListener("touchmove", (e) => {
      e.preventDefault();
      e.stopPropagation();
      const t = e.changedTouches[0];
      const r = joyZone.getBoundingClientRect();
      let dx = t.clientX - (r.left + r.width / 2);
      let dy = t.clientY - (r.top + r.height / 2);
      const d = Math.hypot(dx, dy);
      if (d > 34) { dx = (dx / d) * 34; dy = (dy / d) * 34; }
      joyKnob.style.transform = `translate(${dx}px, ${dy}px)`;
      joyVec = { x: dx / 34, y: dy / 34 };
    }, { passive: false });
    joyZone.addEventListener("touchend", () => {
      joyKnob.style.transform = "translate(0,0)";
      joyVec = { x: 0, y: 0 };
    });
  }

  const raycaster = new THREE.Raycaster();
  const handleTap = (cx, cy) => {
    const ndc = new THREE.Vector2((cx / window.innerWidth) * 2 - 1, -(cy / window.innerHeight) * 2 + 1);
    raycaster.setFromCamera(ndc, camera);
    const hits = raycaster.intersectObjects(artworkMeshes, false);
    if (hits.length > 0) {
      const idx = hits[0].object.userData.index;
      if (focused === idx) {
        focused = -1;
        callbacks.onCloseArtwork();
      } else {
        focused = idx;
        const item = artworkItems[idx];
        camera.position.copy(item.pos).addScaledVector(item.normal, Math.max(1.45, item.w * 1.1));
        const lv = item.pos.clone().sub(camera.position).normalize();
        yaw = Math.atan2(-lv.x, -lv.z);
        pitch = Math.asin(lv.y);
        callbacks.onSelectArtwork(item.p);
      }
    } else if (focused !== -1) {
      focused = -1;
      callbacks.onCloseArtwork();
    }
  };

  renderer.domElement.addEventListener("touchstart", (e) => {
    const t = e.changedTouches[0];
    dragId = t.identifier;
    lastTouch = { x: t.clientX, y: t.clientY };
    moved = 0;
  }, { passive: true });
  renderer.domElement.addEventListener("touchmove", (e) => {
    for (const t of e.changedTouches) {
      if (t.identifier === dragId && focused === -1) {
        const dx = t.clientX - lastTouch.x;
        const dy = t.clientY - lastTouch.y;
        moved += Math.hypot(dx, dy);
        yaw -= dx * 0.004;
        pitch = Math.max(-1.0, Math.min(1.0, pitch - dy * 0.0036));
        lastTouch = { x: t.clientX, y: t.clientY };
      }
    }
  }, { passive: true });
  renderer.domElement.addEventListener("touchend", (e) => {
    const t = e.changedTouches[0];
    if (moved < 12) handleTap(t.clientX, t.clientY);
    dragId = null;
  });

  let rafId = 0;
  const clock = new THREE.Clock();
  let tAccum = 0;
  function tick() {
    rafId = requestAnimationFrame(tick);
    const dt = Math.min(clock.getDelta(), 0.05);
    tAccum += dt;
    particles.forEach((p) => {
      p.position.y += p.userData.speed * dt;
      p.position.x += Math.sin(tAccum + p.userData.phase) * 0.18 * dt;
      if (p.position.y < 0.2) p.position.y = roomH - 0.3;
    });
    if (focused === -1) {
      camera.rotation.set(pitch, yaw, 0, "YXZ");
      const fwd = -joyVec.y, str = joyVec.x;
      if (Math.abs(fwd) > 0.03 || Math.abs(str) > 0.03) {
        const dx = (-Math.sin(yaw) * fwd + Math.cos(yaw) * str) * 3.0 * dt;
        const dz = (-Math.cos(yaw) * fwd - Math.sin(yaw) * str) * 3.0 * dt;
        camera.position.x = Math.max(-roomW / 2 + 0.8, Math.min(roomW / 2 - 0.8, camera.position.x + dx));
        camera.position.z = Math.max(-roomD / 2 + 0.8, Math.min(roomD / 2 - 0.8, camera.position.z + dz));
      }
    }
    renderer.render(scene, camera);
  }
  tick();

  return () => {
    cancelAnimationFrame(rafId);
    renderer.dispose();
  };
}
