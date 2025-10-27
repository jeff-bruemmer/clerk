(ns proserunner.vet-test
  (:require [proserunner.vet :as vet]
            [clojure.test :as t :refer [deftest is]]))

(def config-path (str (System/getProperty "user.home") (java.io.File/separator) ".proserunner" (java.io.File/separator) "config.edn"))

(def input (vet/make-input {:file "resources"
                            :config config-path
                            :output "table"
                            :code-blocks false}))

(def results (vet/compute input))

(deftest compute
  (is (false? (empty? results)))
  ;; Check that we have results (instead of hardcoding count)
  (is (pos? (count (:results results))))
  ;; Verify structure of results
  (is (every? :file (:results results)))
  (is (every? :line-num (:results results)))
  (is (every? :issues (:results results))))
