(ns clerk.storage
  "Functions used for storing and retreiving cached results."
  (:gen-class)
  (:require [clerk
             [checks :as checks]
             [error :as error]
             [text :as text]]
            [clojure
             [edn :as edn]
             [string :as string]]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defrecord Result [lines
                   lines-hash
                   file-hash
                   config-hash
                   check-hash
                   output
                   results])

(defn mk-tmp-dir!
  "Makes dir in tmp directory to store cached results."
  [path]
  (let [p (io/file
           (str
            (System/getProperty "java.io.tmpdir")
            (java.io.File/separator)
            path))]
    (if (.exists p)
      p
      (do (try (.mkdirs p)
               (catch Exception e (error/exit (str "Clerk couldn't create cache directory:\n" (.getMessage e)))))
          p))))

(defn ^:private filepath
  "Builds filepath to cached results."
  [dir result]
  (io/file (str dir (java.io.File/separator) "file" (:file-hash result) ".edn")))

(defn save!
  "Creates a storage directory in the OS's temp directory (if it hasn't already,
  and writes the result to that storage directory."
  [result]
  (let [dir (mk-tmp-dir! "clerk-storage")
        storage-file (io/file (filepath dir result))]
    (if (.exists (io/file dir))
      (spit storage-file (pr-str result))
      (println "Unable to cache results."))))

(def ^:private edn-readers
  {:readers (merge default-data-readers
                   {'clerk.storage.Result map->Result
                    'clerk.text.Line text/map->Line
                    'clerk.text.Issue text/map->Issue
                    'clerk.checks.Check checks/map->Check
                    'clerk.checks.Recommendation checks/map->Recommendation
                    'clerk.checks.Expression checks/map->Expression})})

(defn inventory
  "Checks the storage directory for revelant cached results"
  [file]
  (let [fp (str (string/join (java.io.File/separator)
                             [(System/getProperty "java.io.tmpdir") "clerk-storage" "file"])
                (hash file) ".edn")
        use-cache? (.exists (io/file fp))]
    (if use-cache?
      (edn/read-string edn-readers (slurp fp))
      false)))

;;;; Predicate functions for validating cached results by comparing hashes.

(defn valid-checks?
  [cached-result chs]
  (=
   (:check-hash cached-result)
   (hash chs)))

(defn valid-lines?
  [cached-result lines]
  (=
   (:lines-hash cached-result)
   (hash lines)))

(defn valid-config?
  [cached-result config]
  (=
   (:config-hash cached-result)
   (hash config)))

