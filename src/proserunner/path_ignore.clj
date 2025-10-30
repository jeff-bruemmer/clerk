(ns proserunner.path-ignore
  "Functions for handling path-based ignores (like .gitignore)."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defn read-proserunnerignore
  "Reads .proserunnerignore file from the base directory and returns patterns."
  [base-dir]
  (let [ignore-file (io/file base-dir ".proserunnerignore")]
    (if (.exists ignore-file)
      (try
        (->> (slurp ignore-file)
             (string/split-lines)
             (map string/trim)
             (remove #(or (string/blank? %) (string/starts-with? % "#")))
             (vec))
        (catch Exception _ []))
      [])))

(defn glob-to-regex
  "Converts a glob pattern to a regex pattern.
   Supports:
   - `*` matches any characters except `/`
   - `**` matches any characters including `/` (recursive)
   - `?` matches a single character
   - `.` is escaped to match literal dots"
  [pattern]
  (-> pattern
      (string/replace "." "\\.")     ; Escape dots first
      (string/replace "**" "§§")     ; Temporarily replace ** to protect it
      (string/replace "*" "[^/]*")   ; * matches any char except /
      (string/replace "§§" ".*")     ; ** matches any char including /
      (string/replace "?" ".")       ; ? matches single char
      (re-pattern)))

(defn should-ignore?
  "Checks if a file path should be ignored based on patterns."
  [file-path base-dir patterns]
  (let [relative-path (if (string/starts-with? file-path base-dir)
                        (subs file-path (count base-dir))
                        file-path)
        relative-path (if (string/starts-with? relative-path "/")
                        (subs relative-path 1)
                        relative-path)]
    (some (fn [pattern]
            (let [regex (glob-to-regex pattern)]
              (re-find regex relative-path)))
          patterns)))
