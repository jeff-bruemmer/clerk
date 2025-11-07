(ns proserunner.core
  "Main entry point for Proserunner CLI. Parses command-line arguments and dispatches to command handlers."
  (:gen-class)
  (:require [proserunner
             [commands :as cmd]
             [config :as conf]
             [effects :as effects]
             [error :as error]
             [fmt :as fmt]
             [output :as output]
             [result :as result]
             [text :as text]]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(set! *warn-on-reflection* true)

(def options
  "CLI option configuration. See:
  https://github.com/clojure/tools.cli"
  [["-b" "--code-blocks" "Include code blocks when checking. Default: skip them." :default false]
   ["-C" "--checks" "List all enabled checks with their types and descriptions."]
   ["-c" "--config CONFIG" "Use specific config file, overriding global and project configs." :default nil]
   ["-d" "--cache-dir DIR" "Cache directory location. Priority: CLI > $PROSERUNNER_CACHE_DIR > $XDG_CACHE_HOME/proserunner > $TMPDIR/proserunner-storage"
    :default nil
    :validate [(fn [s] (and s (not (str/blank? s))))
               "Cache directory cannot be empty"]]
   ["-q" "--quoted-text" "Include quoted text when checking. Default: skip it." :default false]
   ["-e" "--exclude PATTERN" "Exclude files/dirs matching glob pattern. Can be used multiple times or comma-separated. Example: --exclude \"*.log,temp/**\""
    :default []
    :assoc-fn (fn [m k v]
                (let [patterns (if (re-find #"," v)
                                 (str/split v #",\s*")
                                 [v])]
                  (update m k (fnil into []) patterns)))]
   ["-f" "--file FILE" "File or directory to check. Directories processed recursively."
    :default nil
    :validate [text/file-exists? text/file-error-msg
               text/less-than-10-MB? text/file-size-msg]]
   ["-h" "--help" "Show this help."]
   ["-i" "--ignore IGNORE" "Ignore file name (default: 'ignore')." :default "ignore"]
   ["-n" "--no-cache" "Skip cache, force re-processing." :default false]
   ["-s" "--skip-ignore" "Skip all ignore lists for this run." :default false]
   ["-o" "--output FORMAT" "Output format: 'group' (default), 'edn', 'json', 'table', 'verbose'."
    :default "group"]
   ["-p" "--parallel-files" "Process files concurrently."
    :default false]
   ["-S" "--sequential-lines" "Process lines sequentially (for debugging)."
    :default false]
   ["-t" "--timer" "Print elapsed time." :default false]
   ["-v" "--version" "Show version."]
   ["-A" "--add-ignore SPECIMEN" "Add specimen to ignore list (applies everywhere)."]
   ["-R" "--remove-ignore SPECIMEN" "Remove specimen from ignore list."]
   ["-L" "--list-ignored" "List all ignored specimens."]
   ["-X" "--clear-ignored" "Clear all ignored specimens. Use with caution."]
   ["-Z" "--ignore-all" "Ignore all current findings (creates contextual ignores)."]
   ["-J" "--ignore-issues NUMBERS" "Ignore issues by number. Supports ranges: 1,3,5-7. Requires --file."]
   ["-U" "--audit-ignores" "Find stale ignores."]
   ["-W" "--clean-ignores" "Remove stale ignores."]
   ["-D" "--restore-defaults" "Download fresh default checks from GitHub."]
   ["-a" "--add-checks SOURCE" "Import checks from directory."]
   ["-N" "--name NAME" "Custom name for imported checks (use with --add-checks)."]
   ["-G" "--global" "Apply to global config (~/.proserunner/)."]
   ["-P" "--project" "Apply to project config (.proserunner/)."]
   ["-I" "--init-project" "Create .proserunner/ with default config."]])

(defn reception
  "Parses command line `args` and applies the relevant function.

  Now uses pure command handlers and effect execution for better testability."
  [args]
  (conf/ensure-global-config!)
  (let [opts (cli/parse-opts args options :summary-fn fmt/summary)
        {:keys [options errors]} opts
        expanded-options (conf/default (merge opts options))]
    (if (seq errors)
      (error/inferior-input errors)
      ;; Validate options, dispatch command, execute effects
      (let [validation-result (cmd/validate-options expanded-options)]
        (if (result/failure? validation-result)
          ;; Validation failed - print error and exit
          (error/inferior-input [(:error validation-result)])
          ;; Valid options - dispatch and execute
          (let [command-result (cmd/dispatch-command expanded-options)
                effect-result (effects/execute-command-result command-result)]
            ;; Return options for follow-up activity (like printing time)
            ;; Use result-or-exit to handle failures consistently
            (result/result-or-exit effect-result 1)
            expanded-options))))))

(defn run
  "For development; same as main, except `run` doesn't shutdown agents."
  [& args]
  (let [start-time (System/currentTimeMillis)
        options (assoc (reception args) :start-time start-time)]
    (output/time-elapsed options)))

(defn -main
  "Sends args to reception for dispatch, then shuts down agents and prints the time."
  [& args]
  (let [start-time (System/currentTimeMillis)
        options (assoc (reception args) :start-time start-time)]
    (shutdown-agents)
    (output/time-elapsed options)))
