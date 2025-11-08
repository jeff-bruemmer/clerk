(ns proserunner.output.prep
  "Issue preparation and sorting."
  (:gen-class)
  (:require [clojure.string :as string]
            [proserunner.fmt :as fmt]))

(set! *warn-on-reflection* true)

(defn time-elapsed
  "Prints elapsed time if timer is enabled."
  [{:keys [timer start-time]}]
  (let [end-time (System/currentTimeMillis)]
    (when timer (println "Completed in" (- end-time start-time) "ms."))))

(defn prep
  "Prepares results for printing by merging line data with each issue."
  [{:keys [line-num issues text]}]
  (reduce (fn [l issue]
            (let [{:keys [file name specimen col-num message kind]} issue]
              (conj l {:file file
                       :line-num line-num
                       :col-num (inc col-num)
                       :specimen (string/trim specimen)
                       :line-text (string/trim text)
                       :name (string/capitalize (string/replace name "-" " "))
                       :message (fmt/sentence-dress message)
                       :kind kind})))
          []
          issues))

(defn issue-str
  "Creates a simplified result string for grouped results.
  Optional issue-num parameter adds a numbered prefix."
  ([issue] (issue-str nil issue))
  ([issue-num {:keys [line-num col-num specimen message]}]
   (string/join "\t"
     (cond-> []
       issue-num (conj (str "[" issue-num "]"))
       true (conj (str line-num ":" col-num))
       true (conj (str "\"" specimen "\"" " -> " message))))))
