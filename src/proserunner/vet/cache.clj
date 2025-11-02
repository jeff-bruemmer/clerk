(ns proserunner.vet.cache
  "Caching validation and incremental computation."
  (:gen-class)
  (:require [proserunner.storage :as storage]))

(set! *warn-on-reflection* true)

(defn stable-hash
  "Computes a stable hash that persists across JVM restarts.
  Uses hash of the string representation rather than JVM's hash."
  [data]
  (hash (pr-str data)))

(defn valid-result?
  "If no changes to the file, checks, or config: reuse the cached results.
  Used to determine if we need to compute new results."
  [{:keys [cached-result lines config checks]}]
  (and (storage/valid-config? cached-result config)
       (storage/valid-checks? cached-result checks)
       (storage/valid-lines? cached-result lines)))

(defn valid-checks?
  "If no changes to config or checks: the checks remain valid.
  We need only compute the changes to lines."
  [{:keys [cached-result config checks]}]
  (and (storage/valid-config? cached-result config)
       (storage/valid-checks? cached-result checks)))

(defn- text-to-line-number-map
  "Takes `lines` and returns a map of each line's text to its line number."
  [lines]
  (reduce (fn [m {:keys [text line-num]}]
            (assoc m text line-num))
          {}
          lines))

(defn- update-line-number
  [cached-line-map results]
  (map (fn [result]
         (let [text (:text result)
               line-num (get cached-line-map text)]
           (assoc result :line-num line-num)))
       results))

(defn- changed
  "Returns a sequence of lines that differ from the cached lines."
  [cached-line-map new-lines]
  (reduce (fn [changed-lines line]
            (if (get cached-line-map (:text line))
              changed-lines
              (conj changed-lines line)))
          []
          new-lines))

(defn- combine
  "Combines old results with new results."
  [cached-results new-results]
  (->> new-results
       (concat cached-results)
       (remove #(nil? (:line-num %)))
       distinct))

(defn compute-changed
  "Only process lines that have changed since the last vetting. If line numbers
  have shifted, update the results so they map to the correct line number."
  [input process-fn]
  (let [{:keys [file lines config checks cached-result output parallel-lines]} input
        cached-line-map (->> cached-result :lines text-to-line-number-map)
        line-num-map (text-to-line-number-map lines)
        updated-results (update-line-number line-num-map (:results cached-result))]
    (storage/->Result
     lines
     (stable-hash lines)
     (stable-hash file)
     config
     (stable-hash config)
     (stable-hash checks)
     output
     (let [changed-lines (changed cached-line-map lines)]
       (->> (process-fn checks changed-lines parallel-lines)
            (combine updated-results))))))
