#!/usr/bin/env bash
set -euo pipefail
NAME="${1:-celerate-installer-app-public}"
cd "$(dirname "$0")/~/work/InstallerApp" 2>/dev/null || cd ~/work/InstallerApp
[ -f settings.gradle.kts ] || { echo "Error: run this from or fix path to your project root"; exit 1; }

git init
git add -A
git commit -m "chore: initial commit (known-good skeleton + Gradle wrapper)" || true
git branch -M main

# Create public repo and push (requires gh auth login)
gh repo create "$NAME" --public --source=. --remote=public --push

echo "✅ Pushed to GitHub as: https://github.com/$(gh repo view "$NAME" --json nameWithOwner -q .nameWithOwner)"

