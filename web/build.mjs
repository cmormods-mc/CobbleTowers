// Builds the web UI.   node build.mjs          -> src/main/resources/web/cobbletowers/  (what the jar ships)
//                      WEB_OUT=<dir> node build.mjs -> the release bundle somewhere else (ci_local compares it with the committed one)
//                      node build.mjs --dev    -> web/dist-dev/  (unminified, with the fixtures, for a browser and the screenshot tests)
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { genData } from './tools/gen-data.mjs';
import { packAtlas } from './tools/atlas.mjs';
import { iconSheet, ICON_NAMES } from './tools/native-art.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const dev = process.argv.includes('--dev');
const out = dev ? path.join(here, 'dist-dev') : process.env.WEB_OUT ? path.resolve(process.env.WEB_OUT) : path.join(root, 'src/main/resources/web/cobbletowers');
const res = path.join(root, 'src/main/resources/assets/cobbletowers');

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, 'assets'), { recursive: true });

// ---- data the server does not send, and the sprite sheet
const data = genData(root);
fs.writeFileSync(path.join(here, 'src/gen/data.js'), 'export default ' + JSON.stringify(data) + ';\n');

const sprites = data.species.filter((s) => fs.existsSync(path.join(res, 'textures/gui/partners', s + '.png')));
const sheet = packAtlas(sprites.map((s) => ({ name: s, file: path.join(res, 'textures/gui/partners', s + '.png') })), 512);
fs.writeFileSync(path.join(out, 'assets/partners.png'), sheet.png);
fs.writeFileSync(path.join(here, 'src/gen/atlas.js'), 'export const ATLAS = ' + JSON.stringify({ width: sheet.width, height: sheet.height, map: sheet.map }) + ';\n');

// ---- the mod's own item pictures (armor, key, trim template): third-party items (Cobblemon, vanilla) are not redistributed and show a gift
const itemFiles = fs.readdirSync(path.join(res, 'textures/item')).filter((n) => n.endsWith('.png'));
const items = packAtlas(itemFiles.map((f) => ({ name: 'cobbletowers:' + f.replace(/\.png$/, ''), file: path.join(res, 'textures/item', f) })), 256);
fs.writeFileSync(path.join(out, 'assets/items.png'), items.png);
fs.writeFileSync(path.join(here, 'src/gen/items.js'), 'export const ITEMS = ' + JSON.stringify({ width: items.width, height: items.height, map: items.map }) + ';\n');

// ---- the texture kit
const copy = (from, to) => { fs.mkdirSync(path.dirname(path.join(out, to)), { recursive: true }); fs.copyFileSync(from, path.join(out, to)); };
for (const dir of ['pixel', 'byzantine']) {
  for (const f of fs.readdirSync(path.join(res, 'textures/gui', dir)).filter((n) => n.endsWith('.png'))) copy(path.join(res, 'textures/gui', dir, f), `assets/gui/${dir}/${f}`);
}
copy(path.join(res, 'textures/gui/raid_points.png'), 'assets/gui/raid_points.png');
copy(path.join(here, 'fonts/Jersey15-Regular.ttf'), 'assets/font/Jersey15-Regular.ttf');
copy(path.join(here, 'fonts/Jersey15-OFL.txt'), 'assets/font/Jersey15-OFL.txt');
copy(path.join(here, 'fonts/Outfit-Variable.ttf'), 'assets/font/Outfit-Variable.ttf');
copy(path.join(here, 'fonts/Outfit-OFL.txt'), 'assets/font/Outfit-OFL.txt');

// ---- the mod's procedural art (icons, landscapes), drawn here the way the native screens draw it
const put = (to, buf) => { fs.mkdirSync(path.dirname(path.join(out, to)), { recursive: true }); fs.writeFileSync(path.join(out, to), buf); };
put('assets/gui/icons.png', iconSheet());
fs.writeFileSync(path.join(here, 'src/gen/icons.js'), 'export const ICONS = ' + JSON.stringify(ICON_NAMES) + ';\n');

// ---- the fixtures module: real payloads in a dev build, nothing in a shipped one
const fixturesPlugin = {
  name: 'fixtures',
  setup(b) {
    b.onResolve({ filter: /fixtures-loader\.js$/ }, () => ({ path: 'fixtures', namespace: 'fixtures' }));
    b.onLoad({ filter: /.*/, namespace: 'fixtures' }, () => {
      const map = {};
      if (dev) {
        const dir = path.join(here, 'fixtures');
        for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.json'))) { const d = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); map[d.kind] = d; }
      }
      return { contents: 'export const fixtures = ' + JSON.stringify(map) + ';', loader: 'js' };
    });
  },
};

const result = await build({
  entryPoints: { app: path.join(here, 'src/main.js') },
  outdir: out, bundle: true, minify: !dev, sourcemap: dev, target: ['chrome110'], format: 'iife', write: true,
  external: ['assets/*'], plugins: [fixturesPlugin], logLevel: 'warning', metafile: true,
});

// ---- index.html with a content stamp so the server knows when to refresh the copy it serves
const stamp = crypto.createHash('sha256');
(function hashTree(d) { for (const e of fs.readdirSync(d, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
  const p = path.join(d, e.name);
  // text files hash the same whether a checkout gave them LF or CRLF line endings
  const text = /\.(txt|js|css|html)$/.test(e.name);
  const bytes = e.isDirectory() ? null : text ? Buffer.from(fs.readFileSync(p, 'utf8').split(String.fromCharCode(13, 10)).join(String.fromCharCode(10))) : fs.readFileSync(p);
  if (e.isDirectory()) hashTree(p); else stamp.update(e.name).update(bytes);
} })(out); // every shipped file, so an art-only change still refreshes the installed copy
const version = stamp.digest('hex').slice(0, 12);
fs.writeFileSync(path.join(out, 'index.html'), `<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8" /><meta name="viewport" content="width=device-width, initial-scale=1" />
<title>CobbleTowers</title><link rel="stylesheet" href="app.css?v=${version}" /></head>
<body><div id="stage"><div id="main-root" style="display:contents"></div></div><div id="hud-root"></div>
<script src="app.js?v=${version}"></script></body></html>
`);

const files = [];
(function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, e.name); e.isDirectory() ? walk(p) : files.push(p); } })(out);
const bytes = files.reduce((n, f) => n + fs.statSync(f).size, 0);
fs.writeFileSync(path.join(out, 'version.txt'), version + '\n');
console.log(`${dev ? 'dev' : 'release'} build ${version}: ${files.length} files, ${(bytes / 1024).toFixed(0)} KB (app.js ${(fs.statSync(path.join(out, 'app.js')).size / 1024).toFixed(0)} KB)`);
