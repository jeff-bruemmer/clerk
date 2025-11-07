(ns proserunner.scope-test
  "Tests for scope resolution (project vs global)."
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.scope :as scope]))

(deftest determine-target-keyword-test
  (testing "Returns :project when :project opt is true"
    (is (= :project (scope/determine-target-keyword {:project true}))))

  (testing "Returns :global when :project opt is false"
    (is (= :global (scope/determine-target-keyword {:project false}))))

  (testing "Returns :global when :project opt is missing"
    (is (= :global (scope/determine-target-keyword {})))))

(deftest get-target-info-test
  (testing "Returns map with :target and :msg-context for project"
    (let [result (scope/get-target-info {:project true})]
      (is (= :project (:target result)))
      (is (= "project" (:msg-context result)))))

  (testing "Returns map with :target and :msg-context for global"
    (let [result (scope/get-target-info {:project false})]
      (is (= :global (:target result)))
      (is (= "global" (:msg-context result)))))

  (testing "Returns global when opts is empty"
    (let [result (scope/get-target-info {})]
      (is (= :global (:target result)))
      (is (= "global" (:msg-context result))))))
