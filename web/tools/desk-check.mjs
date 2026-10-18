// Drives the dev build in a real browser and checks what the screenshots cannot: clicks, the Look setting, the messages sent to the
// server, and that nothing logs an error.   node build.mjs --dev && node tools/desk-check.mjs
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium } from 'playwright-core';

const here = path.dirname(fileURLToPath(import.meta.url));
const url = pathToFileURL(path.resolve(here, '../dist-dev/index.html')).href;
const fixture = (kind, mutate) => {
  const d = JSON.parse(fs.readFileSync(path.resolve(here, '../fixtures', kind + '.json'), 'utf8')).data;
  if (mutate) mutate(d);
  return d;
};

let failed = 0;
const check = (name, ok, extra = '') => { if (!ok) failed++; console.log((ok ? 'ok   ' : 'FAIL ') + name + (ok ? '' : '  ' + extra)); };

const browser = await chromium.launch();
async function open(look, size = { width: 1280, height: 720 }) {
  const page = await browser.newPage({ viewport: size });
  const errors = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push(e.message));
  await page.addInitScript((l) => { window.webgui = { client: { username: 'WebPlayer' } }; try { localStorage.setItem('cobbletowers.guideSeen', '1'); if (l) localStorage.setItem('cobbletowers.ui', JSON.stringify({ look: l })); } catch (e) { /* none */ } }, look);
  await page.goto(url);
  await page.waitForFunction(() => window.TowerDev);
  page.errors = errors;
  page.send = (kind, data) => page.evaluate(([k, d]) => window.TowerDev.dispatch(k, d), [kind, data]);
  page.sentKinds = () => page.evaluate(() => window.TowerDev.sent.filter((m) => m.kind !== 'ping' && m.kind !== 'hello').map((m) => m.kind + ':' + (m.action ?? '') + ':' + (m.argument ?? '')));
  return page;
}

// 1. the native look is still the default and is not touched
{
  const p = await open(null);
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForTimeout(300);
  check('default look is native on the Hall', (await p.locator('.ns').count()) > 0 && (await p.locator('.fd').count()) === 0);
  await p.send('IntermissionState', fixture('IntermissionState'));
  await p.waitForTimeout(200);
  check('default look is native between floors', (await p.locator('.ns').count()) > 0 && (await p.locator('.fd').count()) === 0);
  check('no console errors (native)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}

// 2. the Desk Hall: draws, selects, opens a briefing, switches page
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  check('desk Hall draws the team (6 Pokemon)', (await p.locator('.fd .mon').count()) === 6);
  check('desk Hall draws a row per tower', (await p.locator('.fd .trow').count()) === 5);
  check('the run tower is selected first', (await p.locator('.fd .trow.sel b').first().innerText()) === 'Neutral');
  await p.locator('.fd .trow').nth(0).click();
  check('clicking a row selects it and the Climb button follows', /Climb Duskvale/.test(await p.locator('.fd .dbtn:not(.gh)').last().innerText()));
  check('one row is selected', (await p.locator('.fd .trow.sel').count()) === 1);
  await p.locator('.fd .trow').nth(0).focus();
  await p.keyboard.press('Enter');
  check('a row is a real button (keyboard Enter keeps it selected)', (await p.locator('.fd .trow.sel').count()) === 1);
  await p.locator('.fd .dbtn:not(.gh)').last().click();
  await p.waitForTimeout(200);
  check('Climb opens the native tower briefing', (await p.locator('.ns').count()) > 0 && /Duskvale/.test(await p.locator('.ns').innerText()));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .pills a', { hasText: 'Trials' }).click();
  await p.waitForTimeout(200);
  check('the Trials pill opens the native Trials page', (await p.locator('.ns').count()) > 0 && (await p.locator('.fd').count()) === 0);
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .tl', { hasText: 'Refresh' }).click();
  await p.locator('.fd .dbtn', { hasText: 'Return to run' }).click();
  const kinds = await p.sentKinds();
  check('Refresh and Return to run send the same hall actions as the native page', kinds.includes('TowerHallAction:refresh:') && kinds.includes('TowerHallAction:resume:'), kinds.join(','));
  await p.close();
}

// 3. the Look setting switches in both directions
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .tl', { hasText: 'Settings' }).click();
  await p.waitForTimeout(200);
  const btn = p.locator('.ns button', { hasText: 'Look:' });
  check('Settings shows the Look button as Frontier Desk', /Frontier Desk/.test(await btn.innerText()));
  await btn.click();
  check('clicking it switches to Native', /Native/.test(await p.locator('.ns button', { hasText: 'Look:' }).innerText()));
  check('and the choice is saved', (await p.evaluate(() => JSON.parse(localStorage.getItem('cobbletowers.ui')).look)) === 'native');
  await p.close();
}

// 4. the Desk intermission (a plain draft, no route): choose, confirm, ready, cash out
{
  const p = await open('desk');
  await p.send('IntermissionState', fixture('IntermissionState', (d) => { d.extras.route = { lane: -1, now: [], ahead: [] }; }));
  await p.waitForSelector('.fd .dmc');
  check('three offers, none selected, Confirm asks to choose', (await p.locator('.fd .dmc').count()) === 3 && (await p.locator('.fd .dmc.sel').count()) === 0 && /Choose a card/.test(await p.locator('.fd .dft .dbtn').first().innerText()));
  await p.locator('.fd .dmc').nth(1).click();
  check('choosing an offer selects it and enables Confirm', (await p.locator('.fd .dmc.sel').count()) === 1 && !(await p.locator('.fd .dft .dbtn').first().isDisabled()));
  await p.locator('.fd .dft .dbtn').first().click();
  await p.locator('.fd .dft .dbtn', { hasText: 'Vendor' }).click();
  await p.locator('.fd .dft .dbtn', { hasText: 'Cash out' }).click();
  const kinds = await p.sentKinds();
  check('Confirm, Vendor and Cash out send the intermission actions', kinds.includes('IntermissionAction:PICK_CARD:1') && kinds.includes('IntermissionAction:VENDOR:0') && kinds.includes('IntermissionAction:CASH_OUT:0'), kinds.join(','));
  check('the floor ladder shows ten floors with two milestones', (await p.locator('.fd .dnd').count()) === 10 && (await p.locator('.fd .dnd.ms').count()) === 2);
  // a settled draft shows the chosen card and a refresh keeps the player's choice
  await p.send('IntermissionState', fixture('IntermissionState', (d) => { d.extras.route = { lane: -1, now: [], ahead: [] }; d.draft.state = 2; d.draft.chosen = 2; d.draft.cards[2].votes = 2; }));
  await p.waitForTimeout(150);
  check('a settled draft marks the chosen card and disables the offers', (await p.locator('.fd .dmc.chosen').count()) === 1 && (await p.locator('.fd .dmc:disabled').count()) === 3);
  check('no console errors (desk)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
// 4b. the route map: stops, lanes, voting a stop, a blocked lane, the older server
{
  const p = await open('desk');
  await p.send('IntermissionState', fixture('IntermissionState'));
  await p.waitForSelector('.fd .dstop.now');
  check('a route step draws the map: three stops to choose and the scouted steps after them', (await p.locator('.fd .dstop.now').count()) === 3 && (await p.locator('.fd .dstop.ahead').count()) === 10 && (await p.locator('.fd .dchoices').count()) === 0);
  check('the stops name their kind, their modifier and their payout', /Forge/.test(await p.locator('.fd .dstop.now').nth(1).innerText()) && /Empty Pockets/.test(await p.locator('.fd .dstop.now').nth(1).innerText()) && /\+8%/.test(await p.locator('.fd .dstop.now').nth(1).innerText()));
  await p.locator('.fd .dstop.now').nth(2).click();
  check('choosing a stop selects it, shows its details (with what comes after) and enables Confirm', (await p.locator('.fd .dstop.now.sel').count()) === 1 && /Rest room on the way/.test(await p.locator('.fd .dside').innerText()) && !(await p.locator('.fd .dft .dbtn').first().isDisabled()));
  await p.locator('.fd .dft .dbtn').first().click();
  const kinds = await p.sentKinds();
  check('Confirm votes the card that rides on that stop', kinds.includes('IntermissionAction:PICK_CARD:2'), kinds.join(','));
  await p.send('IntermissionState', fixture('IntermissionState', (d) => { d.floor = 3; d.extras.route.lane = 0; d.extras.route.now = [{ lane: 0, card: 0, kind: 'SHRINE' }, { lane: 1, card: 1, kind: 'BATTLE' }]; d.draft.cards = d.draft.cards.slice(0, 2); }));
  await p.waitForTimeout(150);
  check('a lane the party cannot reach is blocked and cannot be clicked', (await p.locator('.fd .dstop.blocked').count()) >= 1 && (await p.locator('.fd .dstop.now').count()) === 2);
  await p.send('IntermissionState', fixture('IntermissionState', (d) => { d.draft.state = 2; d.draft.chosen = 1; }));
  await p.waitForTimeout(150);
  check('once settled the stops are disabled and the chosen one is marked', (await p.locator('.fd .dstop.now:disabled').count()) === 3 && (await p.locator('.fd .dstop.now.chosen').count()) === 1);
  check('no console errors (route)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('IntermissionState', fixture('IntermissionState', (d) => { delete d.extras.route; }));
  await p.waitForSelector('.fd .dmc');
  check('an older server with no route draws the plain offers', (await p.locator('.fd .dmc').count()) === 3 && (await p.locator('.fd .dstop').count()) === 0 && p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
// 5. an intermission with no extras at all (an older server) still draws
{
  const p = await open('desk');
  await p.send('IntermissionState', fixture('IntermissionState', (d) => { delete d.extras; }));
  await p.waitForSelector('.fd .dmc');
  check('no extras: offers still draw, no ladder, no crash', (await p.locator('.fd .dnd').count()) === 0 && p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState', (d) => { delete d.extras; }));
  await p.waitForSelector('.fd .trow');
  check('Hall with no extras (an older server) draws towers and an empty team', (await p.locator('.fd .trow').count()) === 5 && (await p.locator('.fd .empty').count()) >= 1 && p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
// 7. the Desk lobby: seats, your party, and every button sends what the native lobby sends
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.send('PlayState', fixture('PlayState', (d) => { d.lobby.members = [{ name: 'Ash', accepted: true, ready: false }]; d.lobby.options.modes.readiness = { confirmed: true, drafted: true, mine: false, host: true }; }));
  await p.waitForSelector('.fd .dseat');
  check('lobby draws four seats with the host first and you marked', (await p.locator('.fd .dseat').count()) === 4 && /\(you\)/.test(await p.locator('.fd .dseat').first().innerText()));
  check('your seat shows your party (6 sprites)', (await p.locator('.fd .dseat.me .dseatparty .sp').count()) === 6);
  check('the other seat has no party, only a state', (await p.locator('.fd .dseat').nth(1).locator('.dseatparty').count()) === 0);
  await p.locator('.fd .trow.dl').nth(1).click();
  await p.locator('.fd .seg button', { hasText: 'Monotype' }).click();
  await p.locator('.fd .dedit').fill('Misty');
  await p.keyboard.press('Enter');
  await p.locator('.fd .dbtn', { hasText: 'Ready up' }).click();
  await p.locator('.fd .dstart .dbtn').first().click();
  const kinds = await p.sentKinds();
  const want = ['PlayAction:SELECT_TOWER:cobbletowers:rootvale', 'PlayAction:SET_PLAYLIST:monotype', 'PlayAction:INVITE:Misty', 'PlayAction:READY:', 'PlayAction:START:'];
  check('tower, mode, invite, ready and start send the lobby actions', want.every((w) => kinds.includes(w)), kinds.join(','));
  await p.send('PlayState', fixture('PlayState', (d) => { d.lobby.role = 2; d.lobby.members = [{ name: 'WebPlayer', accepted: false, ready: false }]; d.lobby.hostName = 'Ash'; }));
  await p.waitForTimeout(150);
  await p.locator('.fd .dbtn', { hasText: 'Accept' }).click();
  await p.locator('.fd .dbtn', { hasText: 'Decline' }).click();
  const kinds2 = await p.sentKinds();
  check('a guest can accept or decline, and cannot start or change the tower', kinds2.includes('PlayAction:ACCEPT:Ash') && kinds2.includes('PlayAction:DECLINE:Ash') && (await p.locator('.fd .dstart .dbtn', { hasText: 'Start' }).count()) === 0 && (await p.locator('.fd .trow.dl:disabled').count()) === 5);
  check('no console errors (lobby)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
// 8. a server message shows in the Desk toast on the Desk screens
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState', (d) => { d.play.message = 'Choose a destination to begin.'; }));
  await p.waitForSelector('.fd .trow');
  check('the Desk toast shows a server message and the oak toast does not', (await p.locator('.fd-toast.show').count()) === 1 && (await p.locator('.toast').count()) === 0);
  await p.close();
}
// 9. the Desk progress page: loading state, lanes, claims
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .pills a', { hasText: 'Progress' }).click();
  await p.waitForSelector('.fd .dcard.empty');
  check('Progress asks the server for the tracks and shows a loading card until they arrive', await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'TrackAction' && m.action === 'refresh')));
  await p.send('TrackState', fixture('TrackState'));
  await p.waitForSelector('.fd .dtile');
  check('the mastery lane draws every node and marks the current one', (await p.locator('.fd .dtile').count()) > 10 && (await p.locator('.fd .dtile.cur').count()) === 1);
  check('the banner counts the ready rewards', /2 rewards ready/.test(await p.locator('.fd .cont').innerText()));
  await p.locator('.fd .dtile.open').click();
  check('selecting the ready tile enables Claim', !(await p.locator('.fd .ddetail .dbtn').isDisabled()));
  await p.locator('.fd .ddetail .dbtn').click();
  await p.locator('.fd .dbtn', { hasText: 'Claim all' }).click();
  await p.locator('.fd .trow', { hasText: 'Rootvale' }).click();
  await p.locator('.fd .trow', { hasText: 'Season' }).click();
  const season = /Ends in/.test(await p.locator('.fd .ch').innerText());
  const kinds = await p.evaluate(() => window.TowerDev.sent.filter((m) => m.kind === 'TrackAction').map((m) => m.action + ':' + m.lane + ':' + m.tower + ':' + m.number));
  check('Claim, Claim all and picking a tower send the track actions; the season shows its timer', kinds.includes('claim:mastery:cobbletowers:tideforge:5') && kinds.includes('claim_all:mastery:cobbletowers:tideforge:0') && kinds.includes('refresh::cobbletowers:rootvale:0') && season, kinds.join(',') + ' season=' + season);
  await p.locator('.fd .dtile.cur').focus();
  await p.keyboard.press('Enter');
  check('tiles are real buttons: Enter selects the focused one', (await p.locator('.fd .dtile.sel').count()) === 1);
  check('no console errors (progress)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState', (d) => { delete d.extras; }));
  await p.send('TrackState', fixture('TrackState', (d) => { d.season.present = false; d.towers = d.towers.slice(0, 1); }));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .pills a', { hasText: 'Progress' }).click();
  await p.waitForSelector('.fd .dtile');
  check('progress with no season and no extras still draws one lane', (await p.locator('.fd .trow').count()) === 1 && p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
// 6. sizes: nothing overflows the window
for (const [w, h] of [[800, 500], [1280, 720], [1920, 1080], [2560, 1080]]) {
  const p = await open('desk', { width: w, height: h });
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  const over = await p.evaluate(() => ({ x: document.documentElement.scrollWidth > innerWidth + 1, y: document.documentElement.scrollHeight > innerHeight + 1 }));
  check(`Hall fits a ${w}x${h} window`, !over.x && !over.y, JSON.stringify(over));
  await p.close();
}
await browser.close();
console.log(failed ? failed + ' check(s) failed' : 'all checks passed');
process.exit(failed ? 1 : 0);
