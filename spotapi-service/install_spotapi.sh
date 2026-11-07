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

# Repair two upstream packaging/runtime issues without changing the pinned source:
# - Hatch points at a nonexistent top-level v2 directory.
# - ObjectDict.values() unwraps children, breaking get_at_depth() traversal.
python - "$TEMP_DIR/SpotAPI/pyproject.toml" "$TEMP_DIR/SpotAPI/spotapi/__init__.py" "$TEMP_DIR/SpotAPI/spotapi/v2/query.py" <<'PY'
from pathlib import Path
import sys

pyproject = Path(sys.argv[1])
package_init = Path(sys.argv[2])
query_file = Path(sys.argv[3])

content = pyproject.read_text(encoding="utf-8")
broken_package = 'packages = ["v2"]'
fixed_package = 'packages = ["spotapi"]'
if content.count(broken_package) != 1:
    raise SystemExit("SpotAPI packaging layout changed; refusing an unverified patch")
pyproject.write_text(content.replace(broken_package, fixed_package, 1), encoding="utf-8")
package_init.write_text('"""SpotAPI package namespace."""\n', encoding="utf-8")

query_content = query_file.read_text(encoding="utf-8")
broken_traversal = 'dict_children = [value for value in current.values() if isinstance(value, ObjectDict)]'
fixed_traversal = 'dict_children = [value for value in dict.values(current) if isinstance(value, ObjectDict)]'
if query_content.count(broken_traversal) != 1:
    raise SystemExit("SpotAPI response traversal changed; refusing an unverified patch")
query_file.write_text(query_content.replace(broken_traversal, fixed_traversal, 1), encoding="utf-8")
PY

python -m pip install --no-cache-dir "$TEMP_DIR/SpotAPI"
python -c 'from spotapi.v2 import Public; assert callable(Public.search_tracks)'
