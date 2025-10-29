# Performance benchmarks

Proserunner includes a comprehensive benchmark suite to detect performance regressions and measure optimization impacts.

## Running benchmarks

Run all benchmarks (editor + integration):

```bash
clojure -M:benchmark
```

Run only editor benchmarks (fast, no files required):

```bash
clojure -M:benchmark --editors-only
```

Run only integration benchmarks (tests with real files):

```bash
clojure -M:benchmark --integration-only
```

Run parallel processing benchmarks:

```bash
clojure -M:parallel-bench
```

Run comprehensive scaling benchmarks (file size and count):

```bash
clojure -M:scaling-bench
```

## Performance metrics

Get detailed performance metrics for any proserunner run:

```bash
clojure -M:run -f <file> --metrics
```

The `--metrics` flag automatically disables caching and provides:

- Total execution time
- Files and lines processed
- Throughput (lines/sec)
- Average time per file

## Baseline tracking

Save current performance as baseline:

```bash
./tools/update-baseline.sh
```

## Adding custom benchmarks

To add new benchmarks, create a namespace in `benchmarks/` and use the benchmark utilities:

```clojure
(ns benchmarks.my-benchmark
  (:require [benchmarks.core :as bench]
            [proserunner.vet :as vet])
  (:gen-class))

(defn my-benchmark
  "Benchmark description."
  []
  (bench/run-benchmark
   "Benchmark Name"
   "Description of what is being tested"
   #(do-something)  ; Function to benchmark
   {:iterations 10
    :warmup 3
    :throughput-fn (fn [result mean-ms] (/ items (/ mean-ms 1000)))
    :throughput-unit "items/sec"}))

(defn -main
  "Entry point for benchmark."
  [& args]
  (let [result (my-benchmark)]
    (bench/print-summary [result]))
  (shutdown-agents))
```

Then add an alias in `deps.edn`:

```clojure
:my-bench
{:extra-paths ["."]
 :main-opts ["-m" "benchmarks.my-benchmark"]}
```

Run your benchmark with:

```bash
clojure -M:my-bench
```
