// A player that walks, for walk_tower_test.py (P55): stdin lines drive it, stdout reports.
//   goto <x> <z> [seconds]   walk in a straight line to (x, z) with real physics; prints "ARRIVED x y z" or "TIMEOUT x y z"
//   where                    prints "AT x y z"
// Needs mineflayer and vec3 on NODE_PATH. It jumps only when it is pushed against something, so a stair is climbed by walking.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const [, , username, port] = process.argv;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username, version: '1.21.1', auth: 'offline' });
require('./skipbad')(bot);
bot.on('spawn', () => console.log('spawned'));
bot.on('kicked', (reason) => console.log('kicked: ' + reason));
bot.on('error', (err) => console.log('error: ' + err.message));

let timer = null;
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
  for (const control of ['forward', 'jump', 'sprint']) bot.setControlState(control, false);
}
const where = () => { const p = bot.entity.position; return `${p.x.toFixed(2)} ${p.y.toFixed(2)} ${p.z.toFixed(2)}`; };

function goTo(x, z, seconds) {
  stop();
  const deadline = Date.now() + seconds * 1000;
  timer = setInterval(() => {
    const p = bot.entity.position;
    const dx = x - p.x;
    const dz = z - p.z;
    if (Math.hypot(dx, dz) < 0.45) { stop(); console.log('ARRIVED ' + where()); return; }
    if (Date.now() > deadline) { stop(); console.log('TIMEOUT ' + where()); return; }
    bot.lookAt(new Vec3(x, p.y + 1.62, z), true);
    bot.setControlState('forward', true);
    bot.setControlState('jump', bot.entity.isCollidedHorizontally);
  }, 50);
}

process.stdin.setEncoding('utf8');
let pending = '';
process.stdin.on('data', (chunk) => {
  pending += chunk;
  const lines = pending.split('\n');
  pending = lines.pop();
  for (const line of lines) {
    const [command, ...args] = line.trim().split(/\s+/);
    if (command === 'goto') goTo(Number(args[0]), Number(args[1]), Number(args[2] || 40));
    else if (command === 'where') console.log('AT ' + where());
    else if (command === 'stop') stop();
  }
});
