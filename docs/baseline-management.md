# Baseline management

Track performance and detect regressions.

## Create baseline

```bash
# Using bb task (recommended)
bb update-baseline

# Or manually
clojure -M:benchmark --save benchmark-baseline.edn
clojure -M:benchmark --editors-only --save baseline-editors.edn
```

## Compare against baseline

```bash
# Using bb task (default 10% threshold)
bb baseline

# Or manually with custom threshold
clojure -M:benchmark --baseline benchmark-baseline.edn --threshold 15

# Editors only
clojure -M:benchmark --editors-only --baseline baseline-editors.edn
```

## Workflow

```bash
# 1. Save initial baseline
clojure -M:benchmark --editors-only --save before.edn

# 2. Make performance improvements...

# 3. Compare
clojure -M:benchmark --editors-only --baseline before.edn

# 4. Save new baseline if improved
bb update-baseline
```
