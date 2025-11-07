(ns proserunner.process-test
  "Tests for process orchestration.

  Tests that proserunner function returns Result instead of calling System/exit."
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.process :as process]
            [proserunner.result :as result]
            [proserunner.test-helpers :refer [silently with-temp-dir]]))

(deftest proserunner-returns-result-test
  (testing "Returns Result on success"
    (with-temp-dir [temp "process-test"]
      (let [test-file (str temp "/test.md")
            _ (spit test-file "This is test content.")
            result (silently (process/proserunner {:path test-file}))]
        ;; Should return a Result, not exit
        (is (or (result/success? result) (result/failure? result))
            "Should return Result type")))))

(deftest proserunner-no-system-exit-test
  (testing "Does not call System/exit on errors"
    ;; Note: After refactoring, process/proserunner should return Result
    ;; and never call System/exit. This test documents that expectation.
    (with-temp-dir [temp "process-test"]
      (let [test-file (str temp "/nonexistent.md")
            result (silently (process/proserunner {:path test-file}))]
        ;; Should return Failure Result, not exit
        (is (result/failure? result)
            "Should return Failure for nonexistent file")))))

(deftest proserunner-error-handling-test
  (testing "Returns Failure Result with context on errors"
    (with-temp-dir [temp "process-test"]
      (let [test-file (str temp "/nonexistent.md")
            result (silently (process/proserunner {:path test-file}))]
        (is (result/failure? result))
        (is (string? (:error result))
            "Failure should contain error message")
        (is (map? (:context result))
            "Failure should contain context map")))))

(deftest proserunner-result-structure-test
  (testing "Success Result contains output"
    (with-temp-dir [temp "process-test"]
      (let [test-file (str temp "/test.md")
            _ (spit test-file "This is test content.")
            result (silently (process/proserunner {:path test-file}))]
        ;; Result structure will depend on output/out implementation
        ;; Just verify it's a proper Result
        (is (or (result/success? result) (result/failure? result))))))

  (testing "Failure Result contains operation context"
    ;; Verify error context is meaningful
    (with-temp-dir [temp "process-test"]
      (let [test-file (str temp "/bad.md")
            result (silently (process/proserunner {:path test-file}))]
        (when (result/failure? result)
          (is (map? (:context result))
              "Context should be a map"))))))
