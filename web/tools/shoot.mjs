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
// the event tower (P54) as the Hall lists it inside its window
const withHalloween = (d) => {
  d.towers.push({ id: 'cobbletowers:halloween', name: 'Hollow’s Eve', floors: 10, milestones: 2, ascends: true, endsAt: Date.UTC(2026, 10, 3) });
  d.extras.standings.push({ tower: 'cobbletowers:halloween', rank: 'Unranked', level: 0, toNext: 3, ascension: 0 });
};
const openDraft =(d) => { d.complete = false; d.current = 0; d.packs.forEach((p) => { p.kept = []; }); };

// a branching route map (P56) with every adjacent link, for the look of the atlas; milestones at rows 5 and 10
const atlasGraph = (trail, reveal) => {
  const kinds = ['BATTLE', 'FORGE', 'REST', 'SHRINE', 'ELITE', 'LIBRARY', 'MERCHANT', 'VAULT', 'POOL', 'TRICK_OR_TREAT'];
  const boss = (r) => r === 5 || r === 10, stops = [{ row: 0, lane: 1, kind: 'START' }], links = [];
  for (let r = 1; r <= 10; r++) for (let l = 0; l < 3; l++) if (!boss(r) || l === 1) stops.push({ row: r, lane: l, kind: boss(r) ? 'BOSS' : kinds[(r * 3 + l * 2) % kinds.length] });
  for (let r = 0; r < 10; r++) for (let f = 0; f < 3; f++) for (let t = 0; t < 3; t++) {
    const has = (row, lane) => stops.some((s) => s.row === row && s.lane === lane);
    if (has(r, f) && has(r + 1, t) && Math.abs(f - t) <= 1) links.push({ row: r, from: f, to: t });
  }
  return { rows: 10, reveal, trail, stops, links };
};
const atlasShot = (tower) => fixture('IntermissionState', (d) => {
  d.floor = 2; d.extras.tower = tower; d.extras.cycleFloor = 2; d.extras.cycleLength = 10;
  d.extras.route = { lane: 0, now: [{ lane: 0, card: 0, kind: 'FORGE', arena: '' }, { lane: 1, card: 1, kind: 'BATTLE', arena: '' }], ahead: [], graph: atlasGraph([1, 1, 0], 4) };
  d.draft.cards = d.draft.cards.slice(0, 2);
});

// name -> payloads sent to the page, then optional steps run before the shot
export const SHOTS = {
  intermission: { payloads: [fixture('IntermissionState')] },
  reward: { payloads: [fixture('IntermissionState'), fixture('RewardReveal')] },
  reward_alone: { payloads: [fixture('RewardReveal')] },
  reward_armor: { payloads: [fixture('IntermissionState'), fixture('RewardReveal', (d) => { d.grants.reverse(); })] },
  relic_draft: { payloads: [fixture('IntermissionState', (d) => { d.draft.cards = [
    { id: 'cobbletowers:ember_brand', displayName: 'Ember Brand', votes: 0, risk: 0, theme: 'custom:', lines: ['[+] Your Fire moves deal 25% more damage.', 'Tags: offense', 'Held for this run; not permanent equipment.'] },
    { id: 'cobbletowers:guardian_ward', displayName: 'Guardian Ward', votes: 1, risk: 0, theme: 'custom:', lines: ['[+] Your side takes 10% less damage.', 'Tags: sustain', 'Held for this run; not permanent equipment.'] },
    { id: 'cobbletowers:pacemaker', displayName: 'Pacemaker', votes: 0, risk: 1, theme: 'custom:', lines: ['[+] Your lead starts with +2 Speed.', '[-] Your lead starts at 85% HP.', 'Tags: tempo, risk', 'Held for this run; not permanent equipment.'] }]; d.floor = 5; })] },
  room_forge: { payloads: [fixture('IntermissionState', (d) => { d.draft.cards = [
    { id: 'cobbletowers:event/forge_pay', displayName: 'Forge: every player pays 175 CobbleDollars for a battle relic', votes: 0, risk: -1, theme: 'event', lines: ['Forge: every player pays 175 CobbleDollars for a battle relic'] },
    { id: 'cobbletowers:event/forge_leave', displayName: 'Forge: walk away', votes: 0, risk: -1, theme: 'event', lines: ['Forge: walk away'] }]; d.floor = 3; })] },
  // every modifier picture, three cards a shot (the route is off so the cards show)
  ...Object.fromEntries([
    [['weather:raindance', 'Downpour', 1], ['weather:sunnyday', 'Scorcher', 1], ['weather:sandstorm', 'Dust Bowl', 1]],
    [['weather:snowscape', 'Whiteout', 1], ['terrain:electricterrain', 'Static Field', 1], ['terrain:psychicterrain', 'Mind Field', 1]],
    [['terrain:mistyterrain', 'Fairy Mist', 1], ['terrain:grassyterrain', 'Overgrowth', 0], ['enemy:tough', 'Ironclad', 2]],
    [['enemy:fragile', 'Brittle Boss', 0], ['enemy:champion', 'Champion Rises', 2], ['enemy:veteran', 'Seasoned Ranks', 1]],
    [['enemy:novice', 'Fresh Recruits', 0], ['encounter:crowd', 'The Mob', 2], ['constraint:no_switch', 'Locked Roster', 2]],
    [['constraint:no_heal', 'No Recovery', 2], ['constraint:no_setup', 'No Setup', 1], ['constraint:no_items', 'Empty Pockets', 1]],
    [['scouting', 'Far Sight', 0], ['reward_up:hoard', 'Hazard Pay', 2], ['reward_down', 'Tight Purse', 0]],
    [['custom:glass_cannon', 'Glass Cannon', 2], ['custom:fortunes_wheel', "Fortune's Wheel", 1], ['custom:field_hospital', 'Field Hospital', 1]],
    [['custom:black_market', 'Black Market', 1], ['custom:swift_start', 'Swift Start', 0], ['custom:iron_hide', 'Iron Hide', 1]],
    [['custom:war_banner', 'War Banner', 2], ['unknown', 'Mystery', 1], ['custom:', 'Ember Brand', 0, 'relic']],
  ].map((trio, i) => ['modart_' + (i + 1), { look: 'desk', payloads: [fixture('IntermissionState', (d) => {
    d.extras.route = { lane: -1, now: [], ahead: [] };
    d.draft.cards = trio.map(([theme, name, risk, kind], j) => ({ id: 'cobbletowers:art' + i + j, displayName: name, votes: 0, risk, theme,
      lines: kind === 'relic' ? ['[+] Your Fire moves deal 25% more damage.', 'Tags: offense', 'Held for this run; not permanent equipment.'] : ['[+] Minor risk  *  +3% to your final payout', '[+] An example effect line.', 'The risk you carry is paid out when the tower ends.'] }));
  })] }])),
  vendor: { payloads: [fixture('IntermissionState'), fixture('VendorCatalog')] },
  draft_done: { payloads: [fixture('RentalDraft')] },
  draft_warning: { payloads: [fixture('RentalDraft', openDraft)] },
  draft_sealed: { payloads: [fixture('RentalDraft', openDraft)], steps: async (page) => { await page.click('text=Continue with effects'); } },
  draft_choose: { payloads: [fixture('RentalDraft', openDraft)], steps: async (page) => { await page.click('text=Continue with effects'); await page.click('.pack', { force: true }); await page.waitForSelector('.rcard.rc-pick', { timeout: 20000 }); await page.click('.rcard.rc-pick >> nth=1'); } },
  hall: { payloads: [fixture('TowerHallState')] },
  desk_route: { payloads: [fixture('IntermissionState')], look: 'desk' },
  desk_route_pick: { payloads: [fixture('IntermissionState')], look: 'desk', steps: async (page) => { await page.click('.dstop.now >> nth=1'); } },
  desk_atlas: { payloads: [atlasShot('Tideforge Tower')], look: 'desk' },
  desk_atlas_hollow: { payloads: [atlasShot('Hollow’s Eve')], look: 'desk' },
  desk_route_edge: { payloads: [fixture('IntermissionState', (d) => { d.floor = 3; d.extras.cycleFloor = 3; d.extras.route.lane = 0; d.extras.route.now = [{ lane: 0, card: 0, kind: 'SHRINE' }, { lane: 1, card: 1, kind: 'BATTLE' }]; d.draft.cards = d.draft.cards.slice(0, 2); d.extras.route.ahead = d.extras.route.ahead.slice(0, 2); })], look: 'desk' },
  desk_inter: { payloads: [fixture('IntermissionState', (d) => { d.extras.route = { lane: -1, now: [], ahead: [] }; })], look: 'desk' },
  desk_inter_pick: { payloads: [fixture('IntermissionState', (d) => { d.extras.route = { lane: -1, now: [], ahead: [] }; })], look: 'desk', steps: async (page) => { await page.click('.dmc >> nth=1'); } },
  desk_inter_room: { payloads: [fixture('IntermissionState', (d) => { d.extras.route = { lane: -1, now: [], ahead: [] }; d.floor = 3; d.extras.cycleFloor = 3; d.draft.cards = [
    { id: 'cobbletowers:event/forge_pay', displayName: 'Forge: every player pays 175 CobbleDollars for a battle relic', votes: 1, risk: -1, theme: 'event', lines: ['Forge: every player pays 175 CobbleDollars for a battle relic'] },
    { id: 'cobbletowers:event/forge_leave', displayName: 'Forge: walk away', votes: 0, risk: -1, theme: 'event', lines: ['Forge: walk away'] }]; d.members = [{ name: 'WebPlayer', ready: false, cashOut: false }, { name: 'Misty', ready: true, cashOut: false }]; })], look: 'desk' },
  desk_lobby_host: { payloads: [fixture('TowerHallState'), fixture('PlayState')], look: 'desk' },
  desk_lobby_rental: { payloads: [fixture('TowerHallState'), fixture('PlayState', (d) => { d.lobby.options.modes.readiness = { confirmed: true, drafted: false, mine: false, host: false }; d.lobby.members = [{ name: 'Ash', accepted: true, ready: false }, { name: 'Misty', accepted: false, ready: false }]; d.lobby.options.depth = { chosen: 1, max: 2, offered: true }; })], look: 'desk' },
  desk_lobby_invited: { payloads: [fixture('TowerHallState'), fixture('PlayState', (d) => { d.lobby.role = 2; d.lobby.members = [{ name: 'WebPlayer', accepted: false, ready: false }]; d.lobby.hostName = 'Ash'; })], look: 'desk' },
  desk_progress: { payloads: [fixture('TowerHallState'), fixture('TrackState')], look: 'desk', steps: async (page) => { await page.click('.fd .pills a >> text=Progress'); } },
  desk_progress_pick: { payloads: [fixture('TowerHallState'), fixture('TrackState')], look: 'desk', steps: async (page) => { await page.click('.fd .pills a >> text=Progress'); await page.click('.dtile.open'); } },
  desk_progress_season: { payloads: [fixture('TowerHallState'), fixture('TrackState')], look: 'desk', steps: async (page) => { await page.click('.fd .pills a >> text=Progress'); await page.click('.fd .trow >> nth=2'); await page.click('.dtile >> nth=2'); } },
  desk_tower_brief: { payloads: [fixture('TowerHallState')], look: 'desk', steps: async (page) => { await page.click('.fd .trow >> nth=0'); await page.click('.fd .dbtn:not(.gh) >> nth=-1'); } },
  desk_halloween: { payloads: [fixture('TowerHallState', withHalloween)], look: 'desk' },
  desk_halloween_brief: { payloads: [fixture('TowerHallState', withHalloween)], look: 'desk', steps: async (page) => { await page.click('.fd .trow >> text=Hollow'); await page.click('.fd .dbtn:not(.gh) >> nth=-1'); } },
  desk_trials: { payloads: [fixture('TowerHallState')], look: 'desk', steps: async (page) => { await page.click('.fd .pills a >> text=Trials'); } },
  desk_trial_brief: { payloads: [fixture('TowerHallState')], look: 'desk', steps: async (page) => { await page.click('.fd .pills a >> text=Trials'); await page.click('.fd .dbtn >> nth=0'); } },
  desk_social: { payloads: [fixture('TowerHallState')], look: 'desk', steps: async (page) => { await page.click('.fd .pills a >> text=Social'); } },
  desk_feature_list: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; })], look: 'desk' },
  desk_feature_entry: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; })], look: 'desk', steps: async (page) => { await page.click('.fd .dentry >> nth=0'); } },
  desk_feature_review: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; })], look: 'desk', steps: async (page) => { await page.click('.fd .dentry >> text=Club banner'); await page.click('.fd .dactions .dbtn'); } },
  desk_mastery: { payloads: [fixture('TowerHallState'), fixture('MasteryScreen')], look: 'desk' },
  desk_mastery_board: { payloads: [fixture('TowerHallState'), fixture('MasteryScreen'), ['MasteryScreen', fixture('MasteryBoard')[1]]], look: 'desk' },
  desk_party: { payloads: [fixture('TowerHallState'), fixture('PlayState'), fixture('RegistrationState')], look: 'desk' },
  desk_guide: { payloads: [fixture('TowerHallState')], look: 'desk', steps: async (page) => { await page.click('.fd .tl >> text=Guide'); } },
  desk_settings: { payloads: [fixture('TowerHallState')], look: 'desk', steps: async (page) => { await page.click('.fd .tl >> text=Settings'); } },
  desk_draft_warning: { payloads: [fixture('RentalDraft', openDraft)], look: 'desk' },
  desk_draft_sealed: { payloads: [fixture('RentalDraft', openDraft)], look: 'desk', steps: async (page) => { await page.click('text=Continue with effects'); } },
  desk_draft_choose: { payloads: [fixture('RentalDraft', openDraft)], look: 'desk', steps: async (page) => { await page.click('text=Continue with effects'); await page.click('.pack', { force: true }); await page.waitForSelector('.rcard.rc-pick', { timeout: 20000 }); await page.click('.rcard.rc-pick >> nth=1'); } },
  desk_draft_done: { payloads: [fixture('RentalDraft')], look: 'desk' },
  desk_reward: { payloads: [fixture('TowerHallState'), fixture('RewardReveal')], look: 'desk' },
  desk_reward_lib: { payloads: [fixture('TowerHallState'), fixture('RewardReveal', (d) => { d.grants = ['resonance_dust', 'facet', 'tower_mark', 'scouter', 'ascension_core'].map((m, i) => ({ item: 'ascensionlib:' + m, amount: i ? 2 : 120, label: m.replace(/_/g, ' ') })); })], look: 'desk' },
  desk_vendor: { payloads: [fixture('IntermissionState'), fixture('VendorCatalog')], look: 'desk' },
  desk_scouting: { payloads: [fixture('ScoutingReveal')], look: 'desk', steps: async (page) => { await page.click('.fd .dach >> nth=1'); } },
  desk_spectator: { payloads: [fixture('SpectatorPanel')], hud: true, look: 'desk' },
  desk_waiting: { payloads: [], look: 'desk' },
  desk_hall: { payloads: [fixture('TowerHallState')], look: 'desk' },
  desk_hall_noRun: { payloads: [fixture('TowerHallState', (d) => { d.runStatus = ''; d.canResume = false; d.extras.runTower = ''; d.extras.party[3].fainted = false; })], look: 'desk' },
  desk_hall_empty: { payloads: [fixture('TowerHallState', (d) => { d.runStatus = ''; d.canResume = false; d.extras.runTower = ''; d.extras.party = []; d.extras.standings = []; d.towers = d.towers.slice(0, 2); })], look: 'desk' },
  desk_hall_mode: { payloads: [fixture('TowerHallState', (d) => { d.extras.modeName = 'Monotype'; d.extras.modeProblems = ['Gliscor is not a Fire type', 'Lapras is not a Fire type', 'Ludicolo is not a Fire type']; })], look: 'desk' },
  desk_hall_modeok: { payloads: [fixture('TowerHallState', (d) => { d.extras.modeName = 'Level Cap 50'; d.extras.modeProblems = []; })], look: 'desk' },
  desk_lobby_rosters: { payloads: [fixture('TowerHallState'), fixture('PlayState', (d) => { d.lobby.hostName = 'Ash'; d.lobby.role = 3; d.lobby.members = [{ name: 'WebPlayer', accepted: true, ready: false }, { name: 'Misty', accepted: true, ready: false }];
    const mon = (n, sp, ty) => ({ name: n, species: sp, level: 50, fainted: false, hp: 100, types: ty });
    d.lobby.options.rosters = [{ name: 'Ash', team: [mon('Pikachu', 'pikachu', ['electric']), mon('Charizard', 'charizard', ['fire']), mon('Lapras', 'lapras', ['water'])] }, { name: 'Misty', team: [mon('Starmie', 'starmie', ['water']), mon('Gyarados', 'gyarados', ['water'])] }]; })], look: 'desk' },
  desk_hall_pick: { payloads: [fixture('TowerHallState')], look: 'desk', steps: async (page) => { await page.click('.trow >> nth=2'); } },
  hall_four: { payloads: [fixture('TowerHallState', (d) => { d.towers = d.towers.slice(0, 4); })] },
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
  spectator: { payloads: [fixture('SpectatorPanel')], hud: true },
  offline: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.evaluate(() => window.TowerDev.update((s) => ({ ...s, offline: true }))); } },
  guide_first: { payloads: [fixture('TowerHallState')] },
  guide: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('button:has-text("Guide")'); } },
  settings: { payloads: [fixture('TowerHallState')], steps: async (page) => { await page.click('button:has-text("Settings")'); } },
  current_run: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; d.section = 'run'; d.title = 'Current Run'; d.actions = []; d.entries = [
    { id: 'run', title: 'This run', lines: ['Neutral Tower, floor 6 (Ascension 1)', 'Difficulty rating: Hard (7): 3 challenges, Ascension 1', '[+] Risk carried: +20% to your final payout.', '3 modifiers, 2 relics (room for 6).'], actions: [] },
    { id: 'mod:a', title: 'Modifier: Sharpened Claws x2 (floor 3)', lines: ['[+] Moderate risk  *  +8% to your final payout', '[-] Foes rise 2 levels above the tower.', 'The risk you carry is paid out when the tower ends.'], actions: [] },
    { id: 'mod:b', title: 'Modifier: Glass Cannon (floor 5)', lines: ['[+] Start battles with +2 Attack and Sp. Atk.', '[-] Start battles at 60% HP.'], actions: [] },
    { id: 'relic:a:0', title: 'Relic: Ember Brand (floor 5)', lines: ['[+] Your Fire moves deal 25% more damage.', 'Tags: offense', 'Held for this run; not permanent equipment.'], actions: [] },
    { id: 'relic:b:1', title: 'Relic: Guardian Ward (floor 5)', lines: ['[+] Your side takes 10% less damage.', 'Tags: sustain'], actions: [] },
    { id: 'bonus:s', title: 'Set bonus: Offense Set (2)', lines: ['[+] Your side deals 5% more damage.', 'Set bonus: while you hold 2 or more offense relics.'], actions: [] }]; })] },
  collection: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; d.section = 'collection'; d.title = 'Collection'; d.actions = [];
    const sets = ['Recruit’s Gear', 'Challenger’s Regalia', 'Paragon’s Aegis', 'Tidewalker’s Garb', 'Tideforged Plate', 'Leviathan Mail', 'Sprout Garb', 'Rootwoven Mail', 'Heartwood Bulwark', 'Dusk Wanderer’s Cloak', 'Duskbound Guard', 'Nightfall Carapace'];
    d.entries = sets.map((n, i) => ({ id: 'cobbletowers:set' + i, title: n, lines: ['4 pieces: head, chest, legs, feet', '2 pieces: +6% EXP, +1 max health', '4 pieces: -3% damage taken, vendor -5%'], actions: [] })); })] },
  collection_entry: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; d.section = 'collection'; d.title = 'Collection'; d.actions = [];
    d.entries = [{ id: 'cobbletowers:recruit', title: 'Recruit’s Gear', lines: ['4 pieces: head, chest, legs, feet', '2 pieces: +6% EXP, +1 max health', '4 pieces: -3% damage taken, vendor -5%'], actions: [] }]; })], steps: async (page) => { await page.click('button:has-text("Recruit")'); } },
  feature_list: { payloads: [fixture('TowerHallState'), fixture('TowerFeatureState', (d) => { d.requestId = 0; })] },
  feature_entry: { payloads: [fixture('TowerFeatureState', (d) => { d.requestId = 0; })], steps: async (page) => { await page.click('button:has-text("Champion")'); } },
  feature_review: { payloads: [fixture('TowerFeatureState', (d) => { d.requestId = 0; })], steps: async (page) => { await page.click('button:has-text("Club banner")'); await page.click('button:has-text("Set banner")'); } },
  tracks: { payloads: [fixture('TowerHallState'), fixture('TrackState')], steps: async (page) => { await page.click('button:has-text("Progress")'); await page.click('.slot >> nth=4'); } },
  tracks_season: { payloads: [fixture('TowerHallState'), fixture('TrackState')], steps: async (page) => { await page.click('button:has-text("Progress")'); await page.click('button:has-text("Season")'); await page.click('.slot >> nth=2'); } },
};

const browser = await chromium.launch();
for (const [name, shot] of Object.entries(SHOTS)) {
  if (process.env.ONLY && !name.startsWith(process.env.ONLY)) continue;
  const page = await browser.newPage({ viewport: { width: +(process.env.W || 1280), height: +(process.env.H || 720) } });
  page.on('console', (m) => { if (m.type() === 'error') console.log(`[${name}] console error: ${m.text()}`); });
  page.on('pageerror', (e) => console.log(`[${name}] page error: ${e.message}`));
  // the Hall opens its guide by itself the first time; every shot but the guide's own has seen it
  if (name !== 'guide_first') await page.addInitScript(() => { try { localStorage.setItem('cobbletowers.guideSeen', '1'); } catch (err) { /* none */ } });
  if (shot.look) await page.addInitScript((look) => { try { localStorage.setItem('cobbletowers.ui', JSON.stringify({ look })); } catch (err) { /* none */ } }, shot.look);
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
