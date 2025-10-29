# Testing proserunner

## Running tests

Run all tests:

```bash
clojure -M:test
```

## Performance benchmarks

For comprehensive performance benchmarking and regression detection, see [benchmarks.md](benchmarks.md).

Quick start:

```bash
# Run all benchmarks
clojure -M:benchmark

# Run only editor benchmarks (fast)
clojure -M:benchmark --editors-only
```
