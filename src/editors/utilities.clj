(ns editors.utilities
  (:require [clerk.text :as text]
            [clojure.string :as string])
  (:gen-class))

(defn make-pattern
  "Used to concat regex pattern to search for multiple specimens at once."
  [re-payload case-sensitive?]
  (let [leftb (if case-sensitive? "\\b(" "(?i)\\b(")]
    (->> re-payload
         (#(str leftb  % ")\\b"))
         (re-pattern))))

(defn seek
  "Given a text, regex, and case sensitivity, returns a vector of regex matches."
  [text re-payload case-sensitive?]
  (let [p (make-pattern re-payload case-sensitive?)
        matches (re-seq p text)]
    (mapv first matches)))

(defn add-issue
  "Adds an issue to a text/Line's issues."
  [{:keys [line specimen name kind message]}]
  (let [{:keys [text]} line
        col (string/index-of (string/lower-case text) (string/lower-case specimen))]
    ;; If the string is not in the line, there is no issue.
    ;; Guards against legitimate repetition hits, hits that are valid.
    (if (nil? col)
      line
      (-> line
          (assoc :issue? true)
          (update :issues #(conj % (text/->Issue
                                    name
                                    kind
                                    specimen
                                    col
                                    message)))))))

(defn create-issue-collector
  "Samples lines for each specimen in check, and adds any issues to the line."
  [case-sensitivity]
  (fn
    [line
     {:keys [kind specimens message name]}]
    (if (empty? specimens)
      line
      (let [re-core (string/join "|" specimens)
            matches (seek (:text line) re-core case-sensitivity)]
        (if (empty? matches)
          line
          (reduce
           (fn [l match] (add-issue {:line l
                                     :specimen match
                                     :name name
                                     :kind kind
                                     :message message}))
           line
           matches))))))
