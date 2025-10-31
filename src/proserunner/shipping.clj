(ns proserunner.shipping
  "Shipping contains resources for printing results."
  (:gen-class)
  (:require [proserunner
             [version :as ver]
             [fmt :as fmt]
             [checks :as checks]
             [system :as sys]
             [config :as conf]]
            [clojure
             [pprint :as pp]
             [set :refer [rename-keys]]
             [string :as string]]
            [clojure.java.io :as io]
            [jsonista.core :as json]))

(set! *warn-on-reflection* true)

(defn time-elapsed
  [{:keys [timer start-time]}]
  (let [end-time (System/currentTimeMillis)]
    (when timer (println "Completed in" (- end-time start-time) "ms."))))

(defn prep
  "Prepares results for printing by merging line data with each issue."
  [{:keys [line-num issues]}]
  (reduce (fn [l issue]
            (let [{:keys [file name specimen col-num message kind]} issue]
              (conj l {:file file
                       :line-num line-num
                       :col-num col-num
                       :specimen (string/trim specimen)
                       :name (string/capitalize (string/replace name "-" " "))
                       :message (fmt/sentence-dress message)
                       :kind kind})))
          []
          issues))

;;;; Default print option: group

(defn issue-str
  "Creates a simplified result string for grouped results."
  [{:keys [line-num col-num specimen message]}]
  (string/join "\t" [(str line-num ":" col-num) (str "\"" specimen "\"" " -> " message)]))

(defn group-results
  "Groups results by file."
  [results]
  (doseq [[k v] (group-by :file results)]
    (println "\n" k)
    (doseq [issue (map issue-str v)]
      (println issue))))

;;;; Print version

(defn print-version
  "Prints version number."
  []
  (println "Proserunner version: " ver/number))

;;;; Printing results as a table

(defn make-key-printer
  "Used for printing in a table: makes for sentence-case column headings."
  [km]
  (fn [m] (rename-keys m km)))

(defn ^:private make-row-formatter
  [ks fmts]
  (fn
    [leader divider trailer row]
    (str leader
         (apply str (interpose divider
                               (for [[col fmt] (map vector (map #(get row %) ks) fmts)]
                                 (format fmt (str col)))))
         trailer)))

(defn ^:private col-widths
  "Determines column widths for spacing tables."
  [ks rows]
  (map
   (fn [k]
     (apply max (count (str k))
            (map #(count (str (get % k))) rows)))
   ks))

(defn print-table
  "Modified from pprint library to left-justify columns in table. Prints a
  collection of maps in a textual table. Prints table headings ks, and then a
  line of output for each row, corresponding to the keys in ks."
  ([ks rows]
   (when (seq rows)
     (let [widths (col-widths ks rows)
           spacers (map #(apply str (repeat % "-")) widths)
           fmts (map #(str "%-" % "s") widths) ;; modified to justify left
           fmt-row (make-row-formatter ks fmts)]
       (println)
       (println (fmt-row "| " " | " " |" (zipmap ks ks)))
       (println (fmt-row "|-" "-+-" "-|" (zipmap ks spacers)))
       (doseq [row rows]
         (println (fmt-row "| " " | " " |" row))))))
  ([rows] (print-table (keys (first rows)) rows)))

;;;; Printing command line options

(defn print-options
  "Modified from pprint library to left-justify columns in table
  and remove borders. Prints a collection of maps in a textual table."
  ([ks rows]
   (when (seq rows)
     (let [widths (col-widths ks rows)
           fmts (map #(str "%-" % "s") widths) ;; modified to justify left
           fmt-row (make-row-formatter ks fmts)]
       (println)
       (doseq [row rows]
         (println (fmt-row "  " "   " "  " row))))))
  ([rows] (print-table (keys (first rows)) rows)))

(defn print-opts
  "Utiltiy for printing usage."
  [summary title config]
  (println title)
  (print-options [:option :required :desc] summary)
  (println "\nConfig file: " config)
  (print-version))

(defn print-usage
  "Prints usage, optionally with a message."
  ([{:keys [summary config]}]
   (println "\nProserunner vets a text with the supplied checks.\n")
   (print-opts summary "USAGE:" config))

  ([opts message]
   (let [{:keys [summary config]} opts]
     (println (str "\n" message "\n"))
     (print-opts summary "USAGE:" config))))

(defn results-table
  "Takes results and prints them as a table."
  [results]
  (->> results
       (map (make-key-printer {:file "File"
                               :line-num "Line"
                               :col-num "Col"
                               :specimen "Specimen"
                               :name "Name"
                               :message "Message"}))
       (print-table)))

;;;; Verbose output

(def check-kind-guidance
  "Generic guidance for each check type to help users understand how to fix issues."
  {"existence" "Remove or rephrase."
   "case" "Remove or rephrase (case-sensitive)."
   "recommender" "Replace with the preferred alternative."
   "case-recommender" "Replace with the preferred alternative (case-sensitive)."
   "repetition" "Remove the duplicate word."
   "regex" "Follow the guidance in the message."})

(defn format-verbose-issue
  "Formats a single issue as markdown for verbose output."
  [issue-num {:keys [file line-num col-num specimen name message kind]}]
  (let [kind-lower (string/lower-case kind)
        guidance (get check-kind-guidance kind-lower "Follow the guidance in the message.")
        ;; Convert to absolute path
        abs-file (-> file
                     (string/replace "~" (System/getProperty "user.home"))
                     io/file
                     .getAbsolutePath)]
    (str "### " issue-num ". " name " (" kind-lower ")\n\n"
         "`" abs-file ":" line-num ":" col-num "`\n\n"
         "**Problem:** `" specimen "`"
         (when (and (or (= kind-lower "recommender")
                       (= kind-lower "case-recommender"))
                   (string/starts-with? message "Prefer:"))
           (str " → `" (string/trim (subs message 7)) "`"))
         " - " message "\n\n"
         "**How to fix:** " guidance "\n")))

(defn verbose-results
  "Formats results as verbose markdown output."
  [results]
  (let [file-count (count (distinct (map :file results)))
        issue-count (count results)
        by-kind (group-by :kind results)
        by-file (group-by :file results)]
    (println "# Proserunner Analysis Results\n")
    (println (str "**Files analyzed:** " file-count))
    (println (str "**Issues found:** " issue-count "\n"))
    (println "---\n")
    (println "## Issues\n")
    (doseq [[idx issue] (map-indexed vector results)]
      (println (format-verbose-issue (inc idx) issue))
      (println "---\n"))
    (println "## Summary\n")
    (println (str "- **Total issues:** " issue-count))
    (println "- **By type:**")
    (doseq [[kind issues] (sort-by first by-kind)]
      (println (str "  - " (string/lower-case kind) ": " (count issues))))
    (println "- **By file:**")
    (doseq [[file issues] (sort-by first by-file)]
      (println (str "  - `" file "`: " (count issues) " issue" (when (not= 1 (count issues)) "s"))))))

;;;; Print checks

(defn print-checks
  "Prints a table of the enabled checks: names, kind, and description."
  [config]
  (println "Enabled checks:")
  (let [config-data (conf/fetch-or-create! config)
        check-dir (sys/check-dir config)]
    (->> (checks/create {:config config-data :check-dir check-dir})
         (map (fn [{:keys [name kind explanation]}]
                {:name (string/capitalize name)
                 :kind (string/capitalize kind)
                 :explanation (fmt/sentence-dress explanation)}))
         (sort-by :name)
         (map (make-key-printer {:name "Name" :kind "Kind" :explanation "Explanation"}))
         (print-table))))

;;;; Utilities for generating checks README

(defn print-explanation
  "Prints line of table with check and explanation."
  [{:keys [name explanation]}]
  (let [heading (string/capitalize name)]
    (str "| **" heading "** | " (fmt/sentence-dress explanation) " |")))

;;;; Main egress

(defn ignore?
  "Is the specimen in the ignore file?"
  [ignore-set issue]
  (contains? ignore-set (:specimen issue)))

(defn- load-ignore-set
  "Loads ignore set from project config or file."
  [project-ignore check-dir config]
  (if (some? project-ignore)
    project-ignore
    (set (checks/load-ignore-set! check-dir (:ignore config)))))

(defn- filter-ignored
  "Removes ignored specimens from results."
  [results ignore-set]
  (if (empty? ignore-set)
    results
    (remove (partial ignore? ignore-set) results)))

(defn- process-results
  "Preps, filters, and sorts results."
  [results ignore-set]
  (->> results
       (mapcat prep)
       (#(filter-ignored % ignore-set))
       (sort-by (juxt :file :line-num :col-num))))

(defn- format-output
  "Formats results according to output format."
  [results output]
  (case (string/lower-case output)
    "edn" (pp/pprint results)
    "json" (json/write-value *out* results)
    "group" (group-results results)
    "verbose" (verbose-results results)
    (results-table results)))

(defn out
  "Takes results, preps them, removes specimens to ignore, and
  prints them in the supplied output format."
  [payload]
  (let [{:keys [results output check-dir config project-ignore]} payload]
    (cond
      (empty? results) nil
      (some? results)
      (let [ignore-set (load-ignore-set project-ignore check-dir config)
            final-results (process-results (:results results) ignore-set)]
        (format-output final-results output))
      :else nil)))

