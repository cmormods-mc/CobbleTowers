#!/bin/bash
# Real-client screenshots of the tower in the rig. Needs: Xvfb on :99, the client launched with launch2.sh (--quickPlayMultiplayer 127.0.0.1:25565),
# the resource-pack "Yes" clicked (xdotool mousemove 480 350 click 1, twice, ~15 s after launch), and hold_run.py running so the cell stays loaded.
#   RIG=<rig folder> OUT=<folder> bash validation/rig/shots.sh
# Coordinates are world coordinates in cobbletowers:tower: the cell origin printed by hold_run.py ("ORIGIN x y z") plus the structure-local anchor.
# yaw 180 faces north, 0 south, 90 west, 270 east; pitch positive looks down. The client draws about 12 chunks, so stay within ~170 blocks.
export DISPLAY=:99
C=$RIG/srv/console.txt
cmd(){ echo "$1" >> $C; sleep ${2:-1.5}; }
shot(){ cmd "execute in cobbletowers:tower run tp ShotCam $2 $3 $4 $5 $6" ${7:-14}; import -window root $OUT/$1.png; echo $1; }
mkdir -p $OUT
cmd "op ShotCam"; cmd "gamemode spectator ShotCam" 2; xdotool key F1
# example: shot far_front 1275 130 420 180 2 25
"$@"
