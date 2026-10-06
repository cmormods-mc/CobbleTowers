"""Real client + real server with AscensionLib: does it load, does the Summary magnifier appear, does inspection work."""
import os, re, subprocess, sys, time
from pathlib import Path

ROOT = Path('L:/CobbleTowers-ui')
sys.path.insert(0, str(ROOT / 'validation'))
sys.path.insert(0, str(ROOT / 'validation' / 'smoke'))
import client_launch
from rcon import Rcon
from run_durability_test import Server, read_password, server_port, reset_tower_world, clear_tower
from client_e2e import Remote, PLAYER

RIG = Path('L:/claude-cobbleraids-work/testserver-ascend')
JAVA = Path('L:/claude-cobbleraids-work/jdk-21.0.12.1+1/bin/java.exe')
OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
SCALE = int(sys.argv[2]) if len(sys.argv) > 2 else 3
for old in OUT.glob('*.png'): old.unlink()
tower = ROOT / 'build/libs/CobbleTowers-0.21.0-p21-warm-pixel-preview.jar'
asc = RIG / 'mods/AscensionLib-0.1.0-prototype.jar'
password = read_password(RIG); port = server_port(RIG)

def rc(cmd):
    with Rcon('127.0.0.1', 25575, password) as r:
        return r.command(cmd)

reset_tower_world(RIG)
server = Server(RIG, JAVA); client = None
try:
    print('boot server'); server.start(); server.wait_until_ready()
    log = server.read_log()
    for l in log.splitlines():
        if re.search(r'ascension|ERROR|Mixin.*(fail|error)', l, re.I): print('  S>', l[:200])
    game = Path('L:/claude-cobbleraids-work/clientrig-ascend'); game.mkdir(exist_ok=True)
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
    rc(f'clearparty {PLAYER}')
    rc(f'pokegiveother {PLAYER} growlithe level=30')
    rc(f'pokegiveother {PLAYER} gengar level=50')
    rc(f'pokegiveother {PLAYER} arcanine level=50 shiny=true')
    rc(f'pokegiveother {PLAYER} magikarp level=5')
    time.sleep(3)
    for c in ['ascend admin grant '+PLAYER+' scouter 5']+[f'execute as {PLAYER} run ascend admin initialize {n}' for n in (1, 2, 3)]:
        print('>', c, '=>', rc(c)[:200])
    rc(f'tp {PLAYER} 0 100 0') if False else None
    with Rcon('127.0.0.1', 25575, password) as r: clear_tower(r)
    time.sleep(2)
    remote.send('cmd tower tower cobbletowers:test'); remote.send('wait 1500')
    remote.send('cmd tower start'); 
    for _ in range(60):
        out = rc('cobbletowers runs list')
        if 'ENCOUNTER_ACTIVE' in out: break
        time.sleep(1)
    print('runs:', rc('cobbletowers runs list')[:300])
    time.sleep(30)
    remote.shot('b01_battle_start')
    # Own tile (left): inspect through the real click path, then back to the same battle screen.
    remote.send('click 50 63'); remote.send('wait 2500'); remote.shot('b02_own_tile_inspect')
    remote.send('press ×'); remote.send('wait 1000'); remote.shot('b03_back_in_battle')
    # Opponent tile (right).
    remote.send('click 380 63'); remote.send('wait 2500'); remote.shot('b04_opponent_inspect')
    remote.send('press Use Scouter'); remote.send('wait 2500'); remote.shot('b05_after_scouter')
    remote.send('press ×'); remote.send('wait 800')
    remote.send('cmd tower abandon'); remote.send('wait 1500')
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
print('done', OUT)
