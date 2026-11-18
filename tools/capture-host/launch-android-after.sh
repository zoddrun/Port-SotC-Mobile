#!/usr/bin/env bash
# Starts android-after-pipeline.sh detached, so it survives the shell that
# starts it (and the agent session). Idempotent: refuses to start a second
# copy. Output goes to /root/sotc/logs/android-after-pipeline.out.
set -euo pipefail
LOG=/root/sotc/logs/android-after-pipeline.out
mkdir -p /root/sotc/logs
if pgrep -f android-after-pipeline >/dev/null 2>&1; then
    echo "ALREADY_RUNNING"
    exit 0
fi
nohup setsid bash /root/sotc/android-after-pipeline.sh >"$LOG" 2>&1 </dev/null &
sleep 1
pgrep -f android-after-pipeline >/dev/null 2>&1 && echo "STARTED" || echo "NOT_STARTED"
