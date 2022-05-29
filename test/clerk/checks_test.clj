(ns clerk.checks-test
  (:require  [clerk.checks :as check]
             [clojure.test :as t :refer [deftest is]]))

(deftest path
  (is (= "/path/to/clerk/config.edn"
         (check/path "/path/to/clerk/" "config"))))
