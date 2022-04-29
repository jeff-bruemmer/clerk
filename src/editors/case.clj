(ns editors.case
  (:require
   [editors.utilities :as util])
  (:gen-class))

(def ^:private collect-issues
  "Case sensitive issue collector."
  (util/create-issue-collector true))

(defn proofread
  "Collects any issues with the line."
  [line check]
  (collect-issues line check))

