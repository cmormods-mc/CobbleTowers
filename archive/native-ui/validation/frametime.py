"""Hall frame-time run (FrameSampler, via ScreenshotHarness). Usage: frametime.py [gui-scale] [sodium].
`sodium` adds Sodium from the rig's disabled-mods folder; the report is build/frametime-<tag>/frametime.txt."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import client_launch

root = Path(__file__).resolve().parent.parent
scale = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else 3
iris = 'iris' in sys.argv
sodium = 'sodium' in sys.argv or iris
tag = f'scale{scale}-{"iris" if iris else "sodium" if sodium else "vanilla"}'
game = root / 'build' / f'frametime-client-{tag}'
out = root / 'build' / f'frametime-{tag}'
if game.exists():
    shutil.rmtree(game)
game.mkdir(parents=True)
out.mkdir(parents=True, exist_ok=True)
(out / 'frametime.txt').unlink(missing_ok=True)
mods = game / 'mods'
mods.mkdir()
rig = Path('L:/claude-cobbleraids-work/testserver-181')
for pattern in ('fabric-api-*.jar', 'Cobblemon-*.jar', 'CobbleRaids-*.jar'):
    source = client_launch.first(rig / 'mods', pattern)
    shutil.copy2(source, mods / source.name)
if sodium:
    source = client_launch.first(rig.parent / 'testserver-full' / 'mods-disabled-for-testing', 'sodium-fabric-*.jar')
    shutil.copy2(source, mods / source.name)
if iris:
    source = client_launch.first(rig.parent / 'testserver-full' / 'mods-disabled-for-testing', 'iris-fabric-*.jar')
    shutil.copy2(source, mods / source.name)
    pack = Path(os.environ['SHADERPACK'])
    (game / 'shaderpacks').mkdir()
    shutil.copy2(pack, game / 'shaderpacks' / pack.name)
    (game / 'config').mkdir(exist_ok=True)
    (game / 'config' / 'iris.properties').write_text(f'shaderPack={pack.name}\nenableShaders=true\n', encoding='utf-8')
jars = [p for p in (root / 'build' / 'libs').glob('CobbleTowers-*.jar') if not p.name.endswith('-sources.jar')]
jar = max(jars, key=lambda p: p.stat().st_mtime)
shutil.copy2(jar, mods / jar.name)
(game / 'options.txt').write_text(
    f'guiScale:{scale}\nonboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nfullscreen:false\n'
    'enableVsync:false\nmaxFps:260\nsoundCategory_master:0.0\nlang:en_us\ntutorialStep:none\n', encoding='utf-8')
java = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe')
command = client_launch.command(java, game, [], rig)
env = dict(os.environ, COBBLETOWERS_SCREENSHOTS=str(out), COBBLETOWERS_FRAMETIME_ONLY='1')
with (game / 'client.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen(command, cwd=game, env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        result = process.wait(timeout=300)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        raise
report = out / 'frametime.txt'
print('Client exit:', result, 'Log:', game / 'client.log')
print(report.read_text(encoding='utf-8') if report.exists() else 'No report written.')
sys.exit(0 if result == 0 and report.exists() else 1)
