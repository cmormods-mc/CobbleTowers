// How smoothly the progress reward track scrolls: wheels over a long lane and reports frame times and the share of slow frames.
//   node build.mjs --dev && node tools/scroll-bench.mjs [nodes=150]
// Headless Chromium rasterises in software, so the numbers are relative: compare before and after a change, not against the game.
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const url = pathToFileURL(path.resolve(here, '../dist-dev/index.html')).href;
const fixture = (kind) => JSON.parse(fs.readFileSync(path.resolve(here, '../fixtures', kind + '.json'), 'utf8')).data;
const count = Number(process.argv[2]) || 150;
const extraCss = process.argv[3] || '';

const tracks = fixture('TrackState');
const base = tracks.mastery.nodes;
tracks.mastery.nodes = Array.from({ length: count }, (_, i) => ({ ...base[i % base.length], number: i + 1, state: i < count * 0.3 ? 2 : i === Math.floor(count * 0.3) ? 1 : i < count * 0.6 ? 0 : 3 }));

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
await page.addInitScript(() => { window.webgui = { client: { username: 'WebPlayer' } }; try { localStorage.setItem('cobbletowers.guideSeen', '1'); localStorage.setItem('cobbletowers.ui', JSON.stringify({ look: 'desk' })); } catch (e) { /* none */ } });
await page.goto(url);
await page.waitForFunction(() => window.TowerDev);
await page.evaluate(([k, d]) => window.TowerDev.dispatch(k, d), ['TowerHallState', fixture('TowerHallState')]);
await page.click('.fd .pills a >> text=Progress');
await page.evaluate(([k, d]) => window.TowerDev.dispatch(k, d), ['TrackState', tracks]);
await page.waitForSelector('.dtiles .dtile');
if (extraCss) await page.addStyleTag({ content: extraCss });
const cdp = await page.context().newCDPSession(page);
let layers = 0;
cdp.on('LayerTree.layerTreeDidChange', (e) => { if (e.layers) layers = e.layers.length; });
await cdp.send('LayerTree.enable');
await page.waitForTimeout(3500);
const tiles = await page.evaluate(() => document.querySelectorAll('.dtiles .dtile').length);
await page.mouse.move(700, 400);
await browser.startTracing(page, { categories: ['cc', 'devtools.timeline', 'disabled-by-default-devtools.timeline', 'viz', 'gpu'] });
const frames = await page.evaluate(() => new Promise((resolve) => {
  const el = document.querySelector('.dtiles'); const out = []; let last = performance.now(); let dir = 1; let n = 0;
  const step = () => {
    const now = performance.now(); out.push(now - last); last = now;
    const max = el.scrollHeight - el.clientHeight;
    el.scrollTop += dir * 40; if (el.scrollTop >= max) dir = -1; if (el.scrollTop <= 0) dir = 1;
    if (++n < 240) requestAnimationFrame(step); else resolve(out);
  };
  requestAnimationFrame(step);
}));
console.log('composited layers: ' + layers);
const traced = JSON.parse((await browser.stopTracing()).toString());
const sums = {}; for (const e of traced.traceEvents) if (e.ph === 'X' && e.dur) sums[e.name] = (sums[e.name] || 0) + e.dur;
const ms = (n) => ((sums[n] || 0) / 1000).toFixed(0);
console.log(`trace (ms over the scroll): Paint ${ms('Paint')}, RasterTask ${ms('RasterTask')}, UpdateLayer ${ms('UpdateLayer')}, Layerize ${ms('Layerize')}, PrePaint ${ms('PrePaint')}, Commit ${ms('Commit')}, UpdateLayoutTree ${ms('UpdateLayoutTree')}, Layout ${ms('Layout')}`);
const sorted = frames.slice(5).sort((a, b) => a - b);
const avg = sorted.reduce((a, b) => a + b, 0) / sorted.length;
const p = (q) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * q))];
console.log(`${tiles} tiles: avg ${avg.toFixed(1)} ms (${(1000 / avg).toFixed(0)} fps), p50 ${p(.5).toFixed(1)}, p95 ${p(.95).toFixed(1)}, worst ${sorted[sorted.length - 1].toFixed(1)}; frames over 20 ms: ${sorted.filter((f) => f > 20).length}/${sorted.length}`);
// frames produced per wheel notch (the CDP screencast emits a frame only when the page repainted: the same signal the game gets)
let frameCount = 0;
cdp.on('Page.screencastFrame', (f) => { frameCount++; cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }).catch(() => {}); });
await page.evaluate(() => { document.querySelector('.dtiles').scrollTop = 0; });
await page.waitForTimeout(400);
await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 10, everyNthFrame: 1 });
await page.waitForTimeout(300);
frameCount = 0;
const notches = 12;
for (let i = 0; i < notches; i++) { await page.mouse.wheel(0, 100); await page.waitForTimeout(450); }
await cdp.send('Page.stopScreencast');
const moved = await page.evaluate(() => document.querySelector('.dtiles').scrollTop);
console.log(`wheel: ${notches} notches moved the track ${Math.round(moved)} px and repainted ${frameCount} frames (${(frameCount / notches).toFixed(1)} per notch)`);
// the game's browser draws off-screen and reads every frame back: scroll a step, then capture the page, and time the pair
const shotMs = [];
for (let i = 0; i < 60; i++) {
  await page.evaluate((i) => { const el = document.querySelector('.dtiles'); const max = el.scrollHeight - el.clientHeight; el.scrollTop = ((i * 53) % Math.max(1, max)); }, i);
  const t = performance.now();
  await cdp.send('Page.captureScreenshot', { format: 'jpeg', quality: 20 });
  shotMs.push(performance.now() - t);
}
shotMs.sort((a, b) => a - b);
console.log(`scroll + readback: median ${shotMs[30].toFixed(1)} ms, p90 ${shotMs[54].toFixed(1)} ms, mean ${(shotMs.reduce((a, b) => a + b, 0) / shotMs.length).toFixed(1)} ms`);
await browser.close();
