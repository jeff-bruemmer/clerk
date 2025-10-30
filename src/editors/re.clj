(ns editors.re
  (:require
   [editors.utilities :as util])
  (:gen-class))

;; Pattern compilation without caching for optimal parallel performance.
;; Direct re-pattern compilation is faster than cache overhead under parallel load.

(defn safe-re-pattern
  "Safely compile a regex pattern, returning nil if invalid."
  [re]
  (try
    (re-pattern re)
    (catch java.util.regex.PatternSyntaxException e
      (println (str "Warning: Invalid regex pattern '" re "': " (.getMessage e)))
      nil)))

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
                      pattern (safe-re-pattern re)]
                  (if (nil? pattern)
                    ;; Skip this expression if pattern is invalid
                    line
                    (let [matches (re-seq pattern text)]
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
                                matches))))))
              line
              expressions))))
