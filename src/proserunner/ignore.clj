(ns proserunner.ignore
  "Functions for managing ignored specimens."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [proserunner.context :as context]
            [proserunner.edn-utils :as edn-utils]
            [proserunner.file-utils :as file-utils]
            [proserunner.project-config :as project-config]
            [proserunner.result :as result]
            [proserunner.system :as sys]))

(set! *warn-on-reflection* true)

;;; Global Ignore File Management

(defn ignore-file-path
  "Returns the path to the ignore file."
  []
  (sys/filepath ".proserunner" "ignore.edn"))

(defn read-ignore-file
  "Reads the ignore file and returns a map with :ignore (set) and :ignore-issues (vector)."
  []
  (let [ignore-path (ignore-file-path)]
    (if (.exists (io/file ignore-path))
      (let [result (edn-utils/read-edn-file ignore-path)]
        (if (result/success? result)
          (let [data (:value result)]
            {:ignore (or (:ignore data) #{})
             :ignore-issues (or (:ignore-issues data) [])})
          {:ignore #{} :ignore-issues []}))
      {:ignore #{} :ignore-issues []})))

(defn write-ignore-file!
  "Writes ignore map to the ignore file atomically.
   Takes map with :ignore (set of strings) and :ignore-issues (vector of maps)."
  [{:keys [ignore ignore-issues]}]
  (let [ignore-path (ignore-file-path)
        sorted-simple (set (sort ignore))
        sorted-contextual (vec (sort-by (fn [entry]
                                          [(:file entry)
                                           (or (:line-num entry) (:line entry) 0)
                                           (:specimen entry)])
                                        ignore-issues))
        output-map {:ignore sorted-simple
                    :ignore-issues sorted-contextual}]
    (file-utils/ensure-parent-dir ignore-path)
    (file-utils/atomic-spit ignore-path (with-out-str (pprint/pprint output-map)))))

;;; Set Operations

(defn add-to-set
  "Adds specimen or contextual ignore to ignore set."
  [ignore-set specimen]
  (conj ignore-set specimen))

(defn remove-from-set
  "Removes specimen or contextual ignore from ignore set."
  [ignore-set specimen]
  (disj ignore-set specimen))

;;; Pure Functions for Ignore Manipulation

(defn add-specimen
  "Pure function that adds a specimen to an ignores map.
  Strings are added to :ignore set, maps are added to :ignore-issues vector.

  Args:
    ignores - Map with :ignore (set) and :ignore-issues (vector)
    specimen - String or map to add

  Returns: Updated ignores map

  Example:
    (add-specimen {:ignore #{\"foo\"} :ignore-issues []} \"bar\")
    => {:ignore #{\"foo\" \"bar\"} :ignore-issues []}"
  [ignores specimen]
  (if (string? specimen)
    (update ignores :ignore (fn [ign] (add-to-set (or ign #{}) specimen)))
    (update ignores :ignore-issues (fn [issues] (conj (or issues []) specimen)))))

(defn remove-specimen
  "Pure function that removes a specimen from an ignores map.
  Strings are removed from :ignore set, maps are removed from :ignore-issues vector.

  Args:
    ignores - Map with :ignore (set) and :ignore-issues (vector)
    specimen - String or map to remove

  Returns: Updated ignores map

  Example:
    (remove-specimen {:ignore #{\"foo\" \"bar\"} :ignore-issues []} \"bar\")
    => {:ignore #{\"foo\"} :ignore-issues []}"
  [ignores specimen]
  (if (string? specimen)
    (update ignores :ignore (fn [ign] (remove-from-set (or ign #{}) specimen)))
    (update ignores :ignore-issues (fn [issues] (vec (remove #(= % specimen) (or issues [])))))))

(defn- add-ignore-to-project!
  "Adds a specimen to project .proserunner/config.edn.
   Strings go to :ignore, maps go to :ignore-issues."
  [specimen project-root]
  (let [config (project-config/read project-root)]
    (if (string? specimen)
      (let [current-ignore (or (:ignore config) #{})
            updated-ignore (add-to-set current-ignore specimen)]
        (project-config/write! project-root (assoc config :ignore updated-ignore)))
      (let [current-issues (or (:ignore-issues config) [])
            updated-issues (conj current-issues specimen)]
        (project-config/write! project-root (assoc config :ignore-issues updated-issues))))))

(defn- remove-ignore-from-project!
  "Removes a specimen from project .proserunner/config.edn.
   Removes strings from :ignore, maps from :ignore-issues."
  [specimen project-root]
  (let [config (project-config/read project-root)]
    (if (string? specimen)
      (let [current-ignore (or (:ignore config) #{})
            updated-ignore (remove-from-set current-ignore specimen)]
        (project-config/write! project-root (assoc config :ignore updated-ignore)))
      (let [current-issues (or (:ignore-issues config) [])
            updated-issues (vec (remove #(= % specimen) current-issues))]
        (project-config/write! project-root (assoc config :ignore-issues updated-issues))))))

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

   Note: This is the non-indexed implementation kept for testing compatibility.
   Production code uses should-ignore-issue-indexed? for better performance.

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

;;; Indexed Ignore Implementation for Performance

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

(defn issue->ignore-entry
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
   (issue->ignore-entry issue {}))
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

(defn issues->ignore-entries
  "Converts a collection of issues into contextual ignore entries.
   
   Options:
   - :granularity - Passed to issue->ignore-entry (:file, :line, or :full)
   - :dedupe? - Remove duplicate entries (default true)
   
   Returns a vector of ignore entry maps."
  ([issues]
   (issues->ignore-entries issues {}))
  ([issues {:keys [dedupe?] :or {dedupe? true} :as opts}]
   (let [entries (mapv #(issue->ignore-entry % opts) issues)]
     (if dedupe?
       (vec (distinct entries))
       entries))))

;;; Audit and Cleanup

(defn file-exists?
  "Checks if a file exists."
  [file-path]
  (when file-path
    (.exists (io/file file-path))))

(defn audit-ignores
  "Audits ignore entries to find stale ones (files that don't exist).
   Returns a map with :stale and :active entries.

   Takes map with :ignore (strings, always active) and :ignore-issues (maps, checked for file existence).
   Returns map with :ignore (set) and :ignore-issues (vectors) for both :stale and :active."
  [{:keys [ignore ignore-issues]}]
  (let [stale-issues (filterv (fn [entry]
                                (not (file-exists? (:file entry))))
                              ignore-issues)
        active-issues (filterv (fn [entry]
                                 (file-exists? (:file entry)))
                               ignore-issues)]
    {:stale {:ignore #{}
             :ignore-issues stale-issues}
     :active {:ignore ignore
              :ignore-issues active-issues}}))

(defn format-audit-report
  "Formats an audit result into a human-readable report.
   Returns a vector of strings for display."
  [{:keys [stale active]}]
  (let [total-ignores (+ (count (:ignore active)) (count (:ignore stale)))
        total-issues (+ (count (:ignore-issues active)) (count (:ignore-issues stale)))
        total (+ total-ignores total-issues)
        stale-count (count (:ignore-issues stale))]
    (if (zero? stale-count)
      [(format "✓ All %d ignore entries are active (%d simple, %d contextual)."
               total total-ignores total-issues)
       "No stale ignores found."]
      (concat
       [(format "Found %d stale contextual ignore(s) out of %d total entries:" stale-count total)
        ""]
       (map (fn [entry]
              (format "  - File: %s:%s - \"%s\""
                     (:file entry)
                     (or (:line-num entry) (:line entry) "*")
                     (:specimen entry)))
            (:ignore-issues stale))
       [""]
       [(format "Stale ignores reference files that no longer exist.")]
       [(format "Use --clean-ignores to remove them.")]))))

(defn remove-stale-ignores
  "Removes stale ignores from an ignore map.
   Returns map with only active ignores."
  [ignores]
  (:active (audit-ignores ignores)))

;;; Context-Aware Public API

(defn add-to-ignore!
  "Adds a specimen to the ignore list with context-aware targeting.
   Strings go to :ignore, maps go to :ignore-issues.

   Options:
   - :global - Force global scope (~/.proserunner/ignore.edn)
   - :project - Force project scope (.proserunner/config.edn)
   - :start-dir - Starting directory for project detection"
  ([specimen]
   (let [ignores (read-ignore-file)
         updated (add-specimen ignores specimen)]
     (write-ignore-file! updated)))
  ([specimen options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (let [ignores (read-ignore-file)
               updated (add-specimen ignores specimen)]
           (write-ignore-file! updated))
         ;; Add to project
         (add-ignore-to-project! specimen project-root))))))

(defn remove-from-ignore!
  "Removes a specimen from the ignore list with context-aware targeting.
   Removes strings from :ignore, maps from :ignore-issues.

   Options:
   - :global - Force global scope (~/.proserunner/ignore.edn)
   - :project - Force project scope (.proserunner/config.edn)
   - :start-dir - Starting directory for project detection"
  ([specimen]
   (let [ignores (read-ignore-file)
         updated (remove-specimen ignores specimen)]
     (write-ignore-file! updated)))
  ([specimen options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (let [ignores (read-ignore-file)
               updated (remove-specimen ignores specimen)]
           (write-ignore-file! updated))
         ;; Remove from project
         (remove-ignore-from-project! specimen project-root))))))

(defn list-ignored
  "Returns map with :ignore (set) and :ignore-issues (vector) with context-aware targeting.

   When in project context with :extend mode, shows both global and project ignores.
   When in project context with :replace mode, shows only project ignores.
   When in global context, shows only global ignores.

   Options:
   - :global - Force global scope
   - :project - Force project scope
   - :start-dir - Starting directory for project detection"
  ([]
   (read-ignore-file))
  ([options]
   (context/with-context options
     (fn [{:keys [target start-dir]}]
       (if (= target :global)
         (read-ignore-file)
         ;; List from project context (respecting merge mode)
         (let [config (project-config/load start-dir)]
           ;; The project-config loader already merges global and project ignores
           ;; based on :ignore-mode
           {:ignore (:ignore config)
            :ignore-issues (:ignore-issues config)}))))))

(defn clear-ignore!
  "Clears all ignored specimens and issues."
  ([]
   (write-ignore-file! {:ignore #{} :ignore-issues []}))
  ([options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (write-ignore-file! {:ignore #{} :ignore-issues []})
         ;; Clear project ignores
         (let [config (project-config/read project-root)]
           (project-config/write! project-root (assoc config
                                                      :ignore #{}
                                                      :ignore-issues []))))))))
