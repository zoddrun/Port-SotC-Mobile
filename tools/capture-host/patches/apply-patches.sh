#!/usr/bin/env bash
# Apply the capture-host patches to a celosia checkout. Patches are kept as
# git diffs against the pinned commit f8c269e; git apply is content-checked,
# so a tree that already carries the changes (or drifted) fails loudly.
#
# Usage: ./apply-patches.sh [path-to-celosia-checkout]   (default: /root/celosia)
set -euo pipefail

CELOSIA=${1:-/root/celosia}
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
EXPECTED_COMMIT=f8c269e

cd "$CELOSIA"
head=$(git rev-parse --short HEAD)
if [ "$head" != "$EXPECTED_COMMIT" ]; then
    echo "error: celosia at $head, patches are made against $EXPECTED_COMMIT" >&2
    echo "       (review the diffs under $HERE and rebase them onto the new commit)" >&2
    exit 1
fi

for p in "$HERE"/[0-9]*.patch; do
    echo "==> applying $(basename "$p")"
    git apply --check "$p" 2>/dev/null && git apply "$p" && echo "    applied" || {
        if git diff --quiet 2>/dev/null; then
            echo "    already applied (tree carries uncommitted changes)"; exit 0
        fi
        echo "error: patch does not apply cleanly" >&2; exit 1
    }
done
echo "PATCHES_APPLIED"
