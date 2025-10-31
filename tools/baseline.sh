#!/bin/bash
# Compare benchmark results against baseline
# Usage: ./tools/baseline.sh [threshold]
#   threshold: Optional percentage threshold for regressions (default: 10)
#
# Examples:
#   ./tools/baseline.sh           # Use default 10% threshold
#   ./tools/baseline.sh 15        # Use 15% threshold

set -e

# Parse arguments
THRESHOLD="$1"
BASELINE_FILE="benchmark-baseline.edn"

echo "╔════════════════════════════════════════════════════════════════════════════╗"
echo "║                    PROSERUNNER BASELINE COMPARISON                         ║"
echo "╚════════════════════════════════════════════════════════════════════════════╝"
echo ""

# Check if baseline exists
if [ ! -f "$BASELINE_FILE" ]; then
    echo "ERROR: Baseline file not found: $BASELINE_FILE"
    echo ""
    echo "To create a baseline, run:"
    echo "  ./tools/update-baseline.sh"
    echo ""
    exit 1
fi

echo "Baseline file: $BASELINE_FILE"
if [ -n "$THRESHOLD" ]; then
    echo "Threshold:     ${THRESHOLD}%"
else
    echo "Threshold:     10% (default)"
fi
echo ""

# Run benchmarks with baseline comparison
echo "Running benchmarks and comparing against baseline..."
echo ""

# Build the command
BENCH_CMD="clojure -M:benchmark --baseline $BASELINE_FILE"
if [ -n "$THRESHOLD" ]; then
    BENCH_CMD="$BENCH_CMD --threshold $THRESHOLD"
fi

if $BENCH_CMD; then
    echo ""
    echo "✓ All benchmarks passed!"
    if [ -n "$THRESHOLD" ]; then
        echo "  No performance regressions detected (threshold: ${THRESHOLD}%)"
    else
        echo "  No performance regressions detected (threshold: 10%)"
    fi
    echo ""
    exit 0
else
    EXIT_CODE=$?
    echo ""
    echo "✗ Benchmark comparison failed!"
    echo "  Performance regressions detected or benchmarks errored"
    echo ""
    echo "To update the baseline:"
    echo "  ./tools/update-baseline.sh"
    echo ""
    echo "To run with a higher threshold:"
    echo "  ./tools/baseline.sh 20  # Allow up to 20% regression"
    echo ""
    exit $EXIT_CODE
fi
