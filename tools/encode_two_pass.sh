#!/bin/sh
log="$1"
ffmpeg -i "$1" -y -c:v libvpx-vp9 -b:v 100k -b:a 32k -pass 1 -passlogfile "$log" -speed 4 -tile-columns 6 -frame-parallel 1 -an -f webm -filter_complex 'scale=280x210, pad=w=280:h=280:x=0:y=35:color=black, transpose=2' /dev/null
ffmpeg -i "$1" -y -c:v libvpx-vp9 -b:v 100k -b:a 32k -pass 2 -passlogfile "$log" -speed 1 -tile-columns 6 -frame-parallel 1 -auto-alt-ref 1 -lag-in-frames 25 -f webm -filter_complex 'scale=280x210, pad=w=280:h=280:x=0:y=35:color=black, transpose=2' "$2"
