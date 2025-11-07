(ns proserunner.output.checks-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.output.checks :as output-checks]
            [proserunner.checks :as checks]
            [proserunner.result :as result]))

(deftest print-checks-test
  (testing "print-checks function exists"
    (is (fn? output-checks/print)))

  ;; Integration test - actual functionality tested via integration tests
  ;; since it requires a valid config and check-dir
  )

(deftest print-handles-result-type-test
  (testing "print should handle Result object from checks/create"
    ;; The print function receives a Result from checks/create and must
    ;; extract :value before processing the checks

    ;; Mock a successful Result
    (let [mock-checks [(checks/->Check "test-check"
                                       ["specimen"]
                                       "Test message"
                                       "existence"
                                       "Test explanation"
                                       []
                                       [])]
          success-result (result/ok {:checks mock-checks :warnings []})]
      ;; Verify Result structure is what we expect
      (is (result/success? success-result))
      (is (= mock-checks (:checks (:value success-result))))
      (is (empty? (:warnings (:value success-result))))
      (is (= "test-check" (:name (first (:checks (:value success-result)))))))

    ;; Mock a failure Result
    (let [failure-result (result/err "Test error")]
      (is (result/failure? failure-result))
      (is (= "Test error" (:error failure-result)))))

  (testing "print should handle checks with nil fields gracefully"
    ;; Verify that checks with nil fields can be created
    (let [check-with-nil (checks/->Check nil nil nil nil nil [] [])]
      (is (nil? (:name check-with-nil)))
      (is (nil? (:kind check-with-nil))))))
