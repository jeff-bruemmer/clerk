(ns clerk.text
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defrecord Line [file text line-num code? issue? issues])
(defrecord Issue [file name kind specimen col-num message])

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

(defn handle-invalid-file
  [files]
  (if (empty? files) (throw (Exception. "Not a valid file"))
  files))

;;;; Load text and create lines to vet.

(defn create-line
  [idx text]
  {:line-num (inc idx)
   :text text
   :issue? false
   :issues []})

(defn home-path
  [filepath]
  (string/replace filepath (System/getProperty "user.home") "~"))

(defn fetch!
  "Loads a text and returns its lines."
  [filepath]
  (let [code (atom false)
        boundary "```"
        code? (fn [line] (if (string/starts-with? (:text line) boundary)
                           (assoc line :code? (swap! code not))
                           (assoc line :code? @code)))
        homepath (home-path filepath)]
    (->> filepath
         slurp
         string/split-lines
         (map-indexed create-line)
         (map map->Line)
         (map (comp
               #(assoc % :file homepath)
               code?))
         (remove #(or (string/blank? (:text %))
                      (= boundary (:text %))
                      (:code? %))))))
