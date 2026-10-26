// TowerPanorama.java redrawn: the still frame of a region's landscape, as RGBA pixels. Used by the page (a canvas per size) and the tests.
const PAL = {
  tideforge: [0xFF3E5C6E, 0xFF7C8E94, 0xFF6F7F82, 0xFF4F6670, 0xFF2F5878, 0xFFC9B58F, 0xFF8A7656, 0xFF2D3A44],
  rootvale: [0xFF47603F, 0xFF7C9A5E, 0xFF6A8A4C, 0xFF40683F, 0xFF4F6B3A, 0xFFC4B48A, 0xFF857553, 0xFF2D3A2A],
  duskvale: [0xFF3B3348, 0xFF6E5B7E, 0xFF6C5A7A, 0xFF4E4160, 0xFF3F3752, 0xFFB8A98F, 0xFF7C6F66, 0xFF2A2433],
  halloween: [0xFF2B1A3F, 0xFFD9702B, 0xFF3A2150, 0xFF241435, 0xFF1E1426, 0xFF6A5A74, 0xFF3F3548, 0xFFFF9A2E],
  neutral: [0xFF5A5A62, 0xFFAB9F89, 0xFF978B73, 0xFF7C705B, 0xFF6B6455, 0xFFC9BA96, 0xFF8A7B5C, 0xFF3A322A],
};
export const REGIONS = Object.keys(PAL);

const lerp = (a, b, t) => { let out = 0; for (let s = 24; s >= 0; s -= 8) out |= (Math.round(((a >>> s) & 255) + (((b >>> s) & 255) - ((a >>> s) & 255)) * t) & 255) << s; return out >>> 0; };

/** @param {string} region @param {number} w @param {number} h  the still frame of TowerPanorama.paint (no glint, lamps lit by the seed) */
export function paintPanorama(region, w, h) {
  const g = { a: new Uint32Array(w * h) };
  const fill = (x1, y1, x2, y2, c) => { for (let y = Math.max(0, y1); y < Math.min(h, y2); y++) for (let x = Math.max(0, x1); x < Math.min(w, x2); x++) g.a[y * w + x] = c >>> 0; };
  const gradient = (x1, y1, x2, y2, top, bottom) => { const rows = Math.max(1, y2 - y1); for (let y = Math.max(0, y1); y < Math.min(h, y2); y++) fill(x1, y, x2, y + 1, lerp(top, bottom, (y - y1 + 0.5) / rows)); };
  const [sky, skyLow, far, near, ground, stone, shade, win] = PAL[region] || PAL.neutral;
  const banner = w >= h * 3;
  const horizon = Math.floor((h * (banner ? 80 : 62)) / 100);
  fill(0, 0, w, Math.floor(horizon / 2), sky);
  gradient(0, Math.floor(horizon / 2), w, horizon, sky, skyLow);
  if (region === 'duskvale') {
    for (let i = 0; i < Math.max(4, Math.floor(w / 18)); i++) {
      const sx = (i * 37 + 11) % Math.max(1, w), sy = 3 + ((i * 13) % Math.max(1, Math.floor(horizon / 2)));
      fill(sx, sy, sx + 1, sy + 1, 0xFFF0DFBF);
    }
  }
  if (region === 'halloween') {
    // a big pale moon and a few stars over the orange glow
    const mr = Math.max(4, Math.floor(h / 8)), mcx = Math.floor(w * (banner ? 0.16 : 0.2)), mcy = Math.floor(horizon * 0.38);
    for (let y = -mr; y <= mr; y++) for (let x = -mr; x <= mr; x++) if (x * x + y * y <= mr * mr) fill(mcx + x, mcy + y, mcx + x + 1, mcy + y + 1, x * x + y * y > (mr - 1) * (mr - 1) ? 0xFFE9C987 : 0xFFF6E3A1);
    for (let i = 0; i < Math.max(4, Math.floor(w / 24)); i++) fill((i * 41 + 7) % Math.max(1, w), 2 + ((i * 17) % Math.max(1, Math.floor(horizon / 3))), (i * 41 + 7) % Math.max(1, w) + 1, 3 + ((i * 17) % Math.max(1, Math.floor(horizon / 3))), 0xFFF0DFBF);
  }
  for (let mx = 0; mx < w; mx += 2) { const peak = 6 + Math.abs((((mx >> 1) * 5) % 26) - 13); fill(mx, horizon - Math.max(1, Math.floor((horizon * peak) / 28)), mx + 2, horizon, far); }
  for (let mx = 0; mx < w; mx += 2) { const peak = 3 + Math.abs(((((mx + 20) >> 1) * 7) % 18) - 9); fill(mx, horizon - Math.max(1, Math.floor((horizon * peak) / 40)), mx + 2, horizon, near); }
  fill(0, horizon, w, h, ground);
  if (region === 'rootvale') {
    for (let tx = 3; tx < w; tx += 11) {
      const th = Math.max(8, Math.floor(h / 3) + ((tx * 3) % 7)), top = horizon - th + 6;
      for (let row = 0; row < Math.floor(th / 3); row++) fill(tx - 1 - row, top + row * 3, tx + 2 + row, top + row * 3 + 3, 0xFF2C5237);
      fill(tx, horizon - 5, tx + 1, horizon + 2, 0xFF3A2A1F);
    }
  } else if (region === 'tideforge') {
    for (let ry = horizon + 3; ry < h - 1; ry += 4) for (let rx = (Math.floor((ry - horizon) / 4) % 2) * 6; rx < w; rx += 12) fill(rx, ry, rx + 4, ry + 1, 0xFF6C9CB6);
  } else if (region === 'halloween') {
    // bare trees with forked branches, and a row of jack-o'-lanterns along the front
    for (let tx = 6; tx < w; tx += 29) {
      const th = Math.max(8, Math.floor(h / 3)), top = horizon - th + 4;
      fill(tx, top, tx + 1, horizon + 2, 0xFF120A18);
      fill(tx - 3, top + 3, tx, top + 4, 0xFF120A18); fill(tx - 3, top + 1, tx - 2, top + 4, 0xFF120A18);
      fill(tx + 1, top + 5, tx + 4, top + 6, 0xFF120A18); fill(tx + 3, top + 3, tx + 4, top + 6, 0xFF120A18);
    }
    const s = Math.max(3, Math.floor(h / 14));
    for (let px = 5; px < w; px += 17) {
      const py = horizon + 3 + ((px * 7) % Math.max(1, Math.floor((h - horizon) / 2)));
      if (py + s >= h) continue;
      fill(px, py, px + s, py + s, 0xFFE8731C); fill(px + Math.floor(s / 2), py - 1, px + Math.floor(s / 2) + 1, py, 0xFF3F6B2A);
      if (s >= 4) { fill(px + 1, py + 1, px + 2, py + 2, 0xFF2A1208); fill(px + s - 2, py + 1, px + s - 1, py + 2, 0xFF2A1208); }
    }
  } else {
    for (let ry = horizon + 3; ry < h; ry += 5) for (let x = 0; x < w; x++) { const i = ry * w + x; g.a[i] = lerp(g.a[i], 0xFF000000, 0.2); }
  }
  const tower = (x, base, tw, th, seed) => {
    const top = base - th;
    fill(x, top, x + tw, base, stone); fill(x + tw - 3, top, x + tw, base, shade); fill(x, top, x + tw, top + 1, 0xFFF0DFBF);
    for (let cx = x; cx < x + tw; cx += 4) fill(cx, top - 2, Math.min(cx + 2, x + tw), top, stone);
    let n = 0;
    for (let wy = top + 5; wy < base - 8; wy += 7) for (let wx = x + 3; wx < x + tw - 5; wx += 6) fill(wx, wy, wx + 3, wy + 4, (n++ + seed) % 4 === 0 ? 0xFFE3BD7F : win);
    fill(x + Math.floor(tw / 2) - 2, base - 6, x + Math.floor(tw / 2) + 2, base, win);
  };
  const cx = banner ? Math.floor((w * 70) / 100) : Math.floor(w / 2);
  const room = horizon - 4;
  const flankH = Math.min(room, Math.max(14, Math.floor((horizon * 52) / 100)));
  const mainH = Math.min(room, Math.max(20, Math.floor((horizon * 85) / 100)));
  const flankW = banner ? Math.max(10, Math.min(Math.floor(w / 9), Math.floor((flankH * 3) / 4))) : Math.max(10, Math.floor(w / 9));
  const mainW = banner ? Math.max(14, Math.min(Math.floor(w / 7), Math.floor((mainH * 3) / 4))) : Math.max(14, Math.floor(w / 7));
  const gap = banner ? 4 : 7 * Math.max(1, Math.floor(w / 60));
  tower(cx - (mainW >> 1) - gap - flankW, horizon, flankW, flankH, 0);
  tower(cx + (mainW >> 1) + gap, horizon, flankW, flankH, 1);
  tower(cx - (mainW >> 1), horizon, mainW, mainH, 2);
  const out = new Uint8ClampedArray(w * h * 4);
  for (let i = 0; i < g.a.length; i++) { const c = g.a[i]; out[i * 4] = (c >> 16) & 255; out[i * 4 + 1] = (c >> 8) & 255; out[i * 4 + 2] = c & 255; out[i * 4 + 3] = c >>> 24; }
  return out;
}
