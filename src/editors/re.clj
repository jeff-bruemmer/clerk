(ns editors.re
  (:require
   [editors.utilities :as util])
  (:gen-class))

(defn handle-specimen
  "Re-seq may return either a string or a vector of string matches."
  [specimen]
  (if (= (type specimen) clojure.lang.PersistentVector)
    (first specimen)
    specimen))

(defn proofread
  [line check]
  (let [{:keys [expressions name kind]} check]
    (if (empty? expressions)
      line
      (reduce (fn [line {:keys [re message]}]
                (let [{:keys [file text]} line
                      matches (re-seq (re-pattern re) text)]
                  (if (empty? matches)
                    line
                    (reduce (fn [l m]
                              (util/add-issue {:file file
                                               :line l
                                               :specimen (handle-specimen m)
                                               :name name
                                               :kind kind
                                               :message message}))
                            line
                            matches))))
              line
              expressions))))
