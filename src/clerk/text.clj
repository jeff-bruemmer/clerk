(ns clerk.text
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defrecord Line [text line-num issue? issues])
(defrecord Issue [name kind specimen col-num message])

(def file-error-msg "file must exist.")
(def file-size-msg "file size must be less than 10MB.")
(def file-type-msg "file must be a txt, md, tex, or org file.")

;;;; File validators
(defn file-exists?
  "Is the file real?"
  [filepath]
  (.exists (io/file filepath)))

(defn less-than-10-MB?
  "Is the file less then 10 MB?"
  [filepath]
  (< (.length (io/file filepath)) 10000001))

(defn supported-file-type?
  "File should be a text, markdown, tex, or org file."
  [filepath]
  (contains?
   #{"txt" "tex" "md" "markdown" "org"}
   (peek (string/split filepath #"\."))))

;;;; Load text and create lines to vet.

(defn create-line
  "Decorates a line of text with a map of metadata and a
  vector to store issues."
  [idx text]
  {:line-num (inc idx)
   :text text
   :issue? false
   :issues []})

(defn fetch!
  "Loads a text and returns its lines."
  [filepath]
  (->> filepath
       (slurp)
       (string/split-lines)
       (map-indexed create-line)
       (map map->Line)
       (remove #(string/blank? (:text %)))))
