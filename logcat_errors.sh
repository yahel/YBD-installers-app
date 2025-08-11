#!/usr/bin/env bash
# logcat_errors.sh
# Stream only ERROR-level logs from a specific Android app, auto-reattaching if the process restarts.
# Usage:
#   ./logcat_errors.sh                 # monitors com.celerate.installer
#   ./logcat_errors.sh com.example.app # monitors custom package
#
# A timestamped log file will be written to the current directory.

set -euo pipefail

PKG="${1:-com.celerate.installer}"
OUT="errors-${PKG}-$(date +%Y%m%d-%H%M%S).log"

echo "[info] Writing to ${OUT}"
echo "[info] Package: ${PKG}"
echo "[info] Press Ctrl+C to stop."

# Ensure a device is connected
if ! adb get-state 1>/dev/null 2>&1; then
  echo "[error] No device/emulator detected. Start one and try again."
  exit 1
fi

# Clear existing buffers to reduce noise
adb logcat -c || true

# Loop and reattach whenever the app process restarts
while true; do
  PID="$(adb shell pidof "${PKG}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -z "${PID}" ]]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Waiting for ${PKG} to start..."
    sleep 1
    continue
  fi

  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Attached to ${PKG} (pid ${PID}). Streaming ERROR level entries..."
  # --pid limits to the app's process; *:E shows only errors.
  # -v threadtime includes timestamps, PID/TID, tag, message.
  # If the process dies, adb logcat will exit and we'll reattach.
  adb logcat --pid "${PID}" *:E -v threadtime | tee -a "${OUT}"

  echo "[$(date '+%Y-%m-%d %H:%M:%S')] Process ${PID} ended; reattaching when it restarts..."
  sleep 1
done
