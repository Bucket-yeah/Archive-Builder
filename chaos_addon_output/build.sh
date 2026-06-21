#!/bin/bash
# Find Java 21 - check JAVA_HOME first, then search Nix store
if [ -n "$JAVA_HOME" ] && [ -d "$JAVA_HOME" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
  echo "Using JAVA_HOME: $JAVA_HOME"
else
  JAVA21_NIX=$(ls -d /nix/store/*openjdk-21*/lib/openjdk 2>/dev/null | head -1)
  if [ -d "$JAVA21_NIX" ]; then
    export JAVA_HOME="$JAVA21_NIX"
    echo "Found Java 21 in Nix store: $JAVA_HOME"
  else
    echo "ERROR: Java 21 not found" >&2
    exit 1
  fi
fi
export PATH=$JAVA_HOME/bin:$PATH
echo "Using Java: $JAVA_HOME"
java -version
cd /home/runner/workspace/chaos_addon_output
chmod +x gradlew
./gradlew build --no-daemon 2>&1
