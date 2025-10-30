(ns proserunner.ignore
  "Functions for managing ignored specimens."
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [proserunner.system :as sys]))

(set! *warn-on-reflection* true)

(defn ignore-file-path
  "Returns the path to the ignore file."
  []
  (sys/filepath ".proserunner" "ignore.edn"))

(defn read-ignore-file
  "Reads the ignore file and returns a set of ignored specimens."
  []
  (let [ignore-path (ignore-file-path)]
    (if (.exists (io/file ignore-path))
      (try
        (set (edn/read-string (slurp ignore-path)))
        (catch Exception _ #{}))
      #{})))

(defn write-ignore-file!
  "Writes the set of ignored specimens to the ignore file."
  [ignored-set]
  (let [ignore-path (ignore-file-path)]
    (.mkdirs (.getParentFile (io/file ignore-path)))
    (spit ignore-path (pr-str (vec (sort ignored-set))))))

(defn add-to-ignore!
  "Adds a specimen to the ignore list."
  [specimen]
  (let [current (read-ignore-file)
        updated (conj current specimen)]
    (write-ignore-file! updated)))

(defn remove-from-ignore!
  "Removes a specimen from the ignore list."
  [specimen]
  (let [current (read-ignore-file)
        updated (disj current specimen)]
    (write-ignore-file! updated)))

(defn list-ignored
  "Returns a sorted list of all ignored specimens."
  []
  (sort (read-ignore-file)))

(defn clear-ignore!
  "Clears all ignored specimens."
  []
  (write-ignore-file! #{}))
