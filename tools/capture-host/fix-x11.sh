#!/usr/bin/env bash
# Remove WSLg's read-only /tmp/.X11-unix mount(s) so Xvfb can create its
# socket. WSLg stacks more than one tmpfs mount on the same point (observed
# twice after the 2026-11-11 reboot), so umount in a loop until clean.
set -x
for i in 1 2 3 4 5; do
    mountpoint -q /tmp/.X11-unix || break
    umount /tmp/.X11-unix
done
echo "UMOUNT_EXIT=$?"
findmnt -R /tmp | head -5
chmod 1777 /tmp/.X11-unix
touch /tmp/.X11-unix/w && rm -f /tmp/.X11-unix/w && echo "NOW_WRITABLE" || echo "STILL_READONLY"
