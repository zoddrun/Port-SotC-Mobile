#!/usr/bin/env bash
# Diagnose the /tmp/.X11-unix read-only problem.
echo "== mounts under /tmp =="
findmnt -R /tmp 2>&1 | head -10
echo "== /tmp writable? =="
touch /tmp/plainfile && echo "TMP_WRITABLE" && rm /tmp/plainfile
echo "== socket dir =="
ls -lad /tmp/.X11-unix
touch /tmp/.X11-unix/w 2>&1 && rm -f /tmp/.X11-unix/w
echo "== X0 socket owner =="
ls -la /tmp/.X11-unix/ 2>&1
