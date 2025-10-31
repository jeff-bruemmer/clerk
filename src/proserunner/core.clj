(ns proserunner.core
  (:gen-class)
  (:require [proserunner
             [config :as conf]
             [custom-checks :as custom]
             [error :as error]
             [fmt :as fmt]
             [ignore :as ignore]
             [metrics :as metrics]
             [project-config :as project-conf]
             [shipping :as ship]
             [text :as text]
             [vet :as vet]]
            [clojure.tools.cli :as cli]))

(set! *warn-on-reflection* true)

(def options
  "CLI option configuration. See:
  https://github.com/clojure/tools.cli"
  [["-b" "--code-blocks" "Include code blocks." :default false]
   ["-C" "--checks" "List enabled checks."]
   ["-c" "--config CONFIG" "Set temporary configuration file." :default nil]
   ["-d" "--check-dialogue" "Include dialogue in checks." :default false]
   ["-e" "--exclude PATTERN" "Exclude files/dirs matching pattern (glob)." :default nil]
   ["-f" "--file FILE" "File or dir to proofread."
    :default nil
    :validate [text/file-exists? text/file-error-msg
               text/less-than-10-MB? text/file-size-msg]]
   ["-h" "--help" "Prints this help message."]
   ["-i" "--ignore IGNORE" "EDN file listing specimens to ignore." :default "ignore"]
   ["-n" "--no-cache" "Don't use cached results." :default false]
   ["-o" "--output FORMAT" "Output type: group, edn, json, table, verbose."
    :default "group"]
   ["-p" "--parallel-files" "Process multiple files in parallel."
    :default false]
   ["-S" "--sequential-lines" "Process lines sequentially."
    :default false]
   ["-t" "--timer" "Print time elapsed." :default false]
   ["-m" "--metrics" "Show performance metrics." :default false]
   ["-v" "--version" "Prints version number."]
   ["-A" "--add-ignore SPECIMEN" "Add specimen to ignore list."]
   ["-R" "--remove-ignore SPECIMEN" "Remove specimen from ignore list."]
   ["-L" "--list-ignored" "List all ignored specimens."]
   ["-X" "--clear-ignored" "Clear all ignored specimens."]
   ["-D" "--restore-defaults" "Restore default checks from GitHub."]
   ["-a" "--add-checks SOURCE" "Import checks from local directory."]
   ["-N" "--name NAME" "Custom name for imported checks directory."]
   ["-G" "--global" "Add to global config (~/.proserunner/)."]
   ["-P" "--project" "Add to project config (.proserunner/)."]
   ["-I" "--init-project" "Initialize .proserunner/ directory in current directory."]])

(defn- handle-init-project!
  "Handles project initialization by creating .proserunner/ directory and displaying usage help."
  []
  (try
    (let [cwd (System/getProperty "user.dir")
          _ (project-conf/init-project-config! cwd)]
      (println "Created project configuration directory: .proserunner/")
      (println "  + .proserunner/config.edn - Project configuration")
      (println "  + .proserunner/checks/    - Directory for project-specific checks")
      (println "\nEdit .proserunner/config.edn to customize:")
      (println "  :check-sources - Specify check sources:")
      (println "                   - \"default\" for built-in checks")
      (println "                   - \"checks\" for .proserunner/checks/")
      (println "                   - Local directory paths")
      (println "  :ignore        - Set of specimens to ignore")
      (println "  :ignore-mode   - :extend (merge with global) or :replace")
      (println "  :config-mode   - :merged (use global+project) or :project-only")
      (println "\nAdd custom checks by creating .edn files in .proserunner/checks/"))
    (catch clojure.lang.ExceptionInfo e
      (println "Error:" (.getMessage e))
      (System/exit 1))
    (catch Exception e
      (println "Error initializing project:" (.getMessage e))
      (System/exit 1))))

(defn proserunner
  "Proserunner takes options and vets a text with the supplied checks."
  [options]
  (try
    (let [opts (if (:metrics options)
                 (do
                   (metrics/reset-metrics!)
                   (metrics/start-timing!)
                   ;; Force no-cache when metrics enabled
                   (assoc options :no-cache true))
                 options)
          result (->> opts
                      (vet/compute-or-cached)
                      (ship/out))]
      (when (:metrics options)
        (metrics/end-timing!)
        (metrics/print-metrics))
      result)
    (catch java.util.regex.PatternSyntaxException e
      (println "Error: Invalid regex pattern in check definition.")
      (println "Details:" (.getMessage e))
      (System/exit 1))
    (catch java.io.IOException e
      (println "Error: File I/O operation failed.")
      (println "Details:" (.getMessage e))
      (System/exit 1))
    (catch Exception e
      (println "Error: An unexpected error occurred during processing.")
      (println "Details:" (.getMessage e))
      (System/exit 1))))

(defn reception
  "Parses command line `args` and applies the relevant function."
  [args]
  (let [opts (cli/parse-opts args options :summary-fn fmt/summary)
        {:keys [options errors]} opts
        expanded-options (conf/default (merge opts options))
        {:keys [file config help checks version
                add-ignore remove-ignore list-ignored clear-ignored
                restore-defaults add-checks init-project parallel-files sequential-lines
                global project]} expanded-options
        ;; Validate that both parallel modes are not enabled and flag conflicts
        validation-errors (cond
                            (and parallel-files (not sequential-lines))
                            ["Cannot enable both parallel file and parallel line processing. Use --sequential-lines with --parallel-files."]

                            (and global project)
                            ["Cannot specify both --global and --project flags."]

                            :else nil)]
    (if (or (seq errors) (seq validation-errors))
      (error/inferior-input (concat errors validation-errors))
      ;; Dispatch on command.
      (do (cond
            ;; Ignore management commands
            add-ignore (let [opts (select-keys expanded-options [:global :project])
                             target (project-conf/determine-target opts (System/getProperty "user.dir"))
                             msg-context (if (= target :project) "project" "global")]
                         (ignore/add-to-ignore! add-ignore opts)
                         (println (format "Added to %s ignore list: %s" msg-context add-ignore))
                         (if (= target :project)
                           (println "Use --global to add to global ignore list instead.")
                           (println "Use --project to add to project ignore list instead.")))

            remove-ignore (let [opts (select-keys expanded-options [:global :project])
                                target (project-conf/determine-target opts (System/getProperty "user.dir"))
                                msg-context (if (= target :project) "project" "global")]
                            (ignore/remove-from-ignore! remove-ignore opts)
                            (println (format "Removed from %s ignore list: %s" msg-context remove-ignore)))

            list-ignored (let [opts (select-keys expanded-options [:global :project])
                               ignored (ignore/list-ignored opts)]
                           (if (empty? ignored)
                             (println "No ignored specimens.")
                             (do
                               (println "Ignored specimens:")
                               (doseq [specimen ignored]
                                 (println "  " specimen)))))

            clear-ignored (let [opts (select-keys expanded-options [:global :project])
                                target (project-conf/determine-target opts (System/getProperty "user.dir"))
                                msg-context (if (= target :project) "project" "global")]
                            (ignore/clear-ignore! opts)
                            (println (format "Cleared all %s ignored specimens." msg-context)))

            restore-defaults (conf/restore-defaults!)

            init-project (handle-init-project!)

            ;; Custom checks management
            add-checks (try
                        (custom/add-checks add-checks (select-keys expanded-options [:name :global :project]))
                        (catch Exception e
                          (println "Error adding checks:" (.getMessage e))
                          (System/exit 1)))

            ;; Regular commands
            file (proserunner expanded-options)
            checks (ship/print-checks config)
            help (ship/print-usage expanded-options)
            version (ship/print-version)
            :else (ship/print-usage expanded-options "P R O S E R U N N E R"))
          ;; Return options for any follow-up activity, like printing time elapsed.
          expanded-options))))

(defn run
  "For development; same as main, except `run` doesn't shutdown agents."
  [& args]
  (let [start-time (System/currentTimeMillis)
        options (assoc (reception args) :start-time start-time)]
    (ship/time-elapsed options)))

(defn -main
  "Sends args to reception for dispatch, then shuts down agents and prints the time."
  [& args]
  (let [start-time (System/currentTimeMillis)
        options (assoc (reception args) :start-time start-time)]
    (shutdown-agents)
    (ship/time-elapsed options)))


