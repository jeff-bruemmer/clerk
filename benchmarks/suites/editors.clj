(ns benchmarks.suites.editors
  "Benchmarks for individual editor performance."
  (:require [benchmarks.core :as bench]
            [editors.re :as re]
            [editors.existence :as existence]
            [editors.case :as case-editor]
            [editors.recommender :as recommender]
            [editors.case-recommender :as case-recommender]
            [editors.repetition :as repetition]))

(defn generate-test-lines
  "Generate test lines with various content patterns."
  [n]
  (vec (for [i (range n)]
         {:file "test.md"
          :text (str "This is line " i " with some test content. "
                    "The quick brown fox jumps over the lazy dog. "
                    "Testing various patterns and words here.")})))

(defn benchmark-regex-editor
  "Benchmark regex editor with pattern caching."
  []
  (let [lines (generate-test-lines 1000)
        check {:name "regex-test"
               :kind "regex"
               :expressions [{:re "\\bline\\s+\\d+" :message "Line numbers"}
                           {:re "\\bthe\\b" :message "The word 'the'"}
                           {:re "\\btest\\w*\\b" :message "Test words"}
                           {:re "\\b\\w+ing\\b" :message "Words ending in 'ing'"}]}]

    (bench/run-benchmark
     "Regex Editor"
     "Tests regex pattern matching with caching"
     #(doall (map (fn [line] (re/proofread line check)) lines))
     {:iterations 10
      :warmup 3
      :throughput-fn (fn [_ mean-ms] (/ 1000 (/ mean-ms 1000)))
      :throughput-unit "lines/sec"})))

(defn benchmark-existence-editor
  "Benchmark existence editor."
  []
  (let [lines (generate-test-lines 1000)
        check {:name "existence-test"
               :kind "existence"
               :specimens ["obviously" "basically" "actually" "literally"
                          "just" "very" "really" "quite"]
               :message "Avoid weak modifiers"}]

    (bench/run-benchmark
     "Existence Editor"
     "Tests word/phrase existence checking"
     #(doall (map (fn [line] (existence/proofread line check)) lines))
     {:iterations 10
      :warmup 3
      :throughput-fn (fn [_ mean-ms] (/ 1000 (/ mean-ms 1000)))
      :throughput-unit "lines/sec"})))

(defn benchmark-case-editor
  "Benchmark case-sensitive editor."
  []
  (let [lines (vec (for [i (range 1000)]
                    {:file "test.md"
                     :text (str "Line " i " has Internet, Email, and Website terms.")}))
        check {:name "case-test"
               :kind "case"
               :specimens ["Internet" "Email" "Website"]
               :message "Check proper capitalization"}]

    (bench/run-benchmark
     "Case Editor"
     "Tests case-sensitive word checking"
     #(doall (map (fn [line] (case-editor/proofread line check)) lines))
     {:iterations 10
      :warmup 3
      :throughput-fn (fn [_ mean-ms] (/ 1000 (/ mean-ms 1000)))
      :throughput-unit "lines/sec"})))

(defn benchmark-recommender-editor
  "Benchmark recommender editor."
  []
  (let [lines (generate-test-lines 1000)
        check {:name "recommender-test"
               :kind "recommender"
               :recommendations [{:prefer "use" :avoid "utilize"}
                                {:prefer "help" :avoid "facilitate"}
                                {:prefer "show" :avoid "demonstrate"}
                                {:prefer "about" :avoid "approximately"}]}]

    (bench/run-benchmark
     "Recommender Editor"
     "Tests word recommendation/substitution"
     #(doall (map (fn [line] (recommender/proofread line check)) lines))
     {:iterations 10
      :warmup 3
      :throughput-fn (fn [_ mean-ms] (/ 1000 (/ mean-ms 1000)))
      :throughput-unit "lines/sec"})))

(defn benchmark-case-recommender-editor
  "Benchmark case-sensitive recommender editor."
  []
  (let [lines (vec (for [i (range 1000)]
                    {:file "test.md"
                     :text (str "Line " i " with API, HTTP, and REST terms.")}))
        check {:name "case-recommender-test"
               :kind "case-recommender"
               :recommendations [{:prefer "API" :avoid "api"}
                                {:prefer "HTTP" :avoid "http"}]}]

    (bench/run-benchmark
     "Case Recommender Editor"
     "Tests case-sensitive word recommendation"
     #(doall (map (fn [line] (case-recommender/proofread line check)) lines))
     {:iterations 10
      :warmup 3
      :throughput-fn (fn [_ mean-ms] (/ 1000 (/ mean-ms 1000)))
      :throughput-unit "lines/sec"})))

(defn benchmark-repetition-editor
  "Benchmark repetition detection editor."
  []
  (let [lines (vec (for [i (range 1000)]
                    {:file "test.md"
                     :text (str "This is line " i " with with some duplicate words.")}))
        check {:name "repetition-test"
               :kind "repetition"
               :message "Avoid word repetition"}]

    (bench/run-benchmark
     "Repetition Editor"
     "Tests duplicate word detection"
     #(doall (map (fn [line] (repetition/proofread line check)) lines))
     {:iterations 10
      :warmup 3
      :throughput-fn (fn [_ mean-ms] (/ 1000 (/ mean-ms 1000)))
      :throughput-unit "lines/sec"})))

(defn run-all-editor-benchmarks
  "Run all editor benchmarks and return results."
  []
  [(benchmark-regex-editor)
   (benchmark-existence-editor)
   (benchmark-case-editor)
   (benchmark-recommender-editor)
   (benchmark-case-recommender-editor)
   (benchmark-repetition-editor)])
