(ns clerk.core
  (:gen-class)
  (:require [clerk
             [config :as conf]
             [error :as error]
             [shipping :as ship]
             [text :as text]
             [vet :as vet]
             [system :as sys]]
            [clojure.tools.cli :as cli]))

(set! *warn-on-reflection* true)

(def options
  "CLI option configuration. See:
  https://github.com/clojure/tools.cli"
  [["-f" "--file FILE" "File to proofread."
    :default nil
    :validate [text/file-exists? text/file-error-msg
               text/supported-file-type? text/file-type-msg
               text/less-than-10-MB? text/file-size-msg]]
   ["-o" "--output FORMAT" "Output type: table, EDN, or JSON."
    :default "table"]
   ["-C" "--checks" "List enabled checks."]
   ["-c" "--config CONFIG" "Set temporary configuration file."
    :default (sys/filepath ".clerk" "config.edn")
    :validate [text/file-exists? text/file-error-msg
               conf/valid? conf/invalid-msg]]
   ["-h" "--help" "Prints this help message."]
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
  (let [opts (cli/parse-opts args options :summary-fn ship/format-summary)
        {:keys [options errors]} opts
        {:keys [file config help checks version]} options]
    (if (seq errors)
      (do (error/message errors)
          (error/exit))
      (cond
        file (clerk options)
        checks (ship/print-checks config)
        help (ship/print-usage opts)
        version (ship/print-version)
        :else (ship/print-usage opts "You must supply an option.")))))

(defn -main
  [& args]
  (reception args)
  (shutdown-agents))

;;;; For development; prevents Cider REPL from closing.

;; (defn -main
;;   [& args]
;;   (reception args))
