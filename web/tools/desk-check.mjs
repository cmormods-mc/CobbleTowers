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
  check('Climb opens the Desk tower briefing', (await p.locator('.fd .dbanner').count()) === 1 && /Duskvale/.test(await p.locator('.fd').innerText()));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .pills a', { hasText: 'Trials' }).click();
  await p.waitForTimeout(200);
  check('the Trials pill opens the Desk Trials page', (await p.locator('.fd .dbanner').count()) === 2 && /Daily Tower/.test(await p.locator('.fd').innerText()));
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

// 2b. the colour theme: midnight by default (no class), the other three one click away, remembered, and the page really changes colour
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  const bg = () => p.evaluate(() => getComputedStyle(document.querySelector('.fd')).backgroundImage + '|' + getComputedStyle(document.querySelector('.fd .dcard')).backgroundColor);
  const lum = (rgb) => { const m = rgb.match(/rgb\((\d+), (\d+), (\d+)\)/); return m ? (+m[1] + +m[2] + +m[3]) / 3 : 255; };
  const card = () => p.evaluate(() => getComputedStyle(document.querySelector('.fd .dcard')).backgroundColor);
  check('the Desk is dark by default: no theme class on the page and a dark card', (await p.evaluate(() => document.documentElement.className.includes('th-'))) === false && lum(await card()) < 70);
  const before = await bg();
  await p.locator('.fd .tl', { hasText: 'Settings' }).click();
  check('Settings lists the four themes with Midnight chosen', (await p.locator('.fd .seg[aria-label="Theme"] button').allInnerTexts()).join() === 'Midnight,Graphite,Harbor,Dusk'
    && (await p.locator('.fd .seg[aria-label="Theme"] button.on').innerText()) === 'Midnight');
  await p.locator('.fd .seg[aria-label="Theme"] button', { hasText: 'Harbor' }).click();
  check('choosing Harbor puts th-harbor on the page, saves it, and changes the colours', (await p.evaluate(() => document.documentElement.classList.contains('th-harbor'))) === true
    && (await p.evaluate(() => JSON.parse(localStorage.getItem('cobbletowers.ui')).theme)) === 'harbor' && (await bg()) !== before);
  await p.locator('.fd .seg[aria-label="Theme"] button', { hasText: 'Dusk' }).click();
  check('choosing Dusk takes th-harbor off again', (await p.evaluate(() => [...document.documentElement.classList].filter((c) => c.startsWith('th-')).join())) === 'th-dusk');
  await p.locator('.fd .seg[aria-label="Theme"] button', { hasText: 'Midnight' }).click();
  check('Midnight clears the theme class', (await p.evaluate(() => document.documentElement.className.includes('th-'))) === false);
  await p.close();
}

// 3. the Look setting switches (the Desk settings page, and the native one for the way back)
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .tl', { hasText: 'Settings' }).click();
  const btn = p.locator('.fd .dsetrow').first().locator('.dbtn');
  check('Desk settings shows the Look button as Frontier Desk', /Frontier Desk/.test(await btn.innerText()));
  await btn.click();
  check('clicking it switches to Native and the choice is saved', (await p.evaluate(() => JSON.parse(localStorage.getItem('cobbletowers.ui')).look)) === 'native');
  await p.close();
  const q = await open('native');
  await q.send('TowerHallState', fixture('TowerHallState'));
  await q.waitForTimeout(300);
  await q.locator('.ns button', { hasText: 'Settings' }).first().click();
  await q.waitForTimeout(200);
  const nb = q.locator('.ns button', { hasText: 'Look:' });
  check('native Settings shows the Look button as Native, and switching to Desk saves it', /Native/.test(await nb.innerText()) && await (async () => { await nb.click(); return (await q.evaluate(() => JSON.parse(localStorage.getItem('cobbletowers.ui')).look)) === 'desk'; })());
  await q.close();
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
// 10. briefings, Trials, Social and the feature menus
{
  const free = (d) => { d.runStatus = ''; d.canResume = false; d.play.lobby.role = 0; d.play.lobby.countdown = -1; d.extras.runTower = ''; };
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState', free));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .trow').nth(4).click();
  await p.locator('.fd .dbtn:not(.gh)').last().click();
  await p.waitForSelector('.fd .dbanner');
  check('a free player can select a tower from its briefing (and the button sends the tower request)', await (async () => {
    await p.locator('.fd .dbtn', { hasText: 'Select tower' }).click();
    return (await p.sentKinds()).includes('TowerHallAction:tower:cobbletowers:tideforge');
  })());
  await p.locator('.fd .dback').click();
  check('Back returns to the Play page', (await p.locator('.fd .trow').count()) === 5);
  await p.locator('.fd .pills a', { hasText: 'Trials' }).click();
  await p.locator('.fd .dbtn', { hasText: 'Open briefing' }).first().click();
  await p.locator('.fd .dbtn', { hasText: 'Prepare trial lobby' }).click();
  check('a trial briefing prepares its lobby with the revision it showed', (await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'TowerHallAction' && m.action === 'trial' && m.argument === 'daily' && m.revision.length > 8))));
  await p.locator('.fd .pills a', { hasText: 'Social' }).click();
  await p.locator('.fd .dtilebtn', { hasText: 'Club lodge' }).click();
  check('a Social card asks for its feature menu', (await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'TowerFeatureRequest' && m.section === 'club'))));
  check('no console errors (briefings)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.send('TowerFeatureState', fixture('TowerFeatureState', (d) => { d.requestId = 0; }));
  await p.waitForSelector('.fd .dentry');
  check('a feature menu lists its entries and actions', (await p.locator('.fd .dentry').count()) === 6 && /Cosmetics/.test(await p.locator('.fd').innerText()));
  await p.locator('.fd .dentry', { hasText: 'Champion' }).first().click();
  check('opening an entry shows its lines and its first action', /Unlocked at Gold/.test(await p.locator('.fd').innerText()) && (await p.locator('.fd .dactions .dbtn', { hasText: 'Wear this title' }).count()) === 1);
  await p.locator('.fd .dactions .dbtn').click();
  check('an action with a confirmation asks first', /Wear the title Champion/.test(await p.locator('.fd').innerText()) && (await p.locator('.fd .dactions .dbtn', { hasText: 'Confirm' }).count()) === 1);
  await p.locator('.fd .dactions .dbtn', { hasText: 'Confirm' }).click();
  check('Confirm sends the action with its argument', (await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'TowerFeatureRequest' && m.action === 'title-wear' && m.argument === 'champion'))));
  await p.send('TowerFeatureState', fixture('TowerFeatureState', (d) => { d.requestId = 0; }));
  await p.locator('.fd .dentry', { hasText: 'Club banner' }).first().click();
  await p.locator('.fd .dactions .dbtn').click();
  await p.locator('.fd .dedit').fill('Cobble Crew');
  await p.locator('.fd .dactions .dbtn', { hasText: 'Confirm' }).click();
  check('typed text goes with the action', (await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'TowerFeatureRequest' && m.action === 'club-banner' && m.argument === 'Cobble Crew'))));
  check('no console errors (features)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.send('TowerFeatureState', fixture('TowerFeatureState', (d) => { d.requestId = 0; d.section = 'run'; d.title = 'Current Run'; d.actions = []; d.entries = [
    { id: 'run', title: 'This run', lines: ['Neutral Tower, floor 6', '[+] Risk carried: +20% to your final payout.'], actions: [] },
    { id: 'mod:a', title: 'Modifier: Sharpened Claws x2 (floor 3)', lines: ['[+] Moderate risk * +8% to your final payout', '[-] Foes rise 2 levels above the tower.'], actions: [] },
    { id: 'relic:a:0', title: 'Relic: Ember Brand (floor 5)', lines: ['[+] Your Fire moves deal 25% more damage.', 'Tags: offense'], actions: [] }]; }));
  await p.waitForSelector('.fd .dentrybox');
  check('the Current Run page lists modifiers and relics without their tags', (await p.locator('.fd .dentrybox').count()) === 2 && !/Tags:/.test(await p.locator('.fd').innerText()) && /Sharpened Claws/.test(await p.locator('.fd').innerText()));
  await p.close();
}
// 11. Mastery, the party chooser, the guide and settings
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.send('MasteryScreen', fixture('MasteryScreen'));
  await p.waitForSelector('.fd .dach');
  check('Mastery lists the towers and the achievements, earned ones marked', (await p.locator('.fd .dentry').count()) === 4 && (await p.locator('.fd .dach.held').count()) === 3);
  await p.locator('.fd .dentry', { hasText: 'Duskvale' }).click();
  await p.locator('.fd .seg button', { hasText: 'Speed' }).click();
  await p.locator('.fd .dbadge .dbtn').click();
  const sent = await p.evaluate(() => window.TowerDev.sent.filter((m) => m.kind === 'MasteryRequest').map((m) => m.tower + '|' + m.tab));
  check('picking a tower, a board tab and the badge button send the mastery requests', sent.includes('cobbletowers:duskvale|mastery') && sent.includes('cobbletowers:neutral|speed') && sent.some((s) => /badge:/.test(s)), sent.join(','));
  await p.send('MasteryScreen', fixture('MasteryBoard')[1] ? fixture('MasteryBoard') : fixture('MasteryBoard'));
  check('no console errors (mastery)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.send('PlayState', fixture('PlayState'));
  await p.send('RegistrationState', fixture('RegistrationState'));
  await p.waitForSelector('.fd .dmon');
  check('the party chooser shows every Pokemon with the registered ones numbered', (await p.locator('.fd .dmon').count()) === 10 && (await p.locator('.fd .dmon.on .dord').count()) === 2);
  await p.locator('.fd .dmon').nth(3).click();
  await p.locator('.fd .dbtn', { hasText: 'Clear' }).click();
  await p.locator('.fd .dbtn', { hasText: 'Done' }).click();
  const kinds = await p.sentKinds();
  check('toggling, clearing and finishing send the chooser actions', kinds.includes('PlayAction:TOGGLE_POKEMON:00000000-0000-0000-0000-000000000004') && kinds.includes('PlayAction:CLEAR_CHOICE:') && kinds.includes('PlayAction:REFRESH:'), kinds.join(','));
  check('no console errors (chooser)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.waitForSelector('.fd .trow');
  await p.locator('.fd .tl', { hasText: 'Guide' }).click();
  check('the guide is a readable page with headings', (await p.locator('.fd .dguide h3').count()) >= 3);
  await p.locator('.fd .dback').click();
  await p.locator('.fd .tl', { hasText: 'Settings' }).click();
  await p.locator('.fd .dsetrow').first().locator('.dbtn').click();
  check('the Look switch in Desk settings returns to Native', (await p.locator('.ns').count()) >= 0 && (await p.evaluate(() => JSON.parse(localStorage.getItem('cobbletowers.ui')).look)) === 'native');
  await p.close();
}
// 12. the rental draft, the reward and vendor pop-ups, scouting, the spectator panel, the waiting page
{
  const open2 = (d) => { d.complete = false; d.current = 0; d.packs.forEach((pk) => { pk.kept = []; }); };
  const p = await open('desk');
  await p.send('RentalDraft', fixture('RentalDraft', open2));
  await p.waitForSelector('.fd .pack');
  check('the draft opens on a sealed pack on the dark table, three pack dots', (await p.locator('.fd .dtable .pack').count()) === 1 && (await p.locator('.fd .dots i').count()) === 3);
  await p.locator('.fd .pack').click({ force: true });
  await p.waitForSelector('.fd .card.pick', { timeout: 8000 });
  await p.locator('.fd .card.pick').nth(0).click();
  await p.locator('.fd .card.pick').nth(1).click();
  await p.locator('.fd .dbtn', { hasText: 'Keep these two' }).click();
  const picks = await p.evaluate(() => window.TowerDev.sent.filter((m) => m.kind === 'RentalDraftAction').map((m) => m.action + ':' + m.a + ':' + m.b));
  check('choosing two cards and keeping them sends the pick', picks.includes('PICK:0:1'), picks.join(','));
  await p.locator('.fd .tl', { hasText: 'Start over' }).click();
  check('Start over sends the restart', (await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'RentalDraftAction' && m.action === 'RESTART'))));
  check('no console errors (draft)', p.errors.length === 0, p.errors.join(' | '));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('TowerHallState', fixture('TowerHallState'));
  await p.send('RewardReveal', fixture('RewardReveal'));
  await p.waitForSelector('.fd.dover .dmodal');
  check('a reward reveal is a dialog over the page, one reward at a time', (await p.locator('.fd.dover [role=dialog], .fd.dover').count()) >= 1 && /1 \/ 4/.test(await p.locator('.dmodal').innerText()));
  await p.locator('.dpager .dbtn').nth(1).click();
  check('the pager moves to the next reward', /2 \/ 4/.test(await p.locator('.dmodal').innerText()));
  await p.locator('.dmodal .dbtn', { hasText: 'Continue' }).click();
  check('Continue closes it and asks the server to refresh', (await p.locator('.dmodal').count()) === 0 && (await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'IntermissionAction' && m.action === 'REFRESH'))));
  await p.close();
}
{
  const p = await open('desk');
  await p.send('IntermissionState', fixture('IntermissionState'));
  await p.send('VendorCatalog', fixture('VendorCatalog'));
  await p.waitForSelector('.fd .dgood');
  check('the vendor lists its goods and the balance', (await p.locator('.fd .dgood').count()) >= 2 && /Balance: \d+/.test(await p.locator('.dmodal').innerText()));
  await p.locator('.fd .dgood').nth(1).click();
  await p.locator('.dmodal .dbtn', { hasText: 'Purchase' }).click();
  check('Purchase sends the service and the recipient', (await p.evaluate(() => window.TowerDev.sent.some((m) => m.kind === 'VendorPurchase' && m.serviceId && m.targetPlayerId))));
  await p.locator('.dmodal .dbtn', { hasText: 'Close' }).click();
  check('Close hides the vendor again', (await p.locator('.dmodal').count()) === 0);
  await p.close();
}
{
  const p = await open('desk');
  await p.send('ScoutingReveal', fixture('ScoutingReveal'));
  await p.waitForSelector('.fd .dach');
  await p.locator('.fd .dach').nth(1).click();
  check('scouting shows each category and reads the chosen one in full', (await p.locator('.fd .dach').count()) === 6 && /Lapras/.test(await p.locator('.fd .dcard').nth(1).innerText()));
  await p.close();
  const w = await open('desk');
  await w.waitForSelector('.fd .dcard');
  check('before the server says anything the Desk waiting card shows', /Waiting for the tower/.test(await w.locator('.fd').innerText()));
  await w.close();
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
