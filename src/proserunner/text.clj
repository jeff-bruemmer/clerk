(ns proserunner.text
  "Functions for slurping and processing the text files."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [proserunner.error :as error]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defrecord Line [file text line-num code? dialogue? issue? issues])
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
  "If proserunner has no files to vet, it pours out its beer."
  [files]
  (if (empty? files) (error/exit file-type-msg)
      files))

;;;; Dialogue detection

(defn contains-dialogue?
  "Detects if text contains dialogue (quoted text).
   Handles both double quotes and single quotes, but distinguishes
   apostrophes/possessives from single-quote dialogue."
  [text]
  (let [;; Match double-quoted text: "..."
        double-quote-pattern #"\"[^\"]*\""
        ;; Match single-quoted text, but not apostrophes
        ;; Look for single quotes with space/punctuation before or after
        ;; to distinguish from apostrophes like "it's" or "dog's"
        single-quote-pattern #"(?:^|[\s,;:\.\!\?])'[^']*'(?:[\s,;:\.\!\?]|$)"]
    (or (re-find double-quote-pattern text)
        (re-find single-quote-pattern text))))

(defn strip-dialogue
  "Removes dialogue from text while preserving character positions.
   Replaces quoted text with spaces so column numbers remain accurate."
  [text]
  (-> text
      ;; Replace double-quoted dialogue with spaces
      (string/replace #"\"[^\"]*\""
                      (fn [match] (string/join (repeat (count match) \space))))
      ;; Replace single-quoted dialogue with spaces
      (string/replace #"(?:^|[\s,;:\.\!\?])'[^']*'(?:[\s,;:\.\!\?]|$)"
                      (fn [match] (string/join (repeat (count match) \space))))))

(defn mark-dialogue
  "Marks a Line record with :dialogue? field based on dialogue detection."
  [line]
  (assoc line :dialogue? (boolean (contains-dialogue? (:text line)))))

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
   :dialogue? false
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
  if true, the lines inside code blocks are kept.

  Optional check-dialogue parameter (default false): if true, dialogue lines
  are included in the output; if false, dialogue lines are filtered out."
  ([code-blocks filepath]
   (fetch! code-blocks filepath false))
  ([code-blocks filepath check-dialogue]
   (try
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
            (map mark-dialogue)
            (remove #(or
                      (= boundary (:text %))
                      (and (not code-blocks)
                           (:code? %))
                      (and (not check-dialogue)
                           (:dialogue? %))))))
     (catch java.io.FileNotFoundException _e
       (println (str "Error: File not found: " filepath))
       [])
     (catch java.io.IOException e
       (println (str "Error: Could not read file '" filepath "': " (.getMessage e)))
       [])
     (catch Exception e
       (println (str "Error: Unexpected error reading file '" filepath "': " (.getMessage e)))
       []))))

(defn get-lines
  "Read and decorate lines."
  [files code-blocks]
  (let [line-builder (partial fetch! code-blocks)]
    (mapcat line-builder files)))


