// A player that right-clicks the nearest interaction entity (a modifier pedestal's click box) when stdin says "use"
// (pedestal_test.py, P55). The test teleports the bot next to the pedestal first; reach is limited.
const mineflayer = require('mineflayer');

const [, , username, port] = process.argv;
const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: Number(port),
  username,
  version: '1.21.1',
  auth: 'offline',
});

require('./skipbad')(bot);
bot.on('spawn', () => {
  bot.physicsEnabled = false;
  if (bot.physics) bot.physics.enabled = false;
  console.log('spawned');
});
bot.on('kicked', (reason) => console.log('kicked: ' + reason));
bot.on('error', (err) => console.log('error: ' + err.message));

process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  for (const line of chunk.split('\n')) {
    if (line.trim() !== 'use') continue;
    const box = bot.nearestEntity((e) => e.name === 'interaction');
    if (!box) { console.log('no interaction entity in sight'); continue; }
    console.log('me ' + bot.entity.position + ' box ' + box.position + ' id ' + box.id);
    bot.activateEntity(box)
      .then(() => console.log('used interaction'))
      .catch((err) => console.log('use failed: ' + err.message));
  }
});
