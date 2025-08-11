#!/bin/zsh
set -e
PROJ=~/work/YBD-installers-app
cd "$PROJ" || { echo "Missing $PROJ"; exit 1; }
export JAVA_HOME=$(/usr/libexec/java_home -v 17) || true
chmod +x gradlew 2>/dev/null || true
set -o pipefail
./gradlew clean :app:assembleDebug --no-daemon --stacktrace 2>&1 | tee build_local.log
echo "==== Outputs ===="
ls -R app/build/outputs || true
