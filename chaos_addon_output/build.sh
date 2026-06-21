#!/bin/bash
# Force Java 21 from Nix store (GraalVM 22.3 module provides Java 19, not 21)
JAVA21_NIX="/nix/store/k95pqfzyvrna93hc9a4cg5csl7l4fh0d-openjdk-21.0.7+6/lib/openjdk"
if [ -d "$JAVA21_NIX" ]; then
  export JAVA_HOME="$JAVA21_NIX"
elif [ -n "$JAVA_HOME" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
  export JAVA_HOME="$JAVA_HOME"
else
  echo "ERROR: Java 21 not found" >&2
  exit 1
fi
export PATH=$JAVA_HOME/bin:$PATH
echo "Using Java: $JAVA_HOME"
java -version
cd /home/runner/workspace/chaos_addon_output
chmod +x gradlew
./gradlew build --no-daemon 2>&1
