#!/bin/bash
# Use JAVA_HOME if it points to Java 21, otherwise find java 21 from PATH
if [ -n "$JAVA_HOME" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
  export JAVA_HOME="$JAVA_HOME"
else
  JAVA21=$(which java 2>/dev/null)
  if [ -z "$JAVA21" ]; then
    echo "ERROR: java not found in PATH" >&2
    exit 1
  fi
  export JAVA_HOME="$(dirname $(dirname $JAVA21))"
fi
export PATH=$JAVA_HOME/bin:$PATH
echo "Using Java: $JAVA_HOME"
java -version
cd /home/runner/workspace/chaos_addon_output
chmod +x gradlew
./gradlew build --no-daemon 2>&1
