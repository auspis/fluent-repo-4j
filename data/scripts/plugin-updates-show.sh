#!/bin/bash
# Script to show only stable Maven plugin updates (no RC, M, beta, alpha, etc.)

set -e

if [ ! -f "./mvnw" ]; then
  echo "Maven wrapper script not found! Please ensure you are in the correct directory."
  exit 1
fi

printf '%s\n' 'Searching for Maven plugin updates (stable versions only)...'

./mvnw versions:display-plugin-updates -DallowSnapshots=false -DprocessUnboundPlugins=false \
  | grep -e '->' \
  | awk 'BEGIN{IGNORECASE=1} {
      line = $0
      sub(/^\[INFO\][[:space:]]*/, "", line)
      arrow = index(line, "->")
      beforeArrow = substr(line, 1, arrow - 1)
      candidate = substr(line, arrow + 2)
      sub(/^[[:space:]]+/, "", candidate)
      sub(/[[:space:]]+$/, "", candidate)
      plugin = line
      sub(/[[:space:]].*$/, "", plugin)
      sub(/[[:space:]]+$/, "", beforeArrow)
      sub(/^.*[[:space:]]/, "", beforeArrow)
      if (candidate !~ /-(M|RC|beta|alpha|cr|preview|ea)/) {
        print plugin "\t" beforeArrow "\tC"
        print plugin "\t" candidate "\tU"
      }
    }' \
  | sort -k1,1 -k2,2V -k3,3 \
  | awk -F '\t' '
      function printUpdate() {
        if (current != "" && latestType == "U" && latest != current) {
          printf "[INFO] %-45s %s -> %s\n", plugin, current, latest
        }
      }
      $1 != plugin {
        printUpdate()
        plugin = $1
        current = ""
      }
      {
        if ($3 == "C") current = $2
        latest = $2
        latestType = $3
      }
      END {
        printUpdate()
      }' \
  || true