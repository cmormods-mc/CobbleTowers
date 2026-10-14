// The mod's own procedural pictures (MenuIcons.java, TowerPanorama.java) redrawn here pixel for pixel, so the web pages carry the same art
// as the native screens without a client jar. Keep in step with those two files; the ports are checked by test/art.test.js.
import { PNG } from 'pngjs';
import { SPECS } from '../src/itemart.js';

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

// ---- item pictures: a few shapes in many colours, for items the page has no art of its own for (src/itemart.js picks one)
const shade = (c, k) => { const f = (s) => Math.max(0, Math.min(255, Math.round(((c >>> s) & 255) * k))); return ((0xFF << 24) | (f(16) << 16) | (f(8) << 8) | f(0)) >>> 0; };
const dk = (c) => shade(c, 0.7), lt = (c) => shade(c, 1.25);
const SHAPES = {
  candy: (p, c) => { p.r(5, 5, 10, 10, c); p.r(4, 6, 11, 9, c); p.r(5, 5, 7, 6, lt(c)); p.r(5, 10, 10, 10, dk(c)); p.r(4, 9, 11, 9, dk(c)); p.r(2, 6, 3, 9, C); p.r(12, 6, 13, 9, C); },
  ball: (p, c) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d > 6) continue; p.px(x, y, Math.abs(y - 7.5) < 1 ? O : y < 7.5 ? c : C); } p.disc(7.5, 7.5, 2.2, O); p.disc(7.5, 7.5, 1.4, C); p.r(4, 3, 6, 4, lt(c)); },
  bottle: (p, c) => { p.r(6, 2, 9, 3, WL); p.r(7, 4, 8, 5, C); p.r(5, 6, 10, 13, c); p.r(4, 8, 11, 12, c); p.r(5, 7, 5, 11, lt(c)); p.r(5, 13, 10, 13, dk(c)); p.r(4, 12, 11, 12, dk(c)); },
  berry: (p, c) => { p.disc(8, 9, 4.8, c); p.disc(6, 7, 1.5, lt(c)); p.r(8, 2, 10, 3, G); p.r(7, 3, 8, 5, G); },
  nut: (p, c) => { p.disc(8, 9, 4.8, c); p.r(5, 3, 10, 5, W); p.r(7, 2, 8, 3, W); p.disc(6, 8, 1.4, lt(c)); },
  gem: (p, c) => { p.r(5, 3, 10, 4, lt(c)); p.r(3, 5, 12, 7, c); p.r(4, 8, 11, 8, c); p.r(5, 9, 10, 9, dk(c)); p.r(6, 10, 9, 11, dk(c)); p.r(7, 12, 8, 12, dk(c)); p.r(4, 5, 6, 5, lt(c)); },
  apple: (p, c) => { p.disc(8, 9, 4.8, c); p.disc(5.5, 6.5, 1.4, lt(c)); p.r(8, 2, 8, 4, W); p.r(9, 3, 11, 4, G); },
  pack: (p, c) => { p.r(4, 2, 11, 14, c); p.r(4, 2, 11, 3, lt(c)); p.r(4, 13, 11, 14, lt(c)); p.r(7, 6, 8, 10, C); p.r(6, 7, 9, 9, C); },
  ticket: (p, c) => { p.r(2, 5, 13, 11, c); p.px(2, 5, 0); p.px(13, 5, 0); p.px(2, 11, 0); p.px(13, 11, 0); p.r(4, 7, 11, 8, R); p.r(4, 9, 8, 9, SD); },
  banner: (p, c) => { p.r(3, 1, 12, 2, WL); p.r(4, 3, 11, 10, c); p.r(5, 11, 10, 12, c); p.r(6, 13, 9, 13, c); p.r(4, 6, 11, 7, C); },
};
for (const [name, [shape, colour]] of Object.entries(SPECS)) ICONS[name] = (p) => SHAPES[shape](p, colour);
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

// ---- landscapes (drawn in the page; see src/art/panorama.js). The PNG form is for tests and previews.
import { paintPanorama, REGIONS } from '../src/art/panorama.js';
export { REGIONS };
export function panorama(region, w, h) {
  const png = new PNG({ width: w, height: h });
  png.data = Buffer.from(paintPanorama(region, w, h));
  return PNG.sync.write(png);
}
