(ns proserunner.ignore
  "Functions for managing ignored specimens."
  (:gen-class)
  (:require [clojure.java.io :as io]
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
  "Reads the ignore file and returns a set of ignored specimens."
  []
  (let [ignore-path (ignore-file-path)]
    (if (.exists (io/file ignore-path))
      (let [result (edn-utils/read-edn-file ignore-path)]
        (if (result/success? result)
          (set (:value result))
          #{}))
      #{})))

(defn write-ignore-file!
  "Writes the set of ignored specimens to the ignore file atomically.
   Handles both simple specimens (strings) and contextual ignores (maps)."
  [ignored-set]
  (let [ignore-path (ignore-file-path)
        ;; Separate and sort simple vs contextual ignores
        simple-ignores (filter string? ignored-set)
        contextual-ignores (filter map? ignored-set)
        sorted-ignores (vec (concat (sort simple-ignores)
                                    (sort-by (juxt :file :line :specimen) contextual-ignores)))]
    (file-utils/ensure-parent-dir ignore-path)
    (file-utils/atomic-spit ignore-path (pr-str sorted-ignores))))

;;; Set Operations

(defn add-to-set
  "Adds specimen or contextual ignore to ignore set."
  [ignore-set specimen]
  (conj ignore-set specimen))

(defn remove-from-set
  "Removes specimen or contextual ignore from ignore set."
  [ignore-set specimen]
  (disj ignore-set specimen))

(defn- add-ignore-to-project!
  "Adds a specimen to project .proserunner/config.edn :ignore set."
  [specimen project-root]
  (let [config (project-config/read project-root)
        current-ignore (or (:ignore config) #{})
        updated-ignore (add-to-set current-ignore specimen)]
    (project-config/write! project-root (assoc config :ignore updated-ignore))))

(defn- remove-ignore-from-project!
  "Removes a specimen from project .proserunner/config.edn :ignore set."
  [specimen project-root]
  (let [config (project-config/read project-root)
        current-ignore (or (:ignore config) #{})
        updated-ignore (remove-from-set current-ignore specimen)]
    (project-config/write! project-root (assoc config :ignore updated-ignore))))

;;; Contextual Ignore Support

(defn contextual-ignore?
  "Returns true if the ignore entry is a contextual ignore (map) rather than a simple specimen (string)."
  [entry]
  (boolean
   (and (map? entry)
        (:file entry)
        (:specimen entry))))

(defn normalize-ignore-entry
  "Normalizes an ignore entry to a consistent format.
   Returns {:type :simple :specimen ...} for strings
   Returns {:type :contextual :file ... :specimen ... :line ... :check ...} for maps
   Returns nil if entry is invalid."
  [entry]
  (cond
    (string? entry)
    {:type :simple :specimen entry}

    (and (map? entry) (:file entry) (:specimen entry))
    (merge {:type :contextual}
           (select-keys entry [:file :line :specimen :check]))

    :else
    nil))

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
   - :line/:line-num matches if specified  
   - :check/:name matches if specified
   All matching is case-insensitive for specimens."
  [ignore-entry issue]
  (let [issue-line (or (:line issue) (:line-num issue))
        issue-check (or (:check issue) (:name issue))]
    (and
     ;; File must match
     (= (:file ignore-entry) (:file issue))
     
     ;; Specimen must match (case-insensitive)
     (= (string/lower-case (:specimen ignore-entry))
        (string/lower-case (:specimen issue)))
     
     ;; If line is specified, it must match
     (or (nil? (:line ignore-entry))
         (= (:line ignore-entry) issue-line))
     
     ;; If check is specified, it must match
     (or (nil? (:check ignore-entry))
         (= (:check ignore-entry) issue-check)))))

(defn should-ignore-issue?
  "Returns true if the issue should be ignored based on the ignore list.
   Handles both simple specimen ignores and contextual ignores.
   
   issue: map with :file, :line, :specimen, :check
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

(defn filter-issues
  "Filters out issues that should be ignored based on the ignore list.
   Returns a vector of issues that should NOT be ignored.
   
   issues: collection of issue maps with :file, :line, :specimen, :check
   ignores: collection of strings (simple) and/or maps (contextual)"
  [issues ignores]
  (if (or (nil? ignores) (empty? ignores))
    (vec issues)
    (vec (remove #(should-ignore-issue? % ignores) issues))))

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
   - :line or :line-num - Line number
   - :specimen - The problematic text
   - :check or :name - Check name (optional, used with :full granularity)"
  ([issue]
   (issue->ignore-entry issue {}))
  ([issue {:keys [granularity] :or {granularity :line}}]
   (let [file (:file issue)
         line (or (:line issue) (:line-num issue))
         specimen (:specimen issue)
         check (or (:check issue) (:name issue))]
     (case granularity
       :file
       {:file file :specimen specimen}
       
       :line
       {:file file :line line :specimen specimen}
       
       :full
       {:file file :line line :specimen specimen :check check}))))

(defn issues->ignore-entries
  "Converts a collection of issues into contextual ignore entries.
   
   Options:
   - :granularity - Passed to issue->ignore-entry (:file, :line, or :full)
   - :dedupe? - Remove duplicate entries (default true)
   
   Returns a vector of ignore entry maps."
  ([issues]
   (issues->ignore-entries issues {}))
  ([issues {:keys [granularity dedupe?] :or {dedupe? true} :as opts}]
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
   
   Simple string ignores are always considered active.
   Contextual ignores are checked for file existence."
  [ignores]
  (let [stale (filterv (fn [entry]
                        (and (contextual-ignore? entry)
                             (not (file-exists? (:file entry)))))
                      ignores)
        active (filterv (fn [entry]
                         (or (string? entry)
                             (file-exists? (:file entry))))
                       ignores)]
    {:stale stale
     :active active}))

(defn format-audit-report
  "Formats an audit result into a human-readable report.
   Returns a vector of strings for display."
  [{:keys [stale active]}]
  (let [total (+ (count stale) (count active))
        stale-count (count stale)]
    (if (zero? stale-count)
      [(format "✓ All %d ignore entries are active." total)
       "No stale ignores found."]
      (concat
       [(format "Found %d stale ignore(s) out of %d total:" stale-count total)
        ""]
       (map (fn [entry]
              (if (string? entry)
                (format "  - Simple: \"%s\"" entry)
                (format "  - File: %s:%s - \"%s\""
                       (:file entry)
                       (or (:line entry) "*")
                       (:specimen entry))))
            stale)
       [""]
       [(format "Stale ignores reference files that no longer exist.")]
       [(format "Use --clean-ignores to remove them.")]))))

(defn remove-stale-ignores
  "Removes stale ignores from a collection.
   Returns a collection with only active ignores."
  [ignores]
  (:active (audit-ignores ignores)))

;;; Context-Aware Public API

(defn add-to-ignore!
  "Adds a specimen to the ignore list with context-aware targeting.

   Options:
   - :global - Force global scope (~/.proserunner/ignore.edn)
   - :project - Force project scope (.proserunner/config.edn :ignore)
   - :start-dir - Starting directory for project detection"
  ([specimen]
   (write-ignore-file! (add-to-set (read-ignore-file) specimen)))
  ([specimen options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (write-ignore-file! (add-to-set (read-ignore-file) specimen))
         ;; Add to project
         (add-ignore-to-project! specimen project-root))))))

(defn remove-from-ignore!
  "Removes a specimen from the ignore list with context-aware targeting.

   Options:
   - :global - Force global scope (~/.proserunner/ignore.edn)
   - :project - Force project scope (.proserunner/config.edn :ignore)
   - :start-dir - Starting directory for project detection"
  ([specimen]
   (write-ignore-file! (remove-from-set (read-ignore-file) specimen)))
  ([specimen options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (write-ignore-file! (remove-from-set (read-ignore-file) specimen))
         ;; Remove from project
         (remove-ignore-from-project! specimen project-root))))))

(defn list-ignored
  "Returns a sorted list of all ignored specimens with context-aware targeting.

   When in project context with :extend mode, shows both global and project ignores.
   When in project context with :replace mode, shows only project ignores.
   When in global context, shows only global ignores.

   Options:
   - :global - Force global scope
   - :project - Force project scope
   - :start-dir - Starting directory for project detection"
  ([]
   (sort (read-ignore-file)))
  ([options]
   (context/with-context options
     (fn [{:keys [target start-dir]}]
       (if (= target :global)
         (sort (read-ignore-file))
         ;; List from project context (respecting merge mode)
         (let [config (project-config/load start-dir)
               ;; The project-config loader already merges global and project ignores
               ;; based on :ignore-mode
               merged-ignore (:ignore config)]
           (sort merged-ignore)))))))

(defn clear-ignore!
  "Clears all ignored specimens."
  ([]
   (write-ignore-file! #{}))
  ([options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (write-ignore-file! #{})
         ;; Clear project ignores
         (let [config (project-config/read project-root)]
           (project-config/write! project-root (assoc config :ignore #{}))))))))
