(ns editors.recommender
  (:gen-class)
  (:require [editors.utilities :as util]))

(defn proofread
  "Checks a `line` for specimens to avoid.
  If found, proofread adds an issue to the `line`'s issue vector."
  [line check]
  (let [{:keys [recommendations message name kind]} check]
    (if (empty? recommendations)
      line
      (reduce (fn [line {:keys [prefer avoid]}]
                (let [{:keys [text]} line
                      matches (util/seek text avoid false)]
                  (if (empty? matches)
                    line
                    (reduce (fn [l match] (util/add-issue {:line l
                                                           :specimen match
                                                           :name name
                                                           :kind kind
                                                           :message (str message "Prefer: " prefer)}))
                            line
                            matches))))
              line
              recommendations))))

