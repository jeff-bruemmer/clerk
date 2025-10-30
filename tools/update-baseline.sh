#!/bin/bash
# Update performance baseline
# Usage: ./scripts/update-baseline.sh

set -e

echo "╔════════════════════════════════════════════════════════════════════════════╗"
echo "║                    PROSERUNNER BASELINE UPDATE                             ║"
echo "╚════════════════════════════════════════════════════════════════════════════╝"
echo ""

# Check if old baseline exists
if [ -f "benchmark-baseline.edn" ]; then
    echo "Found existing baseline: benchmark-baseline.edn"
    echo "Creating backup: benchmark-baseline.edn.bak"
    cp benchmark-baseline.edn benchmark-baseline.edn.bak
    echo ""
fi

echo "Running benchmarks to establish new baseline..."
echo ""

# Run benchmarks and save
clojure -M:benchmark --save benchmark-baseline.edn

echo ""
echo "New baseline saved to: benchmark-baseline.edn"
echo ""

if [ -f "benchmark-baseline.edn.bak" ]; then
    echo "To compare old vs new baseline:"
    echo "  diff benchmark-baseline.edn.bak benchmark-baseline.edn"
    echo ""
fi

echo "To test the new baseline:"
echo "  clojure -M:benchmark --baseline benchmark-baseline.edn"
