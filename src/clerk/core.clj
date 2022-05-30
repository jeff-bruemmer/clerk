(ns clerk.core
  (:gen-class)
  (:require [clerk
             [config :as conf]
             [error :as error]
             [fmt :as fmt]
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
   ["-f" "--file FILE" "File or dir to proofread."
    :default nil
    :validate [text/file-exists? text/file-error-msg
               text/less-than-10-MB? text/file-size-msg]]
   ["-h" "--help" "Prints this help message."]
   ["-i" "--ignore IGNORE" "EDN file listing specimens to ignore." :default "ignore"]
   ["-n" "--no-cache" "Don't use cached results." :default false]
   ["-o" "--output FORMAT" "Output type: group, edn, json, table."
    :default "group"]
   ["-t" "--timer" "Print time elapsed." :default false]
   ["-v" "--version" "Prints version number."]])

(defn clerk
  "Clerk takes options and vets a text with the supplied checks."
  [options]
  (->> options
       (vet/compute-or-cached)
       (ship/out)))

(defn reception
  "Parses command line `args` and applies the relevant function."
  [args]
  (let [opts (cli/parse-opts args options :summary-fn fmt/summary)
        {:keys [options errors]} opts
        expanded-options (conf/default (merge opts options))
        {:keys [file config help checks version]} expanded-options]
    (if (seq errors)
      (error/inferior-input errors)
      ;; Dispatch on command.
      (do (cond
            file (clerk expanded-options)
            checks (ship/print-checks config)
            help (ship/print-usage expanded-options)
            version (ship/print-version)
            :else (ship/print-usage expanded-options "You must supply an option."))
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


