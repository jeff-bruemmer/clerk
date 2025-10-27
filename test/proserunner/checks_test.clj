(ns proserunner.checks-test
  (:require  [proserunner.checks :as check]
             [clojure.test :as t :refer [deftest is]]))

(deftest path
  (is (= "/path/to/proserunner/config.edn"
         (check/path "/path/to/proserunner/" "config"))))
