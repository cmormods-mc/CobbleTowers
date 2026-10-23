"""Real client + real server with AscensionLib: a rental's inspector shows its profile but the Upgrade button is locked.

Lends the player a rental team through the real draft commands, opens Cobblemon's summary of a rental, clicks AscensionLib's inspect
magnifier, and checks both the picture and the widget list: the Upgrade button must read Locked and be inactive. Then the same for
nothing else: the run is abandoned and the rentals are gone.

    python validation/rental_craft_lock_test.py <outdir> [gui-scale]
"""
import os, re, shutil, subprocess, sys, time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'validation'))
sys.path.insert(0, str(ROOT / 'validation' / 'smoke'))
import client_launch
import rental_test as rt
from rcon import Rcon
from run_durability_test import Server, clear_tower, read_password, reset_tower_world, server_port
from client_e2e import Remote, PLAYER

# The client acknowledges a 'wait' as soon as it starts it, so keep this script in step with the client's queue.
_send = Remote.send
def _send_and_sleep(self, line, wait=True, seconds=60):
    _send(self, line, wait, seconds)
    if line.startswith('wait '):
        time.sleep(int(line.split()[1]) / 1000)
Remote.send = _send_and_sleep

RIG = Path('L:/claude-cobbleraids-work/testserver-ascend')
JAVA = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe')
OUT = Path(sys.argv[1]).resolve(); OUT.mkdir(parents=True, exist_ok=True)
SCALE = int(sys.argv[2]) if len(sys.argv) > 2 else 3
for old in OUT.glob('*.png'): old.unlink()
tower = next(p for p in (ROOT / 'build/libs').glob('CobbleTowers-*.jar') if not p.name.endswith('-sources.jar'))
asc = next((RIG / 'mods').glob('AscensionLib-*.jar'))
shutil.copy2(tower, RIG / 'mods' / tower.name)
password = read_password(RIG); port = server_port(RIG)
rt.BOT = PLAYER          # the draft helpers drive whoever BOT names

def rc(cmd):
    with Rcon('127.0.0.1', 25575, password) as r:
        return r.command(cmd)

results = []
def check(name, ok, detail=''):
    results.append((name, ok, detail)); print(f"  [{'PASS' if ok else 'FAIL'}] {name} {'' if ok else detail}")

reset_tower_world(RIG)
server = Server(RIG, JAVA); client = None
try:
    print('boot server'); server.start(); server.wait_until_ready()
    game = Path('L:/claude-cobbleraids-work/clientrig-ascend'); game.mkdir(exist_ok=True)
    (game / 'config' / 'ascensionlib-client.properties').unlink(missing_ok=True)
    client_launch.prepare(game, RIG, tower, SCALE, [asc])
    env = dict(os.environ, COBBLETOWERS_REMOTE=str(OUT))
    remote = Remote(OUT)
    client = subprocess.Popen(client_launch.command(JAVA, game, ['--quickPlayMultiplayer', f'127.0.0.1:{port}'], RIG), cwd=game, env=env,
                              stdout=open(game / 'client_output.log', 'w', encoding='utf-8', errors='replace'), stderr=subprocess.STDOUT)
    for _ in range(300):
        if PLAYER in rc('list'): break
        time.sleep(1)
    else: raise RuntimeError('client never joined')
    print('joined'); time.sleep(8)
    with Rcon('127.0.0.1', 25575, password) as r:
        clear_tower(r)
        r.command(f'pokegiveother {PLAYER} glaceon level=100')
    time.sleep(2)
    def start_run(r):
        """The draft through its commands. A real client gets the pack screen from /tower draft, so cards are read from 'draft text'."""
        play = lambda c: r.command(f'execute as {PLAYER} run cobbletowers play {c}')
        play(f'tower {rt.TOWER}'); play('playlist rental'); play('confirm')
        for _ in range(3):
            cards = re.findall(r"(\d)\. \[(\w+)\]", play('draft text'))
            picks = [n for n, rarity in cards if rarity not in ('legendary', 'mythic')][:2]
            play(f'draft pick {picks[0]} {picks[1]}')
        play('ready'); play('start')
        rt.wait_for(lambda: rt.live_run(r) != '', 40)
        return rt.live_run(r)
    with Rcon('127.0.0.1', 25575, password) as r:
        run = start_run(r)
    check('a rental run starts', bool(run))
    time.sleep(3)
    remote.send('close'); remote.send('wait 500')
    remote.send('summary 0'); remote.send('wait 2000')
    remote.send('widget InspectButton'); remote.send('wait 2500'); remote.shot('rental_inspect_locked')
    remote.send('widgets'); remote.send('wait 600')
    log = (game / 'logs' / 'latest.log').read_text(encoding='utf-8', errors='replace')
    upgrade = [l for l in log.splitlines() if 'Widget' in l and "'Locked'" in l]
    check("the rental's inspector shows its Upgrade button as Locked and inactive",
          bool(upgrade) and 'active=false' in upgrade[-1], upgrade[-1][-160:] if upgrade else 'no Locked button among the widgets')
    check("the library refuses to open the upgrade screen for it, saying why",
          'on loan' in rc(f'execute as {PLAYER} run ascend craft 1'))
    remote.send('press Close'); remote.send('wait 600'); remote.send('close'); remote.send('wait 400')
    if run: rc(f'cobbletowers runs advance {run} abandon_requested')
    time.sleep(8)
    check('ending the run deletes the rentals', '0 rentals' in rc(f'cobbletowers play rentals {PLAYER}'))
    remote.send('quit', wait=False)
finally:
    if client:
        try: client.wait(timeout=30)
        except subprocess.TimeoutExpired: client.kill()
    (OUT / 'server.log').write_text(server.read_log(), encoding='utf-8')
    server.stop()
failed = [r for r in results if not r[1]]
print(f'\n{len(results) - len(failed)}/{len(results)} checks passed; pictures in {OUT}')
sys.exit(1 if failed else 0)
