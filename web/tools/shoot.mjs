// Screenshots of the dev build from the real payload fixtures.   node tools/shoot.mjs [outdir]   (run `node build.mjs --dev` first)
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const dist = path.resolve(here, '../dist-dev/index.html');
const outDir = path.resolve(process.argv[2] || path.join(here, '../test/shots'));
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
  reward_armor: { payloads: [fixture('IntermissionState'), fixture('RewardReveal', (d) => { d.grants.reverse(); })] },
  relic_draft: { payloads: [fixture('IntermissionState', (d) => { d.draft.cards = [
    { id: 'cobbletowers:ember_brand', displayName: 'Ember Brand', votes: 0, risk: 0, theme: 'custom:', lines: ['[+] Your Fire moves deal 25% more damage.', 'Tags: offense', 'Held for this run; not permanent equipment.'] },
    { id: 'cobbletowers:guardian_ward', displayName: 'Guardian Ward', votes: 1, risk: 0, theme: 'custom:', lines: ['[+] Your side takes 10% less damage.', 'Tags: sustain', 'Held for this run; not permanent equipment.'] },
    { id: 'cobbletowers:pacemaker', displayName: 'Pacemaker', votes: 0, risk: 1, theme: 'custom:', lines: ['[+] Your lead starts with +2 Speed.', '[-] Your lead starts at 85% HP.', 'Tags: tempo, risk', 'Held for this run; not permanent equipment.'] }]; d.floor = 5; })] },
  room_forge: { payloads: [fixture('IntermissionState', (d) => { d.draft.cards = [
    { id: 'cobbletowers:event/forge_pay', displayName: 'Forge: every player pays 175 CobbleDollars for a battle relic', votes: 0, risk: -1, theme: 'event', lines: ['Forge: every player pays 175 CobbleDollars for a battle relic'] },
    { id: 'cobbletowers:event/forge_leave', displayName: 'Forge: walk away', votes: 0, risk: -1, theme: 'event', lines: ['Forge: walk away'] }]; d.floor = 3; })] },
  vendor: { payloads: [fixture('IntermissionState'), fixture('VendorCatalog')] },
  draft_done: { payloads: [fixture('RentalDraft')] },
  draft_sealed: { payloads: [fixture('RentalDraft', openDraft)] },
  draft_choose: { payloads: [fixture('RentalDraft', openDraft)], steps: async (page) => { await page.click('.pack'); await page.waitForSelector('.card.pick', { timeout: 8000 }); await page.click('.card.pick >> nth=1'); } },
  hall: { payloads: [fixture('TowerHallState')] },
  hall_social: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('text=Social >> nth=0'); } },
  hall_trials: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('button:has-text("Trials")'); } },
  hall_progress: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('button:has-text("Progress")'); } },
  tower_brief: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('.tile >> nth=0'); } },
  trial_brief: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('button:has-text("Daily Tower")'); } },
  lobby_host: { payloads: [fixture('TowerHallState'), fixture('PlayState')] },
  lobby_rental: { payloads: [fixture('PlayState', (d) => { d.lobby.options.modes.readiness = { confirmed: true, drafted: false, mine: false, host: false }; d.lobby.members = [{ name: 'Ash', accepted: true, ready: false }, { name: 'Misty', accepted: false, ready: false }]; d.lobby.options.depth = { chosen: 1, max: 2, offered: true }; })] },
  lobby_invited: { payloads: [fixture('PlayState', (d) => { d.lobby.role = 2; })] },
  party: { payloads: [fixture('TowerHallState'), fixture('PlayState'), fixture('RegistrationState')] },
  mastery: { payloads: [fixture('TowerHallState'), fixture('MasteryScreen')] },
  mastery_board: { payloads: [fixture('TowerHallState'), fixture('MasteryScreen'), ['MasteryScreen', fixture('MasteryBoard')[1]]] },
  scouting: { payloads: [fixture('ScoutingReveal')] },
  spectator: { payloads: [fixture('RunEffects'), fixture('SpectatorPanel')], hud: true },
  settings: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('button:has-text("Settings")'); } },
  feature_list: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; })] },
  feature_entry: { payloads: [fixture('TowerFeatureState', (d) => { d.requestId = 0; })], steps: async (page) => { await page.click('button:has-text("Champion")'); } },
  feature_review: { payloads: [fixture('TowerFeatureState', (d) => { d.requestId = 0; })], steps: async (page) => { await page.click('button:has-text("Club banner")'); await page.click('button:has-text("Set banner")'); } },
  tracks: { payloads: [fixture('TowerHallState'), fixture('TrackState')], steps: async (page) => { await page.click('button:has-text("Progress")'); await page.click('.slot >> nth=4'); } },
  tracks_season: { payloads: [fixture('TowerHallState'), fixture('TrackState')], steps: async (page) => { await page.click('button:has-text("Progress")'); await page.click('button:has-text("Season")'); await page.click('.slot >> nth=2'); } },
  hud: { payloads: [fixture('RunEffects')], hud: true },
};

const browser = await chromium.launch();
for (const [name, shot] of Object.entries(SHOTS)) {
  const page = await browser.newPage({ viewport: { width: +(process.env.W || 1280), height: +(process.env.H || 720) } });
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
