// Packs many small PNGs into one sheet (one request, one texture upload) and says where each went.
import fs from 'node:fs';
import { PNG } from 'pngjs';

/** @param {{name: string, file: string}[]} items @param {number} width sheet width, a power of two */
export function packAtlas(items, width = 1024) {
  const loaded = items.map((it) => ({ ...it, png: PNG.sync.read(fs.readFileSync(it.file)) }));
  loaded.sort((a, b) => b.png.height - a.png.height || a.name.localeCompare(b.name));
  const pad = 1;
  let x = 0, y = 0, rowH = 0;
  const placed = [];
  for (const it of loaded) {
    const w = it.png.width + pad, h = it.png.height + pad;
    if (x + w > width) { x = 0; y += rowH; rowH = 0; }
    placed.push({ it, x, y });
    x += w; rowH = Math.max(rowH, h);
  }
  const height = Math.max(1, Math.pow(2, Math.ceil(Math.log2(y + rowH))));
  const sheet = new PNG({ width, height });
  const map = {};
  for (const { it, x: px, y: py } of placed) {
    PNG.bitblt(it.png, sheet, 0, 0, it.png.width, it.png.height, px, py);
    map[it.name] = [px, py, it.png.width, it.png.height];
  }
  return { png: PNG.sync.write(sheet), map, width, height };
}
