/** "[+] Loot swells" -> {sign: '+', text: 'Loot swells', tone: 'benefit'}; benefits green, costs red (P41). */
export function parseLine(line) {
  const m = /^\[([+-])\]\s*(.*)$/.exec(line);
  const dots = (t) => t.replace(/\s+\*\s+/g, ' · ');
  if (!m) return { sign: '', text: dots(line), tone: 'neutral' };
  return { sign: m[1] === '+' ? '+' : '−', text: dots(m[2]), tone: m[1] === '+' ? 'benefit' : 'cost' };
}
