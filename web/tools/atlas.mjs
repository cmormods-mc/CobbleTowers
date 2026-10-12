// Packs many small PNGs into one sheet (one request, one texture upload) and says where each went.
import fs from 'node:fs';
import { PNG } from 'pngjs';

/** The box the visible pixels fill (the mod's rule: alpha above 127), as [minX, minY, width, height]. */
function inkBox(png) {
  let x0 = png.width, y0 = png.height, x1 = -1, y1 = -1;
  for (let y = 0; y < png.height; y++) for (let x = 0; x < png.width; x++) {
    if (png.data[(y * png.width + x) * 4 + 3] > 127) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  }
  return x1 < 0 ? [0, 0, png.width, png.height] : [x0, y0, x1 - x0 + 1, y1 - y0 + 1];
}

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
    map[it.name] = [px, py, it.png.width, it.png.height, ...inkBox(it.png)];
  }
  return { png: PNG.sync.write(sheet), map, width, height };
}
