// A player that joins and right-clicks the nearest villager when stdin says "use" (vendor_npc_test.py, P28). Reach is
// 6 blocks, so the test teleports the bot next to the vendor first. mineflayer comes from the rig's node_modules via
// NODE_PATH.
const mineflayer = require('mineflayer');

const [, , username, port] = process.argv;
const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: Number(port),
  username,
  version: '1.21.1',
  auth: 'offline',
});

bot.on('spawn', () => {
  // The tower is a void to a bot that has no chunks, so with physics on it falls away from wherever the test
  // teleported it and the click is out of reach by the time it lands. Hold it where the server puts it.
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
    const vendor = bot.nearestEntity((e) => e.name === 'villager');
    if (!vendor) { console.log('no villager in sight'); continue; }
    console.log('me ' + bot.entity.position + ' vendor ' + vendor.position + ' id ' + vendor.id);
    bot.activateEntity(vendor)
      .then(() => console.log('used villager'))
      .catch((err) => console.log('use failed: ' + err.message));
  }
});
