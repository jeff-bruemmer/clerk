(ns clerk.text
  "Functions for slurping and processing the text files."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clerk.error :as error]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defrecord Line [file text line-num code? issue? issues])
(defrecord Issue [file name kind specimen col-num message])

(def supported-files (sorted-set "txt" "tex" "md" "markdown" "org"))
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
   supported-files
   (peek (string/split filepath #"\."))))

(defn handle-invalid-file
  "If clerk has no files to vet, it pours out its beer."
  [files]
  (if (empty? files) (error/exit file-type-msg)
      files))

;;;; Load text and create lines to vet.

(defn get-file-paths
  "Takes the supplied path and returns a sequence
  of file names."
  [path]
  (->> path
       io/file
       file-seq
       (map str)
       (filter supported-file-type?)
       handle-invalid-file))

(defn number-lines
  "Arity conforms to map-indexed spec."
  [idx text]
  {:line-num (inc idx)
   :text text
   :issue? false
   :issues []})

(defn home-path
  "Just in case someone enters a full path, this function
  will mercifully shorten the path in the results."
  [filepath]
  (string/replace filepath (System/getProperty "user.home") "~"))

(defn fetch!
  "Takes a code-blocks boolean and a filepath string. It loads the file
  and returns decorated lines. Code-blocks is false by default, but
  if true, the lines inside code blocks are kept."
  [code-blocks filepath]
  (let [homepath (home-path filepath)
        code (atom false) ;; Are we in a code block?
        boundary "```" ;; assumes code blocks are delimited by triple backticks.
        ;; If we see a boundry, we're either entering or exiting a code block.
        code-fn (fn [line] (if (string/starts-with? (:text line) boundary)
                             (assoc line :code? (swap! code not))
                             (assoc line :code? @code)))]
    (->> filepath
         slurp
         string/split-lines
         (map-indexed number-lines)
         (remove #(string/blank? (:text %)))
         (map (comp
               map->Line
               #(assoc % :file homepath)
               code-fn))
         (remove #(or
                   (= boundary (:text %))
                   (and (not code-blocks)
                        (:code? %)))))))

(defn get-lines
  "Reads line, in parallet if the number of files is greater.
  May not want to use pmap here, but here we are."
  [files code-blocks]
  (let [line-builder (partial fetch! code-blocks)]
    (if (> 1 (count files))
      (concat (pmap line-builder files))
      (mapcat line-builder files))))

