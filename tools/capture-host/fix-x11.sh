#!/usr/bin/env bash
# Remove WSLg's read-only /tmp/.X11-unix mount so Xvfb can create its socket.
set -x
umount /tmp/.X11-unix
echo "UMOUNT_EXIT=$?"
findmnt -R /tmp | head -5
chmod 1777 /tmp/.X11-unix
touch /tmp/.X11-unix/w && rm -f /tmp/.X11-unix/w && echo "NOW_WRITABLE" || echo "STILL_READONLY"
