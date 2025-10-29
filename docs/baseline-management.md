# Baseline management guide

This guide explains how to establish performance baselines and compare against them to detect regressions.

## Quick start

### 1. Establish a new baseline

**Easy way** - Use the convenience script:

```bash
./tools/update-baseline.sh
```

This script will:
- Back up existing baseline
- Run full benchmark suite
- Save new baseline
- Show you next steps

**Manual way** - Run benchmarks and save results:

```bash
# Save all benchmarks (recommended for CI)
clojure -M:benchmark --save benchmark-baseline.edn

# Save only editor benchmarks (faster, for local development)
clojure -M:benchmark --editors-only --save baseline-editors.edn
```

**When to create a new baseline:**
- After major optimizations (like cache removal)
- Before making risky performance changes
- When setting up CI/CD pipelines
- At the start of each release cycle

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

**Exit codes:**
- `0` = No regressions detected (safe to merge)
- `1` = Regressions detected (review needed)

## Understanding baseline comparison

### Example output

```
 ================================================================================
 PERFORMANCE COMPARISON TO BASELINE
 Regression threshold: 10.0%
================================================================================

IMPROVEMENT: Regex Editor
  Baseline: 7.48 ms | Current: 6.24 ms | Change: -16.7%

REGRESSION: Existence Editor
  Baseline: 2.65 ms | Current: 3.38 ms | Change: +27.5%

STABLE: Case Editor
  Baseline: 5.37 ms | Current: 5.29 ms | Change: -1.5%

 ================================================================================
Summary: 1 regressions, 2 improvements, 3 stable
================================================================================
```

### Result categories

**IMPROVEMENT** - Performance got better beyond threshold
- Change is negative (faster execution time)
- Good! Consider documenting what caused the improvement

**REGRESSION** - Performance got worse beyond threshold
- Change is positive (slower execution time)
- Action required: investigate and fix before merging

**STABLE** - Performance change is within threshold
- Change is within ±threshold%
- Expected variation from JIT, GC, system load

## Workflow examples

### Local development

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

### CI/CD pipeline

```bash
#!/bin/bash

# Run benchmarks and compare to baseline
if clojure -M:benchmark --baseline ci-baseline.edn --threshold 10; then
  echo "✓ No performance regressions"
  exit 0
else
  echo "✗ Performance regressions detected"
  exit 1
fi
```

**GitHub Actions example:**

```yaml
- name: Performance Regression Check
  run: clojure -M:benchmark --baseline benchmark-baseline.edn --threshold 10
```

### Updating CI baseline

After confirming improvements are intentional:

```bash
# 1. Run benchmarks and save new baseline
clojure -M:benchmark --save benchmark-baseline.edn

# 2. Commit the updated baseline
git add benchmark-baseline.edn
git commit -m "Update performance baseline after cache removal optimization"
git push
```

## Choosing a threshold

The threshold determines what counts as a regression. Choose based on your needs:

| Threshold | Use Case | Noise Level |
|-----------|----------|-------------|
| 5% | Strict (catch small regressions) | High false positives |
| **10%** | **Recommended** | Balanced |
| 15% | Lenient (only catch major issues) | Low false positives |
| 20% | Very lenient | May miss real issues |

**Recommendation**: Start with 10% and adjust based on your test environment's stability.

## Troubleshooting

### False positives (flaky results)

**Problem**: Baseline comparison shows regressions that aren't real.

**Solutions**:
1. Increase threshold: `--threshold 15`
2. Run benchmarks multiple times and average
3. Ensure no background processes during benchmarking
4. Use `--editors-only` for more stable results
5. Increase warmup/iteration counts in benchmark code

### Existing baseline files

The repository includes these baselines:

- `benchmark-baseline.edn` - Original baseline (with LRU cache)
- `benchmark-baseline-optimized.edn` - After cache removal optimization

**Current recommendation**: Use `benchmark-baseline-optimized.edn` as the new baseline, since cache removal is a permanent improvement.

To update:

```bash
mv benchmark-baseline-optimized.edn benchmark-baseline.edn
git add benchmark-baseline.edn
git commit -m "Update baseline to post-optimization performance"
```

## Baseline file format

Baseline files are EDN format containing benchmark results:

```clojure
{:timestamp "2025-10-29 14:32:11"
 :results [{:name "Regex Editor"
            :description "Tests regex pattern matching"
            :iterations 10
            :mean-ms 6.24
            :throughput 160421.5
            :throughput-unit "lines/sec"}
           ...]}
```

These files are:
- Human-readable (EDN format)
- Version-controlled (commit with your code)
- Portable (work across machines)

## Best practices

✓ **Do:**
- Commit baselines with code changes
- Update baseline after confirmed improvements
- Use consistent benchmark environment
- Document baseline updates in commits
- Run benchmarks before creating baseline

✗ **Don't:**
- Create baselines on busy systems
- Mix different benchmark types (editors vs integration)
- Ignore regressions without investigation
- Update baseline to hide regressions
- Run benchmarks without warmup

## Example: measuring optimization impact

Here's how we measured the cache removal optimization:

```bash
# 1. Baseline with cache (before)
clojure -M:benchmark --editors-only --save with-cache.edn

# 2. Remove cache code
# ... edit utilities.clj and re.clj ...

# 3. Compare
clojure -M:benchmark --editors-only --baseline with-cache.edn

# Results showed improvements, so we saved new baseline:
clojure -M:benchmark --editors-only --save without-cache.edn
```

Result: Parallel speedup improved from 0.92x → 1.75x!
