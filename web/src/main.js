import { render } from 'preact';
import { html } from './html.js';
import { App, HudApp } from './App.js';
import { connect, isHud } from './bridge.js';
import { dispatch } from './store.js';
import './styles/main.css';

window.addEventListener('error', (e) => { document.title = 'ERR: ' + e.message; });

if (isHud) document.documentElement.classList.add('hud-mode');
render(isHud ? html`<${HudApp} />` : html`<${App} />`, document.getElementById(isHud ? 'hud-root' : 'main-root'));
connect(dispatch);

// For tests and for poking a live page from the console: feed a payload as if the server had sent it.
window.TowerDev = { dispatch };
