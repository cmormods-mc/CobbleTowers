import { render } from 'preact';
import { html } from './html.js';
import { App, HudApp } from './App.js';
import { connect, isHud, watchServer } from './bridge.js';
import { dispatch, update } from './store.js';
import { apply, load } from './settings.js';
import './styles/main.css';

window.addEventListener('error', (e) => { document.title = 'ERR: ' + e.message; });

apply(load());
if (isHud) document.documentElement.classList.add('hud-mode');
render(isHud ? html`<${HudApp} />` : html`<${App} />`, document.getElementById(isHud ? 'hud-root' : 'main-root'));
connect(dispatch);
if (!isHud) watchServer((offline) => update((s) => (s.offline === offline ? s : { ...s, offline })));

// For tests and for poking a live page from the console: feed a payload as if the server had sent it.
window.TowerDev = { dispatch, update };
