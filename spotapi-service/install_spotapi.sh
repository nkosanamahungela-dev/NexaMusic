#!/usr/bin/env bash
set -euo pipefail

# Pin the upstream async-v2 source so Render and CI install the same code.
SPOTAPI_REPOSITORY="https://github.com/Aran404/SpotAPI.git"
SPOTAPI_COMMIT="7b8bb9e223839d3ea71ed6b3cebd36ba8111643f"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

git clone --filter=blob:none --no-checkout "$SPOTAPI_REPOSITORY" "$TEMP_DIR/SpotAPI"
git -C "$TEMP_DIR/SpotAPI" fetch --depth 1 origin "$SPOTAPI_COMMIT"
git -C "$TEMP_DIR/SpotAPI" checkout --detach FETCH_HEAD

# Upstream pyproject.toml points Hatch at a nonexistent root-level "v2"
# directory. The actual import package lives in spotapi/v2. Correct the
# wheel include path and make the namespace package explicit for packaging.
python - "$TEMP_DIR/SpotAPI/pyproject.toml" "$TEMP_DIR/SpotAPI/spotapi/__init__.py" <<'PY'
from pathlib import Path
import sys

pyproject = Path(sys.argv[1])
package_init = Path(sys.argv[2])
content = pyproject.read_text(encoding="utf-8")
broken = 'packages = ["v2"]'
fixed = 'packages = ["spotapi"]'

if content.count(broken) != 1:
    raise SystemExit("SpotAPI packaging layout changed; refusing an unverified patch")

pyproject.write_text(content.replace(broken, fixed, 1), encoding="utf-8")
package_init.write_text('"""SpotAPI package namespace."""\n', encoding="utf-8")
PY

python -m pip install --no-cache-dir "$TEMP_DIR/SpotAPI"
python -c 'from spotapi.v2 import Public; assert callable(Public.search_tracks)'
