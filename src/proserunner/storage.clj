(ns proserunner.storage
  "Functions used for storing and retrieving cached results."
  (:gen-class)
  (:require [proserunner
             [checks :as checks]
             [edn-utils :as edn-utils]
             [file-utils :as file-utils]
             [result :as result]
             [text :as text]]
            [proserunner.config.types :as types]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defrecord Result
  [lines
   lines-hash
   file-hash
   config
   config-hash
   check-hash
   output
   results])

;; Cached result structure for storing vetting outcomes.
;; Fields:
;; - lines: Vector of Line records that were vetted
;; - lines-hash: Hash of lines for cache validation
;; - file-hash: Hash of file path for cache identification
;; - config: Config record used for vetting
;; - config-hash: Hash of config for cache validation
;; - check-hash: Hash of checks for cache validation
;; - output: Output format specification
;; - results: Vector of Line records with issues found

(defn mk-tmp-dir!
  "Makes dir in tmp directory to store cached results.

  Returns Result<File> - Success with the directory File object, or Failure with error details."
  [path]
  (result/try-result-with-context
    (fn []
      (let [p (io/file (file-utils/join-path (System/getProperty "java.io.tmpdir") path))]
        (when-not (.exists p)
          (when-not (.mkdirs p)
            (throw (ex-info (str "Failed to create cache directory: " (.getPath p))
                           {:path (.getPath p)
                            :exists (.exists p)
                            :writable (when (.exists (.getParentFile p))
                                       (.canWrite (.getParentFile p)))}))))
        p))
    {:operation :mk-tmp-dir
     :path path}))

(defn ^:private filepath
  "Builds filepath to cached results."
  [dir result]
  (io/file dir (str "file" (:file-hash result) ".edn")))

(defn save!
  "Creates a storage directory in the OS's temp directory (if it hasn't already),
  and writes the result to that storage directory.

  Returns Result<Path> - Success with the path to the cached file, or Failure with error details."
  [result]
  (result/bind
    (mk-tmp-dir! "proserunner-storage")
    (fn [dir]
      (result/try-result-with-context
        (fn []
          (let [storage-file (filepath dir result)]
            (file-utils/atomic-spit storage-file (pr-str result))))
        {:operation :save-cache
         :file-hash (:file-hash result)}))))

(def ^:private edn-readers
  {:readers (merge default-data-readers
                   {'proserunner.storage.Result map->Result
                    'proserunner.text.Line text/map->Line
                    'proserunner.text.Issue text/map->Issue
                    'proserunner.checks.Check checks/map->Check
                    ;; Support both old and new Config locations for backward compatibility
                    'proserunner.config.Config types/map->Config
                    'proserunner.config.types.Config types/map->Config
                    'proserunner.checks.Recommendation checks/map->Recommendation
                    'proserunner.checks.Expression checks/map->Expression})})

(defn stable-hash
  "Computes a stable hash that persists across JVM restarts."
  [data]
  (hash (pr-str data)))

(defn inventory
  "Checks the storage directory for revelant cached results.
   Returns false if cache doesn't exist or is corrupted."
  [file]
  (let [fp (str (file-utils/join-path (System/getProperty "java.io.tmpdir")
                                       "proserunner-storage"
                                       "file")
                (stable-hash file) ".edn")
        cache-file (io/file fp)
        use-cache? (.exists cache-file)]
    (if use-cache?
      (let [read-result (edn-utils/read-edn-file-with-readers fp edn-readers)]
        (if (result/success? read-result)
          (:value read-result)
          (do
            (println (str "Warning: Corrupted cache detected for file '" file "': " (:error read-result)))
            (println "Clearing corrupted cache and recomputing...")
            ;; Delete corrupted cache file
            (try
              (.delete cache-file)
              (catch Exception _del-e
                ;; Silently ignore deletion errors
                nil))
            false)))
      false)))

;;;; Predicate functions for validating cached results by comparing hashes.

(defn valid-checks?
  "Validates that cached checks match current checks by comparing hashes.
  Returns true if the check hash in cached-result matches the hash of checks."
  [cached-result checks]
  (=
   (:check-hash cached-result)
   (stable-hash checks)))

(defn valid-lines?
  "Validates that cached lines match current lines by comparing hashes.
  Returns true if the lines hash in cached-result matches the hash of lines."
  [cached-result lines]
  (=
   (:lines-hash cached-result)
   (stable-hash lines)))

(defn valid-config?
  "Validates that cached config matches current config by comparing hashes.
  Returns true if the config hash in cached-result matches the hash of config."
  [cached-result config]
  (=
   (:config-hash cached-result)
   (stable-hash config)))

