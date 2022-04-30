(ns clerk.vet-test
  (:require [clerk.vet :as vet]
            [clojure.test :as t :refer [deftest is]]))

(def config-path (str (System/getProperty "user.home") (java.io.File/separator) ".clerk" (java.io.File/separator) "config.edn"))

(def input (vet/make-input {:file "resources"
                            :config config-path
                            :output "table"
                            :code-blocks false}))

(def results (vet/compute input))

(deftest compute
  (is (false? (empty? results)))
  (is (= 14 (count (:results results)))))
