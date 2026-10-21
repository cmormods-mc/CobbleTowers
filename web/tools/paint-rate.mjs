// How many frames the browser produces while a screen sits idle. The game's browser draws off-screen and uploads every frame it
// produces as a texture, so frames per second on an idle page is the cost to keep low.
//   node build.mjs --dev && node tools/paint-rate.mjs [dir-with-index.html]      (default: dist-dev)
// Uses the CDP screencast, which emits a frame only when the page was repainted: the same signal the game gets.
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const dir = path.resolve(process.argv[2] || path.join(here, '../dist-dev'));
const url = pathToFileURL(path.join(dir, 'index.html')).href;
const fixture = (kind) => JSON.parse(fs.readFileSync(path.resolve(here, '../fixtures', kind + '.json'), 'utf8')).data;

const SETTLE_MS = 2500, WINDOW_MS = 5000;
const browser = await chromium.launch();

/** Frames per second over WINDOW_MS, after SETTLE_MS of letting entrances finish. */
async function rate(kind, wait, settle = SETTLE_MS) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  await page.addInitScript(() => { window.webgui = { client: { username: 'WebPlayer' } }; try { localStorage.setItem('cobbletowers.guideSeen', '1'); localStorage.setItem('cobbletowers.ui', JSON.stringify({ look: 'desk' })); } catch (e) { /* none */ } });
  await page.goto(url);
  await page.waitForFunction(() => window.TowerDev);
  await page.evaluate(([k, d]) => window.TowerDev.dispatch(k, d), [kind, fixture(kind)]);
  await page.waitForSelector(wait);
  await page.waitForTimeout(settle);
  const cdp = await page.context().newCDPSession(page);
  let frames = 0;
  cdp.on('Page.screencastFrame', (f) => { frames++; cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }).catch(() => {}); });
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 10, everyNthFrame: 1 });
  await page.waitForTimeout(WINDOW_MS);
  await cdp.send('Page.stopScreencast');
  await page.close();
  return frames / (WINDOW_MS / 1000);
}

const rows = [
  ['Hall, settled', 'TowerHallState', '.fd .trow', SETTLE_MS],
  ['Route map, settled', 'IntermissionState', '.fd .dstop.now', 8000],
  ['Hall, first 5 s (entrance included)', 'TowerHallState', '.fd .trow', 0],
];
for (const [name, kind, sel, settle] of rows) console.log(name.padEnd(40) + (await rate(kind, sel, settle)).toFixed(1) + ' frames/s');
await browser.close();
