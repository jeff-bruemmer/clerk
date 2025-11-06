(ns proserunner.error
  "Utilities for error messages"
  (:gen-class)
  (:require [proserunner.fmt :as fmt]
            [proserunner.result :as result]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(def exit-msg "Proserunner needs to lie down for a  bit.\n")

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

;; Result-returning alternatives (preferred for testability and composability)

(defn message-result
  "Returns a Result Failure containing error messages.

  Alternative to `message` that returns a Result instead of printing.
  Useful for testing and composable error handling.

  Example:
    (message-result [\"Error 1\" \"Error 2\"])
    ;; => Failure with formatted error message"
  [errors]
  (result/err (str \newline (string/join \newline errors) \newline)
              {:errors errors}))

(defn exit-result
  "Returns a Result Failure instead of exiting.

  Alternative to `exit` that returns a Result for composable error handling.

  Example:
    (exit-result)
    (exit-result \"Custom error message\")
    (exit-result \"Error\" {:code 404})"
  ([]
   (result/err exit-msg {}))
  ([msg]
   (result/err (str exit-msg \newline (fmt/sentence-dress msg))
               {:message msg}))
  ([msg context]
   (result/err (str exit-msg \newline (fmt/sentence-dress msg))
               (assoc context :message msg))))

(defn inferior-input-result
  "Returns a Result Failure for invalid input.

  Alternative to `inferior-input` that returns a Result instead of exiting.
  Enables testing and error recovery without process termination.

  Example:
    (inferior-input-result [\"Invalid option: --foo\"])
    ;; => Failure with formatted error"
  [errors]
  (result/err (str "Invalid input:" \newline (string/join \newline errors))
              {:errors errors}))

