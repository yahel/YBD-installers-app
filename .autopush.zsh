#!/bin/zsh
set -e
cd "$(dirname "$0")"

echo "[autopush] watching for changes…  (Ctrl+C to stop)"
while true; do
  # are there unstaged or uncommitted changes?
  if [[ -n "$(git status --porcelain)" ]]; then
    git add -A
    git commit -m "auto: ChatGPT edits $(date '+%F %T')"
    git pull --rebase origin main || true
    git push origin main || true
    echo "[autopush] pushed at $(date '+%H:%M:%S')"
  fi
  sleep 3
done
