"""Isolated native Hall screenshot run; writes only inside this checkout's build directory."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import client_launch

root=Path(__file__).resolve().parent.parent
scale=int(sys.argv[1]) if len(sys.argv)>1 else 3
iris='iris' in sys.argv
mode=sys.argv[2] if len(sys.argv)>2 and sys.argv[2] in ('features','all','modifier') else 'hall'
game=root/'build'/f'{mode}-client-scale{scale}{"-iris" if iris else ""}'
shots=root/'build'/f'{mode}-screens-scale{scale}{"-iris" if iris else ""}'
game.mkdir(parents=True,exist_ok=True)
shots.mkdir(parents=True,exist_ok=True)
mods=game/'mods'
mods.mkdir(exist_ok=True)
rig=Path('L:/claude-cobbleraids-work/testserver-181')
for pattern in ('fabric-api-*.jar','Cobblemon-*.jar','CobbleRaids-*.jar'):
    source=client_launch.first(rig/'mods',pattern)
    shutil.copy2(source,mods/source.name)
if iris:
    for pattern in ('iris-fabric-*.jar','sodium-fabric-*.jar'):
        source=client_launch.first(rig.parent/'testserver-full'/'mods-disabled-for-testing',pattern)
        shutil.copy2(source,mods/source.name)
    pack=Path(os.environ['SHADERPACK'])
    (game/'shaderpacks').mkdir(exist_ok=True)
    shutil.copy2(pack,game/'shaderpacks'/pack.name)
    (game/'config').mkdir(exist_ok=True)
    (game/'config'/'iris.properties').write_text(f'shaderPack={pack.name}\nenableShaders=true\n',encoding='utf-8')
jar=root/'build/libs/CobbleTowers-0.24.1-p41-card-colors.jar'
for prior in mods.glob('CobbleTowers-*.jar'):
    if prior.name != jar.name:
        prior.unlink()
shutil.copy2(jar,mods/jar.name)
(game/'options.txt').write_text(f'guiScale:{scale}\nonboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nfullscreen:false\nsoundCategory_master:0.0\nlang:en_us\ntutorialStep:none\n',encoding='utf-8')
java=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.12.1+1/bin/java.exe')
command=client_launch.command(java,game,[],rig)
startup=subprocess.STARTUPINFO()
startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW
startup.wShowWindow=0
env=dict(os.environ,COBBLETOWERS_SCREENSHOTS=str(shots),COBBLETOWERS_HALL_ONLY='0' if mode in ('all','modifier') else '1',COBBLETOWERS_FEATURES_ONLY='1' if mode=='features' else '0',COBBLETOWERS_MODIFIER_ONLY='1' if mode=='modifier' else '0')
with (game/'hall-client.log').open('w',encoding='utf-8') as log:
    process=subprocess.Popen(command,cwd=game,env=env,stdout=log,stderr=subprocess.STDOUT,startupinfo=startup)
    try:
        result=process.wait(timeout=240)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        raise
print('Client exit:',result,'Screenshots:',len(list(shots.glob('*.png'))),'Log:',game/'hall-client.log')
log_text=(game/'hall-client.log').read_text(encoding='utf-8',errors='replace')
sys.exit(0 if result==0 and len(list(shots.glob('*.png')))>=(12 if mode=='modifier' else 5) and 'Screenshot harness finished' in log_text and not ('Screenshot step' in log_text and 'failed' in log_text) else 1)
