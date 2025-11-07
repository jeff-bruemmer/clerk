(ns proserunner.ignore.core
  "Filtering logic and indexing for ignore matching."
  (:gen-class)
  (:require [clojure.string :as string]))

(set! *warn-on-reflection* true)

;;; Set Operations

(defn add-to-set
  "Adds specimen or contextual ignore to ignore set."
  [ignore-set specimen]
  (conj ignore-set specimen))

(defn remove-from-set
  "Removes specimen or contextual ignore from ignore set."
  [ignore-set specimen]
  (disj ignore-set specimen))

;;; Ignore Manipulation Functions

(defn add-specimen
  "Adds a specimen to an ignores map.
  Strings are added to :ignore set, maps are added to :ignore-issues set.

  Args:
    ignores - Map with :ignore (set) and :ignore-issues (set)
    specimen - String or map to add

  Returns: Updated ignores map

  Example:
    (add-specimen {:ignore #{\"foo\"} :ignore-issues #{}} \"bar\")
    => {:ignore #{\"foo\" \"bar\"} :ignore-issues #{}}"
  [ignores specimen]
  (if (string? specimen)
    (update ignores :ignore (fn [ign] (add-to-set (or ign #{}) specimen)))
    (update ignores :ignore-issues (fn [issues] (conj (or issues #{}) specimen)))))

(defn remove-specimen
  "Removes a specimen from an ignores map.
  Strings are removed from :ignore set, maps are removed from :ignore-issues set.

  Args:
    ignores - Map with :ignore (set) and :ignore-issues (set)
    specimen - String or map to remove

  Returns: Updated ignores map

  Example:
    (remove-specimen {:ignore #{\"foo\" \"bar\"} :ignore-issues #{}} \"bar\")
    => {:ignore #{\"foo\"} :ignore-issues #{}}"
  [ignores specimen]
  (if (string? specimen)
    (update ignores :ignore (fn [ign] (remove-from-set (or ign #{}) specimen)))
    (update ignores :ignore-issues (fn [issues] (disj (or issues #{}) specimen)))))

;;; Helper functions for contextual ignore matching

(defn contextual-ignore?
  "Returns true if the ignore entry is a contextual ignore (map) rather than a simple specimen (string).
   Contextual ignores must have :file and :specimen keys."
  [entry]
  (boolean
   (and (map? entry)
        (:file entry)
        (:specimen entry))))

(defn matches-simple-ignore?
  "Returns true if a simple specimen ignore matches the issue.
   Matching is case-insensitive."
  [specimen issue]
  (= (string/lower-case specimen)
     (string/lower-case (:specimen issue))))

(defn matches-contextual-ignore?
  "Returns true if a contextual ignore entry matches the issue.
   Matches based on provided fields:
   - :file and :specimen must always match
   - :line-num matches if specified
   - :check matches if specified
   All matching is case-insensitive for specimens."
  [ignore-entry issue]
  (let [ignore-line (or (:line-num ignore-entry) (:line ignore-entry))
        issue-line (or (:line-num issue) (:line issue))
        ignore-check (or (:check ignore-entry) (:name ignore-entry))
        issue-check (or (:name issue) (:check issue))]
    (and
     ;; File must match
     (= (:file ignore-entry) (:file issue))

     ;; Specimen must match (case-insensitive)
     (= (string/lower-case (:specimen ignore-entry))
        (string/lower-case (:specimen issue)))

     ;; If line-num is specified, it must match
     (or (nil? ignore-line)
         (= ignore-line issue-line))

     ;; If check is specified, it must match
     (or (nil? ignore-check)
         (= ignore-check issue-check)))))

(defn should-ignore-issue?
  "Returns true if the issue should be ignored based on the ignore list.
   Handles both simple specimen ignores and contextual ignores.

   issue: map with :file, :line-num, :specimen, :name
   ignores: collection of strings (simple) and/or maps (contextual)"
  [issue ignores]
  (boolean
   (some (fn [ignore-entry]
           (cond
             (string? ignore-entry)
             (matches-simple-ignore? ignore-entry issue)

             (contextual-ignore? ignore-entry)
             (matches-contextual-ignore? ignore-entry issue)

             :else
             false))
         ignores)))

;;; Indexed Ignore Matching

(defn- add-simple-ignore
  "Adds a simple string ignore to the index."
  [acc ignore-str]
  (update acc :simple conj (string/lower-case ignore-str)))

(defn- add-contextual-ignore
  "Adds a contextual ignore (map) to the index, grouping by file and line."
  [acc ignore-map]
  (let [file (:file ignore-map)
        line (or (:line-num ignore-map) (:line ignore-map))]
    (if line
      (update-in acc [:contextual file :by-line line] (fnil conj []) ignore-map)
      (update-in acc [:contextual file :file-wide] (fnil conj []) ignore-map))))

(defn build-ignore-index
  "Builds indexed structure for fast lookups from separated ignore structure.

  Takes: {:ignore #{\"word1\" \"word2\"...}
          :ignore-issues [{:file \"x.md\" :line 10 :specimen \"y\"}...]}

  Returns: {:simple #{\"lowercase-word1\" \"lowercase-word2\" ...}
            :contextual {\"file.md\" {:by-line {10 [{:specimen \"x\" :check nil}]
                                                20 [...]}
                                      :file-wide [{:specimen \"y\"}]}}}"
  [{:keys [ignore ignore-issues]}]
  (let [simple-index (reduce add-simple-ignore {:simple #{} :contextual {}} ignore)]
    (reduce add-contextual-ignore simple-index ignore-issues)))

(defn- check-line-specific-ignores
  "Checks if issue matches any line-specific ignores."
  [issue file-ignores issue-line]
  (when issue-line
    (some #(matches-contextual-ignore? % issue)
          (get-in file-ignores [:by-line issue-line]))))

(defn- check-file-wide-ignores
  "Checks if issue matches any file-wide ignores."
  [issue file-ignores]
  (some #(matches-contextual-ignore? % issue)
        (:file-wide file-ignores)))

(defn- should-ignore-issue-indexed?
  "Checks if issue matches any entry in pre-built index."
  [issue ignore-index]
  (boolean
   (or
    ;; Check simple ignores
    (contains? (:simple ignore-index) (string/lower-case (:specimen issue)))

    ;; Check contextual ignores
    (when-let [file-ignores (get-in ignore-index [:contextual (:file issue)])]
      (let [issue-line (or (:line issue) (:line-num issue))]
        (or (check-line-specific-ignores issue file-ignores issue-line)
            (check-file-wide-ignores issue file-ignores)))))))

(defn filter-issues
  "Filters out issues that should be ignored based on the ignore structure.
   Returns a vector of issues that should be checked.

   issues: collection of issue maps with :file, :line-num, :specimen, :name
   ignores: map with :ignore (set of strings) and :ignore-issues (vector of maps)"
  [issues ignores]
  (if (or (nil? ignores)
          (and (empty? (:ignore ignores)) (empty? (:ignore-issues ignores))))
    (vec issues)
    (let [index (build-ignore-index ignores)]
      (vec (remove #(should-ignore-issue-indexed? % index) issues)))))

;;; Issue-to-Ignore Conversion

(defn issue->entry
  "Converts an issue into a contextual ignore entry.

   Options:
   - :granularity - Level of specificity:
     - :file - Ignore specimen anywhere in file (default for file-wide issues)
     - :line - Ignore specimen on specific line (default)
     - :full - Include check name for maximum specificity

   The issue map should contain:
   - :file - File path
   - :line-num - Line number
   - :specimen - The problematic text
   - :name - Check name (used with :full granularity)"
  ([issue]
   (issue->entry issue {}))
  ([issue {:keys [granularity] :or {granularity :line}}]
   (let [file (:file issue)
         line-num (or (:line-num issue) (:line issue))
         specimen (:specimen issue)
         check (or (:name issue) (:check issue))]
     (case granularity
       :file
       {:file file :specimen specimen}

       :line
       {:file file :line-num line-num :specimen specimen}

       :full
       {:file file :line-num line-num :specimen specimen :check check}))))

(defn issues->entries
  "Converts a collection of issues into contextual ignore entries.

   Options:
   - :granularity - Passed to issue->entry (:file, :line, or :full)

   Returns a set of ignore entry maps (automatically deduplicated)."
  ([issues]
   (issues->entries issues {}))
  ([issues opts]
   (into #{} (map #(issue->entry % opts)) issues)))
