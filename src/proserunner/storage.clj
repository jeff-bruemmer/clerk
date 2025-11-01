(ns proserunner.storage
  "Functions used for storing and retrieving cached results."
  (:gen-class)
  (:require [proserunner
             [checks :as checks]
             [error :as error]
             [config :as conf]
             [edn-utils :as edn-utils]
             [file-utils :as file-utils]
             [result :as result]
             [text :as text]]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defrecord Result [lines
                   lines-hash
                   file-hash
                   config
                   config-hash
                   check-hash
                   output
                   results])

(defn mk-tmp-dir!
  "Makes dir in tmp directory to store cached results."
  [path]
  (let [p (io/file (file-utils/join-path (System/getProperty "java.io.tmpdir") path))]
    (if (.exists p)
      p
      (do (try (.mkdirs p)
               (catch Exception e (error/exit (str "Proserunner couldn't create cache directory:\n" (.getMessage e)))))
          p))))

(defn ^:private filepath
  "Builds filepath to cached results."
  [dir result]
  (io/file dir (str "file" (:file-hash result) ".edn")))

(defn save!
  "Creates a storage directory in the OS's temp directory (if it hasn't already,
  and writes the result to that storage directory."
  [result]
  (try
    (let [dir (mk-tmp-dir! "proserunner-storage")
          storage-file (filepath dir result)]
      (if (.exists (io/file dir))
        (file-utils/atomic-spit storage-file (pr-str result))
        (println "Warning: Unable to cache results - storage directory doesn't exist.")))
    (catch Exception e
      (println (str "Warning: Failed to save cache: " (.getMessage e))))))

(def ^:private edn-readers
  {:readers (merge default-data-readers
                   {'proserunner.storage.Result map->Result
                    'proserunner.text.Line text/map->Line
                    'proserunner.text.Issue text/map->Issue
                    'proserunner.checks.Check checks/map->Check
                    'proserunner.config.Config conf/map->Config
                    'proserunner.checks.Recommendation checks/map->Recommendation
                    'proserunner.checks.Expression checks/map->Expression})})

(defn inventory
  "Checks the storage directory for revelant cached results.
   Returns false if cache doesn't exist or is corrupted."
  [file]
  (let [fp (str (file-utils/join-path (System/getProperty "java.io.tmpdir")
                                       "proserunner-storage"
                                       "file")
                (hash file) ".edn")
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

(defn- stable-hash
  "Computes a stable hash that persists across JVM restarts."
  [data]
  (hash (pr-str data)))

(defn valid-checks?
  [cached-result chs]
  (=
   (:check-hash cached-result)
   (stable-hash chs)))

(defn valid-lines?
  [cached-result lines]
  (=
   (:lines-hash cached-result)
   (stable-hash lines)))

(defn valid-config?
  [cached-result config]
  (=
   (:config-hash cached-result)
   (stable-hash config)))

