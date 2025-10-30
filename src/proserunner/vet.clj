(ns proserunner.vet
  "Computes results of running all the checks on each file,
   using cached results where possible."
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [proserunner
    [checks :as checks]
    [config :as conf]
    [metrics :as metrics]
    [path-ignore :as path-ignore]
    [storage :as store]
    [system :as sys]
    [text :as text]]
   [editors
    [re :as re]
    [case :as c]
    [case-recommender :as cr]
    [existence :as existence]
    [recommender :as recommender]
    [repetition :as repetition]
    [registry :as registry]]))

(set! *warn-on-reflection* true)

;;;; Cache performance tracking

(def ^:private cache-stats (atom {:hits 0 :misses 0 :partial-hits 0}))

(defn reset-cache-stats!
  "Reset cache statistics."
  []
  (reset! cache-stats {:hits 0 :misses 0 :partial-hits 0}))

(defn get-cache-stats
  "Get current cache statistics."
  []
  @cache-stats)

(defn- record-cache-hit!
  "Record a full cache hit."
  []
  (swap! cache-stats update :hits inc))

(defn- record-cache-miss!
  "Record a cache miss (full recompute)."
  []
  (swap! cache-stats update :misses inc))

(defn- record-partial-cache-hit!
  "Record a partial cache hit (only some lines recomputed)."
  []
  (swap! cache-stats update :partial-hits inc))

;;;; Register all standard editors

(registry/register-editor! "existence" existence/proofread)
(registry/register-editor! "recommender" recommender/proofread)
(registry/register-editor! "repetition" repetition/proofread)
(registry/register-editor! "case" c/proofread)
(registry/register-editor! "case-recommender" cr/proofread)
(registry/register-editor! "regex" re/proofread)

;;;; Combine user input and the relevant cached results

(defrecord Input
           [files
            lines
            config
            checks
            cached-result
            output
            no-cache
            parallel-lines])

(defn build-ignore-patterns
  "Merges ignore patterns from CLI flags and .proserunnerignore file for consistent filtering."
  [file exclude-patterns]
  (let [base-dir (-> file io/file .getAbsolutePath)
        ignore-patterns (path-ignore/read-proserunnerignore base-dir)]
    {:base-dir base-dir
     :patterns (concat exclude-patterns ignore-patterns)}))

(defn filter-valid-files
  "Discovers files to check, respecting ignore patterns and supported file types."
  [file {:keys [base-dir patterns]}]
  (->> file
       io/file
       file-seq
       (map str)
       (filter text/supported-file-type?)
       (remove #(path-ignore/should-ignore? % base-dir patterns))
       text/handle-invalid-file))

(defn process-files
  "Dispatches to parallel or sequential file processing based on configuration and file count."
  [files fetch-fn parallel?]
  (let [num-files (count files)]
    (if (and parallel? (> num-files 1))
      (->> files
           (pmap (comp doall fetch-fn))
           doall
           (apply concat))
      (mapcat fetch-fn files))))

(defn get-lines-from-all-files
  "Get lines from all files, optionally processing files in parallel.
   When parallel? is true, files are processed concurrently using pmap."
  [code-blocks check-dialogue file exclude-patterns parallel?]
  (let [ignore-info (build-ignore-patterns file exclude-patterns)
        files (filter-valid-files file ignore-info)
        fetch-fn #(text/fetch! code-blocks % check-dialogue)]
    (dotimes [_ (count files)]
      (metrics/record-file!))
    (process-files files fetch-fn parallel?)))

(defn make-input
  "Input combines user-defined options and arguments
  with the relevant cached results."
  [options]
  (let [{:keys [file config output code-blocks check-dialogue exclude parallel-files sequential-lines no-cache]} options
        exclude-patterns (if exclude (vector exclude) [])
        ;; Default to sequential file processing (false if not specified)
        parallel-files? (if (nil? parallel-files) false parallel-files)
        ;; Default to parallel line processing (true unless sequential-lines is set)
        parallel-lines? (if sequential-lines false true)
        ;; Save original config filepath before loading
        config-filepath config
        c (conf/fetch-or-create! config-filepath)
        cd (sys/check-dir config-filepath)
        updated-options (assoc options :config c :check-dir cd)]
    (map->Input
     {:file file
      :lines (get-lines-from-all-files code-blocks check-dialogue file exclude-patterns parallel-files?)
      :config c
      :check-dir cd
      :checks (checks/create updated-options)
      :cached-result (store/inventory file)
      :output output
      :no-cache no-cache
      :parallel-lines parallel-lines?})))

;;;; Core functions for vetting a lines of text with each check.

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
      ;; Return line unchanged if check fails
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

(defn compute
  "Takes an input, and returns the results of
  running the configured checks on each line of text in the file."
  [{:keys [file lines config checks output parallel-lines]}]
  (metrics/record-lines! (count lines))
  (store/map->Result {:lines lines
                      :lines-hash (hash lines)
                      :file-hash (hash file)
                      :config config
                      :config-hash (hash config)
                      :check-hash (hash checks)
                      :output output
                      :results (process checks lines parallel-lines)}))

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
  (let [{:keys [file lines config checks cached-result output parallel-lines]} input
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
     (hash config)
     (hash checks)
     output
     (let [changed-lines (changed cached-line-map lines)]
       (->> (process checks changed-lines parallel-lines)
            ;; Combine new results with cached results
            (combine updated-results))))))

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
        {:keys [cached-result output]} inputs
        results
        (cond
          (:no-cache inputs)
          (do
            (record-cache-miss!)
            (compute-and-store inputs))

          ;; If nothing has changed, return cached results
          ;; As well as the output format, which may have changed.
          (valid-result? inputs)
          (do
            (record-cache-hit!)
            (assoc cached-result :output output))

          ;; If checks and config are the same, we only need to reprocess
          ;; any lines that have changed.
          (valid-checks? inputs)
          (let [result (compute-changed inputs)]
            (record-partial-cache-hit!)
            (store/save! result)
            result)

          ;; Otherwise process texts and cache results.
          :else
          (do
            (record-cache-miss!)
            (compute-and-store inputs)))]
    (assoc inputs :results results)))
