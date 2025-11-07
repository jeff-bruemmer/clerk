(ns proserunner.vet.cache
  "Caching validation and incremental computation for prose checking.

   Provides three-level caching strategy to minimize redundant work:

   1. Full cache hit - No changes to file, checks, or config
      → Reuse entire cached result (valid-result?)

   2. Partial cache hit - Only file content changed, checks/config unchanged
      → Incremental recompute: process only changed lines (compute-changed)
      → Reuse cached results for unchanged lines

   3. Cache miss - Checks or config changed
      → Full recompute required

   Line matching uses text content as key, allowing cache to survive line
   number shifts when lines are inserted/deleted elsewhere in the file.

   See: proserunner.storage for cache persistence."
  (:gen-class)
  (:require [proserunner.storage :as storage]))

(set! *warn-on-reflection* true)

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
     (storage/stable-hash lines)
     (storage/stable-hash file)
     config
     (storage/stable-hash config)
     (storage/stable-hash checks)
     output
     (let [changed-lines (changed cached-line-map lines)]
       (->> (process-fn checks changed-lines parallel-lines)
            (combine updated-results))))))
