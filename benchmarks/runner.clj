(ns benchmarks.runner
  "Main benchmark runner - executes all benchmarks and reports results."
  (:require [benchmarks.core :as bench]
            [benchmarks.suites.editors :as editor-bench]
            [benchmarks.suites.integration :as integration-bench]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-s" "--save FILE" "Save results to FILE for baseline tracking"
    :default nil]
   ["-b" "--baseline FILE" "Compare against baseline results from FILE"
    :default nil]
   ["-t" "--threshold PCT" "Regression threshold percentage"
    :default 10.0
    :parse-fn #(Double/parseDouble %)]
   ["-e" "--editors-only" "Run only editor benchmarks"]
   ["-i" "--integration-only" "Run only integration benchmarks"]
   ["-h" "--help" "Show help"]])

(defn print-banner
  "Print benchmark suite banner."
  []
  (println "\n")
  (println "╔════════════════════════════════════════════════════════════════════════════╗")
  (println "║                    PROSERUNNER PERFORMANCE BENCHMARK SUITE                 ║")
  (println "╚════════════════════════════════════════════════════════════════════════════╝")
  (println))

(defn print-usage
  "Print usage information."
  [options-summary]
  (println "\nUsage: clojure -M:benchmark [options]\n")
  (println "Options:")
  (println options-summary)
  (println "\nExamples:")
  (println "  # Run all benchmarks")
  (println "  clojure -M:benchmark")
  (println "\n  # Save results as baseline")
  (println "  clojure -M:benchmark --save benchmark-results.edn")
  (println "\n  # Compare against baseline")
  (println "  clojure -M:benchmark --baseline benchmark-results.edn --threshold 15")
  (println "\n  # Run only editor benchmarks")
  (println "  clojure -M:benchmark --editors-only"))

(defn run-benchmarks
  "Run all benchmarks based on options."
  [{:keys [editors-only integration-only]}]
  (let [run-editors? (or (not integration-only) editors-only)
        run-integration? (or (not editors-only) integration-only)]

    (cond-> []
      run-editors?
      (into (do
              (println "\n┌─ EDITOR BENCHMARKS ────────────────────────────────────────────────┐")
              (println "│ Testing individual editor performance with synthetic data          │")
              (println "└────────────────────────────────────────────────────────────────────┘")
              (editor-bench/run-all-editor-benchmarks)))

      run-integration?
      (into (do
              (println "\n┌─ INTEGRATION BENCHMARKS ───────────────────────────────────────────┐")
              (println "│ Testing end-to-end performance with real files                     │")
              (println "└────────────────────────────────────────────────────────────────────┘")
              (integration-bench/run-all-integration-benchmarks))))))

(defn -main
  "Main entry point for benchmark runner."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]

    (cond
      (:help options)
      (print-usage summary)

      errors
      (do
        (println "Errors:")
        (doseq [error errors]
          (println " " error))
        (print-usage summary)
        (System/exit 1))

      :else
      (do
        (print-banner)

        ;; Check if benchmark files exist for integration tests
        (when-not (and (:editors-only options)
                      (not (:integration-only options)))
          (when-not (.exists (io/file "resources/benchmark-data/small.md"))
            (println "Warning: resources/benchmark-data/ files not found. Integration benchmarks will be skipped.")
            (println "These files should exist in the repository.\n")))

        ;; Run benchmarks
        (println "Starting benchmark suite...\n")
        (let [start-time (System/currentTimeMillis)
              results (run-benchmarks options)
              end-time (System/currentTimeMillis)
              total-time-sec (/ (- end-time start-time) 1000.0)]

          ;; Print results
          (bench/print-summary results)

          ;; Save results if requested
          (when-let [save-file (:save options)]
            (bench/save-results results save-file))

          ;; Compare to baseline if requested
          (when-let [baseline-file (:baseline options)]
            (if-let [baseline (bench/load-baseline baseline-file)]
              (let [comparison (bench/compare-to-baseline results baseline (:threshold options))]
                (bench/print-comparison comparison (:threshold options))

                ;; Exit with error code if regressions detected
                (when (seq (:regressions comparison))
                  (println "\nPerformance regressions detected!")
                  (System/exit 1)))
              (println (format "\nWarning: Could not load baseline from %s" baseline-file))))

          (println (format "\nTotal benchmark time: %.2f seconds" total-time-sec))
          (println "\nBenchmark suite completed successfully\n"))))
  (shutdown-agents)))
