# Benchmarks

## Running benchmarks

```bash
# All benchmarks
clojure -M:benchmark

# Editor benchmarks only (fast)
clojure -M:benchmark --editors-only

# Integration benchmarks only
clojure -M:benchmark --integration-only

# Parallel processing
clojure -M:parallel-bench

# Scaling tests
clojure -M:scaling-bench
```

## Baseline tracking

See [baseline-management.md](baseline-management.md) for performance tracking and regression detection.

## Custom benchmarks

Create namespace in `benchmarks/`:

```clojure
(ns benchmarks.my-benchmark
  (:require [benchmarks.core :as bench]))

(defn my-benchmark []
  (bench/run-benchmark
   "Name"
   "Description"
   #(do-something)
   {:iterations 10 :warmup 3}))
```

Add alias to `deps.edn`:

```clojure
:my-bench {:main-opts ["-m" "benchmarks.my-benchmark"]}
```
