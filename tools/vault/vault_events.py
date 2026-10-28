"""Event manifest: sounds, particles, reward and encounter triggers, reveal-candy spawns and the auto-close request, kept OUT of the
animation file. Each event fires on a state's own tick counter (`state`, `tick` = ticks since the state was entered). `outcome` is
"any", "treat" or "trick". Until the lid is fully OPEN (state OPEN, tick 0) every event is outcome "any", so a trick and a treat are
indistinguishable on screen. Positions are model units in vault space (x 0..16, y up from the floor, z 0..16, front = low z) and are
rotated with the vault's facing by the runtime. Only vanilla sound and particle ids are used, so no client mod or pack is needed."""

ANCHORS = {"plate": [8.0, 8.2, -4.6], "pile": [8.0, 14.5, 8.0], "seam": [8.0, 13.5, -1.8], "above": [8.0, 27.0, 8.0], "floor": [8.0, 0.2, 8.0]}


def sound(state, tick, sid, vol, pitch, at, outcome="any"):
    return {"state": state, "tick": tick, "outcome": outcome, "type": "sound", "id": sid, "volume": vol, "pitch": pitch, "at": at}


def particle(state, tick, pid, count, spread, speed, at, outcome="any"):
    return {"state": state, "tick": tick, "outcome": outcome, "type": "particle", "id": pid, "count": count, "spread": spread, "speed": speed,
            "at": at}


EVENTS = [
    sound("ANTICIPATING", 0, "minecraft:block.chest.locked", 0.9, 0.6, "plate"),
    particle("ANTICIPATING", 0, "minecraft:soul_fire_flame", 8, [0.25, 0.3, 0.25], 0.01, "plate"),
    sound("ANTICIPATING", 4, "minecraft:entity.evoker.prepare_summon", 0.5, 1.4, "above"),
    particle("ANTICIPATING", 6, "minecraft:flame", 6, [0.3, 0.05, 0.3], 0.01, "seam"),
    sound("UNLOCKING", 2, "minecraft:block.stone_button.click_on", 1.0, 0.7, "plate"),
    particle("UNLOCKING", 2, "minecraft:smoke", 4, [0.15, 0.15, 0.05], 0.01, "plate"),
    sound("OPENING", 0, "minecraft:block.barrel.open", 1.0, 0.7, "seam"),
    sound("OPENING", 1, "minecraft:block.wooden_trapdoor.open", 0.8, 0.6, "seam"),
    particle("OPENING", 4, "minecraft:flame", 14, [0.5, 0.15, 0.5], 0.04, "pile"),
    particle("OPENING", 10, "minecraft:end_rod", 10, [0.4, 0.3, 0.4], 0.06, "pile"),
    # the lid is open: from here the two outcomes may differ
    {"state": "OPEN", "tick": 0, "outcome": "treat", "type": "reward_trigger", "kind": "treat"},
    {"state": "OPEN", "tick": 0, "outcome": "treat", "type": "reveal_spawn", "count": 3},
    sound("OPEN", 0, "minecraft:entity.player.levelup", 0.7, 1.5, "pile", "treat"),
    particle("OPEN", 0, "minecraft:happy_villager", 12, [0.5, 0.3, 0.5], 0.0, "pile", "treat"),
    sound("OPEN", 2, "minecraft:block.amethyst_block.chime", 0.8, 1.2, "pile", "treat"),
    {"state": "OPEN", "tick": 72, "outcome": "treat", "type": "reveal_collect"},
    {"state": "OPEN", "tick": 80, "outcome": "treat", "type": "close_request"},
    {"state": "OPEN", "tick": 0, "outcome": "trick", "type": "encounter_trigger", "kind": "trick"},
    sound("OPEN", 0, "minecraft:entity.vex.charge", 0.9, 0.6, "pile", "trick"),
    sound("OPEN", 2, "minecraft:block.sculk_shrieker.shriek", 0.5, 1.1, "above", "trick"),
    particle("OPEN", 0, "minecraft:soul_fire_flame", 16, [0.5, 0.4, 0.5], 0.05, "pile", "trick"),
    {"state": "OPEN", "tick": 20, "outcome": "trick", "type": "close_request"},
    sound("CLOSING", 0, "minecraft:block.wooden_trapdoor.close", 0.9, 0.8, "seam"),
    sound("CLOSING", 11, "minecraft:block.barrel.close", 1.0, 0.7, "seam"),
    particle("CLOSING", 11, "minecraft:poof", 4, [0.4, 0.05, 0.4], 0.02, "seam"),
]
