(ns proserunner.shipping
  "Shipping contains resources for printing results."
  (:gen-class)
  (:require [proserunner
             [version :as ver]
             [fmt :as fmt]
             [checks :as checks]
             [ignore :as ignore]
             [system :as sys]
             [config :as conf]]
            [clojure
             [pprint :as pp]
             [set :refer [rename-keys]]
             [string :as string]]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

(set! *warn-on-reflection* true)

(defn time-elapsed
  [{:keys [timer start-time]}]
  (let [end-time (System/currentTimeMillis)]
    (when timer (println "Completed in" (- end-time start-time) "ms."))))

(defn prep
  "Prepares results for printing by merging line data with each issue."
  [{:keys [line-num issues text]}]
  (reduce (fn [l issue]
            (let [{:keys [file name specimen col-num message kind]} issue]
              (conj l {:file file
                       :line-num line-num
                       :col-num col-num
                       :specimen (string/trim specimen)
                       :line-text (string/trim text)
                       :name (string/capitalize (string/replace name "-" " "))
                       :message (fmt/sentence-dress message)
                       :kind kind})))
          []
          issues))

;;;; Default print option: group

(defn issue-str
  "Creates a simplified result string for grouped results.
  Optional issue-num parameter adds a numbered prefix."
  ([issue] (issue-str nil issue))
  ([issue-num {:keys [line-num col-num specimen message]}]
   (string/join "\t"
     (cond-> []
       issue-num (conj (str "[" issue-num "]"))
       true (conj (str line-num ":" col-num))
       true (conj (str "\"" specimen "\"" " -> " message))))))

(defn group-results
  "Groups results by file."
  [results]
  (doseq [[k v] (group-by :file results)]
    (println "\n" k)
    (doseq [issue (map issue-str v)]
      (println issue))))

(defn group-results-numbered
  "Groups results by file with issue numbers, preserving order."
  [results]
  (let [indexed-results (map-indexed (fn [idx issue] [(inc idx) issue]) results)
        ;; Group by file but preserve order using partition-by
        grouped (partition-by (fn [[_ issue]] (:file issue)) indexed-results)]
    (doseq [file-group grouped]
      (when-let [first-item (first file-group)]
        (println "\n" (:file (second first-item)))
        (doseq [[num issue] file-group]
          (println (issue-str num issue)))))))

;;;; Formatters for command output

(defn format-ignored-list
  "Formats separated ignore lists for display.
   Takes map with :ignore (set of strings) and :ignore-issues (vector of maps)."
  [{:keys [ignore ignore-issues]}]
  (let [has-simple? (seq ignore)
        has-contextual? (seq ignore-issues)]
    (cond
      (and (not has-simple?) (not has-contextual?))
      ["No ignored specimens or issues."]

      :else
      (concat
        (when has-simple?
          (cons "Simple ignores (apply everywhere):"
                (concat
                  (map #(str "  - " %) (sort ignore))
                  [""])))
        (when has-contextual?
          (cons "Contextual ignores (specific locations):"
                (map (fn [{:keys [file line-num line specimen]}]
                       (format "  - %s:%s \"%s\""
                               file
                               (or line-num line "*")
                               specimen))
                     (sort-by (juxt :file #(or (:line-num %) (:line %) 0) :specimen)
                              ignore-issues))))))))

(defn format-init-project
  "Formats project initialization success message."
  [_]
  ["Created project configuration directory: .proserunner/"
   "  + .proserunner/config.edn - Project configuration"
   "  + .proserunner/checks/    - Directory for project-specific checks"
   ""
   "Edit .proserunner/config.edn to customize:"
   "  :checks        - Specify checks (vector of string references or maps):"
   "                   - \"default\" or \"custom\" to reference global checks"
   "                   - {:directory \"checks\"} for .proserunner/checks/"
   "                   - {:directory \"path\" :files [...]} for custom"
   "  :ignore        - Set of specimens to ignore"
   "  :ignore-mode   - :extend (merge with global) or :replace"
   "  :config-mode   - :merged (use global+project) or :project-only"
   ""
   "Add custom checks by creating .edn files in .proserunner/checks/"])

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

(defn- categorize-options
  "Organizes command-line options into logical categories for better help output."
  [summary]
  (let [category-order ["Basic Usage"
                        "Output Options"
                        "Ignore Management"
                        "Ignore Maintenance"
                        "Configuration & Checks"
                        "Scope Control"
                        "Advanced Options"]
        categories {"Basic Usage" #{"--file" "--help" "--version"}
                    "Output Options" #{"--output" "--code-blocks" "--quoted-text"}
                    "Ignore Management" #{"--add-ignore" "--remove-ignore" "--list-ignored"
                                         "--clear-ignored" "--ignore-all" "--ignore-issues"}
                    "Ignore Maintenance" #{"--audit-ignores" "--clean-ignores"}
                    "Configuration & Checks" #{"--init-project" "--restore-defaults"
                                               "--add-checks" "--checks" "--exclude"}
                    "Scope Control" #{"--global" "--project"}
                    "Advanced Options" #{"--config" "--ignore" "--no-cache" "--skip-ignore"
                                        "--parallel-files" "--sequential-lines" "--timer" "--name"}}
        ;; Group options by category
        categorized (reduce (fn [acc opt]
                             (let [long-opt (second (string/split (:option opt) #", "))
                                   category (some (fn [[cat opts]] (when (opts long-opt) cat)) categories)
                                   category (or category "Advanced Options")]
                               (update acc category (fnil conj []) opt)))
                           {}
                           summary)]
    ;; Return categories in the specified order
    (map (fn [cat] [cat (get categorized cat)])
         (filter #(get categorized %) category-order))))

(defn- print-option-with-desc
  "Prints a single option with description on separate line if long."
  [{:keys [option required desc]}]
  (let [opt-line (str "  " option (when required (str " " required)))]
    (println opt-line)
    (println (str "      " desc))))

(defn- print-categorized-options
  "Prints options organized by category with descriptions on separate lines."
  [summary]
  (let [categories (categorize-options summary)]
    (doseq [[category options] categories]
      (println (str "\n" category ":"))
      (doseq [opt options]
        (print-option-with-desc opt)))))

(defn- print-examples
  "Prints usage examples."
  []
  (println "\n\nEXAMPLES:")
  (println "  Check a single file:")
  (println "    proserunner --file document.md")
  (println)
  (println "  Check all markdown files in current directory:")
  (println "    proserunner --file .")
  (println)
  (println "  Ignore specific issues by number:")
  (println "    proserunner --file README.md --ignore-issues 1,3,5-7")
  (println)
  (println "  Check files excluding certain patterns:")
  (println "    proserunner --file . --exclude \"*.log,temp/**\"")
  (println)
  (println "  Find stale ignores in configuration:")
  (println "    proserunner --audit-ignores")
  (println)
  (println "  List all enabled checks:")
  (println "    proserunner --checks"))

(defn print-opts
  "Utility for printing usage."
  [summary title config]
  (println title)
  (print-options [:option :required :desc] summary)
  (println "\nConfig file: " config)
  (print-version))

(defn print-usage
  "Prints usage, optionally with a message."
  ([{:keys [summary config]}]
   (println "\nP R O S E R U N N E R\n")
   (println "A prose linter with default checks for style, grammar, and clarity.\n")
   (println "Proserunner analyzes text files (markdown, prose, documentation) using a curated")
   (println "set of default checks. You can add custom checks to enforce your own style guide,")
   (println "ignore specific findings, and configure both global and project-specific settings.\n")
   (println "USAGE:")
   (println "  proserunner [OPTIONS]\n")
   (print-categorized-options summary)
   (print-examples)
   (println "\n\nCONFIGURATION:")
   (println "  Global config: ~/.proserunner/config.edn")
   (println "  Project config: .proserunner/config.edn")
   (println "  Current config file: " config)
   (println)
   (print-version))

  ([opts message]
   ;; When called with a message (e.g., from default handler), show full help
   (print-usage opts)))

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

(defn results-table-numbered
  "Takes results and prints them as a table with issue numbers."
  [results]
  (->> results
       (map-indexed (fn [idx issue]
                      (assoc issue :issue-num (inc idx))))
       (map (make-key-printer {:issue-num "#"
                               :file "File"
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
  [issue-num {:keys [file line-num col-num specimen line-text name message kind]}]
  (let [kind-lower (string/lower-case kind)
        guidance (get check-kind-guidance kind-lower "Follow the guidance in the message.")
        ;; Convert to absolute path
        abs-file (-> file
                     (string/replace "~" (System/getProperty "user.home"))
                     io/file
                     .getAbsolutePath)]
    (str "### " issue-num ". " name " (" kind-lower ")\n\n"
         "`" abs-file ":" line-num ":" col-num "`\n\n"
         "**Line:** " line-text "\n\n"
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

;;;; Main egress

(defn- load-ignore-set
  "Loads ignore map from project config or file.
   Returns map with :ignore (set of strings) and :ignore-issues (vector of maps)."
  [project-ignore project-ignore-issues check-dir config]
  (if (some? project-ignore)
    {:ignore project-ignore
     :ignore-issues (or project-ignore-issues [])}
    (checks/load-ignore-set! check-dir (:ignore config))))

(defn- filter-ignored
  "Removes ignored issues from results using contextual ignore matching.
   Handles both simple specimen ignores and contextual ignores (file+line+specimen+check)."
  [results ignore-map]
  (if (and (empty? (:ignore ignore-map)) (empty? (:ignore-issues ignore-map)))
    results
    (ignore/filter-issues results ignore-map)))

(defn- process-results
  "Preps, filters, and sorts results."
  [results ignore-map]
  (->> results
       (mapcat prep)
       (#(filter-ignored % ignore-map))
       (sort-by (juxt :file :line-num :col-num))))

(defn- format-output
  "Formats results according to output format with issue numbers."
  [results output]
  (case (string/lower-case output)
    "edn" (pp/pprint results)
    "json" (json/generate-stream results *out*)
    "group" (group-results-numbered results)
    "verbose" (verbose-results results)
    (results-table-numbered results)))

(defn out
  "Takes results, preps them, removes specimens to ignore, and
  prints them in the supplied output format."
  [payload]
  (let [{:keys [results output check-dir config project-ignore project-ignore-issues]} payload]
    (cond
      (empty? results) nil
      (some? results)
      (let [ignore-map (load-ignore-set project-ignore project-ignore-issues check-dir config)
            final-results (process-results (:results results) ignore-map)]
        (format-output final-results output))
      :else nil)))

