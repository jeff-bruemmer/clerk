(ns proserunner.commands
  "Command handlers that return effect descriptions.

  Handlers determine what operations to perform based on CLI options,
  returning data structures that describe effects to execute. This separates
  command logic from I/O operations.

  Each handler returns a map with:
  - :effects   - vector of effect tuples [effect-type & args]
  - :messages  - optional vector of user messages
  - :format-fn - optional function to format effect results

  The effects namespace executes these descriptions."
  (:gen-class)
  (:require [proserunner.ignore :as ignore]
            [proserunner.result :as result]
            [proserunner.shipping :as ship]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn project-exists?
  "Checks if a .proserunner/config.edn file exists in the current directory."
  []
  (let [project-root (System/getProperty "user.dir")
        config-file (io/file project-root ".proserunner" "config.edn")]
    (.exists config-file)))

(defn determine-target
  "Determines whether to use :project or :global based on flags and project existence.
  
  Logic:
  - If --global flag is explicitly set, use :global
  - If --project flag is explicitly set, use :project  
  - If .proserunner/config.edn exists, default to :project
  - Otherwise, default to :global"
  [{:keys [global project]}]
  (cond
    global  :global
    project :project
    (project-exists?) :project
    :else :global))

(set! *warn-on-reflection* true)

;;;; Issue number parsing utilities

(defn parse-issue-numbers
  "Parses a string of issue numbers and ranges into a sorted vector of unique numbers.
  
  Examples:
    \"1\" -> [1]
    \"1,3,5\" -> [1 3 5]
    \"1-5\" -> [1 2 3 4 5]
    \"1-3,5,7-9\" -> [1 2 3 5 7 8 9]
    
  Returns nil for empty or nil input."
  [s]
  (when (and s (not (str/blank? s)))
    (->> (str/split s #",")
         (mapcat (fn [part]
                   (let [trimmed (str/trim part)]
                     (when-not (str/blank? trimmed)
                       (if (str/includes? trimmed "-")
                         (let [[start end] (str/split trimmed #"-")
                               start-num (Integer/parseInt (str/trim start))
                               end-num (Integer/parseInt (str/trim end))]
                           (if (<= start-num end-num)
                             (range start-num (inc end-num))
                             []))  ; Return empty for invalid range
                         [(Integer/parseInt trimmed)])))))
         (remove nil?)
         (distinct)
         (sort)
         (vec))))


(defn filter-issues-by-numbers
  "Filters a sequence of issues to only those at the specified 1-based indices.
  
  Example:
    (filter-issues-by-numbers [issue1 issue2 issue3] [1 3])
    => [issue1 issue3]"
  [issues numbers]
  (let [number-set (set numbers)]
    (->> issues
         (map-indexed (fn [idx issue] [(inc idx) issue]))
         (filter (fn [[num _]] (contains? number-set num)))
         (map second)
         (vec))))

(defn handle-add-ignore
  "Handler for adding a specimen to ignore list."
  [{:keys [add-ignore project] :as opts}]
  (let [target (if project :project :global)
        msg-context (if (= target :project) "project" "global")
        alt-msg (if (= target :project)
                  "Use --global to add to global ignore list instead."
                  "Use --project to add to project ignore list instead.")]
    {:effects [[:ignore/add add-ignore opts]]
     :messages [(format "Added to %s ignore list: %s" msg-context add-ignore)
                alt-msg]}))

(defn handle-remove-ignore
  "Handler for removing a specimen from ignore list."
  [{:keys [remove-ignore project] :as opts}]
  (let [target (if project :project :global)
        msg-context (if (= target :project) "project" "global")]
    {:effects [[:ignore/remove remove-ignore opts]]
     :messages [(format "Removed from %s ignore list: %s" msg-context remove-ignore)]}))

(defn handle-list-ignored
  "Handler for listing all ignored specimens."
  [opts]
  {:effects [[:ignore/list opts]]
   :format-fn ship/format-ignored-list})

(defn handle-clear-ignored
  "Handler for clearing all ignored specimens."
  [{:keys [project] :as opts}]
  (let [target (if project :project :global)
        msg-context (if (= target :project) "project" "global")]
    {:effects [[:ignore/clear opts]]
     :messages [(format "Cleared all %s ignored specimens." msg-context)]}))

(defn handle-ignore-all
  "Handler for ignoring all current findings.
   Runs the file through proserunner, collects all issues, and adds them as contextual ignores.
   Defaults to project if .proserunner/config.edn exists, otherwise global."
  [opts]
  (let [target (determine-target opts)
        msg-context (if (= target :project) "project" "global")
        opts-with-target (assoc opts :project (= target :project))]
    {:effects [[:ignore/add-all opts-with-target]]
     :messages [(format "Adding all current findings to %s ignore list..." msg-context)]}))

(defn handle-ignore-issues
  "Handler for ignoring specific issues by number.
  Re-runs proserunner to get the same issue numbering, then creates
  contextual ignores for only the specified issue numbers.
  Defaults to project if .proserunner/config.edn exists, otherwise global."
  [{:keys [ignore-issues] :as opts}]
  (let [target (determine-target opts)
        msg-context (if (= target :project) "project" "global")
        issue-nums (parse-issue-numbers ignore-issues)
        opts-with-target (assoc opts :project (= target :project))]
    {:effects [[:ignore/add-issues issue-nums opts-with-target]]
     :messages [(format "Ignoring issues %s in %s ignore list..." 
                        (str/join ", " issue-nums) 
                        msg-context)]}))

(defn handle-audit-ignores
  "Handler for auditing ignore entries to find stale ones."
  [opts]
  {:effects [[:ignore/audit opts]]
   :format-fn ignore/format-audit-report})

(defn handle-clean-ignores
  "Handler for cleaning stale ignore entries."
  [{:keys [project] :as opts}]
  (let [target (if project :project :global)
        msg-context (if (= target :project) "project" "global")]
    {:effects [[:ignore/clean opts]]
     :messages [(format "Cleaning stale ignores from %s ignore list..." msg-context)]}))

(defn handle-restore-defaults
  "Handler for restoring default checks from GitHub."
  [_opts]
  {:effects [[:config/restore-defaults]]
   :messages ["Restoring default checks from GitHub..."]})

(defn handle-init-project
  "Handler for initializing project configuration."
  [_opts]
  {:effects [[:project/init]]
   :format-fn ship/format-init-project})

(defn handle-add-checks
  "Handler for adding custom checks from a directory."
  [{:keys [add-checks] :as opts}]
  {:effects [[:checks/add add-checks (select-keys opts [:name :global :project])]]
   :messages [(format "Adding checks from: %s" add-checks)]})

(defn handle-file
  "Handler for processing a file or directory."
  [opts]
  {:effects [[:file/process opts]]})

(defn handle-checks
  "Handler for listing enabled checks."
  [{:keys [config]}]
  {:effects [[:checks/print config]]})

(defn handle-help
  "Handler for printing help/usage."
  [opts]
  {:effects [[:help/print opts]]})

(defn handle-version
  "Handler for printing version."
  [_opts]
  {:effects [[:version/print]]})

(defn handle-default
  "Handler for default action (print usage with title)."
  [opts]
  {:effects [[:help/print opts "P R O S E R U N N E R"]]})

;; Command handler registry
(def handlers
  "Map of command keywords to their handler functions."
  {:add-ignore       handle-add-ignore
   :remove-ignore    handle-remove-ignore
   :list-ignored     handle-list-ignored
   :clear-ignored    handle-clear-ignored
   :ignore-all       handle-ignore-all
   :ignore-issues    handle-ignore-issues
   :audit-ignores    handle-audit-ignores
   :clean-ignores    handle-clean-ignores
   :restore-defaults handle-restore-defaults
   :init-project     handle-init-project
   :add-checks       handle-add-checks
   :file             handle-file
   :checks           handle-checks
   :help             handle-help
   :version          handle-version
   :default          handle-default})

;; Command determination
(defn determine-command
  "Determines which command to execute based on options.
  Returns a keyword identifying the command."
  [{:keys [add-ignore remove-ignore list-ignored clear-ignored ignore-all ignore-issues
           audit-ignores clean-ignores
           restore-defaults init-project add-checks
           file checks help version]}]
  (cond
    add-ignore       :add-ignore
    remove-ignore    :remove-ignore
    list-ignored     :list-ignored
    clear-ignored    :clear-ignored
    ignore-all       :ignore-all
    ignore-issues    :ignore-issues
    audit-ignores    :audit-ignores
    clean-ignores    :clean-ignores
    restore-defaults :restore-defaults
    init-project     :init-project
    add-checks       :add-checks
    file             :file
    checks           :checks
    help             :help
    version          :version
    :else            :default))

(defn dispatch-command
  "Dispatches command based on options, returning effect description.

  Returns a map with :command, :effects, and optionally :messages or :format-fn."
  [opts]
  (let [cmd (determine-command opts)
        handler (get handlers cmd handle-default)]
    (assoc (handler opts) :command cmd)))

;; Validation
(defn validate-options
  "Validates options for conflicts and errors.
  Returns Success with options or Failure with error messages."
  [{:keys [parallel-files sequential-lines global project] :as opts}]
  (cond
    (and parallel-files (not sequential-lines))
    (result/err "Cannot enable both parallel file and parallel line processing. Use --sequential-lines with --parallel-files.")

    (and global project)
    (result/err "Cannot specify both --global and --project flags.")

    :else
    (result/ok opts)))
