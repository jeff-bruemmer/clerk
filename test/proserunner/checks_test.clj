(ns proserunner.checks-test
  (:require  [proserunner.checks :as check]
             [clojure.test :as t :refer [deftest is testing]]))

(deftest path
  (is (= "/path/to/proserunner/config.edn"
         (check/path "/path/to/proserunner/" "config"))))

(deftest filter-specimens-test
  (testing "Filters specimens from existence check"
    (let [check (check/->Check "test" ["foo" "bar" "baz"] "msg" "existence" nil [] [])
          ignore-set #{"bar"}
          result (#'check/filter-specimens check ignore-set)]
      (is (= ["foo" "baz"] (:specimens result)))))

  (testing "Case-insensitive filtering"
    (let [check (check/->Check "test" ["Foo" "Bar" "baz"] "msg" "existence" nil [] [])
          ignore-set #{"bar" "FOO"}
          result (#'check/filter-specimens check ignore-set)]
      (is (= ["baz"] (:specimens result)))))

  (testing "Empty ignore set returns check unchanged"
    (let [check (check/->Check "test" ["foo" "bar"] "msg" "existence" nil [] [])]
      (is (= check (#'check/filter-specimens check #{})))))

  (testing "Nil ignore set returns check unchanged"
    (let [check (check/->Check "test" ["foo" "bar"] "msg" "existence" nil [] [])]
      (is (= check (#'check/filter-specimens check nil)))))

  (testing "Check with no specimens returns unchanged"
    (let [check (check/->Check "test" nil "msg" "regex" nil [] [])]
      (is (= check (#'check/filter-specimens check #{"foo"}))))))

(deftest filter-recommendations-test
  (testing "Filters recommendations by :avoid value"
    (let [rec1 (check/->Recommendation "good" "bad")
          rec2 (check/->Recommendation "better" "worse")
          rec3 (check/->Recommendation "best" "worst")
          check (check/->Check "test" nil nil "recommender" nil [rec1 rec2 rec3] [])
          ignore-set #{"worse"}
          result (#'check/filter-recommendations check ignore-set)]
      (is (= 2 (count (:recommendations result))))
      (is (= "bad" (:avoid (first (:recommendations result)))))
      (is (= "worst" (:avoid (second (:recommendations result)))))))

  (testing "Case-insensitive filtering for recommendations"
    (let [rec1 (check/->Recommendation "rank" "Prioritize")
          rec2 (check/->Recommendation "use" "utilize")
          check (check/->Check "test" nil nil "recommender" nil [rec1 rec2] [])
          ignore-set #{"prioritize"}
          result (#'check/filter-recommendations check ignore-set)]
      (is (= 1 (count (:recommendations result))))
      (is (= "utilize" (:avoid (first (:recommendations result)))))))

  (testing "Empty ignore set returns check unchanged"
    (let [rec (check/->Recommendation "good" "bad")
          check (check/->Check "test" nil nil "recommender" nil [rec] [])]
      (is (= check (#'check/filter-recommendations check #{})))))

  (testing "Check with no recommendations returns unchanged"
    (let [check (check/->Check "test" nil nil "existence" nil nil [])]
      (is (= check (#'check/filter-recommendations check #{"foo"}))))))
