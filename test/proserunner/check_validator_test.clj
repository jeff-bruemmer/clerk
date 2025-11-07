(ns proserunner.check-validator-test
  (:require [clojure.test :refer :all]
            [proserunner.check-validator :as validator]))

(deftest test-validate-has-fields-success
  (testing "all required fields present"
    (let [result (validator/validate-has-fields
                   #{:name :kind}
                   {:name "test" :kind "existence"})]
      (is (:valid? result))))

  (testing "extra fields don't cause failure"
    (let [result (validator/validate-has-fields
                   #{:name}
                   {:name "test" :kind "existence" :extra "field"})]
      (is (:valid? result)))))

(deftest test-validate-has-fields-failure
  (testing "missing one field"
    (let [result (validator/validate-has-fields
                   #{:name :kind}
                   {:kind "existence"})]
      (is (false? (:valid? result)))
      (is (contains? (:missing result) :name))
      (is (re-find #":name" (:error result)))))

  (testing "missing multiple fields"
    (let [result (validator/validate-has-fields
                   #{:name :kind :message}
                   {})]
      (is (false? (:valid? result)))
      (is (= 3 (count (:missing result))))
      (is (string? (:error result))))))

(deftest test-validate-kind-valid
  (testing "valid existence kind"
    (is (:valid? (validator/validate-kind {:kind "existence"}))))

  (testing "valid case kind"
    (is (:valid? (validator/validate-kind {:kind "case"}))))

  (testing "valid recommender kind"
    (is (:valid? (validator/validate-kind {:kind "recommender"}))))

  (testing "valid case-recommender kind"
    (is (:valid? (validator/validate-kind {:kind "case-recommender"}))))

  (testing "valid repetition kind"
    (is (:valid? (validator/validate-kind {:kind "repetition"}))))

  (testing "valid regex kind"
    (is (:valid? (validator/validate-kind {:kind "regex"})))))

(deftest test-validate-kind-invalid
  (testing "invalid kind fails"
    (let [result (validator/validate-kind {:kind "invalid-type"})]
      (is (false? (:valid? result)))
      (is (re-find #"Invalid kind" (:error result)))
      (is (= "invalid-type" (:invalid-kind result)))))

  (testing "error mentions valid kinds"
    (let [result (validator/validate-kind {:kind "bad"})]
      (is (re-find #"existence" (:error result)))
      (is (re-find #"regex" (:error result))))))

(deftest test-validate-specimens-type-existence
  (testing "existence with vector is valid"
    (is (:valid? (validator/validate-specimens-type
                   {:kind "existence" :specimens ["foo" "bar"]}))))

  (testing "existence with list is valid"
    (is (:valid? (validator/validate-specimens-type
                   {:kind "existence" :specimens '("foo" "bar")}))))

  (testing "existence with map fails"
    (let [result (validator/validate-specimens-type
                   {:kind "existence" :specimens {"foo" "bar"}})]
      (is (false? (:valid? result)))
      (is (re-find #"vector" (:error result))))))

(deftest test-validate-specimens-type-recommender
  (testing "recommender with map is valid"
    (is (:valid? (validator/validate-specimens-type
                   {:kind "recommender" :specimens {"bad" "good"}}))))

  (testing "recommender with vector fails"
    (let [result (validator/validate-specimens-type
                   {:kind "recommender" :specimens ["bad" "good"]})]
      (is (false? (:valid? result)))
      (is (re-find #"map" (:error result)))))

  (testing "case-recommender follows same rules"
    (is (:valid? (validator/validate-specimens-type
                   {:kind "case-recommender" :specimens {"Bad" "Good"}})))
    (let [result (validator/validate-specimens-type
                   {:kind "case-recommender" :specimens ["Bad"]})]
      (is (false? (:valid? result))))))

(deftest test-validate-specimens-type-no-specimens
  (testing "missing specimens field passes type check"
    (is (:valid? (validator/validate-specimens-type
                   {:kind "existence"})))))

(deftest test-validate-check-complete-valid
  (testing "complete valid existence check"
    (is (:valid? (validator/validate-check
                   {:name "test-check"
                    :kind "existence"
                    :specimens ["foo" "bar"]
                    :message "Test message"}))))

  (testing "complete valid recommender check"
    (is (:valid? (validator/validate-check
                   {:name "test-rec"
                    :kind "recommender"
                    :specimens {"bad" "good"}}))))

  (testing "complete valid regex check"
    (is (:valid? (validator/validate-check
                   {:name "test-regex"
                    :kind "regex"
                    :pattern "\\d+"
                    :message "Numbers found"}))))

  (testing "minimal repetition check"
    (is (:valid? (validator/validate-check
                   {:name "test-rep"
                    :kind "repetition"})))))

(deftest test-validate-check-missing-base-fields
  (testing "missing name"
    (let [result (validator/validate-check
                   {:kind "existence" :specimens ["foo"] :message "msg"})]
      (is (false? (:valid? result)))
      (is (contains? (:missing result) :name))))

  (testing "missing kind"
    (let [result (validator/validate-check
                   {:name "test" :specimens ["foo"] :message "msg"})]
      (is (false? (:valid? result)))
      (is (contains? (:missing result) :kind)))))

(deftest test-validate-check-invalid-kind
  (testing "invalid kind fails early"
    (let [result (validator/validate-check
                   {:name "test" :kind "invalid" :specimens ["foo"]})]
      (is (false? (:valid? result)))
      (is (re-find #"Invalid kind" (:error result))))))

(deftest test-validate-check-missing-kind-specific
  (testing "existence missing specimens"
    (let [result (validator/validate-check
                   {:name "test" :kind "existence" :message "msg"})]
      (is (false? (:valid? result)))
      (is (contains? (:missing result) :specimens))))

  (testing "existence missing message"
    (let [result (validator/validate-check
                   {:name "test" :kind "existence" :specimens ["foo"]})]
      (is (false? (:valid? result)))
      (is (contains? (:missing result) :message))))

  (testing "regex missing pattern"
    (let [result (validator/validate-check
                   {:name "test" :kind "regex" :message "msg"})]
      (is (false? (:valid? result)))
      (is (contains? (:missing result) :pattern))))

  (testing "regex missing message"
    (let [result (validator/validate-check
                   {:name "test" :kind "regex" :pattern "\\d+"})]
      (is (false? (:valid? result)))
      (is (contains? (:missing result) :message)))))

(deftest test-validate-check-wrong-specimens-type
  (testing "recommender with vector specimens"
    (let [result (validator/validate-check
                   {:name "test" :kind "recommender" :specimens ["bad"]})]
      (is (false? (:valid? result)))
      (is (re-find #"map" (:error result)))))

  (testing "existence with map specimens"
    (let [result (validator/validate-check
                   {:name "test" :kind "existence" :specimens {"bad" "good"} :message "msg"})]
      (is (false? (:valid? result)))
      (is (re-find #"vector" (:error result))))))

(deftest test-validate-checks-collection
  (testing "all valid checks"
    (let [checks [{:name "c1" :kind "existence" :specimens ["foo"] :message "m1"}
                  {:name "c2" :kind "regex" :pattern "\\d+" :message "m2"}
                  {:name "c3" :kind "repetition"}]
          result (validator/validate-checks checks)]
      (is (:valid? result))))

  (testing "some invalid checks"
    (let [checks [{:kind "existence"}  ; missing name
                  {:name "c2" :kind "invalid"}  ; invalid kind
                  {:name "c3" :kind "regex"}]  ; missing pattern and message
          result (validator/validate-checks checks)]
      (is (false? (:valid? result)))
      (is (= 3 (:count result)))
      (is (= 3 (count (:errors result))))))

  (testing "empty checks list is valid"
    (is (:valid? (validator/validate-checks [])))))

(deftest test-check-name-fallback
  (testing "uses check name when present"
    (is (= "my-check" (validator/check-name {:name "my-check"}))))

  (testing "fallback for unnamed check"
    (is (= "unnamed-check" (validator/check-name {})))))
