(ns editors.repetition
  "Detects consecutive repeated words in text."
  (:gen-class)
  (:require [clojure.string :as string]
            [editors.utilities :as util]))

(defn ^:private find-index
  "Finds column number(s) of `specimens`, and adds issue to `line`.
   If no column is found, util/add-issue returns the line."
  [line check specimens]
  (let [targets (map #(apply str (interpose " " %)) specimens)
        {:keys [name message kind]} check]
    (reduce (fn [line t]
              (util/add-issue {:line line
                               :specimen t
                               :name name
                               :kind kind
                               :message message}))
            line targets)))

(defn proofread
  "If proofread finds consecutive repeated words in the `line`,
  proofread creates an issue and adds it to the line's issues vector."
  [line check]
  (let [specimens (->> line
                       (:text)
                       (#(string/split % #"\s+"))
                       ;; strip punctuation
                       (map #(string/replace % #"\W" ""))
                       (partition-by identity)
                       (filter #(> (count %) 1)))
        words (filter #(re-find #"\w" (apply str %)) specimens)]
    (if (empty? words)
      line
      (find-index line check words))))
