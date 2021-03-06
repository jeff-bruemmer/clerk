(ns editors.repetition-test
  (:require [clerk
             [checks :as checks]
             [text :as text]]
            [clojure.test :as t :refer [deftest is]]
            [editors.repetition :as r]))

(def error-line (text/->Line
                 "There is is something wrong with this this sentence."
                 42
                 false
                 []))

(def handsome-line (text/->Line
                    "This sentence looks handsome."
                    42
                    false
                    []))

(def repetition (checks/map->Check {:name "Consecutive repeat words"
                                    :specimens []
                                    :message "Consecutive repeat words"
                                    :kind "repetition"
                                    :explanation "explanation"
                                    :recommendations []}))

(deftest existence
  (is (true? (:issue? (r/proofread error-line repetition))))
  (is (false? (:issue? (r/proofread handsome-line repetition)))))
