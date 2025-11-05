(ns proserunner.text
  "Functions for slurping and processing the text files."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [proserunner.result :as result]
            [proserunner.file-utils :as file-utils]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defrecord Line
  [file text line-num code? quoted? issue? issues])

(defrecord Issue
  [file name kind specimen col-num message])

;; Line represents a line of text to be vetted.
;; Fields:
;; - file: Path to source file (relative to working directory)
;; - text: Text content of the line
;; - line-num: Line number in the source file
;; - code?: Boolean indicating if line is within a code block
;; - quoted?: Boolean indicating if line contains quoted text
;; - issue?: Boolean indicating if any issues were found
;; - issues: Vector of Issue records found on this line

;; Issue represents a prose issue found during vetting.
;; Fields:
;; - file: Path to source file where issue was found
;; - name: Name of the check that identified this issue
;; - kind: Kind of check (e.g., existence, substitution, conditional)
;; - specimen: The problematic text that triggered the issue
;; - col-num: Column number where issue starts (0-indexed)
;; - message: Human-readable message describing the issue

(def supported-files (sorted-set "txt" "tex" "md" "markdown" "org"))
(def file-error-msg "file must exist.")
(def file-size-msg "individual files must be less than 10MB.")
(def file-type-msg "file must be a txt, md, tex, or org file.")

;;;; File validators
(defn file-exists?
  "Is the file real?"
  [filepath]
  (.exists (io/file filepath)))

(defn less-than-10-MB?
  "Validates file/directory size. Directories always pass, individual files limited to 10MB."
  [filepath]
  (let [f (io/file filepath)]
    (or (.isDirectory f)
        (< (.length f) 10000001))))

(defn supported-file-type?
  "File should be a text, markdown, tex, or org file."
  [filepath]
  (contains?
   supported-files
   (peek (string/split filepath #"\."))))

(defn handle-invalid-file
  "Returns Result with files if valid, Failure if no files to vet.

  Previously called error/exit, now returns Result for better composability."
  [files]
  (if (empty? files)
    (result/err file-type-msg {:reason :no-valid-files})
    (result/ok files)))

;;;; Quoted text detection

;; Regex patterns for detecting quoted text
(def ^:private double-quote-pattern
  "Matches double-quoted text with both straight and curly quotes."
  #"(?:\"[^\"]*\"|\u201C[^\u201D]*\u201D)")

(def ^:private single-quote-pattern
  "Matches single-quoted text with both straight and curly quotes.
   Requires whitespace or punctuation before/after to distinguish from
   contractions like \"it's\" or possessives like \"dog's\"."
  #"(?:(?:^|[\s,;:\.\!\?])'[^']*'(?:[\s,;:\.\!\?]|$)|(?:^|[\s,;:\.\!\?])\u2018[^\u2019]*\u2019(?:[\s,;:\.\!\?]|$))")

(defn contains-quoted-text?
  "Detects if text contains quoted text.
   Handles both double quotes and single quotes, but distinguishes
   apostrophes/possessives from single-quote quoted text."
  [text]
  (or (re-find double-quote-pattern text)
      (re-find single-quote-pattern text)))

(defn- replace-with-spaces
  "Replaces a matched string with equivalent number of spaces."
  [match]
  (string/join (repeat (count match) \space)))

(defn strip-quoted-text
  "Removes quoted text from text while preserving character positions.
   Replaces quoted text with spaces so column numbers remain accurate."
  [text]
  (-> text
      (string/replace double-quote-pattern replace-with-spaces)
      (string/replace single-quote-pattern replace-with-spaces)))

(defn mark-quoted-text
  "Marks a Line record with :quoted? field based on quoted text detection."
  [line]
  (assoc line :quoted? (boolean (contains-quoted-text? (:text line)))))

(defn process-quoted-text
  "Processes quoted text based on check-quoted-text flag.
   If check-quoted-text is false, strips quoted text from the line.
   If true, keeps the line unchanged."
  [check-quoted-text line]
  (if (or check-quoted-text (not (:quoted? line)))
    line
    (assoc line :text (strip-quoted-text (:text line)))))

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
   :quoted? false
   :issue? false
   :issues []})

(defn home-path
  "Just in case someone enters a full path, this function
  will mercifully shorten the path in the results."
  [filepath]
  (string/replace filepath (System/getProperty "user.home") "~"))

(defn fetch!
  "Takes a code-blocks boolean and a filepath string. It loads the file
  and returns a Result containing decorated lines. Code-blocks is false by default,
  but if true, the lines inside code blocks are kept.

  Optional check-quoted-text parameter (default false): if true, quoted text is
  included in checks; if false, quoted portions are stripped from lines before checking.

  Returns Result<vector<Line>> on success, Failure on file errors."
  ([code-blocks filepath]
   (fetch! code-blocks filepath false))
  ([code-blocks filepath check-quoted-text]
   (result/try-result-with-context
    (fn []
      (let [normalized-path (file-utils/normalize-path filepath)
            code (atom false) ;; Are we in a code block?
            boundary "```" ;; assumes code blocks are delimited by triple backticks.
            ;; If we see a boundry, we're either entering or exiting a code block.
            code-fn (fn [line] (if (string/starts-with? (:text line) boundary)
                                 (assoc line :code? (swap! code not))
                                 (assoc line :code? @code)))]
        (vec
         (->> filepath
              slurp
              string/split-lines
              (map-indexed number-lines)
              (remove #(string/blank? (:text %)))
              (map (comp
                    map->Line
                    #(assoc % :file normalized-path)
                    code-fn))
              (map mark-quoted-text)
              (map (partial process-quoted-text check-quoted-text))
              (remove #(or
                        (= boundary (:text %))
                        (and (not code-blocks)
                             (:code? %))))))))
    {:filepath filepath :operation :fetch})))


