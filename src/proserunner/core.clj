(ns proserunner.core
  (:gen-class)
  (:require [proserunner
             [config :as conf]
             [error :as error]
             [fmt :as fmt]
             [ignore :as ignore]
             [metrics :as metrics]
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
   ["-D" "--restore-defaults" "Restore default checks from GitHub."]])

(defn proserunner
  "Proserunner takes options and vets a text with the supplied checks."
  [options]
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
    result))

(defn reception
  "Parses command line `args` and applies the relevant function."
  [args]
  (let [opts (cli/parse-opts args options :summary-fn fmt/summary)
        {:keys [options errors]} opts
        expanded-options (conf/default (merge opts options))
        {:keys [file config help checks version
                add-ignore remove-ignore list-ignored clear-ignored
                restore-defaults parallel-files sequential-lines]} expanded-options
        ;; Validate that both parallel modes are not enabled
        validation-errors (cond
                            (and parallel-files (not sequential-lines))
                            ["Cannot enable both parallel file and parallel line processing. Use --sequential-lines with --parallel-files."]
                            :else nil)]
    (if (or (seq errors) (seq validation-errors))
      (error/inferior-input (concat errors validation-errors))
      ;; Dispatch on command.
      (do (cond
            ;; Ignore management commands
            add-ignore (do
                         (ignore/add-to-ignore! add-ignore)
                         (println "Added to ignore list:" add-ignore)
                         (println "Ignored specimens:" (count (ignore/read-ignore-file))))
            remove-ignore (do
                            (ignore/remove-from-ignore! remove-ignore)
                            (println "Removed from ignore list:" remove-ignore)
                            (println "Ignored specimens:" (count (ignore/read-ignore-file))))
            list-ignored (let [ignored (ignore/list-ignored)]
                           (if (empty? ignored)
                             (println "No ignored specimens.")
                             (do
                               (println "Ignored specimens:")
                               (doseq [specimen ignored]
                                 (println "  " specimen)))))
            clear-ignored (do
                            (ignore/clear-ignore!)
                            (println "Cleared all ignored specimens."))

            restore-defaults (conf/restore-defaults!)

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


