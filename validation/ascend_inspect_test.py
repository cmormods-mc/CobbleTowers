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
    for c in [f'ascend admin initialize {PLAYER} 1',f'ascend admin initialize {PLAYER} 1 common',f'ascend admin initialize {PLAYER} 2 common','ascend admin grant '+PLAYER+' scouter 5',f'execute as {PLAYER} run ascend admin initialize 1',f'execute as {PLAYER} run ascend inspect 1',f'execute as {PLAYER} run ascend inspect 2',f'execute as {PLAYER} run ascend wallet']:
        print('>',c,'=>',rc(c)[:600])
    remote.send('summary 0'); remote.send('wait 2500'); remote.shot('s01_summary')
    remote.send('widgets'); remote.send('wait 500')
    remote.send('widget InspectButton'); remote.send('wait 3000'); remote.shot('s02_after_magnifier')
    remote.send('widgets'); remote.send('wait 500')
    remote.send('close'); remote.send('wait 800')
    remote.send('summary 1'); remote.send('wait 2000'); remote.send('widget InspectButton'); remote.send('wait 3000'); remote.shot('s03_gengar')
    remote.send('close'); remote.send('wait 800')
    remote.send('summary 3'); remote.send('wait 2000'); remote.send('widget InspectButton'); remote.send('wait 3000'); remote.shot('s04_magikarp_fallback')
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
