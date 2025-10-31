(ns editors.recommender-test
  (:require [editors.utilities :as util]
            [proserunner.text :as text]
            [proserunner.checks :as checks]
            [clojure.test :as t :refer [deftest is]]))

(def error-line (text/->Line
                 "resources"
                 "This sentence is ironical."
                 42
                 false
                 false
                 false
                 []))

(def error-line-double (text/->Line
                        "resources"
                        "This sentence is ironical and extensible."
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

(def needless-variant (checks/map->Check {:name "Needless variant."
                                          :kind "recommender"
                                          :explanation "explanation"
                                          :recommendations [{:avoid "ironical" :prefer "ironic"}
                                                            {:avoid "extensible", :prefer "extendable"}]}))

(deftest recommender
  (let [proofread (util/create-editor "recommender")]
    (is (true? (:issue? (proofread error-line needless-variant))))
    (is (false? (:issue? (proofread handsome-line needless-variant))))
    (is (= 2 (count (:issues (proofread error-line-double needless-variant)))))))

