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
  // the play-state deserializer is a new object once the login finishes
  bot._client.on('state', patch);
};
