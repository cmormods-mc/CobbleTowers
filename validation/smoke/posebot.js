// A player that joins and keeps a pose or motion, so what other clients see can be photographed: mode "spin" turns the head
// and body steadily, "sneak" holds sneak, "walk" paces back and forth. mineflayer comes from NODE_PATH like joinbot.js.
const mineflayer = require('mineflayer');

const [, , username, port, mode = 'spin'] = process.argv;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username, version: '1.21.1', auth: 'offline' });

require('./skipbad')(bot);
bot.on('kicked', (reason) => console.log('kicked: ' + reason));
bot.on('error', (err) => console.log('error: ' + err.message));
bot.once('spawn', () => {
  console.log('spawned');
  if (mode === 'sneak') bot.setControlState('sneak', true);
  let yaw = 0;
  let step = 0;
  setInterval(() => {
    step++;
    if (mode === 'fast') {
      yaw += 0.3;
      bot.look(yaw, 0, true);
    } else if (mode === 'spin') {
      yaw += 0.06;
      bot.look(yaw, 0.3 * Math.sin(step / 20), true);
    } else if (mode === 'walk') {
      bot.setControlState('forward', Math.floor(step / 16) % 2 === 0);
      bot.setControlState('back', Math.floor(step / 16) % 2 === 1);
    }
  }, 50);
});
