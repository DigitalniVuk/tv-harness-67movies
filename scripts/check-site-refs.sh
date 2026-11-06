#!/usr/bin/env bash
# Guard rail: nothing site-specific may live outside SiteConfig.java.
# Fails if it finds URL literals, href selectors, or host suffix lists
# in any harness source file other than SiteConfig.java.
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0
for f in $(find src -name '*.java' ! -name 'SiteConfig.java'); do
  # Literal URLs inside Java strings
  if grep -nE '"https?://|"/watch|"/genre|"/latest|"/most-viewed' "$f" >/dev/null; then
    echo "FAIL: URL literal in $f"; grep -nE '"https?://|"/watch|"/genre|"/latest|"/most-viewed' "$f"; fail=1
  fi
  # CSS/JS selector literals that belong in SiteConfig (id selectors, data- attrs)
  if grep -nE '#w-|#player|data-num|data-src.*https' "$f" >/dev/null; then
    echo "FAIL: site selector in $f"; grep -nE '#w-|#player|data-num' "$f"; fail=1
  fi
done
if [ "$fail" -eq 0 ]; then
  echo "OK: no site-specific references outside SiteConfig.java"
else
  echo "Move those values into src/tv/harness/SiteConfig.java (see docs/AGENT_GUIDE.md)."
  exit 1
fi
