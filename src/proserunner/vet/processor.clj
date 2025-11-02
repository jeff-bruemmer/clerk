(ns proserunner.vet.processor
  "Core check processing engine."
  (:gen-class)
  (:require [editors.registry :as registry]))

(set! *warn-on-reflection* true)

(defn safe-dispatch
  "Safely dispatch to an editor, catching and logging any errors.
   Returns the line unchanged if an error occurs."
  [line check]
  (try
    (registry/dispatch line check)
    (catch Exception e
      (let [{:keys [name kind]} check
            {:keys [file line-num]} line]
        (println (str "Warning: Check '" name "' (" kind ") failed on "
                     file ":" line-num ": " (.getMessage e))))
      line)))

(defn dispatch
  "Takes a `line` and a `check` returns result of relevant editor.
  Uses dynamic registry for extensibility."
  [line check]
  (safe-dispatch line check))

(defn process
  "Takes `checks` and `lines` and runs each check on each line,
  return lines with any issues found.
  Uses pmap for parallel processing of lines when parallel? is true."
  [checks lines parallel?]
  (let [map-fn (if parallel? pmap map)]
    (->> lines
         (map-fn #(reduce dispatch % checks))
         (filter :issue?))))
