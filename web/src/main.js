import { render } from 'preact';
import { html } from './html.js';
import { App, HudApp } from './App.js';
import { connect, isHud, sent, watchLibCalm, watchServer } from './bridge.js';
import { dispatch, update } from './store.js';
import { setLook } from './reducer.js';
import { apply, load, setLibCalm } from './settings.js';
import './styles/main.css';

window.addEventListener('error', (e) => { document.title = 'ERR: ' + e.message; });

apply(load());
update((s) => setLook(s, load().look));
if (isHud) document.documentElement.classList.add('hud-mode');
render(isHud ? html`<${HudApp} />` : html`<${App} />`, document.getElementById(isHud ? 'hud-root' : 'main-root'));
connect(dispatch);
watchLibCalm((on) => { setLibCalm(on); apply(load()); });
document.addEventListener('visibilitychange', () => document.documentElement.classList.toggle('paused', document.visibilityState === 'hidden'));
if (!isHud) watchServer((offline) => update((s) => (s.offline === offline ? s : { ...s, offline })));

// For tests and for poking a live page from the console: feed a payload as if the server had sent it.
window.TowerDev = { dispatch, update, sent };
