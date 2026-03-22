#!/bin/bash
# Script to show only stable Maven plugin updates (no RC, M, beta, alpha, etc.)

set -e

# Filtro gli aggiornamenti dei plugin mostrando solo versioni stabili ed eliminando i duplicati
./mvnw versions:display-plugin-updates -DallowSnapshots=false -Dversions.displayAllVersions=true \
  | grep -e '->' \
  | awk 'BEGIN{IGNORECASE=1} {split($0, a, "-> "); if (a[2] !~ /-(M|RC|beta|alpha|cr|preview|ea)/) print $0}' \
  | sort -u \
  || true