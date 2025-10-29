(ns proserunner.metrics
  "Performance metrics collection and reporting."
  (:gen-class))

(def ^:private perf-stats
  (atom {:editor-calls {}
         :parallel-chunks 0
         :files-processed 0
         :lines-processed 0
         :start-time nil
         :end-time nil}))

(defn reset-metrics!
  "Reset all performance metrics."
  []
  (reset! perf-stats {:editor-calls {}
                      :parallel-chunks 0
                      :files-processed 0
                      :lines-processed 0
                      :start-time nil
                      :end-time nil}))

(defn start-timing!
  "Mark the start of a timed operation."
  []
  (swap! perf-stats assoc :start-time (System/nanoTime)))

(defn end-timing!
  "Mark the end of a timed operation."
  []
  (swap! perf-stats assoc :end-time (System/nanoTime)))

(defn record-editor-call!
  "Record a call to a specific editor."
  [editor-kind elapsed-ns]
  (swap! perf-stats
         (fn [stats]
           (update-in stats [:editor-calls editor-kind]
                     (fn [old]
                       (let [existing (or old {:count 0 :total-ns 0})]
                         {:count (inc (:count existing))
                          :total-ns (+ (:total-ns existing) elapsed-ns)}))))))

(defn record-parallel-chunk!
  "Record that a parallel chunk was processed."
  []
  (swap! perf-stats update :parallel-chunks inc))

(defn record-file!
  "Record that a file was processed."
  []
  (swap! perf-stats update :files-processed inc))

(defn record-lines!
  "Record number of lines processed."
  [n]
  (swap! perf-stats update :lines-processed + n))

(defn get-metrics
  "Get current performance metrics."
  []
  @perf-stats)

(defn print-metrics
  "Print performance metrics report."
  []
  (let [stats @perf-stats
        {:keys [editor-calls parallel-chunks files-processed
                lines-processed start-time end-time]} stats]
    (println "\n╔════════════════════════════════════════════════════════════════════════════╗")
    (println "║                     PERFORMANCE METRICS REPORT                             ║")
    (println "╚════════════════════════════════════════════════════════════════════════════╝\n")

    (when (and start-time end-time)
      (let [elapsed-ms (/ (- end-time start-time) 1000000.0)]
        (println (format "Total time: %.2f ms" elapsed-ms))
        (println (format "Files processed: %d" files-processed))
        (println (format "Lines processed: %d" lines-processed))
        (when (> files-processed 0)
          (println (format "Avg time per file: %.2f ms" (/ elapsed-ms files-processed))))
        (when (> lines-processed 0)
          (println (format "Throughput: %.2f lines/sec" (/ lines-processed (/ elapsed-ms 1000)))))))

    (when (> parallel-chunks 0)
      (println (format "\nParallel chunks executed: %d" parallel-chunks)))

    (when (seq editor-calls)
      (println "\nEditor Performance:")
      (println (clojure.string/join "" (repeat 80 "-")))
      (doseq [[editor {:keys [count total-ns]}] (sort-by (comp :total-ns second) > editor-calls)]
        (let [avg-ns (/ total-ns count)
              total-ms (/ total-ns 1000000.0)
              avg-ms (/ avg-ns 1000000.0)]
          (println (format "%-20s: %6d calls, %8.2f ms total, %6.3f ms avg"
                          (name editor) count total-ms avg-ms)))))))
