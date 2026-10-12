// Screenshots of the dev build from the real payload fixtures.   node test/shoot.mjs [outdir]   (run `node build.mjs --dev` first)
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const dist = path.resolve(here, '../dist-dev/index.html');
const outDir = path.resolve(process.argv[2] || path.join(here, 'shots'));
fs.mkdirSync(outDir, { recursive: true });

const fixture = (kind, mutate) => {
  const d = JSON.parse(fs.readFileSync(path.resolve(here, '../fixtures', kind + '.json'), 'utf8')).data;
  if (mutate) mutate(d);
  return [kind, d];
};
const openDraft = (d) => { d.complete = false; d.current = 0; d.packs.forEach((p) => { p.kept = []; }); };

// name -> payloads sent to the page, then optional steps run before the shot
export const SHOTS = {
  intermission: { payloads: [fixture('IntermissionState')] },
  reward: { payloads: [fixture('IntermissionState'), fixture('RewardReveal')] },
  vendor: { payloads: [fixture('IntermissionState'), fixture('VendorCatalog')] },
  draft_done: { payloads: [fixture('RentalDraft')] },
  draft_sealed: { payloads: [fixture('RentalDraft', openDraft)] },
  draft_choose: { payloads: [fixture('RentalDraft', openDraft)], steps: async (page) => { await page.click('.pack'); await page.waitForSelector('.card.pick', { timeout: 8000 }); await page.click('.card.pick >> nth=1'); } },
  hud: { payloads: [fixture('RunEffects')], hud: true },
};

const browser = await chromium.launch();
for (const [name, shot] of Object.entries(SHOTS)) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  page.on('console', (m) => { if (m.type() === 'error') console.log(`[${name}] console error: ${m.text()}`); });
  page.on('pageerror', (e) => console.log(`[${name}] page error: ${e.message}`));
  await page.goto(pathToFileURL(dist).href + (shot.hud ? '#hud' : ''));
  await page.waitForFunction(() => window.TowerDev);
  for (const [kind, data] of shot.payloads) await page.evaluate(([k, d]) => window.TowerDev.dispatch(k, d), [kind, data]);
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(outDir, name + '.png') });
  if (shot.steps) { await shot.steps(page); await page.waitForTimeout(300); await page.screenshot({ path: path.join(outDir, name + '.png') }); }
  await page.close();
}
await browser.close();
console.log('shots in ' + outDir);
