(ns proserunner.vet.input
  "Input construction and file processing."
  (:gen-class)
  (:require [proserunner
             [checks :as checks]
             [config :as conf]
             [path-ignore :as path-ignore]
             [project-config :as project-conf]
             [result :as result]
             [storage :as store]
             [system :as sys]
             [text :as text]]
            [proserunner.config.types :as types]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defrecord Input
  [files
   lines
   config
   checks
   cached-result
   output
   no-cache
   parallel-lines
   project-ignore
   project-ignore-issues])

;; Input data structure for vetting operations.
;; Fields:
;; - files: Path to file(s) being vetted
;; - lines: Vector of Line records to vet
;; - config: Config record with checks and ignore settings
;; - checks: Vector of Check records to apply
;; - cached-result: Previously cached Result for incremental computation
;; - output: Output format specification
;; - no-cache: Boolean flag to bypass cache
;; - parallel-lines: Boolean flag for parallel line processing
;; - project-ignore: Set of project-specific simple ignore patterns
;; - project-ignore-issues: Set of contextual ignore maps

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
  "Dispatches to parallel or sequential file processing based on configuration and file count.

  fetch-fn returns Result<lines>, this function collects all Results and combines them."
  [files fetch-fn parallel?]
  (let [num-files (count files)
        results (if (and parallel? (> num-files 1))
                  (doall (pmap fetch-fn files))
                  (map fetch-fn files))
        failures (filter result/failure? results)
        successes (filter result/success? results)]
    (if (seq failures)
      (first failures)
      (result/ok (vec (mapcat :value successes))))))

(defn get-lines-from-all-files
  "Get lines from all files, optionally processing files in parallel.
   When parallel? is true, files are processed concurrently using pmap.

   Returns Result<lines> - Success with all lines, or Failure on error."
  [code-blocks check-quoted-text file exclude-patterns parallel?]
  (let [ignore-info (build-ignore-patterns file exclude-patterns)
        files-result (filter-valid-files file ignore-info)]
    (if (result/failure? files-result)
      files-result
      (let [files (:value files-result)
            fetch-fn #(text/fetch! code-blocks % check-quoted-text)]
        (process-files files fetch-fn parallel?)))))

(defn- determine-parallel-settings
  "Determines parallel processing settings from options."
  [{:keys [parallel-files sequential-lines]}]
  {:parallel-files? (boolean parallel-files)
   :parallel-lines? (not sequential-lines)})

(defn- load-config-and-dir
  "Loads configuration and check directory, preferring project config if available."
  [config project-config]
  (if (= :project (:source project-config))
    {:config (types/map->Config {:checks (:checks project-config)
                                 :ignore (:ignore project-config)})
     :check-dir ""}
    {:config (conf/fetch-or-create! config)
     :check-dir (sys/check-dir config)}))

(defn make
  "Input combines user-defined options and arguments
  with the relevant cached results.

  Returns Result<Input> - Success with Input record, or Failure on error."
  [options]
  (let [{:keys [file config output code-blocks quoted-text exclude no-cache skip-ignore]} options
        ;; Handle exclude as vector, single string, or nil for backward compatibility
        exclude-patterns (cond
                           (vector? exclude) exclude
                           (string? exclude) [exclude]
                           :else [])
        {:keys [parallel-files? parallel-lines?]} (determine-parallel-settings options)

        current-dir (System/getProperty "user.dir")
        project-config (project-conf/load current-dir)
        {:keys [config check-dir]} (load-config-and-dir config project-config)

        project-ignore (if skip-ignore #{} (:ignore project-config))
        project-ignore-issues (if skip-ignore #{} (:ignore-issues project-config))

        updated-options (assoc options
                              :config config
                              :check-dir check-dir
                              :project-ignore project-ignore)
        lines-result (get-lines-from-all-files code-blocks quoted-text file exclude-patterns parallel-files?)]
    (result/bind
      lines-result
      (fn [lines]
        (let [checks-result (checks/create updated-options)]
          (result/bind
            checks-result
            (fn [loaded-checks]
              (result/ok
                (map->Input
                  {:file file
                   :lines lines
                   :config config
                   :check-dir check-dir
                   :checks loaded-checks
                   :cached-result (store/inventory file)
                   :output output
                   :no-cache no-cache
                   :parallel-lines parallel-lines?
                   :project-ignore project-ignore
                   :project-ignore-issues project-ignore-issues})))))))))
