// Real rendering regressions for large reward sets and extended scouting previews.
import { chromium } from 'playwright-core';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const fixture = k => JSON.parse(fs.readFileSync(path.join(root, 'fixtures', k + '.json'))).data;
const out = process.env.LAYOUT_OUT || path.join(root, 'test/layout');
fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch();
let failures = 0;
const check = (name, ok) => { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`); if (!ok) failures++; };
for (const [width, height] of [[1280,720], [1920,1080], [2560,1440], [1024,768]]) {
 const p = await browser.newPage({viewport:{width,height}});
 const errors=[]; p.on('pageerror', e=>errors.push(e.message));
 await p.addInitScript(() => { window.webgui={client:{username:'WebPlayer'}}; localStorage.setItem('cobbletowers.guideSeen','1'); localStorage.setItem('cobbletowers.ui',JSON.stringify({look:'desk',reducedMotion:true})); });
 await p.goto(pathToFileURL(path.join(root,'dist-dev/index.html')).href);
 await p.waitForFunction(()=>window.TowerDev);
 const send = (k,d)=>p.evaluate(([k,d])=>window.TowerDev.dispatch(k,d),[k,d]);
 const inter=fixture('IntermissionState');
 inter.extras.route.ahead=Array.from({length:6},(_,i)=>({floor:9+i,milestone:i===1,kinds:['POOL','BATTLE','LIBRARY']}));
 await send('IntermissionState',inter); await p.waitForTimeout(600);
 const overlap=await p.evaluate(()=>{
   const rects=[...document.querySelectorAll('.dhead,.dstop')].map(e=>e.getBoundingClientRect());
   return rects.some((a,i)=>rects.slice(i+1).some(b=>Math.min(a.right,b.right)-Math.max(a.left,b.left)>1 && Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>1));
 });
 check(`${width}x${height}: route headings and stops do not overlap`,!overlap);
 await p.screenshot({path:path.join(out,`route-${width}.png`)});
 await p.locator('.droute').hover(); await p.mouse.wheel(0,1000); await p.waitForTimeout(100);
 check(`${width}x${height}: wheel reaches distant scouting columns`, await p.locator('.droute').evaluate(e=>e.scrollLeft>0));
 const reward=fixture('RewardReveal'); reward.grants=Array.from({length:72},(_,i)=>({...reward.grants[i%reward.grants.length],label:'Reward '+(i+1)}));
 await send('RewardReveal',reward); await p.waitForTimeout(600);
 check(`${width}x${height}: Continue fits inside reward dialog`,await p.evaluate(()=>{const m=document.querySelector('.dreward').getBoundingClientRect(),b=[...document.querySelectorAll('.dreward button')].find(e=>e.textContent==='Continue').getBoundingClientRect();return b.bottom<=m.bottom && b.top>=m.top && m.bottom<=innerHeight;}));
 const chips=p.locator('.dchips');
 await chips.hover(); await p.mouse.wheel(0,2000); await p.waitForTimeout(100);
 check(`${width}x${height}: mouse wheel scrolls contained loot`,await chips.evaluate(e=>e.scrollTop>0 && getComputedStyle(e).overflowY==='auto'));
 await p.locator('.dchip').last().scrollIntoViewIfNeeded(); await p.locator('.dchip').last().click();
 check(`${width}x${height}: last reward selectable`,await p.locator('.dpager').textContent().then(t=>t.includes('72 / 72')));
 await p.waitForTimeout(500);
 await p.screenshot({path:path.join(out,`reward-${width}.png`)});
 check(`${width}x${height}: no browser errors`,errors.length===0);
 await p.close();
}
await browser.close();
process.exitCode=failures?1:0;
