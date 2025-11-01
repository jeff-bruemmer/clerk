(ns proserunner.process
  "File processing orchestration.

  This namespace contains the main file processing logic, separated from
  core.clj to avoid circular dependencies with the effects system."
  (:gen-class)
  (:require [proserunner
             [result :as result]
             [shipping :as ship]
             [vet :as vet]]))

(set! *warn-on-reflection* true)

(defn proserunner
  "Proserunner takes options and vets a text with the supplied checks.

  Returns Result with output or Failure on error."
  [options]
  (try
    (let [result (vet/compute-or-cached options)]
      (if (result/failure? result)
        (do
          (println "Error:" (:error result))
          (when (seq (:context result))
            (println "Context:" (:context result)))
          (System/exit 1))
        (ship/out (:value result))))
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
