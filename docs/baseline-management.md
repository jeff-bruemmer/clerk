# Baseline management guide

This guide explains how to establish performance baselines and compare against them to detect regressions.

## Quick start

### 1. Establish a new baseline

**Easy way** - Use the convenience script:

```bash
./tools/update-baseline.sh
```

**Manual way** - Run benchmarks and save results:

```bash
# Save all benchmarks (recommended for CI)
clojure -M:benchmark --save benchmark-baseline.edn

# Save only editor benchmarks (faster, for local development)
clojure -M:benchmark --editors-only --save baseline-editors.edn
```

### 2. Compare against baseline

Run benchmarks and compare against saved baseline:

```bash
# Compare with 10% threshold (default)
clojure -M:benchmark --baseline benchmark-baseline.edn

# Compare with custom threshold (e.g., 15%)
clojure -M:benchmark --baseline benchmark-baseline.edn --threshold 15

# Compare only editors (faster)
clojure -M:benchmark --editors-only --baseline baseline-editors.edn
```

## Workflow examples


```bash
# 1. Save baseline before making changes
clojure -M:benchmark --editors-only --save before-optimization.edn

# 2. Make your performance changes
# ... edit code ...

# 3. Compare to see impact
clojure -M:benchmark --editors-only --baseline before-optimization.edn

# 4. If improved, save as new baseline
clojure -M:benchmark --editors-only --save after-optimization.edn
```
