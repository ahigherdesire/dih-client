/* DIH Client site. The hero is a small seed map: a seeded world, structures placed by
   biome, and A* pathing with a Baritone-style path render. No dependencies. */
(() => {
  'use strict';

  const R = window.DIH_RELEASE || {};
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const $ = (s, el = document) => el.querySelector(s);

  // ------------------------------------------------------------ release wiring
  document.querySelectorAll('[data-release]').forEach(el => {
    const key = el.dataset.release;
    if (key === 'download') { if (R.download) el.href = R.download; }
    else if (key === 'repo') { if (R.repo) el.href = R.repo; }
    else if (R[key] != null) el.textContent = R[key];
  });
  const copyBtn = $('#copy-sha');
  if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(R.sha256 || $('#sha').textContent.trim());
        copyBtn.textContent = 'copied';
      } catch { copyBtn.textContent = 'select it'; }
      setTimeout(() => { copyBtn.textContent = 'copy'; }, 1600);
    });
  }

  // top bar goes solid once past the hero's top
  const bar = $('.bar');
  const onScroll = () => bar.classList.toggle('solid', window.scrollY > 40);
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  // ------------------------------------------------------------ noise
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function hash32(...n) {
    let h = 2166136261 >>> 0;
    for (const v of n) { h ^= v | 0; h = Math.imul(h, 16777619) >>> 0; h ^= h >>> 13; }
    return h >>> 0;
  }
  function perlin(seed) {
    const rnd = mulberry32(seed);
    const perm = new Uint8Array(512);
    const p = Array.from({ length: 256 }, (_, i) => i);
    for (let i = 255; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [p[i], p[j]] = [p[j], p[i]]; }
    for (let i = 0; i < 512; i++) perm[i] = p[i & 255];
    const g = (h, x, y) => {
      switch (h & 7) {
        case 0: return x + y; case 1: return -x + y; case 2: return x - y; case 3: return -x - y;
        case 4: return x; case 5: return -x; case 6: return y; default: return -y;
      }
    };
    const f = t => t * t * t * (t * (t * 6 - 15) + 10);
    return (x, y) => {
      const xi = Math.floor(x), yi = Math.floor(y);
      const X = xi & 255, Y = yi & 255;
      x -= xi; y -= yi;
      const u = f(x), v = f(y);
      const a = perm[X] + Y, b = perm[X + 1] + Y;
      const l1 = g(perm[a], x, y) + u * (g(perm[b], x - 1, y) - g(perm[a], x, y));
      const l2 = g(perm[a + 1], x, y - 1) + u * (g(perm[b + 1], x - 1, y - 1) - g(perm[a + 1], x, y - 1));
      return (l1 + v * (l2 - l1)) * 0.7;
    };
  }
  function fbm(n, x, y, oct) {
    let s = 0, a = 1, fr = 1, t = 0;
    for (let i = 0; i < oct; i++) { s += a * n(x * fr, y * fr); t += a; a *= 0.5; fr *= 2.03; }
    return s / t;
  }

  // ------------------------------------------------------------ biomes
  const B = { DEEP: 0, OCEAN: 1, RIVER: 2, BEACH: 3, PLAINS: 4, FOREST: 5, DARK: 6, TAIGA: 7, SNOW: 8,
    SNOWTAIGA: 9, DESERT: 10, BADLANDS: 11, SAVANNA: 12, JUNGLE: 13, SWAMP: 14, MOUNTAIN: 15, PEAK: 16 };
  const COLORS = [
    [20, 34, 68], [31, 57, 110], [44, 82, 146], [206, 190, 138], [112, 146, 70], [70, 112, 50], [46, 80, 38],
    [76, 104, 78], [196, 206, 212], [160, 178, 176], [212, 188, 124], [170, 92, 58], [158, 152, 84],
    [56, 118, 42], [68, 90, 56], [116, 116, 110], [214, 218, 222]];
  const WATER = b => b <= B.RIVER;
  const LAND = b => b >= B.BEACH;

  // ------------------------------------------------------------ structures (colours match the client)
  const FAM = [
    { id: 'village', name: 'Village', c: '#43A047', shape: 'circle', fp: [9, 7], on: [B.PLAINS, B.DESERT, B.SAVANNA, B.TAIGA, B.SNOW], sp: 34, p: 0.72 },
    { id: 'outpost', name: 'Pillager Outpost', c: '#9E9E9E', shape: 'tri', fp: [3, 3], on: [B.PLAINS, B.DESERT, B.SAVANNA, B.TAIGA, B.SNOW], sp: 50, p: 0.42 },
    { id: 'desert', name: 'Desert Temple', c: '#E0C36A', shape: 'tri', fp: [4, 4], on: [B.DESERT], sp: 28, p: 0.6 },
    { id: 'jungle', name: 'Jungle Temple', c: '#2E7D32', shape: 'tri', fp: [3, 3], on: [B.JUNGLE], sp: 28, p: 0.65 },
    { id: 'hut', name: 'Witch Hut', c: '#6D4C41', shape: 'tri', fp: [2, 2], on: [B.SWAMP], sp: 24, p: 0.7 },
    { id: 'igloo', name: 'Igloo', c: '#B3E5FC', shape: 'circle', fp: [2, 2], on: [B.SNOW, B.SNOWTAIGA], sp: 28, p: 0.6 },
    { id: 'monument', name: 'Ocean Monument', c: '#00ACC1', shape: 'diamond', fp: [14, 14], on: [B.DEEP], sp: 64, p: 0.8, landmark: true },
    { id: 'shipwreck', name: 'Shipwreck', c: '#A1887F', shape: 'square', fp: [3, 2], on: [B.OCEAN, B.BEACH], sp: 26, p: 0.4 },
    { id: 'ruin', name: 'Ocean Ruin', c: '#26C6DA', shape: 'square', fp: [3, 3], on: [B.OCEAN, B.DEEP], sp: 30, p: 0.3 },
    { id: 'portal', name: 'Ruined Portal', c: '#AB47BC', shape: 'square', fp: [2, 2], on: 'land', sp: 40, p: 0.4 },
    { id: 'mansion', name: 'Woodland Mansion', c: '#8D6E63', shape: 'diamond', fp: [16, 16], on: [B.DARK], sp: 120, p: 0.95, landmark: true },
    { id: 'trail', name: 'Trail Ruins', c: '#BCAAA4', shape: 'circle', fp: [4, 4], on: [B.TAIGA, B.JUNGLE, B.FOREST], sp: 48, p: 0.45 },
    { id: 'treasure', name: 'Buried Treasure', c: '#FFD700', shape: 'square', fp: [1, 1], on: [B.BEACH], sp: 22, p: 0.35 },
    { id: 'trial', name: 'Trial Chambers', c: '#E08A2E', shape: 'diamond', fp: [10, 10], on: 'solid', sp: 96, p: 0.55, landmark: true },
    { id: 'stronghold', name: 'Stronghold', c: '#9575CD', shape: 'diamond', fp: [12, 12], ring: true, landmark: true },
  ];

  // ------------------------------------------------------------ world state
  const canvas = $('#world');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const hero = canvas.parentElement;
  const BPC = 4;               // blocks per map cell
  let CELL = 3;                // css px per cell
  let W = 0, H = 0, dpr = 1, seed = 0n;
  let biome, height, cost, terrain, explored, exploredCtx, exploredImg;
  let structures = [];
  const hidden = new Set();
  let player = { x: 0, y: 0, a: -Math.PI / 2 };
  let spawn = { x: 0, y: 0 };
  let path = null, pathPos = 0, goal = null, search = null;
  let revealStart = 0, hover = null, mouse = null, visible = true, frame = 0, demo = true;

  const hud = { seed: $('#hud-seed'), pos: $('#hud-pos'), goal: $('#hud-goal'), path: $('#hud-path') };
  const actionbar = $('#actionbar');
  const tip = $('#tip');

  function randomSeed() {
    const a = new Uint32Array(2);
    (window.crypto || window.msCrypto).getRandomValues(a);
    return BigInt.asIntN(64, (BigInt(a[0]) << 32n) | BigInt(a[1]));
  }

  function blockX(cx) { return Math.round((cx - spawn.x) * BPC); }
  function blockZ(cy) { return Math.round((cy - spawn.y) * BPC); }
  function fmt(n) { return n.toLocaleString('en-US').replace(/,/g, ' '); }

  function generate() {
    const s = Number(BigInt.asUintN(32, seed));
    const nC = perlin(s), nT = perlin(s ^ 0x9e3779b9), nH = perlin(s + 7), nR = perlin(s + 13),
      nM = perlin(s + 29), nD = perlin(s + 31), nJ = perlin(s + 37);
    const N = W * H;
    biome = new Uint8Array(N); height = new Float32Array(N); cost = new Float32Array(N);
    const cxm = W * 0.62, cym = H * 0.5, rmax = Math.hypot(W, H) * 0.5;

    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const i = y * W + x;
        const wx = x - W / 2, wy = y - H / 2;
        const bias = 0.2 * (1 - Math.hypot(x - cxm, y - cym) / rmax) - 0.04;
        const c = fbm(nC, wx / 140, wy / 140, 5) * 1.5 + bias;
        const t = fbm(nT, wx / 230 + 40, wy / 230, 3) * 1.6;
        const m = fbm(nH, wx / 200 - 30, wy / 200 + 11, 3) * 1.6;
        const ridge = 1 - Math.abs(fbm(nM, wx / 58, wy / 58, 4)) * 2.2;
        let b;
        if (c < -0.2) b = B.DEEP;
        else if (c < -0.015) b = B.OCEAN;
        else if (c < 0.02) b = t < -0.38 ? B.SNOW : B.BEACH;
        else if (Math.abs(fbm(nR, wx / 160, wy / 160, 4)) < 0.02 && c < 0.5) b = B.RIVER;
        else if (c > 0.3 && ridge > 0.68) b = ridge > 0.86 && c > 0.4 ? B.PEAK : B.MOUNTAIN;
        else if (t > 0.3) b = m < -0.12 ? (fbm(nD, wx / 90, wy / 90, 3) > 0.12 ? B.BADLANDS : B.DESERT) : m > 0.26 ? B.JUNGLE : B.SAVANNA;
        else if (t < -0.36) b = m > 0.04 ? B.SNOWTAIGA : B.SNOW;
        else if (t < -0.12) b = B.TAIGA;
        else if (m > 0.28 && c < 0.13) b = B.SWAMP;
        else if (m > 0.32) b = B.DARK;
        else if (m > 0.06) b = B.FOREST;
        else b = B.PLAINS;
        biome[i] = b;
        height[i] = c + (b >= B.MOUNTAIN ? ridge * 0.25 : 0);
        cost[i] = b === B.DEEP ? 9 : b === B.OCEAN || b === B.RIVER ? 5 : b === B.PEAK ? 7 : b === B.MOUNTAIN ? 3 : b === B.SWAMP ? 1.6 : 1;
        void nJ;
      }
    }

    // terrain image, hill-shaded, at one pixel per cell
    terrain = document.createElement('canvas');
    terrain.width = W; terrain.height = H;
    const tctx = terrain.getContext('2d');
    const img = tctx.createImageData(W, H);
    const dither = mulberry32(s + 99);
    for (let y = 0; y < H; y++) {
      for (let x = 0; x < W; x++) {
        const i = y * W + x, b = biome[i];
        let col = COLORS[b];
        let k = 1;
        if (WATER(b) && b !== B.RIVER) {
          const d = Math.min(1, Math.max(0, (-height[i] - 0.015) / 0.45));
          k = 1.08 - d * 0.42;
        } else {
          const a = height[Math.max(0, y - 1) * W + Math.max(0, x - 1)];
          const z = height[Math.min(H - 1, y + 1) * W + Math.min(W - 1, x + 1)];
          k = 1 + Math.max(-0.3, Math.min(0.3, (a - z) * 3.4));
        }
        k *= 0.97 + dither() * 0.06;
        k *= 0.86;
        const o = i * 4;
        img.data[o] = Math.min(255, col[0] * k);
        img.data[o + 1] = Math.min(255, col[1] * k);
        img.data[o + 2] = Math.min(255, col[2] * k);
        img.data[o + 3] = 255;
      }
    }
    tctx.putImageData(img, 0, 0);

    explored = document.createElement('canvas');
    explored.width = W; explored.height = H;
    exploredCtx = explored.getContext('2d');
    exploredImg = exploredCtx.createImageData(W, H);

    // spawn: nearest land to a point right of the headline
    spawn = findLand(Math.round(W * 0.66), Math.round(H * 0.52));
    player = { x: spawn.x + 0.5, y: spawn.y + 0.5, a: -Math.PI / 2 };

    placeStructures(s);
    path = null; goal = null; search = null; hover = null;
    revealStart = performance.now();
    buildLegend();
    updateHud();
    flashActionbar();
  }

  function findLand(x0, y0) {
    for (let r = 0; r < Math.max(W, H); r++) {
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
          const x = x0 + dx, y = y0 + dy;
          if (x < 0 || y < 0 || x >= W || y >= H) continue;
          const b = biome[y * W + x];
          if (LAND(b) && b < B.MOUNTAIN) return { x, y };
        }
      }
    }
    return { x: x0, y: y0 };
  }

  function placeStructures(s) {
    structures = [];
    const far = (x, y, min) => structures.every(o => Math.hypot(o.x - x, o.y - y) >= min);
    FAM.forEach((f, fi) => {
      if (f.ring) return;
      for (let ry = -1; ry * f.sp < H + f.sp; ry++) {
        for (let rx = -1; rx * f.sp < W + f.sp; rx++) {
          const rnd = mulberry32(hash32(s, fi, rx, ry));
          if (rnd() > f.p) continue;
          const x = Math.floor(rx * f.sp + rnd() * f.sp * 0.72);
          const y = Math.floor(ry * f.sp + rnd() * f.sp * 0.72);
          if (x < 2 || y < 2 || x >= W - 2 || y >= H - 2) continue;
          const b = biome[y * W + x];
          const ok = f.on === 'land' ? LAND(b) && b !== B.PEAK
            : f.on === 'solid' ? LAND(b) && b !== B.BEACH
            : f.on.includes(b);
          if (!ok || !far(x, y, 6)) continue;
          structures.push({ f, x, y });
        }
      }
    });
    // strongholds sit on a ring around spawn, whatever is above them
    const sf = FAM.find(f => f.ring);
    const rnd = mulberry32(hash32(s, 777));
    const ringR = Math.min(W, H) * 0.42;
    const a0 = rnd() * Math.PI * 2;
    for (let k = 0; k < 3; k++) {
      const a = a0 + k * (Math.PI * 2 / 3);
      const x = Math.round(spawn.x + Math.cos(a) * ringR * (0.9 + rnd() * 0.2));
      const y = Math.round(spawn.y + Math.sin(a) * ringR * (0.9 + rnd() * 0.2));
      if (x > 4 && y > 4 && x < W - 4 && y < H - 4) structures.push({ f: sf, x, y });
    }
    for (const st of structures) st.d = Math.hypot(st.x - spawn.x, st.y - spawn.y);
    structures.sort((a, b) => a.d - b.d);
  }

  // ------------------------------------------------------------ A*
  function astar(sx, sy, gx, gy) {
    const t0 = performance.now();
    const N = W * H;
    const g = new Float32Array(N).fill(Infinity);
    const from = new Int32Array(N).fill(-1);
    const closed = new Uint8Array(N);
    const heap = new Int32Array(N * 2); const hf = new Float32Array(N * 2); let hn = 0;
    const order = [];
    const S = sy * W + sx, G = gy * W + gx;
    const h = i => { const x = i % W, y = (i / W) | 0; const dx = Math.abs(x - gx), dy = Math.abs(y - gy); return (dx + dy + (Math.SQRT2 - 2) * Math.min(dx, dy)) * 1.02; };
    const push = (i, f) => {
      let k = hn++; heap[k] = i; hf[k] = f;
      while (k > 0) { const p = (k - 1) >> 1; if (hf[p] <= hf[k]) break; [heap[p], heap[k]] = [heap[k], heap[p]]; [hf[p], hf[k]] = [hf[k], hf[p]]; k = p; }
    };
    const pop = () => {
      const top = heap[0]; hn--; heap[0] = heap[hn]; hf[0] = hf[hn];
      let k = 0;
      for (;;) { const l = 2 * k + 1, r = l + 1; let m = k;
        if (l < hn && hf[l] < hf[m]) m = l; if (r < hn && hf[r] < hf[m]) m = r;
        if (m === k) break; [heap[m], heap[k]] = [heap[k], heap[m]]; [hf[m], hf[k]] = [hf[k], hf[m]]; k = m; }
      return top;
    };
    g[S] = 0; push(S, h(S));
    const DX = [1, -1, 0, 0, 1, 1, -1, -1], DY = [0, 0, 1, -1, 1, -1, 1, -1];
    while (hn > 0) {
      const i = pop();
      if (closed[i]) continue;
      closed[i] = 1; order.push(i);
      if (i === G) break;
      const x = i % W, y = (i / W) | 0;
      for (let d = 0; d < 8; d++) {
        const nx = x + DX[d], ny = y + DY[d];
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        const j = ny * W + nx;
        if (closed[j]) continue;
        const step = (d < 4 ? 1 : Math.SQRT2) * (cost[i] + cost[j]) * 0.5;
        const ng = g[i] + step;
        if (ng < g[j]) { g[j] = ng; from[j] = i; push(j, ng + h(j)); }
      }
    }
    const pts = [];
    for (let i = G; i !== -1; i = from[i]) pts.push({ x: i % W + 0.5, y: ((i / W) | 0) + 0.5 });
    pts.reverse();
    return { pts, order, ms: performance.now() - t0 };
  }

  function setGoal(cx, cy, label) {
    cx = Math.max(0, Math.min(W - 1, cx | 0)); cy = Math.max(0, Math.min(H - 1, cy | 0));
    const sx = Math.max(0, Math.min(W - 1, player.x | 0)), sy = Math.max(0, Math.min(H - 1, player.y | 0));
    const res = astar(sx, sy, cx, cy);
    goal = { x: cx + 0.5, y: cy + 0.5, label, born: performance.now() };
    let len = 0;
    for (let k = 1; k < res.pts.length; k++) len += Math.hypot(res.pts[k].x - res.pts[k - 1].x, res.pts[k].y - res.pts[k - 1].y);
    path = { pts: res.pts, len };
    pathPos = 0;
    search = { order: res.order, shown: 0, start: performance.now(), ms: res.ms, fade: 0 };
    exploredImg.data.fill(0);
    if (reduceMotion) { search.shown = search.order.length; paintExplored(0, search.order.length); }
    hud.goal.textContent = `${label}  ${fmt(blockX(cx))}, ${fmt(blockZ(cy))}`;
    hud.path.textContent = `${fmt(Math.round(len * BPC))} blocks · ${fmt(res.order.length)} nodes · ${res.ms < 1 ? '<1' : res.ms.toFixed(0)} ms`;
  }

  function paintExplored(from, to) {
    const d = exploredImg.data;
    for (let k = from; k < to; k++) {
      const o = search.order[k] * 4;
      d[o] = 90; d[o + 1] = 140; d[o + 2] = 255; d[o + 3] = 70;
    }
    exploredCtx.putImageData(exploredImg, 0, 0);
  }

  // ------------------------------------------------------------ drawing
  function hexA(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${a})`;
  }
  function drawShape(shape, x, y, r, fill) {
    ctx.beginPath();
    if (shape === 'circle') ctx.arc(x, y, r, 0, Math.PI * 2);
    else if (shape === 'tri') { ctx.moveTo(x, y - r * 1.15); ctx.lineTo(x + r * 1.1, y + r * 0.85); ctx.lineTo(x - r * 1.1, y + r * 0.85); ctx.closePath(); }
    else if (shape === 'diamond') { ctx.moveTo(x, y - r * 1.3); ctx.lineTo(x + r * 1.3, y); ctx.lineTo(x, y + r * 1.3); ctx.lineTo(x - r * 1.3, y); ctx.closePath(); }
    else ctx.rect(x - r, y - r, r * 2, r * 2);
    ctx.fillStyle = fill; ctx.fill();
    ctx.lineWidth = 1.5; ctx.strokeStyle = 'rgba(8,9,12,.95)'; ctx.stroke();
  }

  function draw(now) {
    const cw = W * CELL, ch = H * CELL;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(terrain, 0, 0, cw, ch);

    // region grid every 32 cells (128 blocks)
    ctx.strokeStyle = 'rgba(255,255,255,.035)'; ctx.lineWidth = 1;
    ctx.beginPath();
    for (let x = (spawn.x % 32); x < W; x += 32) { ctx.moveTo(x * CELL + .5, 0); ctx.lineTo(x * CELL + .5, ch); }
    for (let y = (spawn.y % 32); y < H; y += 32) { ctx.moveTo(0, y * CELL + .5); ctx.lineTo(cw, y * CELL + .5); }
    ctx.stroke();

    // A* search frontier
    if (search) {
      if (search.shown < search.order.length) {
        const t = Math.min(1, (now - search.start) / 520);
        const target = Math.floor(search.order.length * t);
        paintExplored(search.shown, target);
        search.shown = target;
      } else if (!search.doneAt) search.doneAt = now;
      const alpha = search.doneAt ? Math.max(0, 1 - (now - search.doneAt) / 1400) : 1;
      if (alpha > 0) { ctx.globalAlpha = alpha; ctx.drawImage(explored, 0, 0, cw, ch); ctx.globalAlpha = 1; }
    }

    // structures (revealed nearest-first like the in-game scan)
    const reveal = reduceMotion ? 1 : Math.min(1, (now - revealStart) / 1700);
    const shownCount = Math.floor(structures.length * reveal);
    for (let k = 0; k < shownCount; k++) {
      const st = structures[k];
      if (hidden.has(st.f.id)) continue;
      const [fw, fh] = st.f.fp;
      const x = (st.x - fw / 2) * CELL, y = (st.y - fh / 2) * CELL;
      ctx.fillStyle = hexA(st.f.c, 0.16);
      ctx.fillRect(x, y, fw * CELL, fh * CELL);
      ctx.strokeStyle = hexA(st.f.c, 0.7); ctx.lineWidth = 1;
      ctx.strokeRect(x + .5, y + .5, fw * CELL - 1, fh * CELL - 1);
    }
    const pathOn = path && search && search.shown >= search.order.length;
    if (pathOn) drawPath(now);
    for (let k = 0; k < shownCount; k++) {
      const st = structures[k];
      if (hidden.has(st.f.id)) continue;
      const px = st.x * CELL, py = st.y * CELL;
      const r = (st.f.landmark ? 5.5 : 4) * (hover === st ? 1.35 : 1);
      drawShape(st.f.shape, px, py, r, st.f.c);
      if (st.f.landmark) {
        ctx.font = '600 11px "JetBrains Mono", monospace';
        ctx.fillStyle = 'rgba(8,9,12,.85)';
        ctx.fillText(st.f.name, px + 11, py + 5);
        ctx.fillStyle = '#ece7dc';
        ctx.fillText(st.f.name, px + 10, py + 4);
      }
    }

    // goal beacon
    if (goal) {
      const gx = goal.x * CELL, gy = goal.y * CELL;
      const t = ((now - goal.born) / 1200) % 1;
      ctx.strokeStyle = `rgba(67,224,122,${0.9 * (1 - t)})`; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(gx, gy, 4 + t * 22, 0, Math.PI * 2); ctx.stroke();
      ctx.strokeStyle = '#43e07a'; ctx.lineWidth = 2;
      ctx.strokeRect(gx - 5, gy - 5, 10, 10);
      ctx.fillStyle = 'rgba(67,224,122,.25)'; ctx.fillRect(gx - 5, gy - 5, 10, 10);
    }

    // player
    const px = player.x * CELL, py = player.y * CELL;
    ctx.beginPath(); ctx.arc(px, py, 15, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(255,255,255,.12)'; ctx.fill();
    ctx.lineWidth = 1; ctx.strokeStyle = 'rgba(255,255,255,.55)'; ctx.stroke();
    ctx.save(); ctx.translate(px, py); ctx.rotate(player.a + Math.PI / 2);
    ctx.beginPath(); ctx.moveTo(0, -10); ctx.lineTo(7.5, 7.5); ctx.lineTo(0, 3.5); ctx.lineTo(-7.5, 7.5); ctx.closePath();
    ctx.fillStyle = '#fff'; ctx.fill(); ctx.lineWidth = 2; ctx.strokeStyle = '#0b0c10'; ctx.stroke();
    ctx.restore();
    ctx.font = '600 11px "JetBrains Mono", monospace';
    ctx.fillStyle = 'rgba(8,9,12,.85)'; ctx.fillText('you', px + 19, py + 5);
    ctx.fillStyle = '#fff'; ctx.fillText('you', px + 18, py + 4);
  }

  function drawPath(now) {
    const pts = path.pts;
    // where along the path the player is
    let idx = 0, acc = 0;
    for (; idx < pts.length - 1; idx++) {
      const seg = Math.hypot(pts[idx + 1].x - pts[idx].x, pts[idx + 1].y - pts[idx].y);
      if (acc + seg > pathPos) break;
      acc += seg;
    }
    if (idx >= pts.length - 1) return;
    // current path: red with a soft glow, like Baritone's colorCurrentPath
    ctx.save();
    ctx.lineJoin = 'round'; ctx.lineCap = 'round';
    ctx.shadowColor = 'rgba(236,31,39,.8)'; ctx.shadowBlur = 8;
    ctx.strokeStyle = '#ec1f27'; ctx.lineWidth = 2.2;
    ctx.beginPath();
    ctx.moveTo(pts[idx + 1].x * CELL, pts[idx + 1].y * CELL);
    for (let k = idx + 2; k < pts.length; k++) ctx.lineTo(pts[k].x * CELL, pts[k].y * CELL);
    ctx.stroke();
    ctx.restore();
    // blocks it will break/place: a few boxes along the path where the terrain is costly
    ctx.strokeStyle = 'rgba(255,197,61,.8)'; ctx.lineWidth = 1;
    for (let k = idx + 1; k < pts.length; k += 3) {
      const c = cost[(pts[k].y | 0) * W + (pts[k].x | 0)];
      if (c >= 3) ctx.strokeRect(pts[k].x * CELL - 3, pts[k].y * CELL - 3, 6, 6);
    }
    // guide line: player -> next node
    const next = pts[Math.min(pts.length - 1, idx + 1)];
    ctx.setLineDash([3, 3]);
    ctx.strokeStyle = 'rgba(255,255,255,.95)'; ctx.lineWidth = 1.2;
    ctx.beginPath(); ctx.moveTo(player.x * CELL, player.y * CELL); ctx.lineTo(next.x * CELL, next.y * CELL); ctx.stroke();
    ctx.setLineDash([]);
    void now;
  }

  // ------------------------------------------------------------ simulation
  let last = performance.now();
  function step(now) {
    const dt = Math.min(0.05, (now - last) / 1000); last = now;
    if (path && search && search.shown >= search.order.length && !reduceMotion) {
      const speed = 18; // cells per second
      pathPos = Math.min(path.len, pathPos + speed * dt);
      let acc = 0;
      const pts = path.pts;
      for (let k = 0; k < pts.length - 1; k++) {
        const seg = Math.hypot(pts[k + 1].x - pts[k].x, pts[k + 1].y - pts[k].y);
        if (acc + seg >= pathPos) {
          const t = seg ? (pathPos - acc) / seg : 0;
          const nx = pts[k].x + (pts[k + 1].x - pts[k].x) * t, ny = pts[k].y + (pts[k + 1].y - pts[k].y) * t;
          const target = Math.atan2(pts[k + 1].y - pts[k].y, pts[k + 1].x - pts[k].x);
          let da = target - player.a; while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
          player.a += da * Math.min(1, dt * 14);
          player.x = nx; player.y = ny;
          break;
        }
        acc += seg;
      }
      if (pathPos >= path.len) {
        const end = path.pts[path.pts.length - 1];
        player.x = end.x; player.y = end.y;
        path = null;
        showActionbar(`Arrived: ${goal ? goal.label : 'goal'}`, 1800);
        if (demo) setTimeout(demoStep, 2600);
      }
      if ((frame & 7) === 0) updateHud();
    }
    frame++;
  }

  function loop(now) {
    if (visible) { step(now); draw(now); }
    requestAnimationFrame(loop);
  }

  function updateHud() {
    hud.seed.textContent = seed.toString();
    hud.pos.textContent = `${fmt(blockX(player.x))}, ${fmt(blockZ(player.y))}`;
  }

  let actionTimer = 0;
  function showActionbar(text, ms) {
    actionbar.textContent = text;
    actionbar.classList.add('show');
    clearTimeout(actionTimer);
    actionTimer = setTimeout(() => actionbar.classList.remove('show'), ms);
  }
  function flashActionbar() {
    if (reduceMotion) return;
    const t0 = performance.now();
    const tick = () => {
      const p = Math.min(1, (performance.now() - t0) / 1700);
      if (p < 1) {
        actionbar.textContent = `Seed map: checking structures… ${Math.round(p * 100)}%`;
        actionbar.classList.add('show');
        requestAnimationFrame(tick);
      } else {
        const fams = new Set(structures.map(s => s.f.id)).size;
        showActionbar(`Seed map: ${structures.length} structures, ${fams} kinds. Click anywhere to path there.`, 3200);
      }
    };
    tick();
  }

  // ------------------------------------------------------------ legend
  function buildLegend() {
    const list = $('#legend-list');
    if (!list) return;
    const counts = new Map();
    for (const s of structures) counts.set(s.f, (counts.get(s.f) || 0) + 1);
    list.textContent = '';
    [...counts.entries()].sort((a, b) => b[1] - a[1]).forEach(([f, n]) => {
      const li = document.createElement('li');
      const b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('aria-pressed', String(!hidden.has(f.id)));
      b.innerHTML = `<span class="sw" style="background:${f.c}"></span><span></span><span class="ct">${n}</span>`;
      b.children[1].textContent = f.name;
      b.addEventListener('click', () => {
        if (hidden.has(f.id)) hidden.delete(f.id); else hidden.add(f.id);
        b.setAttribute('aria-pressed', String(!hidden.has(f.id)));
      });
      li.appendChild(b); list.appendChild(li);
    });
  }

  // ------------------------------------------------------------ input
  function cellAt(ev) {
    const r = canvas.getBoundingClientRect();
    return { x: (ev.clientX - r.left) / CELL, y: (ev.clientY - r.top) / CELL, px: ev.clientX - r.left, py: ev.clientY - r.top };
  }
  function structureNear(c) {
    let best = null, bd = 9 / CELL;
    for (const s of structures) {
      if (hidden.has(s.f.id)) continue;
      const d = Math.hypot(s.x - c.x, s.y - c.y);
      if (d < bd) { bd = d; best = s; }
    }
    return best;
  }
  canvas.addEventListener('pointermove', ev => {
    mouse = cellAt(ev);
    hover = structureNear(mouse);
    if (hover) {
      const dist = Math.round(Math.hypot(hover.x - player.x, hover.y - player.y) * BPC);
      tip.innerHTML = `<div class="tt-name"></div><div class="tt-dim"></div><div class="tt-hint">click: path here</div>`;
      tip.children[0].textContent = hover.f.name;
      tip.children[0].style.color = hover.f.c;
      tip.children[1].textContent = `${fmt(blockX(hover.x))}, ${fmt(blockZ(hover.y))} · ${fmt(dist)} blocks away`;
      tip.hidden = false;
      const hr = hero.getBoundingClientRect(), cr = canvas.getBoundingClientRect();
      const x = mouse.px + (cr.left - hr.left) + 14, y = mouse.py + (cr.top - hr.top) + 14;
      tip.style.left = Math.min(x, hr.width - tip.offsetWidth - 8) + 'px';
      tip.style.top = Math.min(y, hr.height - tip.offsetHeight - 8) + 'px';
      canvas.style.cursor = 'pointer';
    } else {
      tip.hidden = true;
      canvas.style.cursor = '';
    }
  });
  canvas.addEventListener('pointerleave', () => { hover = null; tip.hidden = true; });
  canvas.addEventListener('click', ev => {
    demo = false;
    const c = cellAt(ev);
    const s = structureNear(c);
    if (s) setGoal(s.x, s.y, s.f.name);
    else setGoal(c.x, c.y, 'GoalXZ');
    if (!reduceMotion) showActionbar(`Baritone: pathing to ${s ? s.f.name : 'goal'}`, 1400);
  });
  const reseed = $('#reseed');
  if (reseed) reseed.addEventListener('click', () => {
    seed = randomSeed(); generate();
    if (demo && !reduceMotion) setTimeout(demoStep, 1900);
  });

  // ------------------------------------------------------------ sizing
  function resize() {
    const r = canvas.getBoundingClientRect();
    dpr = Math.min(2, window.devicePixelRatio || 1);
    CELL = r.width > 1800 ? 4 : 3;
    W = Math.ceil(r.width / CELL); H = Math.ceil(r.height / CELL);
    canvas.width = Math.round(r.width * dpr); canvas.height = Math.round(r.height * dpr);
    generate();
  }
  let resizeTimer = 0, lastW = 0;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      const w = canvas.getBoundingClientRect().width;
      if (Math.abs(w - lastW) > 40) { lastW = w; resize(); }
    }, 220);
  });
  new IntersectionObserver(e => { visible = e[0].isIntersecting; }).observe(hero);

  seed = randomSeed();
  lastW = canvas.getBoundingClientRect().width;
  resize();
  // tour a few structures so the page explains itself before anyone clicks
  function demoStep() {
    if (!demo || path) return;
    const here = { x: player.x, y: player.y };
    const pick = structures.filter(s => !hidden.has(s.f.id) && s.x > W * 0.45 && s.x < W - 20 && s.y > 20 && s.y < H - 20)
      .map(s => ({ s, d: Math.hypot(s.x - here.x, s.y - here.y) }))
      .filter(o => o.d > 45 && o.d < 150);
    if (!pick.length) return;
    const o = pick[Math.floor(Math.random() * pick.length)];
    setGoal(o.s.x, o.s.y, o.s.f.name);
  }
  if (!reduceMotion) setTimeout(demoStep, 1900);
  requestAnimationFrame(loop);

  // ------------------------------------------------------------ macro playhead
  const steps = document.querySelectorAll('#macro-steps li');
  const state = $('#macro-state');
  if (steps.length && !reduceMotion) {
    let i = 0;
    const tickMacro = () => {
      steps.forEach((li, k) => { li.classList.toggle('on', k === i); li.classList.toggle('did', k < i); });
      if (i >= steps.length) {
        state.textContent = 'done'; state.classList.add('done');
        setTimeout(() => { i = 0; state.textContent = 'running'; state.classList.remove('done'); tickMacro(); }, 1600);
        return;
      }
      i++;
      setTimeout(tickMacro, 620);
    };
    tickMacro();
  } else if (steps.length) steps[0].classList.add('on');

  // ------------------------------------------------------------ terminal
  const term = $('#term');
  const SCRIPT = [
    ['in', '#goto 1200 -340'],
    ['out', '[Baritone] Pathing to GoalXZ{x=1200, z=-340}'],
    ['out', '[Baritone] <b>Found path</b> in 41 ms: 1 437 nodes, 212 blocks'],
    ['in', '#mine diamond_ore'],
    ['out', '[Baritone] Mining diamond_ore, 14 already in cache'],
    ['in', '#seedmap strongholds'],
    ['out', '[DIH] <b>3 strongholds</b> in ring 1, nearest 1520, -2980'],
    ['in', '#elytra'],
    ['out', '[Baritone] Nether elytra: route planned, 4 810 blocks'],
    ['in', '#ai on'],
    ['out', '[AI] <b>Online</b>. Reading chat, playing through Baritone.'],
  ];
  if (term) {
    if (reduceMotion) {
      term.innerHTML = SCRIPT.map(([k, t]) => `<p class="${k}">${t}</p>`).join('');
    } else {
      let line = 0;
      const typeLine = () => {
        if (line >= SCRIPT.length) { setTimeout(() => { term.textContent = ''; line = 0; typeLine(); }, 4200); return; }
        const [kind, text] = SCRIPT[line++];
        const p = document.createElement('p');
        p.className = kind;
        term.appendChild(p);
        while (term.children.length > 9) term.removeChild(term.firstChild);
        if (kind === 'out') { p.innerHTML = text; setTimeout(typeLine, 520); return; }
        let c = 0;
        p.classList.add('caret');
        const t = setInterval(() => {
          p.textContent = text.slice(0, ++c);
          if (c >= text.length) { clearInterval(t); p.classList.remove('caret'); setTimeout(typeLine, 380); }
        }, 42);
      };
      const io = new IntersectionObserver(e => { if (e[0].isIntersecting) { io.disconnect(); typeLine(); } });
      io.observe(term);
    }
  }

  // ------------------------------------------------------------ packet log
  const plog = $('#plog');
  const PACKETS = [
    ['c2s', 'ServerboundMovePlayerPacket.Pos', 21, 7], ['c2s', 'ServerboundMovePlayerPacket.PosRot', 29, 5],
    ['c2s', 'ServerboundSwingPacket', 2, 3], ['c2s', 'ServerboundUseItemOnPacket', 18, 2],
    ['c2s', 'ServerboundContainerClickPacket', 34, 3], ['c2s', 'ServerboundKeepAlivePacket', 9, 1],
    ['c2s', 'ServerboundPlayerActionPacket', 13, 2], ['c2s', 'ServerboundClientTickEndPacket', 1, 4],
    ['s2c', 'ClientboundSetEntityMotionPacket', 11, 5], ['s2c', 'ClientboundMoveEntityPacket.Pos', 10, 6],
    ['s2c', 'ClientboundContainerSetSlotPacket', 42, 3], ['s2c', 'ClientboundLevelChunkWithLightPacket', 9214, 1],
    ['s2c', 'ClientboundSystemChatPacket', 64, 1], ['s2c', 'ClientboundBlockUpdatePacket', 12, 3],
    ['s2c', 'ClientboundSetTimePacket', 18, 1], ['s2c', 'ClientboundKeepAlivePacket', 9, 1],
  ];
  const bag = PACKETS.flatMap(p => Array(p[3]).fill(p));
  function addPacket() {
    const [dir, name, bytes] = bag[Math.floor(Math.random() * bag.length)];
    const li = document.createElement('li');
    const roll = Math.random();
    if (dir === 'c2s' && name.includes('Swing') && roll < 0.5) li.className = 'x';
    else if (dir === 'c2s' && name.includes('ContainerClick') && roll < 0.6) li.className = 'h';
    const size = Math.max(1, Math.round(bytes * (0.8 + Math.random() * 0.4)));
    li.innerHTML = `<span class="d ${dir}">${dir === 'c2s' ? 'C→S' : 'S→C'}</span><span class="p"></span><span class="b">${size}</span>`;
    li.children[1].textContent = name;
    plog.prepend(li);
    while (plog.children.length > 12) plog.removeChild(plog.lastChild);
  }
  if (plog) {
    for (let k = 0; k < 11; k++) addPacket();
    plog.querySelectorAll('li').forEach(li => li.classList.add('seed'));
    if (!reduceMotion) {
      let timer = 0;
      const io = new IntersectionObserver(e => {
        clearInterval(timer);
        if (e[0].isIntersecting) timer = setInterval(addPacket, 900);
      });
      io.observe(plog);
    }
  }

  // ------------------------------------------------------------ module GUI
  const gui = $('#gui');
  const status = $('#gui-status');
  const searchBox = $('#msearch');
  const MODS = window.DIH_MODULES || {};
  if (gui) {
    const on = new Set(['KillAura', 'Sprint', 'StorageESP', 'Fullbright', 'AutoTotem', 'Waypoints']);
    for (const [cat, mods] of Object.entries(MODS)) {
      const panel = document.createElement('section');
      panel.className = 'panel';
      panel.setAttribute('aria-label', cat);
      panel.innerHTML = `<div class="panel-head"><span></span><span>${mods.length}</span></div><ul></ul><p class="empty" hidden>nothing here</p>`;
      panel.querySelector('.panel-head span').textContent = cat;
      const ul = panel.querySelector('ul');
      for (const m of mods) {
        const li = document.createElement('li');
        const b = document.createElement('button');
        b.type = 'button';
        b.textContent = m.n;
        b.dataset.desc = m.d;
        b.setAttribute('aria-pressed', String(on.has(m.n)));
        b.addEventListener('click', () => b.setAttribute('aria-pressed', String(b.getAttribute('aria-pressed') !== 'true')));
        const show = () => { status.innerHTML = '<b></b> '; status.firstChild.textContent = m.n; status.append(m.d); };
        b.addEventListener('mouseenter', show);
        b.addEventListener('focus', show);
        li.appendChild(b); ul.appendChild(li);
      }
      gui.appendChild(panel);
    }
    const filter = () => {
      const q = searchBox.value.trim().toLowerCase();
      let total = 0;
      gui.querySelectorAll('.panel').forEach(panel => {
        let n = 0;
        panel.querySelectorAll('li').forEach(li => {
          const b = li.firstChild;
          const hit = !q || b.textContent.toLowerCase().includes(q) || b.dataset.desc.toLowerCase().includes(q);
          li.hidden = !hit; if (hit) n++;
        });
        panel.querySelector('.empty').hidden = n > 0;
        total += n;
      });
      status.textContent = q ? `${total} match${total === 1 ? '' : 'es'} for “${searchBox.value.trim()}”` : 'hover a module';
    };
    searchBox.addEventListener('input', filter);
    document.addEventListener('keydown', ev => {
      if (ev.key === '/' && document.activeElement !== searchBox && !/input|textarea/i.test(document.activeElement.tagName)) {
        ev.preventDefault(); searchBox.focus();
      } else if (ev.key === 'Escape' && document.activeElement === searchBox) {
        searchBox.value = ''; filter(); searchBox.blur();
      }
    });
  }
})();
