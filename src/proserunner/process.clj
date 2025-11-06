(ns proserunner.process
  "File processing orchestration.

  This namespace contains the main file processing logic, separated from
  core.clj to avoid circular dependencies with the effects system."
  (:gen-class)
  (:require [proserunner
             [output :as output]
             [result :as result]
             [vet :as vet]]))

(set! *warn-on-reflection* true)

(defn proserunner
  "Proserunner takes options and vets a text with the supplied checks.

  Returns Result with output or Failure on error."
  [options]
  (result/try-result-with-context
   (fn []
     (let [vet-result (vet/compute-or-cached options)]
       (if (result/failure? vet-result)
         ;; Propagate vet failure
         vet-result
         ;; Process successful vet result
         (let [output-result (output/out (:value vet-result))]
           ;; output/out may return Result or nil
           (if (or (nil? output-result) (result/success? output-result))
             (result/ok output-result)
             output-result)))))
   {:operation :process-file
    :options options}))
