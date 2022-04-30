(ns clerk.error
  "Utilities for error messages."
  (:gen-class)
  (:require [clojure.string :as string]))

(set! *warn-on-reflection* true)

(def exit-msg "Clerk needs to go lie down for a bit.\n")

(defn message
  "Prints `errors`. Used to print command line option errors."
  [errors]
  (println \newline (string/join \newline errors) \newline))

(defn exit
  "Prints an optional `msg` and exists with status 0."
  ([]
   (println exit-msg)
   (System/exit 0))

  ([msg]
   (println exit-msg)
   (println msg)
   (System/exit 0)))

