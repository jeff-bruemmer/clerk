(ns tasks.baseline
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.string :as str]
            [tasks.util :refer [print-lines]]))

;; Pure functions

(defn format-header
  "Format a header string with border."
  [title]
  (str "╔════════════════════════════════════════════════════════════════════════════╗\n"
       "║" (format "%-76s" (str "  " title)) "║\n"
       "╚════════════════════════════════════════════════════════════════════════════╝"))

(defn parse-baseline-args
  "Parse command-line arguments for baseline comparison."
  [args]
  {:threshold (first args)
   :baseline-file "benchmark-baseline.edn"
   :default-threshold 10})

(defn build-benchmark-cmd
  "Build benchmark command string."
  [{:keys [baseline-file threshold]}]
  (let [base-cmd (str "clojure -M:benchmark --baseline " baseline-file)]
    (if threshold
      (str base-cmd " --threshold " threshold)
      base-cmd)))

(defn format-threshold-msg
  "Format threshold message."
  [threshold]
  (if threshold
    (format "Threshold:     %s%%" threshold)
    "Threshold:     10% (default)"))

(defn format-success-msg
  "Format success message with threshold."
  [threshold]
  (let [thresh-value (or threshold 10)]
    (format "  No performance regressions detected (threshold: %s%%)" thresh-value)))

(def error-messages
  "Map of error message components."
  {:comparison-failed ["✗ Benchmark comparison failed!"
                       "  Performance regressions detected or benchmarks errored"]
   :update-instructions ["To update the baseline:"
                         "  bb update-baseline"]
   :threshold-instructions ["To run with a higher threshold:"
                            "  bb baseline 20  # Allow up to 20% regression"]})

;; Side effects

(defn check-baseline-exists!
  "Check if baseline file exists, exit if not."
  [baseline-file]
  (when-not (fs/exists? baseline-file)
    (print-lines ["ERROR: Baseline file not found:" baseline-file
                  ""
                  "To create a baseline, run:"
                  "  bb update-baseline"
                  ""])
    (System/exit 1)))

(defn run-shell-cmd
  "Run shell command and return result map."
  [cmd]
  (shell {:continue true} cmd))

(defn exit-on-failure!
  "Exit process if result indicates failure."
  [result error-msgs]
  (when-not (zero? (:exit result))
    (println "")
    (print-lines (:comparison-failed error-msgs))
    (println "")
    (print-lines (:update-instructions error-msgs))
    (println "")
    (print-lines (:threshold-instructions error-msgs))
    (println "")
    (System/exit (:exit result))))

;; Main workflows

(defn baseline
  "Compare benchmark results against baseline."
  [& args]
  (let [config (parse-baseline-args args)
        {:keys [threshold baseline-file]} config]

    ;; Print header
    (println (format-header "PROSERUNNER BASELINE COMPARISON"))
    (println "")

    ;; Check prerequisites
    (check-baseline-exists! baseline-file)

    ;; Print configuration
    (print-lines ["Baseline file:" baseline-file
                  (format-threshold-msg threshold)
                  ""
                  "Running benchmarks and comparing against baseline..."
                  ""])

    ;; Run benchmark
    (let [cmd (build-benchmark-cmd config)
          result (run-shell-cmd cmd)]

      ;; Handle results
      (if (zero? (:exit result))
        (print-lines [""
                      "✓ All benchmarks passed!"
                      (format-success-msg threshold)
                      ""])
        (exit-on-failure! result error-messages)))))

(defn backup-baseline
  "Create backup of existing baseline file."
  [baseline-file backup-file]
  (when (fs/exists? baseline-file)
    (println "Found existing baseline:" baseline-file)
    (println "Creating backup:" backup-file)
    (fs/copy baseline-file backup-file {:replace-existing true})
    (println "")))

(defn show-update-instructions
  "Show instructions for using new baseline."
  [baseline-file backup-file]
  (print-lines [""
                "New baseline saved to:" baseline-file
                ""])
  (when (fs/exists? backup-file)
    (print-lines ["To compare old vs new baseline:"
                  (format "  diff %s %s" backup-file baseline-file)
                  ""]))
  (print-lines ["To test the new baseline:"
                "  bb baseline"]))

(defn update-baseline
  "Update performance baseline."
  []
  (let [baseline-file "benchmark-baseline.edn"
        backup-file "benchmark-baseline.edn.bak"]

    (println (format-header "PROSERUNNER BASELINE UPDATE"))
    (println "")

    (backup-baseline baseline-file backup-file)

    (print-lines ["Running benchmarks to establish new baseline..."
                  ""])

    (let [result (shell {:continue true} (str "clojure -M:benchmark --save " baseline-file))]
      (when-not (zero? (:exit result))
        (println "\nERROR: Failed to run benchmarks")
        (System/exit (:exit result))))

    (show-update-instructions baseline-file backup-file)))
