#!/bin/bash
export JAVA_HOME=/nix/store/k95pqfzyvrna93hc9a4cg5csl7l4fh0d-openjdk-21.0.7+6
export PATH=$JAVA_HOME/bin:$PATH
cd /home/runner/workspace/chaos_addon_output
gradle build --no-daemon 2>&1
