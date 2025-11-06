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
  [["-b" "--code-blocks" "Include code blocks when checking. By default, code blocks are skipped." :default false]
   ["-C" "--checks" "List all enabled checks with their types and descriptions."]
   ["-c" "--config CONFIG" "Use a specific configuration file temporarily, overriding global and project configs." :default nil]
   ["-q" "--quoted-text" "Include quoted text when checking. By default, quoted text is skipped." :default false]
   ["-e" "--exclude PATTERN" "Exclude files/dirs matching glob pattern. Can be specified multiple times or as comma-separated values. Example: --exclude \"*.log,temp/**\""
    :default []
    :assoc-fn (fn [m k v]
                (let [patterns (if (re-find #"," v)
                                 (str/split v #",\s*")
                                 [v])]
                  (update m k (fnil into []) patterns)))]
   ["-f" "--file FILE" "File or directory to check. Directories are processed recursively."
    :default nil
    :validate [text/file-exists? text/file-error-msg
               text/less-than-10-MB? text/file-size-msg]]
   ["-h" "--help" "Print this help message with detailed command descriptions."]
   ["-i" "--ignore IGNORE" "Name of ignore file (default: 'ignore'). Stores specimens to skip during checks." :default "ignore"]
   ["-n" "--no-cache" "Force re-processing without using cached results. Useful when check definitions change." :default false]
   ["-s" "--skip-ignore" "Temporarily skip all ignore lists (global and project) for this run." :default false]
   ["-o" "--output FORMAT" "Output format: 'group' (default, grouped by file), 'edn' (Clojure data), 'json' (JSON data), 'table' (formatted table), 'verbose' (detailed markdown)."
    :default "group"]
   ["-p" "--parallel-files" "Process multiple files concurrently for better performance on large codebases."
    :default false]
   ["-S" "--sequential-lines" "Process lines one at a time instead of in parallel. Use for debugging or deterministic results."
    :default false]
   ["-t" "--timer" "Print elapsed time after completion." :default false]
   ["-v" "--version" "Print version number."]
   ["-A" "--add-ignore SPECIMEN" "Add a specimen to the simple ignore list (applies everywhere in all files)."]
   ["-R" "--remove-ignore SPECIMEN" "Remove a specimen from the simple ignore list."]
   ["-L" "--list-ignored" "List all ignored specimens (both simple ignores and contextual file+line ignores)."]
   ["-X" "--clear-ignored" "Remove all ignored specimens from the ignore list. Use with caution."]
   ["-Z" "--ignore-all" "Ignore all current findings by creating contextual ignores (file+line+specimen specific)."]
   ["-J" "--ignore-issues NUMBERS" "Ignore specific issues by their number from the output. Supports ranges: 1,3,5-7. Requires --file."]
   ["-U" "--audit-ignores" "Check for stale ignores (specimens that no longer appear in any checked files)."]
   ["-W" "--clean-ignores" "Remove stale ignores (specimens that no longer appear in any checked files)."]
   ["-D" "--restore-defaults" "Download and restore the default check definitions from GitHub."]
   ["-a" "--add-checks SOURCE" "Import custom check definitions from a local directory. Copies .edn files to your checks directory."]
   ["-N" "--name NAME" "Custom name for the imported checks directory (used with --add-checks)."]
   ["-G" "--global" "Apply operation to global config in ~/.proserunner/ (user-wide settings)."]
   ["-P" "--project" "Apply operation to project config in .proserunner/ (repository-specific settings)."]
   ["-I" "--init-project" "Initialize a .proserunner/ directory in the current directory with default project configuration."]])

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


