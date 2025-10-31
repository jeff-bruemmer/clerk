(ns proserunner.vet-test
  (:require [proserunner.vet :as vet]
            [clojure.test :as t :refer [deftest is testing use-fixtures]]
            [editors.registry :as registry]
            [editors.utilities :as util]
            [editors.repetition :as repetition]
            [editors.re :as re]))

(defn setup-editors [f]
  ;; Register editors before each test
  (doseq [editor-type (util/standard-editor-types)]
    (registry/register-editor! editor-type (util/create-editor editor-type)))
  (registry/register-editor! "repetition" repetition/proofread)
  (registry/register-editor! "regex" re/proofread)
  (f))

(use-fixtures :each setup-editors)

(deftest compute
  (let [config-path (str (System/getProperty "user.home")
                         (java.io.File/separator)
                         ".proserunner"
                         (java.io.File/separator)
                         "config.edn")
        input (vet/make-input {:file "resources"
                               :config config-path
                               :output "table"
                               :code-blocks false
                               :parallel-files false
                               :parallel-lines false})
        results (vet/compute input)]
    (is (false? (empty? results)))
    ;; Check that we have results (instead of hardcoding count)
    (is (pos? (count (:results results))))
    ;; Verify structure of results
    (is (every? :file (:results results)))
    (is (every? :line-num (:results results)))
    (is (every? :issues (:results results)))))

(deftest no-cache-option
  (testing "no-cache option bypasses cache and forces recomputation"
    ;; Reset cache stats before test
    (vet/reset-cache-stats!)

    ;; First run - will compute and cache
    (let [config-path (str (System/getProperty "user.home")
                           (java.io.File/separator)
                           ".proserunner"
                           (java.io.File/separator)
                           "config.edn")
          opts {:file "resources"
                :config config-path
                :output "table"
                :code-blocks false
                :parallel-files false
                :parallel-lines false}]
      (vet/compute-or-cached opts)

      ;; Second run without no-cache - should use cache
      (vet/reset-cache-stats!)
      (vet/compute-or-cached opts)
      (let [stats-cached (vet/get-cache-stats)]
        (is (or (pos? (:hits stats-cached))
                (pos? (:partial-hits stats-cached)))
            "Should have cache hits when no-cache is not set"))

      ;; Third run WITH no-cache - should NOT use cache
      (vet/reset-cache-stats!)
      (vet/compute-or-cached (assoc opts :no-cache true))
      (let [stats-no-cache (vet/get-cache-stats)]
        (is (pos? (:misses stats-no-cache))
            "Should have cache miss when no-cache is true")
        (is (zero? (:hits stats-no-cache))
            "Should have no cache hits when no-cache is true")))))
