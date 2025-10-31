(ns editors.case-recommender-test
  (:require [editors.utilities :as util]
            [proserunner.text :as text]
            [proserunner.checks :as checks]
            [clojure.test :as t :refer [deftest is]]))

(def error-line (text/->Line
                 "resources"
                 "We love monday."
                 42
                 false
                 false
                 false
                 []))

(def error-line-double (text/->Line
                        "resources"
                        "Week starts on monday, not sunday."
                        42
                        false
                        false
                        false
                        []))

(def handsome-line (text/->Line
                    "resources"
                    "This sentence looks handsome."
                    42
                    false
                    false
                    false
                    []))

(def case-sensitive-check (checks/map->Check {:name "Case sensitive check."
                                              :kind "case-recommender"
                                              :explanation "explanation"
                                              :recommendations [{:avoid "monday" :prefer "Monday"}
                                                                {:avoid "sunday", :prefer "Sunday"}]}))

(deftest recommender
  (let [proofread (util/create-editor "case-recommender")]
    (is (true? (:issue? (proofread error-line case-sensitive-check))))
    (is (false? (:issue? (proofread handsome-line case-sensitive-check))))
    (is (= 2 (count (:issues (proofread error-line-double case-sensitive-check)))))))

