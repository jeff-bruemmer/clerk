(ns proserunner.process
  "File processing orchestration.

  This namespace contains the main file processing logic, separated from
  core.clj to avoid circular dependencies with the effects system."
  (:gen-class)
  (:require [proserunner
             [metrics :as metrics]
             [shipping :as ship]
             [vet :as vet]]))

(set! *warn-on-reflection* true)

(defn proserunner
  "Proserunner takes options and vets a text with the supplied checks."
  [options]
  (try
    (let [opts (if (:metrics options)
                 (do
                   (metrics/reset-metrics!)
                   (metrics/start-timing!)
                   ;; Force no-cache when metrics enabled
                   (assoc options :no-cache true))
                 options)
          result (->> opts
                      (vet/compute-or-cached)
                      (ship/out))]
      (when (:metrics options)
        (metrics/end-timing!)
        (metrics/print-metrics))
      result)
    (catch java.util.regex.PatternSyntaxException e
      (println "Error: Invalid regex pattern in check definition.")
      (println "Details:" (.getMessage e))
      (System/exit 1))
    (catch java.io.IOException e
      (println "Error: File I/O operation failed.")
      (println "Details:" (.getMessage e))
      (System/exit 1))
    (catch Exception e
      (println "Error: An unexpected error occurred during processing.")
      (println "Details:" (.getMessage e))
      (System/exit 1))))
