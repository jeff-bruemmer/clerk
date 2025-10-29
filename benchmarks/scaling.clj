(ns benchmarks.scaling
  "Scaling benchmarks - tests performance across file sizes and counts."
  (:require [benchmarks.core :as bench]
            [proserunner.vet :as vet]
            [clojure.java.io :as io]
            [editors.registry :as registry]
            [editors.existence :as existence]
            [editors.recommender :as recommender]
            [editors.repetition :as repetition]
            [editors.case :as case-editor]
            [editors.case-recommender :as case-recommender]
            [editors.re :as re])
  (:gen-class))

(defn setup-editors!
  "Register all editors."
  []
  (registry/register-editor! "existence" existence/proofread)
  (registry/register-editor! "recommender" recommender/proofread)
  (registry/register-editor! "repetition" repetition/proofread)
  (registry/register-editor! "case" case-editor/proofread)
  (registry/register-editor! "case-recommender" case-recommender/proofread)
  (registry/register-editor! "regex" re/proofread))

(defn benchmark-file-size
  "Benchmark a single file of specific size."
  [file-name config-path parallel?]
  (when (.exists (io/file file-name))
    (let [line-count (with-open [rdr (io/reader file-name)]
                       (count (line-seq rdr)))
          mode (if parallel? "parallel" "sequential")]
      (bench/run-benchmark
       (format "%d lines (%s)" line-count mode)
       (format "Processing %s with %s line processing" file-name mode)
       #(let [input (vet/make-input {:file file-name
                                      :config config-path
                                      :output "table"
                                      :code-blocks false
                                      :no-cache true
                                      :parallel-files false
                                      :parallel-lines parallel?})]
          (vet/compute input))
       {:iterations 10
        :warmup 3
        :throughput-fn (fn [_ mean-ms] (/ line-count (/ mean-ms 1000)))
        :throughput-unit "lines/sec"}))))

(defn benchmark-file-count
  "Benchmark processing multiple files in parallel."
  [files config-path]
  (let [existing-files (filter #(.exists (io/file %)) files)
        total-lines (reduce + (map (fn [f]
                                    (with-open [rdr (io/reader f)]
                                      (count (line-seq rdr))))
                                  existing-files))]
    (when (seq existing-files)
      (bench/run-benchmark
       (format "%d files (%d lines)" (count existing-files) total-lines)
       (format "Processing %d files in parallel" (count existing-files))
       #(doall (pmap (fn [file]
                       (let [input (vet/make-input {:file file
                                                    :config config-path
                                                    :output "table"
                                                    :code-blocks false
                                                    :no-cache true
                                                    :parallel-files false
                                                    :parallel-lines true})]
                         (vet/compute input)))
                     existing-files))
       {:iterations 10
        :warmup 3
        :throughput-fn (fn [_ mean-ms] (/ total-lines (/ mean-ms 1000)))
        :throughput-unit "lines/sec"}))))

(defn -main
  "Main entry point for scaling benchmarks."
  [& args]
  (println "\n╔════════════════════════════════════════════════════════════════════════════╗")
  (println "║                  PROSERUNNER SCALING BENCHMARKS                            ║")
  (println "╚════════════════════════════════════════════════════════════════════════════╝\n")

  (setup-editors!)

  (let [config-path (str (System/getProperty "user.home")
                        (java.io.File/separator)
                        ".proserunner"
                        (java.io.File/separator)
                        "config.edn")]

    ;; Test 1: File Size Scaling (Sequential vs Parallel)
    (println "\n┌─ FILE SIZE SCALING ────────────────────────────────────────────────┐")
    (println "│ How does performance scale with file size?                         │")
    (println "└────────────────────────────────────────────────────────────────────┘\n")

    (let [seq-results (remove nil?
                        [(benchmark-file-size "resources/benchmark-data/small.md" config-path false)
                         (benchmark-file-size "resources/benchmark-data/medium.md" config-path false)
                         (benchmark-file-size "resources/benchmark-data/large.md" config-path false)
                         (benchmark-file-size "resources/benchmark-data/xlarge.md" config-path false)])
          par-results (remove nil?
                        [(benchmark-file-size "resources/benchmark-data/small.md" config-path true)
                         (benchmark-file-size "resources/benchmark-data/medium.md" config-path true)
                         (benchmark-file-size "resources/benchmark-data/large.md" config-path true)
                         (benchmark-file-size "resources/benchmark-data/xlarge.md" config-path true)])]

      (println "\nSequential Processing:")
      (bench/print-summary seq-results)

      (println "\nParallel Line Processing:")
      (bench/print-summary par-results)

      ;; Calculate speedup at each size
      (println "\n" (clojure.string/join "" (repeat 80 "=")))
      (println " PARALLEL SPEEDUP BY FILE SIZE")
      (println (clojure.string/join "" (repeat 80 "=")))
      (doseq [[seq-r par-r] (map vector seq-results par-results)]
        (let [speedup (/ (:mean-ms seq-r) (:mean-ms par-r))]
          (println (format "%s: %.2fx speedup (seq: %.2f ms, par: %.2f ms)"
                          (:name seq-r)
                          speedup
                          (:mean-ms seq-r)
                          (:mean-ms par-r))))))

    ;; Test 2: File Count Scaling
    (println "\n\n┌─ FILE COUNT SCALING ───────────────────────────────────────────────┐")
    (println "│ How does parallel file processing scale with file count?           │")
    (println "└────────────────────────────────────────────────────────────────────┘\n")

    (let [small "resources/benchmark-data/small.md"
          medium "resources/benchmark-data/medium.md"
          count-results (remove nil?
                          [(benchmark-file-count [small] config-path)
                           (benchmark-file-count [small medium] config-path)
                           (benchmark-file-count [small medium small medium] config-path)
                           (benchmark-file-count [small medium small medium small medium small medium] config-path)])]
      (bench/print-summary count-results)))

  (shutdown-agents))
