(ns editors.utilities
  (:require [clerk.text :as text]
            [clojure.string :as string])
  (:gen-class))

(defn make-pattern
  "Used to concat regex pattern to search for multiple specimens at once."
  [re-payload case-sensitive?]
  (let [ignore-chars "[^\\[\\#-_]"
        boundary "\\b("
        leftb (if case-sensitive?
                ;; Ignore matches in markdown/org links
                (str ignore-chars boundary)
                (str "(?i)" ignore-chars boundary))]
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
  (let [{:keys [file text]} line
        col (string/index-of (string/lower-case text) (string/lower-case specimen))]
    ;; If the string is not in the line, there is no issue.
    ;; Guards against legitimate repetition hits, hits that are valid.
    (if (nil? col)
      line
      (-> line
          (assoc :issue? true)
          (update :issues #(conj % (text/->Issue
                                    (text/home-path file)
                                    name
                                    kind
                                    specimen
                                    col
                                    message)))))))

(defn create-issue-collector
  "Samples lines for each specimen in check, and adds any issues to the line."
  [case-sensitive?]
  (fn
    [line
     {:keys [file kind specimens message name]}]
    (if (empty? specimens)
      line
      (let [re-core (string/join "|" specimens)
            matches (seek (:text line) re-core case-sensitive?)]
        (if (empty? matches)
          line
          (reduce
           (fn [l match] (add-issue {:file file
                                     :line l
                                     :specimen match
                                     :name name
                                     :kind kind
                                     :message message}))
           line
           matches))))))

(defn create-recommender
  "Returns a function that accepts a boolean to determine whether the
  recommender is case sensitive."
  [case-sensitive?]
  (fn [line check]
    (let [{:keys [recommendations name kind]} check]
      (if (empty? recommendations)
        line
        (reduce (fn [line {:keys [prefer avoid]}]
                  (let [{:keys [file text]} line
                        matches (seek text avoid case-sensitive?)]
                    (if (empty? matches)
                      line
                      (reduce (fn [l match] (add-issue {:file file
                                                        :line l
                                                        :specimen match
                                                        :name name
                                                        :kind kind
                                                        :message (str "Prefer: " prefer)}))
                              line
                              matches))))
                line
                recommendations)))))
