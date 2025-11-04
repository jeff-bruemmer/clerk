(ns proserunner.file-utils
  "File utility functions for safe file operations."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [proserunner.result :as result])
  (:gen-class))

(defn absolute-path?
  "Check if a path is absolute."
  [path]
  (or (.isAbsolute (io/file path))
      (string/starts-with? path "~")))

(defn join-path
  "Joins path components using the system's file separator.

  Examples:
    (join-path \"home\" \"user\" \"file.txt\") => \"home/user/file.txt\" (on Unix)
    (join-path \"C:\" \"Users\" \"file.txt\") => \"C:\\Users\\file.txt\" (on Windows)"
  [& components]
  (string/join java.io.File/separator components))

(defn atomic-spit
  "Atomically writes content to a file by writing to a temp file and renaming.
   This prevents partial writes and race conditions during concurrent updates."
  [file-path content]
  (let [file (io/file file-path)
        parent-dir (.getParentFile file)
        temp-file (java.io.File/createTempFile ".proserunner-" ".tmp" parent-dir)]
    (try
      ;; Write to temp file
      (spit temp-file content)
      ;; Atomic rename (overwrites target if exists)
      (java.nio.file.Files/move
       (.toPath temp-file)
       (.toPath file)
       (into-array java.nio.file.StandardCopyOption
                   [java.nio.file.StandardCopyOption/ATOMIC_MOVE
                    java.nio.file.StandardCopyOption/REPLACE_EXISTING]))
      (catch Exception e
        ;; Clean up temp file on failure
        (.delete temp-file)
        (throw e)))))

(defn ensure-parent-dir
  "Ensures that the parent directory of the given filepath exists.
  Creates all necessary parent directories if they don't exist.

  Example:
    (ensure-parent-dir \"/path/to/nested/file.txt\")
    ;; Creates /path/to/nested/ if it doesn't exist"
  [filepath]
  (when-let [parent-file (.getParentFile (io/file filepath))]
    (.mkdirs parent-file)))

(defn write-edn-file
  "Writes EDN data to a file, creating parent directories if needed.
  Returns Result<filepath> on success, Failure on error.

  Uses atomic-spit for safe concurrent writes.

  Example:
    (write-edn-file \"config.edn\" {:foo \"bar\"})
    ;; => #proserunner.result.Success{:value \"config.edn\"}"
  [filepath data]
  (result/try-result-with-context
   (fn []
     (ensure-parent-dir filepath)
     (atomic-spit filepath (pr-str data))
     filepath)
   {:filepath filepath :operation :write-edn-file}))

(defn mkdirs-if-missing
  "Creates a directory and all necessary parent directories if they don't exist.
  Idempotent - safe to call multiple times.

  Example:
    (mkdirs-if-missing \"/path/to/nested/dir\")
    ;; Creates the entire directory structure"
  [dirpath]
  (.mkdirs (io/file dirpath)))
