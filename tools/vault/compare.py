import sys
sys.path.insert(0, '.')
import vault_spec as S, vault_paint as P, vault_render as R
from PIL import Image
REF = '/root/.claude/uploads/2df97ef2-20a0-5a98-ab01-d5146231f20a/3c5274f3-image.png'
m = S.build(); atlas, rects, size = P.build_atlas(m)
ref = Image.open(REF).convert('RGBA')
pcs = ('body', 'lid', 'face_plate', 'candy_fill')
az = float(sys.argv[1]) if len(sys.argv) > 1 else -23
el = float(sys.argv[2]) if len(sys.argv) > 2 else 24
closed = ref.crop((60, 180, 760, 770)); opened = ref.crop((800, 90, 1500, 770))
a = R.render(m, atlas, rects, size, pcs, R.Pose(0), az=az, el=el, px=30, out=(700, 590), centre=(8, 8, 8))
b = R.render(m, atlas, rects, size, pcs, R.Pose(115), az=az, el=el+12, px=30, out=(700, 680), centre=(8, 11, 8))
w = Image.new('RGBA', (1400, 1300), (20, 20, 24, 255))
w.paste(closed, (0, 0)); w.paste(a, (700, 0)); w.paste(opened, (0, 600)); w.paste(b, (700, 600))
w.save('/tmp/cmp.png')
