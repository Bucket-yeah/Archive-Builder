#!/bin/bash
# Locate Java 21 from PATH (set by replit.nix / nix modules)
JAVA21=$(which java 2>/dev/null)
if [ -z "$JAVA21" ]; then
  echo "ERROR: java not found in PATH" >&2
  exit 1
fi
export JAVA_HOME="$(dirname $(dirname $JAVA21))"
export PATH=$JAVA_HOME/bin:$PATH
cd /home/runner/workspace/chaos_addon_output
chmod +x gradlew
./gradlew build --no-daemon 2>&1
