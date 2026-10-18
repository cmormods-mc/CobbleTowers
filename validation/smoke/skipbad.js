// A packet mineflayer cannot parse (an item with Polymer's data, Cobblemon's entity metadata) used to end the packet stream, so the
// bot never spawned properly and timed out 30 s after joining. Marking the error as a partial read makes protodef skip that one packet.
module.exports = function skipUnparseable(bot) {
  function patch() {
    const des = bot._client.deserializer;
    if (!des || des.__skipBad) return;
    des.__skipBad = true;
    const parse = des.parsePacketBuffer.bind(des);
    des.parsePacketBuffer = (buffer) => {
      try {
        return parse(buffer);
      } catch (err) {
        err.partialReadError = true;
        throw err;
      }
    };
  }
  patch();
  // A server that makes its resource pack required (Polymer auto-host) holds the login until the client has been through the real
  // sequence: accepted, downloaded, loaded. mineflayer's own accept sends accepted and loaded only, which that server does not take.
  bot._client.on('add_resource_pack', async (data) => {
    const send = (result) => { try { bot._client.write('resource_pack_receive', { uuid: data.uuid, result }); } catch (err) { /* the bot is gone */ } };
    send(3);
    try { const reply = await fetch(data.url); await reply.arrayBuffer(); send(4); } catch (err) { /* a pack that cannot be fetched still ends in "loaded" for a bot */ }
    send(0);
  });
  // the play-state deserializer is a new object once the login finishes
  bot._client.on('state', patch);
};
