(ns editors.case-recommender-test
  (:require [editors.case-recommender :as cr]
            [clerk.text :as text]
            [clerk.checks :as checks]
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
  (is (true? (:issue? (cr/proofread error-line case-sensitive-check))))
  (is (false? (:issue? (cr/proofread handsome-line case-sensitive-check))))
  (is (= 2 (count (:issues (cr/proofread error-line-double case-sensitive-check))))))

