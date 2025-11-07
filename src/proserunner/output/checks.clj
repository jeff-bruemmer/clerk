(ns proserunner.output.checks
  "Check listing and printing."
  (:refer-clojure :exclude [print])
  (:gen-class)
  (:require [clojure.string :as string]
            [proserunner.checks :as checks]
            [proserunner.config :as conf]
            [proserunner.fmt :as fmt]
            [proserunner.output.format :as format]
            [proserunner.result :as result]
            [proserunner.system :as sys]))

(set! *warn-on-reflection* true)

(defn print
  "Prints a table of the enabled checks: names, kind, and description."
  [config]
  (println "Enabled checks:")
  (let [config-data (conf/fetch-or-create! config)
        check-dir (sys/check-dir config)
        checks-result (checks/create {:config config-data :check-dir check-dir})]
    (if (result/success? checks-result)
      (let [{:keys [checks warnings]} (:value checks-result)]
        (->> checks
             (map (fn [{:keys [name kind explanation]}]
                    {:name (string/capitalize name)
                     :kind (string/capitalize kind)
                     :explanation (fmt/sentence-dress explanation)}))
             (sort-by :name)
             (map (format/make-key-printer {:name "Name" :kind "Kind" :explanation "Explanation"}))
             (format/print-table))
        (when (seq warnings)
          (let [failed-count (count warnings)]
            (println (str "\nWarning: " failed-count " check(s) failed to load and will be skipped."))
            (doseq [{:keys [path error]} warnings]
              (println (str "  - " path ": " error))))))
      (println "Error loading checks:" (:error checks-result)))))
