import test from 'node:test';
import assert from 'node:assert/strict';
import { PNG } from 'pngjs';
import { iconSheet, ICON_NAMES, panorama, REGIONS } from '../tools/native-art.mjs';

test('the icon sheet has one 16x16 cell per icon, each with an outline', () => {
  const png = PNG.sync.read(iconSheet());
  assert.equal(png.width, 16 * ICON_NAMES.length);
  assert.equal(png.height, 16);
  ICON_NAMES.forEach((name, i) => {
    let ink = 0;
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (png.data[(y * png.width + i * 16 + x) * 4 + 3] > 0) ink++;
    assert.ok(ink > 20, name + ' draws something');
  });
});

test('the tower icon matches the mod: a stone keep with the dark outline', () => {
  const png = PNG.sync.read(iconSheet());
  const px = (x, y) => [...png.data.slice((y * png.width + x) * 4, (y * png.width + x) * 4 + 4)];
  assert.deepEqual(px(4, 6), [0x8C, 0x95, 0xA0, 255]); // S, the stone body
  assert.deepEqual(px(11, 8), [0x5A, 0x5E, 0x66, 255]); // SD, the shaded side
  assert.deepEqual(px(2, 6), [0x21, 0x15, 0x10, 255]); // O, the outline to the left of the keep
  assert.equal(px(0, 0)[3], 0);
});

test('every region paints a fully opaque landscape of the asked size, and the regions differ', () => {
  const seen = new Set();
  for (const r of REGIONS) {
    const png = PNG.sync.read(panorama(r, 144, 42));
    assert.equal(png.width, 144);
    assert.equal(png.height, 42);
    for (let i = 3; i < png.data.length; i += 4) assert.equal(png.data[i], 255);
    seen.add(png.data.subarray(0, 64).join());
  }
  assert.equal(seen.size, REGIONS.length);
});
