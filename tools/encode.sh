#!/bin/sh
exec ffmpeg -i "$1" -c:v libvpx-vp9 -b:v .2M -b:a 32k -filter_complex 'scale=280x210, pad=w=280:h=280:x=0:y=35:color=black, rotate=angle=-PI/2' "$2"
