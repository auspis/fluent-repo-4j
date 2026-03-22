#!/bin/bash
# Script to show only stable Maven dependency updates (no RC, M, beta, alpha, etc.)

set -e

# Check if the Maven wrapper script exists
if [ ! -f "./mvnw" ]; then
  echo "Maven wrapper script not found! Please ensure you are in the correct directory."
  exit 1
fi

echo "\searching for dependency updates (stable versions only)...\n"

./mvnw versions:display-dependency-updates -DallowSnapshots=false -Dversions.displayAllVersions=true \
  | grep -e '->' \
  | awk 'BEGIN{IGNORECASE=1} {split($0, a, "-> "); if (a[2] !~ /-(M|RC|beta|alpha|cr|preview|ea)/) print $0}' \
  | sort -u \
  || true