(ns proserunner.storage
  "Functions used for storing and retrieving cached results."
  (:gen-class)
  (:require [proserunner
             [cache-config :as cache-config]
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

(defn resolve-cache-dir
  "Resolves cache directory from options and environment."
  [opts]
  (cache-config/resolve-cache-path
    (merge (cache-config/cache-config opts)
           {:env-vars (into {} (System/getenv))
            :system-props (into {} (System/getProperties))})))

(defn mk-cache-dir!
  "Creates cache directory if needed.

   Returns Result with cache directory path string."
  [opts]
  (result/try-result-with-context
    (fn []
      (let [cache-dir (resolve-cache-dir opts)
            p (io/file cache-dir)]
        (when-not (.exists p)
          (when-not (.mkdirs p)
            (throw (ex-info (str "Failed to create cache directory: " cache-dir)
                           {:path cache-dir
                            :exists (.exists p)
                            :writable (when (.exists (.getParentFile p))
                                       (.canWrite (.getParentFile p)))}))))
        cache-dir))
    {:operation :mk-cache-dir}))

(defn save!
  "Creates cache directory and writes result to cache file.

   Takes result (Result record) and opts map (may contain :cache-dir).
   Returns Result<Path> - Success with path to cached file, or Failure."
  [result opts]
  (result/bind
    (mk-cache-dir! opts)
    (fn [cache-dir]
      (result/try-result-with-context
        (fn []
          (let [cache-file (cache-config/make-cache-file-path
                             cache-dir
                             (:file-hash result))]
            (file-utils/atomic-spit cache-file (pr-str result))
            cache-file))
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

(defn get-cached-result
  "Retrieves cached result for file if exists and valid.

   Takes file path and opts map (may contain :cache-dir).
   Returns Result:
   - Success with cached record if valid cache exists
   - Failure with :cache-miss if no cache file
   - Failure with :corrupted-cache if cache file unreadable"
  [file opts]
  (let [cache-dir (resolve-cache-dir opts)
        cache-file-path (cache-config/make-cache-file-path
                          cache-dir
                          (stable-hash file))
        cache-file (io/file cache-file-path)]
    (if-not (.exists cache-file)
      (result/err "No cache" {:type :cache-miss})
      (let [read-result (edn-utils/read-edn-file-with-readers cache-file-path edn-readers)]
        (if (result/success? read-result)
          (result/ok (:value read-result))
          (do
            (println (str "Warning: Corrupted cache detected for file '" file "': " (:error read-result)))
            (println "Clearing corrupted cache and recomputing...")
            ;; Delete corrupted cache file
            (try
              (.delete cache-file)
              (catch Exception _del-e
                ;; Silently ignore deletion errors
                nil))
            (result/err "Corrupted cache"
                       {:type :corrupted-cache
                        :file cache-file-path})))))))

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
