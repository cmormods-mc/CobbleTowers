// A player that joins and does nothing.
//
// The run commands take a player selector, so a durability test needs somebody on the server --
// but nothing about this test needs them to act. mineflayer comes from the CobbleRaids rig's
// node_modules via NODE_PATH; this repo deliberately vendors no node dependencies of its own.
const mineflayer = require('mineflayer');

const [, , username, port] = process.argv;
const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: Number(port),
  username,
  version: '1.21.1',
  auth: 'offline',
});

bot.on('spawn', () => console.log('spawned'));
bot.on('kicked', (reason) => console.log('kicked: ' + reason));
// Cobblemon's entity metadata is not in mineflayer's vanilla protocol definitions, so parse errors
// are expected and routine here; this bot never reads the world.
bot.on('error', (err) => console.log('error: ' + err.message));
