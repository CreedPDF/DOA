#!/usr/bin/env bash
# Builds every DOA model, baked texture and preview in Blender (headless).
# Usage: tools/blender/build.sh [path/to/blender]
# Headless Cycles occasionally segfaults on CI runners; such runs are retried without previews.
set -uo pipefail
BLENDER="${1:-${BLENDER:-blender}}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
run() {
  local script="$1"; shift
  local extra=""
  for attempt in 1 2 3; do
    "$BLENDER" -b --factory-startup --python-exit-code 1 -P "$ROOT/tools/blender/$script" -- \
      --assets "$ROOT/src/main/resources/assets/doa" --out "$ROOT/build/blender" "$@" $extra
    local code=$?
    if [ $code -eq 0 ]; then return 0; fi
    if [ $code -ne 139 ] && [ $code -ne 134 ]; then return $code; fi
    extra="--no-render"
    echo "Blender crashed (exit $code), retrying ($attempt/3)..."
  done
  return 1
}
run doa_models.py --glyphs "$ROOT/tools/glyphs" || exit $?
run doa_meshes.py || exit $?
