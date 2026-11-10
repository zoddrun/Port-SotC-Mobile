#!/usr/bin/env bash
# capture-module: replicate celosia's capture() for the WSL Xvfb host.
#
# Runs the recompiled boot ELF until the guest jumps into code that is not
# recompiled yet (the next module's entry) and PS2X_DUMP_RAM_ON_MISSING saves
# the 32 MiB guest RAM dump at that moment.
#
# Usage: ./capture-module.sh KERNEL|MANAGER|GAMECORE
set -uo pipefail

ROOT=/root/celosia
BUILD=/root/sotc/build
DISC=/root/sotc/disc
BOOT=SCUS_974.72
RUNNER=$BUILD/cmake/ps2xRuntime/ps2EntryRunner
CAPTURES=$BUILD/captures
LOGS=/root/sotc/logs

name=$1
out=$CAPTURES/$(echo "$name" | tr '[:upper:]' '[:lower:]').bin

mkdir -p "$CAPTURES" "$LOGS"
rm -f "$out"

if [ ! -x "$RUNNER" ]; then
    echo "error: runner not built: $RUNNER" >&2
    exit 1
fi

echo "==> capturing $name (display ${DISPLAY:-none})"
(cd "$ROOT" && DISPLAY=${DISPLAY:-:99} LIBGL_ALWAYS_SOFTWARE=1 PS2X_DUMP_RAM_ON_MISSING=$out exec "$RUNNER" "$DISC/$BOOT") >"$LOGS/capture-$(echo "$name" | tr '[:upper:]' '[:lower:]').log" 2>&1 &
pid=$!
for i in $(seq 240); do
    if [ -f "$out" ] && [ "$(wc -c <"$out")" -ge 33554432 ]; then break; fi
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
done
sleep 1
kill "$pid" 2>/dev/null || true
sleep 1
kill -9 "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true

if [ -f "$out" ] && [ "$(wc -c <"$out")" -ge 33554432 ]; then
    echo "==> $name captured: $out ($(wc -c <"$out") bytes)"
    exit 0
fi
echo "error: $name was never reached (log: $LOGS/capture-$(echo "$name" | tr '[:upper:]' '[:lower:]').log)" >&2
exit 1
