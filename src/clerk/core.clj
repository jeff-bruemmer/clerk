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
   ["-b" "--code-blocks" "Include code blocks." :default false]
   ["-n" "--no-cache" "Don't use cached results." :default false]
   ["-v" "--version" "Prints version number."]])

(defn clerk
  "Clerk vets a text with the supplied checks."
  [options]
  (->> options
       (vet/compute-or-cached)
       (ship/out)))

(defn generate-config
  [options]
  (if (nil? (:config options))
    (assoc options :config (sys/filepath ".clerk" "config.edn"))
    options))

(defn reception
  "Parses command line `args` and applies the relevant function."
  [args]
  (let [opts (cli/parse-opts args options :summary-fn format-summary)
        {:keys [options errors]} opts
        options (generate-config options)
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
  (let [start-time (System/currentTimeMillis)]
    (reception args)
    (shutdown-agents)
    (println "Completed in" (- (System/currentTimeMillis) start-time) "ms.")))

;;;; For development; prevents Cider REPL from closing.

;; (defn -main
;;   [& args]
;;   (let [start-time (System/currentTimeMillis)]
;;     (reception args)
;;     (println "Completed in" (- (System/currentTimeMillis) start-time) "ms.")))
