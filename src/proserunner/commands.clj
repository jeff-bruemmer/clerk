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
  (:require [proserunner.result :as result]))

(set! *warn-on-reflection* true)

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
   :format-fn (fn [ignored]
                (if (empty? ignored)
                  ["No ignored specimens."]
                  (cons "Ignored specimens:"
                        (map #(str "   " %) ignored))))})

(defn handle-clear-ignored
  "Handler for clearing all ignored specimens."
  [{:keys [project] :as opts}]
  (let [target (if project :project :global)
        msg-context (if (= target :project) "project" "global")]
    {:effects [[:ignore/clear opts]]
     :messages [(format "Cleared all %s ignored specimens." msg-context)]}))

(defn handle-restore-defaults
  "Handler for restoring default checks from GitHub."
  [_opts]
  {:effects [[:config/restore-defaults]]
   :messages ["Restoring default checks from GitHub..."]})

(defn handle-init-project
  "Handler for initializing project configuration."
  [_opts]
  {:effects [[:project/init]]
   :format-fn (fn [_]
                ["Created project configuration directory: .proserunner/"
                 "  + .proserunner/config.edn - Project configuration"
                 "  + .proserunner/checks/    - Directory for project-specific checks"
                 ""
                 "Edit .proserunner/config.edn to customize:"
                 "  :check-sources - Specify check sources:"
                 "                   - \"default\" for built-in checks"
                 "                   - \"checks\" for .proserunner/checks/"
                 "                   - Local directory paths"
                 "  :ignore        - Set of specimens to ignore"
                 "  :ignore-mode   - :extend (merge with global) or :replace"
                 "  :config-mode   - :merged (use global+project) or :project-only"
                 ""
                 "Add custom checks by creating .edn files in .proserunner/checks/"])})

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
  [{:keys [add-ignore remove-ignore list-ignored clear-ignored
           restore-defaults init-project add-checks
           file checks help version]}]
  (cond
    add-ignore       :add-ignore
    remove-ignore    :remove-ignore
    list-ignored     :list-ignored
    clear-ignored    :clear-ignored
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
