(ns clerk.core
  (:gen-class)
  (:require [clerk
             [config :as conf]
             [error :as error]
             [shipping :as ship]
             [text :as text]
             [vet :as vet]]
            [clojure.tools.cli :as cli]))

(set! *warn-on-reflection* true)

(defn format-summary
  "Function supplied to cli/parse-opts to format map of command line options.
   Map produced sent to ship/print-options."
  [summary]
  (->> summary
       ;; combine short and long option
       (map (fn [m] (assoc m :option (str (:short-opt m) ", " (:long-opt m)))))
       (map #(dissoc % :short-opt :long-opt :id :validate-fn :validate-msg))))

(def options
  "CLI option configuration. See:
  https://github.com/clojure/tools.cli"
  [["-f" "--file FILE" "File or dir to proofread."
    :default nil
    :validate [text/file-exists? text/file-error-msg
               text/less-than-10-MB? text/file-size-msg]]
   ["-o" "--output FORMAT" "Output type: group, edn, json, table."
    :default "group"]
   ["-C" "--checks" "List enabled checks."]
   ["-c" "--config CONFIG" "Set temporary configuration file." :default nil]
   ["-h" "--help" "Prints this help message."]
   ["-i" "--ignore IGNORE" "EDN file listing specimens to ignore." :default "ignore"]
   ["-b" "--code-blocks" "Include code blocks." :default false]
   ["-n" "--no-cache" "Don't use cached results." :default false]
   ["-t" "--time" "Print time elapsed." :default false]
   ["-v" "--version" "Prints version number."]])

(defn clerk
  "Clerk vets a text with the supplied checks."
  [options]
  (->> options
       (vet/compute-or-cached)
       (ship/out)))

(defn reception
  "Parses command line `args` and applies the relevant function."
  [args]
  (let [opts (cli/parse-opts args options :summary-fn format-summary)
        {:keys [options errors]} opts
        expanded-options (conf/default (merge opts options))
        {:keys [file config help checks version]} expanded-options]
    (if (seq errors)
      (do (error/message errors)
          (error/exit))
      (do (cond
            file (clerk expanded-options)
            checks (ship/print-checks config)
            help (ship/print-usage expanded-options)
            version (ship/print-version)
            :else (ship/print-usage expanded-options "You must supply an option."))
          expanded-options))))

;; (defn -main
;;   [& args]
;;   (let [start-time (System/currentTimeMillis)
;;         options (reception args)]
;;     (shutdown-agents)
;;     (when (:time options) (println "Completed in" (- (System/currentTimeMillis) start-time) "ms."))))

;;;; For development; prevents Cider REPL from closing.

(defn -main
  [& args]
  (let [start-time (System/currentTimeMillis)
        options (reception args)]
    (when (:time options) (println "Completed in" (- (System/currentTimeMillis) start-time) "ms."))))


