(ns proserunner.error-test
  "Tests for error handling utilities."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as string]
            [proserunner.error :as error]
            [proserunner.result :as result]
            [proserunner.test-helpers :refer [capture-output]]))

;; Tests for existing exit-based functions

(deftest message-prints-errors-test
  (testing "message prints errors separated by newlines"
    (let [{:keys [output]} (capture-output
                             #(error/message ["Error 1" "Error 2" "Error 3"]))]
      (is (string/includes? output "Error 1"))
      (is (string/includes? output "Error 2"))
      (is (string/includes? output "Error 3")))))

;; Note: We don't test exit and inferior-input directly because they call System/exit
;; which terminates the test process. Instead, we test the Result-returning versions.

(deftest message-result-returns-failure-test
  (testing "message-result returns Failure with errors"
    (let [result (error/message-result ["Error 1" "Error 2"])]
      (is (result/failure? result))
      (is (string/includes? (:error result) "Error 1"))
      (is (string/includes? (:error result) "Error 2")))))

(deftest message-result-includes-context-test
  (testing "message-result includes error details in context"
    (let [errors ["Error 1" "Error 2"]
          result (error/message-result errors)]
      (is (result/failure? result))
      (is (= errors (:errors (:context result)))))))

(deftest inferior-input-result-returns-failure-test
  (testing "inferior-input-result returns Failure instead of exiting"
    (let [result (error/inferior-input-result ["Bad input"])]
      (is (result/failure? result))
      (is (string/includes? (:error result) "Invalid input")))))

(deftest inferior-input-result-includes-errors-in-context-test
  (testing "inferior-input-result includes all errors in context"
    (let [errors ["Error 1" "Error 2" "Error 3"]
          result (error/inferior-input-result errors)]
      (is (result/failure? result))
      (is (= errors (:errors (:context result)))))))

(deftest exit-result-returns-failure-test
  (testing "exit-result returns Failure with error message"
    (let [result (error/exit-result)]
      (is (result/failure? result))
      (is (string/includes? (:error result) "Proserunner needs to lie down for a  bit"))))

  (testing "exit-result with custom message returns Failure"
    (let [result (error/exit-result "custom error message")]
      (is (result/failure? result))
      ;; Note: sentence-dress capitalizes and adds period
      (is (string/includes? (:error result) "Custom error message")))))

(deftest exit-result-preserves-error-context-test
  (testing "exit-result can include context"
    (let [result (error/exit-result "Error" {:code 404 :source "test"})]
      (is (result/failure? result))
      (is (= 404 (:code (:context result))))
      (is (= "test" (:source (:context result)))))))
