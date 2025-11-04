# Performance Characteristics

## Overview

Proserunner is designed for speed, typically processing thousands of lines per second. However, certain features have specific performance characteristics worth understanding.

## Command Performance

### `--ignore-issues`

**Behavior**: Re-runs proserunner analysis to ensure consistent issue numbering.

**Why**: Issue numbers depend on:
- Which files are processed
- Current ignore filters applied
- Sort order (file path, line number)

To guarantee `--ignore-issues 1,3,5` references the exact issues you saw in output, proserunner reconstructs the same view.

**Impact**: ~2× execution time compared to single run.

**Example**:
```bash
# First run: see issues with numbers
$ proserunner
[1] docs/guide.md:10:5 - cliche - "very unique"
[2] docs/guide.md:15:1 - repetition - "very very"
[3] src/readme.md:5:10 - cliche - "in my opinion"

# Second run: internally re-runs to match numbering
$ proserunner --ignore-issues 1,3
# Execution time: ~2× a normal run
```

**Mitigation**: This is a design trade-off for correctness. The feature is opt-in.

---

## File Operations

### Multiple Exclude Patterns

**Performance**: O(n) where n = number of files scanned

**Impact**: Negligible - pattern matching happens during file discovery.

**Example**:
```bash
# Minimal overhead regardless of pattern count
$ proserunner -e "node_modules/*" -e "build/*" -e "*.log" -e "test/fixtures/*"
```

**How it works**: Glob patterns are compiled once, then applied as files are discovered.

---

## Ignore Filtering

### Contextual Ignores

**Performance**: O(n × m) where:
- n = number of issues found
- m = number of ignore entries

**Typical scale**: Hundreds of issues × hundreds of ignores = ~100k comparisons

**Impact**: Negligible for typical projects (<1ms overhead)

**Edge cases**: Projects with 10,000+ issues and 1,000+ ignores may see measurable impact (~100ms)

**Optimization**: Contextual ignores with specific `:file`, `:line`, and `:check` fields are more selective and reduce false-positive comparisons.

---

## Caching

### Current Behavior

**Between invocations**: No caching between separate `proserunner` runs.

**Rationale**: Files change frequently during writing; caching could serve stale results.

**Within invocation**: Results are cached within a single run to avoid re-processing the same file when referenced multiple times.

### File System

**File reads**: Each file read once per invocation.

**Memory usage**: Files processed in streaming fashion - only active check contexts held in memory.

**Typical memory**: <100MB for projects with thousands of files.

---

## Parallelization

Proserunner uses parallel processing where beneficial:

### File-level parallelism

**What**: Multiple files processed concurrently (configurable).

**Default**: Auto-detects based on CPU cores.

**Control**: Use `--parallel-files` or `--sequential-files` flags.

**Trade-off**: Parallel processing uses more memory but improves throughput on multi-core systems.

### Line-level parallelism

**What**: Lines within a file can be processed in parallel.

**Default**: Enabled for large files (>1000 lines).

**Control**: Use `--sequential-lines` to disable.

**When to disable**: Debugging, or when checks have stateful dependencies between lines.

---

## Benchmarks

Typical performance on 2023-era hardware (M1 MacBook Pro):

| Project Size | Files | Total Lines | Time (single-threaded) | Time (parallel) |
|-------------|-------|-------------|----------------------|-----------------|
| Small | 10 | 1,000 | ~50ms | ~30ms |
| Medium | 100 | 10,000 | ~200ms | ~80ms |
| Large | 1,000 | 100,000 | ~2s | ~600ms |
| Very Large | 10,000 | 1,000,000 | ~20s | ~6s |

*Note: Times include file I/O, parsing, all default checks, and result formatting*

### Performance tips

1. **Use exclude patterns** for build directories and dependencies:
   ```bash
   proserunner -e "node_modules/*" -e "dist/*" -e "vendor/*"
   ```

2. **Limit file scope** when working on specific files:
   ```bash
   proserunner -f docs/**/*.md
   ```

3. **Use project ignores** instead of global for better performance:
   ```bash
   # Project .proserunner-ignore.edn is faster than ~/.proserunner-ignore.edn
   # (fewer entries to check)
   ```

4. **Profile with timing** to identify bottlenecks:
   ```bash
   time proserunner  # Simple timing
   ```

---

## Future Optimizations

Potential future improvements (not currently implemented):

- **Persistent caching**: Cache results between runs with smart invalidation
- **Incremental analysis**: Only re-analyze changed files (git-aware)
- **Lazy loading**: Stream results as they're found rather than buffering all
- **Index building**: Pre-build file index for very large repositories

---

## Questions?

If you encounter performance issues not covered here, please report them with:
- Project size (file count, total lines)
- Command used
- Timing results
- System specs (CPU, RAM, OS)
