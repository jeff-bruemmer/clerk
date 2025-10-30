(ns proserunner.error
  "Utilities for error messages"
  (:gen-class)
  (:require [proserunner.fmt :as fmt]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(def exit-msg "Proserunner encountered an error.\n")

(defn message
  "Prints `errors`. Used to print command line option errors."
  [errors]
  (println \newline (string/join \newline errors) \newline))

(defn exit
  "Prints an optional `msg` and exits with status 1."
  ([]
   (println exit-msg)
   (System/exit 1))

  ([msg]
   (println exit-msg)
   (println (fmt/sentence-dress msg))
   (System/exit 1)))

(defn inferior-input
  "Handles input that Proserunner has no time for."
  [errors]
  (message errors)
  (exit))

