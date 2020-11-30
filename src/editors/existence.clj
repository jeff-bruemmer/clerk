(ns editors.existence
  (:require
   [editors.utilities :as util])
  (:gen-class))

(defn proofread
  "If proofread finds a specimen in the `line`, proofread adds an issue to the `line`."
  [line check]
  (let [issues (util/create-issue-collector false)]
    (issues line check)))


