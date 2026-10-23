// A picture for each thing the tower vendor sells, from its id: the vendor's goods are data, so anything new falls back to a bag. Pure.
export const vendorGlyph = (id) => {
  const tail = String(id).split(':').pop();
  if (/revive/.test(tail)) return 'heart';
  if (/heal|potion/.test(tail)) return 'heart';
  if (/cure|status|antidote/.test(tail)) return 'flask';
  if (/key|ticket|pass/.test(tail)) return 'scroll';
  return 'bag';
};
