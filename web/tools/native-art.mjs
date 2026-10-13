// The mod's own procedural pictures (MenuIcons.java, TowerPanorama.java) redrawn here pixel for pixel, so the web pages carry the same art
// as the native screens without a client jar. Keep in step with those two files; the ports are checked by test/art.test.js.
import { PNG } from 'pngjs';

// ---- icons: 16x16, one sheet
const O = 0xFF211510, B = 0xFFB8935A, BL = 0xFFE3BD7F, W = 0xFF6B4A33, WL = 0xFF9A7448, C = 0xFFF0DFBF, S = 0xFF8C95A0, SD = 0xFF5A5E66,
  R = 0xFF9A3A3A, RL = 0xFFC0453A, G = 0xFF6FA55A, U = 0xFF4F7FA0, UL = 0xFF8CB0C8, Y = 0xFFE3A95E;

class Pen {
  constructor(w, h) { this.w = w; this.h = h; this.a = new Uint32Array(w * h); }
  px(x, y, c) { if (x >= 0 && y >= 0 && x < this.w && y < this.h) this.a[y * this.w + x] = c >>> 0; }
  r(x1, y1, x2, y2, c) { for (let y = y1; y <= y2; y++) for (let x = x1; x <= x2; x++) this.px(x, y, c); }
  clr(x1, y1, x2, y2) { this.r(x1, y1, x2, y2, 0); }
  disc(cx, cy, rad, c) { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (Math.hypot(x - cx, y - cy) <= rad) this.px(x, y, c); }
  ring(cx, cy, rad, th, c) { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (Math.abs(Math.hypot(x - cx, y - cy) - rad) <= th) this.px(x, y, c); }
  line(x1, y1, x2, y2, c) {
    const dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
    let err = dx - dy;
    for (;;) {
      this.px(x1, y1, c);
      if (x1 === x2 && y1 === y2) return;
      const e2 = 2 * err;
      if (e2 > -dy) { err -= dy; x1 += sx; }
      if (e2 < dx) { err += dx; y1 += sy; }
    }
  }
  outline() {
    const filled = Array.from({ length: 16 }, (_, x) => Array.from({ length: 16 }, (_, y) => (this.a[y * 16 + x] >>> 24) !== 0));
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      if (filled[x][y]) continue;
      if ((x > 0 && filled[x - 1][y]) || (x < 15 && filled[x + 1][y]) || (y > 0 && filled[x][y - 1]) || (y < 15 && filled[x][y + 1])) this.px(x, y, O);
    }
  }
}

const ICONS = {
  tower: (p) => { p.r(3, 3, 5, 4, S); p.r(7, 3, 8, 4, S); p.r(10, 3, 12, 4, S); p.r(3, 5, 12, 14, S); p.r(10, 5, 12, 14, SD); p.r(6, 7, 7, 9, O); p.r(6, 7, 6, 8, BL); p.r(6, 11, 9, 14, O); p.r(7, 12, 8, 14, W); },
  hourglass: (p) => { p.r(3, 1, 12, 2, B); p.r(3, 13, 12, 14, B); p.r(4, 3, 11, 4, C); p.r(5, 5, 10, 5, C); p.r(6, 6, 9, 6, C); p.r(7, 7, 8, 8, C); p.r(6, 9, 9, 9, C); p.r(5, 10, 10, 10, C); p.r(4, 11, 11, 12, C); p.r(5, 3, 10, 4, Y); p.r(6, 5, 9, 5, Y); p.r(7, 7, 8, 7, Y); p.r(5, 11, 10, 12, Y); p.r(6, 10, 9, 10, Y); },
  book: (p) => { p.r(2, 3, 7, 13, C); p.r(8, 3, 13, 13, C); p.r(7, 2, 8, 14, B); p.r(3, 5, 6, 5, SD); p.r(3, 7, 6, 7, SD); p.r(3, 9, 6, 9, SD); p.r(9, 5, 12, 5, SD); p.r(9, 7, 12, 7, SD); p.r(9, 9, 11, 9, SD); p.r(11, 11, 12, 15, R); },
  stairs: (p) => { p.r(2, 12, 5, 14, B); p.r(6, 9, 9, 14, B); p.r(10, 6, 13, 14, B); p.r(2, 12, 5, 12, BL); p.r(6, 9, 9, 9, BL); p.r(10, 6, 13, 6, BL); p.r(11, 1, 12, 4, Y); p.r(10, 2, 13, 3, Y); },
  echo: (p) => { p.ring(8, 8, 6, 0.6, UL); p.ring(8, 8, 4, 0.6, U); p.ring(8, 8, 2, 0.5, UL); p.disc(8, 8, 1, Y); },
  group: (p) => { p.disc(5, 5, 2, BL); p.r(2, 8, 8, 14, B); p.disc(11, 6, 2, C); p.r(8, 9, 14, 14, R); },
  cards: (p) => { p.r(2, 4, 8, 14, R); p.r(5, 3, 11, 13, B); p.r(8, 2, 14, 12, C); p.r(10, 4, 12, 6, Y); p.r(9, 8, 13, 8, SD); p.r(9, 10, 12, 10, SD); },
  gear: (p) => { p.disc(8, 8, 5, B); p.r(7, 1, 8, 3, B); p.r(7, 13, 8, 15, B); p.r(1, 7, 3, 8, B); p.r(13, 7, 15, 8, B); p.r(3, 3, 4, 4, B); p.r(11, 3, 12, 4, B); p.r(3, 11, 4, 12, B); p.r(11, 11, 12, 12, B); p.disc(8, 8, 2, 0); p.ring(8, 8, 4, 0.5, BL); },
  refresh: (p) => {
    for (let a = 40; a < 340; a += 6) {
      const rad = (a * Math.PI) / 180;
      p.px(Math.round(8 + 5 * Math.cos(rad)), Math.round(8 - 5 * Math.sin(rad)), BL);
      p.px(Math.round(8 + 4 * Math.cos(rad)), Math.round(8 - 4 * Math.sin(rad)), B);
    }
    p.r(11, 1, 14, 2, BL); p.r(12, 3, 14, 4, BL); p.r(13, 5, 14, 6, BL);
  },
  door: (p) => { p.r(4, 2, 11, 14, W); p.r(5, 3, 10, 13, WL); p.r(5, 7, 10, 7, W); p.r(8, 3, 8, 13, W); p.r(9, 8, 10, 9, Y); p.r(2, 14, 13, 15, SD); },
  pouch: (p) => { p.r(5, 3, 10, 5, W); p.r(4, 6, 11, 14, W); p.r(3, 8, 12, 13, W); p.r(5, 6, 6, 13, WL); p.r(5, 5, 10, 6, B); p.r(7, 9, 8, 11, Y); p.r(6, 10, 9, 10, Y); },
  coins: (p) => { p.r(3, 10, 12, 13, B); p.r(3, 10, 12, 10, BL); p.r(4, 6, 13, 9, Y); p.r(4, 6, 13, 6, BL); p.r(6, 2, 11, 5, BL); p.r(8, 3, 9, 4, B); },
  trophy: (p) => { p.r(5, 2, 10, 8, Y); p.r(6, 8, 9, 9, Y); p.r(2, 3, 4, 5, B); p.r(11, 3, 13, 5, B); p.r(7, 10, 8, 11, B); p.r(5, 12, 10, 13, B); p.r(6, 3, 6, 6, BL); },
  lock: (p) => { p.ring(8, 6, 3.4, 0.9, S); p.clr(7, 5, 8, 7); p.r(4, 8, 11, 14, B); p.r(4, 8, 11, 8, BL); p.r(7, 10, 8, 12, O); },
  check: (p) => { p.line(3, 8, 6, 11, G); p.line(3, 9, 6, 12, G); p.line(6, 11, 12, 4, G); p.line(6, 12, 12, 5, G); },
  star: (p) => { p.r(7, 1, 8, 15, Y); p.r(1, 6, 14, 9, Y); p.r(4, 4, 11, 11, Y); p.r(3, 12, 5, 14, Y); p.r(10, 12, 12, 14, Y); p.r(6, 5, 9, 8, BL); },
  gift: (p) => { p.r(3, 7, 12, 14, R); p.r(2, 5, 13, 7, RL); p.r(7, 5, 8, 14, B); p.r(5, 2, 7, 4, B); p.r(8, 2, 10, 4, B); },
  sun: (p) => { p.disc(8, 8, 3.5, Y); p.r(7, 1, 8, 3, BL); p.r(7, 12, 8, 14, BL); p.r(1, 7, 3, 8, BL); p.r(12, 7, 14, 8, BL); p.r(3, 3, 4, 4, BL); p.r(11, 3, 12, 4, BL); p.r(3, 11, 4, 12, BL); p.r(11, 11, 12, 12, BL); },
  calendar: (p) => { p.r(2, 3, 13, 14, C); p.r(2, 3, 13, 6, R); p.r(4, 1, 5, 4, SD); p.r(10, 1, 11, 4, SD); p.r(4, 8, 5, 9, SD); p.r(7, 8, 8, 9, SD); p.r(10, 8, 11, 9, SD); p.r(4, 11, 5, 12, SD); p.r(7, 11, 8, 12, B); },
  flag: (p) => { p.r(3, 2, 4, 14, W); p.r(5, 3, 13, 9, R); p.r(5, 3, 13, 4, RL); p.r(5, 9, 9, 10, R); },
  eye: (p) => { p.r(1, 6, 14, 10, C); p.r(3, 4, 12, 12, C); p.r(5, 3, 10, 13, C); p.disc(8, 8, 3, U); p.disc(8, 8, 1, O); p.px(7, 7, C); },
  scroll: (p) => { p.r(3, 3, 12, 13, C); p.r(2, 2, 13, 4, WL); p.r(2, 12, 13, 14, WL); p.r(5, 6, 10, 6, SD); p.r(5, 8, 10, 8, SD); p.r(5, 10, 8, 10, SD); },
  crown: (p) => { p.r(2, 5, 4, 12, Y); p.r(6, 3, 9, 12, Y); p.r(11, 5, 13, 12, Y); p.r(2, 9, 13, 13, Y); p.r(2, 12, 13, 13, B); p.r(7, 5, 8, 6, RL); p.r(4, 10, 4, 10, R); p.r(11, 10, 11, 10, R); },
  left: (p) => { p.line(10, 3, 5, 8, BL); p.line(10, 4, 5, 9, BL); p.line(5, 8, 10, 13, BL); p.line(5, 7, 10, 12, BL); },
  right: (p) => { p.line(5, 3, 10, 8, BL); p.line(5, 4, 10, 9, BL); p.line(10, 8, 5, 13, BL); p.line(10, 7, 5, 12, BL); },
  coin: (p) => { p.disc(8, 8, 5.5, Y); p.ring(8, 8, 4, 0.5, BL); p.r(7, 5, 8, 11, B); },
};
export const ICON_NAMES = Object.keys(ICONS);

function toPng(pen) {
  const png = new PNG({ width: pen.w, height: pen.h });
  for (let i = 0; i < pen.a.length; i++) {
    const c = pen.a[i];
    png.data[i * 4] = (c >> 16) & 255; png.data[i * 4 + 1] = (c >> 8) & 255; png.data[i * 4 + 2] = c & 255; png.data[i * 4 + 3] = c >>> 24;
  }
  return png;
}

/** All icons in one row of 16x16 cells, in ICON_NAMES order. */
export function iconSheet() {
  const sheet = new Pen(16 * ICON_NAMES.length, 16);
  ICON_NAMES.forEach((name, i) => {
    const p = new Pen(16, 16);
    ICONS[name](p);
    p.outline();
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) sheet.a[y * sheet.w + i * 16 + x] = p.a[y * 16 + x];
  });
  return PNG.sync.write(toPng(sheet));
}

// ---- landscapes
const PAL = {
  tideforge: [0xFF3E5C6E, 0xFF7C8E94, 0xFF6F7F82, 0xFF4F6670, 0xFF2F5878, 0xFFC9B58F, 0xFF8A7656, 0xFF2D3A44],
  rootvale: [0xFF47603F, 0xFF7C9A5E, 0xFF6A8A4C, 0xFF40683F, 0xFF4F6B3A, 0xFFC4B48A, 0xFF857553, 0xFF2D3A2A],
  duskvale: [0xFF3B3348, 0xFF6E5B7E, 0xFF6C5A7A, 0xFF4E4160, 0xFF3F3752, 0xFFB8A98F, 0xFF7C6F66, 0xFF2A2433],
  neutral: [0xFF5A5A62, 0xFFAB9F89, 0xFF978B73, 0xFF7C705B, 0xFF6B6455, 0xFFC9BA96, 0xFF8A7B5C, 0xFF3A322A],
};
export const REGIONS = Object.keys(PAL);

const lerp = (a, b, t) => { let out = 0; for (let s = 24; s >= 0; s -= 8) out |= (Math.round(((a >>> s) & 255) + (((b >>> s) & 255) - ((a >>> s) & 255)) * t) & 255) << s; return out >>> 0; };

/** @param {string} region @param {number} w @param {number} h  the still frame of TowerPanorama.paint (no glint, lamps lit by the seed) */
export function panorama(region, w, h) {
  const g = new Pen(w, h);
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
  return PNG.sync.write(toPng(g));
}
