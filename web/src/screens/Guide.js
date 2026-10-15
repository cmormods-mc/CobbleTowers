import { html } from '../html.js';
import DATA from '../gen/data.js';
import { at, box, Panel } from '../components/native.js';

const KEY = 'cobbletowers.guideSeen';

/** True the first time this browser shows the Hall (and marks it seen); false when storage is unavailable or it was seen. */
export function firstVisit() {
  try {
    if (localStorage.getItem(KEY)) return false;
    localStorage.setItem(KEY, '1');
    return true;
  } catch (err) {
    return false;
  }
}

/** The guide as a flat list of headings and lines, for drawing and for tests. */
export function guideLines(guide = DATA.guide) {
  const out = [{ kind: 'title', text: guide.title }];
  for (const section of guide.sections) {
    out.push({ kind: 'heading', text: section.heading });
    for (const line of section.lines) out.push({ kind: 'line', text: line });
  }
  return out;
}

/** The how-it-works page of the Hall: one scrolling parchment. The same text is /tower help in chat. */
export function Guide() {
  const b = box();
  return html`<${Panel} x=${b.x} y=${b.y} w=${b.w} h=${b.bottom - b.y + 20} kind="parch">
    <div class="n-lines scroll" style=${at(6, 6, b.w - 14, b.bottom - b.y + 8)}>${guideLines().map((l, i) => (l.kind === 'line'
      ? html`<p key=${i}>${l.text}</p>` : html`<p key=${i}><b class=${l.kind === 'title' ? 'c-burg' : 'c-soft'}>${l.text}</b></p>`))}</div>
  <//>`;
}
