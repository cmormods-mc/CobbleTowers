"""Real client + real server with AscensionLib: does it load, does the Summary magnifier appear, does inspection work."""
import os, re, subprocess, sys, time
from pathlib import Path

ROOT = Path('L:/CobbleTowers-ui')
sys.path.insert(0, str(ROOT / 'validation'))
sys.path.insert(0, str(ROOT / 'validation' / 'smoke'))
import client_launch
from rcon import Rcon
from run_durability_test import Server, read_password, server_port
from client_e2e import Remote, PLAYER

# The client acknowledges a 'wait' as soon as it starts it, so keep this script in step with the client's queue.
_send = Remote.send
def _send_and_sleep(self, line, wait=True, seconds=60):
    _send(self, line, wait, seconds)
    if line.startswith('wait '):
        time.sleep(int(line.split()[1]) / 1000)
Remote.send = _send_and_sleep

RIG = Path('L:/claude-cobbleraids-work/testserver-ascend')
JAVA = Path('L:/claude-cobbleraids-work/jdk-21.0.12.1+1/bin/java.exe')
OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
SCALE = int(sys.argv[2]) if len(sys.argv) > 2 else 3
for old in OUT.glob('*.png'): old.unlink()
tower = ROOT / 'build/libs/CobbleTowers-0.23.1-p39-loot.jar'
asc = RIG / 'mods/AscensionLib-0.1.0-prototype.jar'
password = read_password(RIG); port = server_port(RIG)

def rc(cmd):
    with Rcon('127.0.0.1', 25575, password) as r:
        return r.command(cmd)

CARDS = len(sys.argv) > 3 and sys.argv[3] == 'cards'
card_jars = client_launch.card_mods(RIG) if CARDS else []
import shutil
for jar in card_jars:
    shutil.copy2(jar, RIG / 'mods' / jar.name)
for stale in RIG.glob('mods/cobblemon-cards-*.jar') if not CARDS else []: stale.unlink()
server = Server(RIG, JAVA); client = None
try:
    print('boot server'); server.start(); server.wait_until_ready()
    log = server.read_log()
    for l in log.splitlines():
        if re.search(r'ascension|ERROR|Mixin.*(fail|error)', l, re.I): print('  S>', l[:200])
    game = Path('L:/claude-cobbleraids-work/clientrig-ascend'); game.mkdir(exist_ok=True)
    (game / 'config' / 'ascensionlib-client.properties').unlink(missing_ok=True)   # the settings persist between runs
    client_launch.prepare(game, RIG, tower, SCALE, [asc] + card_jars)
    env = dict(os.environ, COBBLETOWERS_REMOTE=str(OUT))
    remote = Remote(OUT)
    client = subprocess.Popen(client_launch.command(JAVA, game, ['--quickPlayMultiplayer', f'127.0.0.1:{port}'], RIG), cwd=game, env=env,
                              stdout=open(game / 'client_output.log', 'w', encoding='utf-8', errors='replace'), stderr=subprocess.STDOUT)
    for _ in range(300):
        if PLAYER in rc('list'): break
        time.sleep(1)
    else: raise RuntimeError('client never joined')
    print('joined'); time.sleep(8)
    rc(f'clearparty {PLAYER}')
    rc(f'pokegiveother {PLAYER} growlithe level=30')
    rc(f'pokegiveother {PLAYER} gengar level=50')
    rc(f'pokegiveother {PLAYER} arcanine level=50 shiny=true')
    rc(f'pokegiveother {PLAYER} magikarp level=5')
    time.sleep(3)
    print('> acquire', [rc(f'execute as {PLAYER} run ascend admin acquire {n} {r}')[:60] for n, r in ((1, 'common'), (2, 'rare'), (3, 'legendary'), (4, 'mythical'))])
    # Let the four reveals play out: close them as they come.
    for i in range(4):
        remote.send('wait 1800'); remote.send('press Continue'); remote.send('wait 900')
    rc(f'pokegiveother {PLAYER} lairon level=35')   # a fifth Pokemon with no profile
    remote.send('wait 800')
    for n, label in ((1, 'gengar_rare'), (3, 'magikarp_mythic'), (2, 'arcanine_legendary'), (0, 'growlithe_common'), (4, 'lairon_noprofile')):
        remote.send(f'summary {n}'); remote.send('wait 2000')
        remote.send('widget InspectButton'); remote.send('wait 2500'); remote.shot(f'i_{label}')
        remote.send('press Close'); remote.send('wait 700'); remote.send('close'); remote.send('wait 500')
    remote.send('quit', wait=False)
finally:
    if client:
        try: client.wait(timeout=30)
        except subprocess.TimeoutExpired: client.kill()
    # Pull the interesting client + server log lines before stopping.
    cl = (Path('L:/claude-cobbleraids-work/clientrig-ascend/logs/latest.log')).read_text(encoding='utf-8', errors='replace') if (Path('L:/claude-cobbleraids-work/clientrig-ascend/logs/latest.log')).exists() else ''
    (OUT / 'client_latest.log').write_text(cl, encoding='utf-8')
    (OUT / 'server.log').write_text(server.read_log(), encoding='utf-8')
    server.stop()
    for jar in card_jars:
        (RIG / 'mods' / jar.name).unlink(missing_ok=True)
print('done', OUT)
