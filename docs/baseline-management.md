# Baseline management

Track performance and detect regressions. See [tools.md](tools.md) for script details.

## Create baseline

```bash
# Convenience script
./tools/update-baseline.sh

# Or manually
clojure -M:benchmark --save benchmark-baseline.edn
clojure -M:benchmark --editors-only --save baseline-editors.edn
```

## Compare against baseline

```bash
# Default 10% threshold
clojure -M:benchmark --baseline benchmark-baseline.edn

# Custom threshold
clojure -M:benchmark --baseline benchmark-baseline.edn --threshold 15

# Editors only
clojure -M:benchmark --editors-only --baseline baseline-editors.edn
```

## Workflow

```bash
# Save baseline
clojure -M:benchmark --editors-only --save before.edn

# Make changes...

# Compare
clojure -M:benchmark --editors-only --baseline before.edn

# Save new baseline if improved
clojure -M:benchmark --editors-only --save after.edn
```
