(ns clerk.vet
  "Computes results of running all the checks on each file,
   using cached results where possible."
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clerk
    [checks :as checks]
    [config :as conf]
    [storage :as store]
    [text :as text]]
   [editors
    [re :as re]
    [case :as c]
    [case-recommender :as cr]
    [existence :as existence]
    [recommender :as recommender]
    [repetition :as repetition]]))

(set! *warn-on-reflection* true)

;;;; Combine user input and the relevant cached results

(defrecord Input
           [files
            lines
            config
            checks
            cached-result
            output])

(defn get-lines-from-all-files
  [code-blocks file]
  (->> file
       io/file
       file-seq
       (map str)
       (filter text/supported-file-type?)
       text/handle-invalid-file
       (mapcat (partial text/fetch! code-blocks))))

(defn make-input
  "Input combines user-defined options and arguments
  with the relevant cached results."
  [{:keys [file config output code-blocks]}]
  (let [c (conf/fetch-or-create! config)]
    (map->Input
     {:file file
      :lines (get-lines-from-all-files code-blocks file)
      :config c
      :checks (checks/create c)
      :cached-result (store/inventory file)
      :output output})))

;;;; Core functions for vetting a lines of text with each check.

(defn dispatch
  "Takes a `line` and a `check` returns result of relevant editor.
  If no matching check, `dispatch` throws an exception."
  [line check]
  (let [{:keys [kind]} check]
    (case kind
      "existence" (existence/proofread line check)
      "recommender" (recommender/proofread line check)
      "repetition" (repetition/proofread line check)
      "case" (c/proofread line check)
      "case-recommender" (cr/proofread line check)
      "regex" (re/proofread line check)
      (throw (Exception. (str "Not a valid check: " kind))))))

(defn process
  "Takes `checks` and `lines` and runs each check on each line,
  return lines with any issues found."
  [checks lines]
  (->> lines
       (pmap #(reduce dispatch % checks))
       (filter #(:issue? %))))

(defn compute
  "Takes an input, and returns the results of
  running the configured checks on each line of text in the file."
  [{:keys [file lines config checks output]}]
  (store/map->Result {:lines lines
                      :lines-hash (hash lines)
                      :file-hash (hash file)
                      :config config
                      :config-hash (hash config)
                      :check-hash (hash checks)
                      :output output
                      :results (process checks lines)}))

;;;; Determine which lines have changed, and only process those changes.

(defn assoc-line
  "Adds the `text` of a line as a key to the map `m`, with val `line-num`."
  [m {:keys [text line-num]}]
  (assoc m text line-num))

(defn text-ln
  "Takes `lines` and returns a map of each line's text to its line number."
  [lines]
  (reduce assoc-line {} lines))

(defn update-line-num
  [cached-line-map results]
  (map (fn [result]
         (let [t (:text result)
               ln (get cached-line-map t)]
           (assoc result :line-num ln))) results))

(defn changed
  "Returns a sequence of lines that differ from the cached lines."
  [cached-line-map new-lines]
  (reduce (fn [changed-lines l]
            (if (get cached-line-map (:text l))
              ;; If l's text is in the map,
              ;; we don't need to recompute the results
              changed-lines
              ;; otherwise we add the line to changed-lines
              (conj changed-lines l)))
          []
          new-lines))

(defn combine
  "Combines old results with new results."
  [cached-results new-results]
  (->> new-results
       (concat cached-results)
       (remove #(nil? (:line-num %)))
       distinct))

(defn compute-changed
  "Only process lines that have changed since the last vetting. If line numbers
  have shifted, update the results so they map to the correct line number."
  [input]
  (let [{:keys [file lines config checks cached-result output]} input
        ;; build a map of previous lines
        ;; to determine which lines we need to reprocess.
        cached-line-map (->> cached-result
                             :lines
                             text-ln)
        line-num-map (text-ln lines)
        ;; Changes to text may have shifted line numbers,
        ;; so we update the cached results.
        updated-results (update-line-num line-num-map (:results cached-result))]
    ;; Return a result.
    (store/->Result
     lines
     (hash lines)
     (hash file)
     config
     (hash checks)
     output
     (->> lines
          ;; what lines have changed?
          (changed cached-line-map)
          ;; Process only changed lines
          (process checks)
          ;; Combine new results with cached results
          (combine updated-results)))))

;;;; Validate cached results

(defn valid-result?
  "If no changes to the file, checks, or config: reuse the cached results.
  Used to determine if we need to compute new results."
  [{:keys [cached-result lines config checks]}]
  (and (store/valid-config? cached-result config)
       (store/valid-checks? cached-result checks)
       (store/valid-lines? cached-result lines)))

(defn valid-checks?
  "If no changes to config or checks: the checks remain valid.
  We need only compute the changes to lines."
  [{:keys [cached-result config checks]}]
  (and (store/valid-config? cached-result config)
       (store/valid-checks? cached-result checks)))

(defn compute-and-store
  [inputs]
  (let [result (compute inputs)]
    (store/save! result)
    result))

(defn compute-or-cached
  "Returns computed or cached results of running checks on text."
  [options]
  (let [inputs (make-input options)
        {:keys [cached-result output]} inputs]
    (cond
      (:no-cache options) (compute-and-store inputs)
      ;; If nothing has changed, return cached results
      ;; As well as the output format, which may have changed.
      (valid-result? inputs) (assoc cached-result :output output)
      ;; If checks and config are the same, we only need to reprocess
      ;; any lines that have changed.
      (valid-checks? inputs) (let [result (compute-changed inputs)]
                               (store/save! result)
                               result)
      ;; Otherwise process texts and cache results.
      :else (compute-and-store inputs))))
