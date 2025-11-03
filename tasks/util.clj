(ns tasks.util
  (:require [babashka.process :refer [shell]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

;; Shell utilities (isolated side effects)

(defn run-shell
  "Run shell command and return output string."
  [cmd]
  (str/trim (:out (shell {:out :string} cmd))))

(defn run-shell-check
  "Run shell command with continue flag, return result map."
  [cmd]
  (shell {:continue true} cmd))

;; Output utilities

(defn print-lines
  "Print multiple lines."
  [lines]
  (doseq [line lines]
    (println line)))

;; File utilities

(defn get-file-size
  "Get formatted file size."
  [file-path]
  (let [bytes (fs/size file-path)
        kb (/ bytes 1024.0)
        mb (/ kb 1024.0)]
    (cond
      (>= mb 1.0) (format "%.1fM" mb)
      (>= kb 1.0) (format "%.1fK" kb)
      :else (str bytes "B"))))
