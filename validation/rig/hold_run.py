import sys, re, time
sys.path.insert(0,__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'..','smoke'))
from pathlib import Path
import walk_tower_test as w
from rcon import Rcon
SP=__import__('os').environ['RIG']
node=Path(SP+'/bots/node_modules'); d=Path(SP+'/srv')
def last(r):
    ls=[l for l in r.command('cobbletowers runs list').splitlines() if re.search(w.UUID_RE,l)]
    return ls[-1] if ls else ''
ws=[w.Walker(n,25565,node,d/'logs'/f'hold-{n}.log') for n in (w.A,w.B)]
with Rcon('127.0.0.1',25575,'walktest') as r:
    for n in (w.A,w.B):
        w.wait_for(lambda n=n: n in r.command('list'),90); w.give_party(r,n)
    w.play(r,w.A,f"tower {w.TOWER}"); w.play(r,w.A,f"invite {w.B}"); w.play(r,w.B,f"accept {w.A}"); w.play(r,w.A,"confirm"); w.play(r,w.A,"start")
    w.wait_for(lambda: 'ENCOUNTER_ACTIVE' in last(r),60)
    time.sleep(3)
    for _ in range(8):
        r.command(f"cobbletowers runs winfloor {w.A}")
        if w.wait_for(lambda: 'INTERMISSION' in last(r),6): break
    e=w.layout_of(1)['entry']; p=min((w.position(r,n) for n in (w.A,w.B)),key=lambda q:q[0])
    print(last(r)); print('ORIGIN',round(p[0]-e['x']-.5),round(p[1]-e['y']),round(p[2]-e['z']-.5),flush=True)
time.sleep(3000)
