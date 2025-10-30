(ns benchmarks.suites.integration
  "End-to-end integration benchmarks using real files."
  (:require [benchmarks.core :as bench]
            [proserunner.vet :as vet]
            [clojure.java.io :as io]
            [editors.registry :as registry]
            [editors.existence :as existence]
            [editors.recommender :as recommender]
            [editors.repetition :as repetition]
            [editors.case :as case-editor]
            [editors.case-recommender :as case-recommender]
            [editors.re :as re]))

(defn setup-editors!
  "Register all editors for benchmarking."
  []
  (registry/register-editor! "existence" existence/proofread)
  (registry/register-editor! "recommender" recommender/proofread)
  (registry/register-editor! "repetition" repetition/proofread)
  (registry/register-editor! "case" case-editor/proofread)
  (registry/register-editor! "case-recommender" case-recommender/proofread)
  (registry/register-editor! "regex" re/proofread))

(defn count-lines-in-file
  "Count lines in a file."
  [filepath]
  (with-open [rdr (io/reader filepath)]
    (count (line-seq rdr))))

(defn benchmark-file-processing
  "Benchmark processing a specific file."
  [file-path config-path description]
  (when (.exists (io/file file-path))
    (let [line-count (count-lines-in-file file-path)
          input (vet/make-input {:file file-path
                                :config config-path
                                :output "table"
                                :code-blocks false
                                :no-cache true})]

      (bench/run-benchmark
       (str "File: " (.getName (io/file file-path)))
       (format "%s (%d lines)" description line-count)
       #(vet/compute input)
       {:iterations 5
        :warmup 2
        :throughput-fn (fn [_ mean-ms] (/ line-count (/ mean-ms 1000)))
        :throughput-unit "lines/sec"}))))

(defn benchmark-small-file
  "Benchmark processing a small file (~10 lines)."
  [config-path]
  (benchmark-file-processing
   "resources/benchmark-data/small.md"
   config-path
   "Small file"))

(defn benchmark-medium-file
  "Benchmark processing a medium file (~100 lines)."
  [config-path]
  (benchmark-file-processing
   "resources/benchmark-data/medium.md"
   config-path
   "Medium file"))

(defn benchmark-large-file
  "Benchmark processing a large file (~1000 lines)."
  [config-path]
  (benchmark-file-processing
   "resources/benchmark-data/large.md"
   config-path
   "Large file"))

(defn benchmark-xlarge-file
  "Benchmark processing an extra large file (~5000 lines)."
  [config-path]
  (benchmark-file-processing
   "resources/benchmark-data/xlarge.md"
   config-path
   "Extra large file"))

(defn benchmark-cache-performance
  "Benchmark cache hit performance by processing the same file twice."
  [config-path]
  (when (.exists (io/file "resources/benchmark-data/large.md"))
    (let [file-path "resources/benchmark-data/large.md"
          input (vet/make-input {:file file-path
                                :config config-path
                                :output "table"
                                :code-blocks false
                                :no-cache false})]

      ;; First run to populate cache
      (vet/compute input)

      ;; Benchmark second run with cache
      (bench/run-benchmark
       "Cache Performance"
       "Cached file processing (should be very fast)"
       #(vet/compute input)
       {:iterations 10
        :warmup 2
        :throughput-fn (fn [_ mean-ms] (/ (count-lines-in-file file-path) (/ mean-ms 1000)))
        :throughput-unit "lines/sec"}))))

(defn run-all-integration-benchmarks
  "Run all integration benchmarks and return results."
  []
  (setup-editors!)
  (let [config-path (str (System/getProperty "user.home")
                        (java.io.File/separator)
                        ".proserunner"
                        (java.io.File/separator)
                        "config.edn")]
    (remove nil?
            [(benchmark-small-file config-path)
             (benchmark-medium-file config-path)
             (benchmark-large-file config-path)
             (benchmark-xlarge-file config-path)
             (benchmark-cache-performance config-path)])))
