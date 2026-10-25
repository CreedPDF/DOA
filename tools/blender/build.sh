#!/usr/bin/env bash
# Builds every DOA model, atlas and preview in Blender (headless).
# Usage: tools/blender/build.sh [path/to/blender]
# Headless Cycles occasionally segfaults on CI runners; such runs are retried without previews.
set -uo pipefail
BLENDER="${1:-${BLENDER:-blender}}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXTRA=""
for attempt in 1 2 3; do
  "$BLENDER" -b --factory-startup --python-exit-code 1 \
    -P "$ROOT/tools/blender/doa_models.py" -- \
    --assets "$ROOT/src/main/resources/assets/doa" \
    --out "$ROOT/build/blender" \
    --glyphs "$ROOT/tools/glyphs" $EXTRA
  code=$?
  if [ $code -eq 0 ]; then exit 0; fi
  if [ $code -ne 139 ] && [ $code -ne 134 ]; then exit $code; fi
  EXTRA="--no-render"
  echo "Blender crashed (exit $code), retrying ($attempt/3)..."
done
exit 1
