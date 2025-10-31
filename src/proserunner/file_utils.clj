(ns proserunner.file-utils
  "File utility functions for safe file operations."
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:gen-class))

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
