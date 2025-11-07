(ns proserunner.output.usage
  "Help and usage messages."
  (:refer-clojure :exclude [print])
  (:gen-class)
  (:require [clojure.string :as string]
            [proserunner.version :as ver]))

(set! *warn-on-reflection* true)

(defn version
  "Prints version number."
  []
  (println "Proserunner version: " ver/number))

(defn categorize-options
  "Organizes command-line options into logical categories for better help output."
  [summary]
  (let [category-order ["Basic Usage"
                        "Output Options"
                        "Ignore Management"
                        "Ignore Maintenance"
                        "Configuration & Checks"
                        "Scope Control"
                        "Advanced Options"]
        categories {"Basic Usage" #{"--file" "--help" "--version"}
                    "Output Options" #{"--output" "--code-blocks" "--quoted-text"}
                    "Ignore Management" #{"--add-ignore" "--remove-ignore" "--list-ignored"
                                         "--clear-ignored" "--ignore-all" "--ignore-issues"}
                    "Ignore Maintenance" #{"--audit-ignores" "--clean-ignores"}
                    "Configuration & Checks" #{"--init-project" "--restore-defaults"
                                               "--add-checks" "--checks" "--exclude"}
                    "Scope Control" #{"--global" "--project"}
                    "Advanced Options" #{"--config" "--ignore" "--no-cache" "--skip-ignore"
                                        "--parallel-files" "--sequential-lines" "--timer" "--name"}}
        ;; Group options by category
        categorized (reduce (fn [acc opt]
                             (let [long-opt (second (string/split (:option opt) #", "))
                                   category (some (fn [[cat opts]] (when (opts long-opt) cat)) categories)
                                   category (or category "Advanced Options")]
                               (update acc category (fnil conj []) opt)))
                           {}
                           summary)]
    ;; Return categories in the specified order
    (map (fn [cat] [cat (get categorized cat)])
         (filter #(get categorized %) category-order))))

(defn- print-option-with-desc
  "Prints a single option with description on separate line if long."
  [{:keys [option required desc]}]
  (let [opt-line (str "  " option (when required (str " " required)))]
    (println opt-line)
    (println (str "      " desc))))

(defn- print-categorized-options
  "Prints options organized by category with descriptions on separate lines."
  [summary]
  (let [categories (categorize-options summary)]
    (doseq [[category options] categories]
      (println (str "\n" category ":"))
      (doseq [opt options]
        (print-option-with-desc opt)))))

(defn- print-examples
  "Prints usage examples."
  []
  (println "\n\nEXAMPLES:")
  (println "  Check a single file:")
  (println "    proserunner --file document.md")
  (println)
  (println "  Check all markdown files in current directory:")
  (println "    proserunner --file .")
  (println)
  (println "  Ignore specific issues by number:")
  (println "    proserunner --file README.md --ignore-issues 1,3,5-7")
  (println)
  (println "  Check files excluding certain patterns:")
  (println "    proserunner --file . --exclude \"*.log,temp/**\"")
  (println)
  (println "  Find stale ignores in configuration:")
  (println "    proserunner --audit-ignores")
  (println)
  (println "  List all enabled checks:")
  (println "    proserunner --checks"))

(defn print
  "Prints usage, optionally with a message."
  ([{:keys [summary config]}]
   (println "\nP R O S E R U N N E R\n")
   (println "Fast prose linter. Finds issues, lets you ignore what you don't care about.\n")
   (println "USAGE:")
   (println "  proserunner [OPTIONS]\n")
   (print-categorized-options summary)
   (print-examples)
   (println "\n\nCONFIG:")
   (println "  Global: ~/.proserunner/config.edn")
   (println "  Project: .proserunner/config.edn")
   (println "  Current: " config)
   (println)
   (version))

  ([opts _]
   ;; When called with a message (e.g., from default handler), show full help
   (print opts)))
