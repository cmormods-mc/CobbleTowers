import sys, io
sys.path.insert(0, '.')
import vault_spec as S, vault_paint as P, vault_render as R
from PIL import Image
m = S.build()
atlas, rects, size = P.build_atlas(m)
print('atlas', size, 'elements', len(m.els), 'islands', len(rects))
atlas.save('/tmp/atlas.png')
pcs = ('body', 'lid', 'face_plate', 'candy_fill')
a = R.render(m, atlas, rects, size, pcs, R.Pose(0), out=(640, 700))
b = R.render(m, atlas, rects, size, pcs, R.Pose(112), out=(640, 700))
w = Image.new('RGBA', (1280, 700)); w.paste(a, (0, 0)); w.paste(b, (640, 0)); w.save('/tmp/prev.png')
