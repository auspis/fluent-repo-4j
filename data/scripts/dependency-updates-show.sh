#!/bin/bash
# Script to show only stable Maven dependency updates (no RC, M, beta, alpha, etc.)

set -e

# Check if the Maven wrapper script exists
if [ ! -f "./mvnw" ]; then
  echo "Maven wrapper script not found! Please ensure you are in the correct directory."
  exit 1
fi

printf '%s\n' 'Searching for dependency updates (stable versions only)...'

./mvnw versions:display-dependency-updates -DallowSnapshots=false -Dversions.displayAllVersions=true \
  -DprocessDependencies=true \
  -DprocessDependencyManagement=false \
  -DshowVersionless=true \
  -DprocessPluginDependencies=false \
  -DprocessPluginDependenciesInPluginManagement=false \
  | awk 'BEGIN{IGNORECASE=1}
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    {
      line = $0
      if (index(line, "->") == 0) {
        previous = line
        next
      }

      report = line
      if (line ~ /^\[INFO\][[:space:]]+[0-9]/ &&
          previous ~ /^\[INFO\][[:space:]]+[^[:space:]]+:[^[:space:]]+[[:space:]]+\.\.\./) {
        report = previous " " line
      }

      split(report, versions, "->")
      beforeArrow = trim(versions[1])
      candidate = trim(versions[2])
      sub(/[[:space:]].*$/, "", candidate)
      current = beforeArrow
      sub(/^.*[[:space:]]/, "", current)
      artifact = beforeArrow
      sub(/^\[INFO\][[:space:]]*/, "", artifact)
      sub(/[[:space:]].*$/, "", artifact)

      if (candidate !~ /-(M|RC|beta|alpha|cr|preview|ea)/) {
        printf "[INFO] %-45s %s -> %s\n", artifact, current, candidate
      }
      previous = line
    }' \
  | sort -u \
  || true