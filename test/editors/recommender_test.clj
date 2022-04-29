(ns editors.recommender-test
  (:require [editors.recommender :as r]
            [clerk.text :as text]
            [clerk.checks :as checks]
            [clojure.test :as t :refer [deftest is]]))

(def error-line (text/->Line
                 "resources"
                 "This sentence is ironical."
                 42
                 false
                 []))

(def error-line-double (text/->Line
                        "resources"
                        "This sentence is ironical and extensible."
                        42
                        false
                        []))

(def handsome-line (text/->Line
                    "resources"
                    "This sentence looks handsome."
                    42
                    false
                    []))

(def needless-variant (checks/map->Check {:name "Needless variant."
                                          :kind "recommender"
                                          :explanation "explanation"
                                          :recommendations [{:avoid "ironical" :prefer "ironic"}
                                                            {:avoid "extensible", :prefer "extendable"}]}))

(deftest recommender
  (is (true? (:issue? (r/proofread error-line needless-variant))))
  (is (false? (:issue? (r/proofread handsome-line needless-variant))))
  (is (= 2 (count (:issues (r/proofread error-line-double needless-variant))))))

