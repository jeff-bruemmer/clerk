(ns editors.existence-test
  (:require [proserunner
             [checks :as checks]
             [text :as text]]
            [clojure.test :as t :refer [deftest is]]
            [editors.utilities :as util]))

(def error-line (text/->Line
                 "resources"
                 "There is hopefully something wrong with this sentence."
                 42
                 false
                 false
                 false
                 []))

(def error-line-double (text/->Line
                        "resources"
                        "There is hopefully something deceptively wrong with this sentence."
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

(def skunked-term (checks/map->Check {:name "Skunked term"
                                      :specimens ["hopefully" "deceptively"]
                                      :message "Skunked term"
                                      :kind "existence"
                                      :explanation "explanation"
                                      :recommendations []}))

(deftest existence
  (let [proofread (util/create-editor "existence")]
    (is (true? (:issue? (proofread error-line skunked-term))))
    (is (false? (:issue? (proofread handsome-line skunked-term))))
    (is (= 2 (count (:issues (proofread error-line-double skunked-term)))))))

