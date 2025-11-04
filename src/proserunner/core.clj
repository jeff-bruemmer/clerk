(ns proserunner.core
  "Main entry point for Proserunner CLI. Parses command-line arguments and dispatches to command handlers."
  (:gen-class)
  (:require [proserunner
             [commands :as cmd]
             [config :as conf]
             [effects :as effects]
             [error :as error]
             [fmt :as fmt]
             [result :as result]
             [shipping :as ship]
             [text :as text]]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(set! *warn-on-reflection* true)

(def options
  "CLI option configuration. See:
  https://github.com/clojure/tools.cli"
  [["-b" "--code-blocks" "Include code blocks." :default false]
   ["-C" "--checks" "List enabled checks."]
   ["-c" "--config CONFIG" "Set temporary configuration file." :default nil]
   ["-q" "--quoted-text" "Include quoted text in checks." :default false]
   ["-e" "--exclude PATTERN" "Exclude files/dirs matching pattern (glob). Can be specified multiple times or as comma-separated values."
    :default []
    :assoc-fn (fn [m k v]
                (let [patterns (if (re-find #"," v)
                                 (str/split v #",\s*")
                                 [v])]
                  (update m k (fnil into []) patterns)))]
   ["-f" "--file FILE" "File or dir to proofread."
    :default nil
    :validate [text/file-exists? text/file-error-msg
               text/less-than-10-MB? text/file-size-msg]]
   ["-h" "--help" "Prints this help message."]
   ["-i" "--ignore IGNORE" "EDN file listing specimens to ignore." :default "ignore"]
   ["-n" "--no-cache" "Don't use cached results." :default false]
   ["-s" "--skip-ignore" "Skip all ignore lists (global and project)." :default false]
   ["-o" "--output FORMAT" "Output type: group, edn, json, table, verbose."
    :default "group"]
   ["-p" "--parallel-files" "Process multiple files in parallel."
    :default false]
   ["-S" "--sequential-lines" "Process lines sequentially."
    :default false]
   ["-t" "--timer" "Print time elapsed." :default false]
   ["-v" "--version" "Prints version number."]
   ["-A" "--add-ignore SPECIMEN" "Add specimen to ignore list."]
   ["-R" "--remove-ignore SPECIMEN" "Remove specimen from ignore list."]
   ["-L" "--list-ignored" "List all ignored specimens."]
   ["-X" "--clear-ignored" "Clear all ignored specimens."]
   ["-Z" "--ignore-all" "Ignore all current findings (creates contextual ignores)."]
   ["-J" "--ignore-issues NUMBERS" "Ignore specific issues by number (e.g., 1,3,5-7)."]
   ["-U" "--audit-ignores" "Check for stale ignore entries."]
   ["-W" "--clean-ignores" "Remove stale ignore entries."]
   ["-D" "--restore-defaults" "Restore default checks from GitHub."]
   ["-a" "--add-checks SOURCE" "Import checks from local directory."]
   ["-N" "--name NAME" "Custom name for imported checks directory."]
   ["-G" "--global" "Add to global config (~/.proserunner/)."]
   ["-P" "--project" "Add to project config (.proserunner/)."]
   ["-I" "--init-project" "Initialize .proserunner/ directory in current directory."]])

(defn reception
  "Parses command line `args` and applies the relevant function.

  Now uses pure command handlers and effect execution for better testability."
  [args]
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
            (if (result/failure? effect-result)
              ;; Effect failed - exit with error code
              (do
                (System/exit 1)
                expanded-options)
              expanded-options)))))))

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


