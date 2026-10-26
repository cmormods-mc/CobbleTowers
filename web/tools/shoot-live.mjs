// Draws the real page from payloads a live server produced.   node tools/shoot-live.mjs <dumpDir> [outDir]   (run `node build.mjs --dev` first)
// The dumps come from validation/smoke/route_map_live_shots.py (the dev command `cobbletowers web dump intermission`). The page is a
// headless Chromium, not the game client, but every byte it draws is what the server sent.
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const dist = path.resolve(here, '../dist-dev/index.html');
const dumpDir = path.resolve(process.argv[2] || '.');
const outDir = path.resolve(process.argv[3] || path.join(dumpDir, 'shots'));
fs.mkdirSync(outDir, { recursive: true });

const exe = process.env.CHROME || undefined;
const browser = await chromium.launch(exe ? { executablePath: exe } : {});
for (const file of fs.readdirSync(dumpDir).filter((f) => f.endsWith('.json')).sort()) {
  const env = JSON.parse(fs.readFileSync(path.join(dumpDir, file), 'utf8'));
  const page = await browser.newPage({ viewport: { width: +(process.env.W || 1280), height: +(process.env.H || 720) } });
  page.on('console', (m) => { if (m.type() === 'error') console.log(`[${file}] console error: ${m.text()}`); });
  page.on('pageerror', (e) => console.log(`[${file}] page error: ${e.message}`));
  await page.addInitScript(() => { try { localStorage.setItem('cobbletowers.guideSeen', '1'); localStorage.setItem('cobbletowers.ui', JSON.stringify({ look: 'desk' })); } catch (err) { /* none */ } });
  await page.goto(pathToFileURL(dist).href);
  await page.waitForFunction(() => window.TowerDev);
  await page.evaluate(([k, d]) => window.TowerDev.dispatch(k, d), [env.kind, env.data]);
  await page.waitForTimeout(500);
  const g = env.data?.extras?.route?.graph;
  console.log(file, '->', env.data?.extras?.tower, 'floor', env.data?.floor, g ? `map ${g.rows} rows, ${g.stops.length} stops, ${g.links.length} links, trail ${JSON.stringify(g.trail)}` : 'no map');
  await page.screenshot({ path: path.join(outDir, file.replace(/\.json$/, '.png')) });
  await page.close();
}
await browser.close();
console.log('shots in ' + outDir);
