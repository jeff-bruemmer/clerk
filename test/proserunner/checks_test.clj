(ns proserunner.checks-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [proserunner.checks :as check]
            [proserunner.result :as result]
            [proserunner.test-helpers :refer [with-temp-dir silently]])
  (:import java.io.File))

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

;;; Error handling tests

(deftest load-edn-file-not-found
  (testing "load-edn! returns Result Failure for missing check file"
    (let [result (check/load-edn! "/nonexistent/check.edn")]
      (is (result/failure? result)
          "Should return Failure for missing file")
      (is (string? (:error result))
          "Error should be a string")
      (is (contains? (:context result) :filepath)
          "Context should include filepath"))))

(deftest load-edn-parse-error
  (testing "load-edn! returns Result Failure for invalid EDN"
    (with-temp-dir [temp-dir "checks-test"]
      (let [check-file (str temp-dir File/separator "bad.edn")
            _ (spit check-file "{:invalid :edn [")  ; Malformed EDN
            result (check/load-edn! check-file)]

        (is (result/failure? result)
            "Should return Failure for invalid EDN")
        (is (string? (:error result))
            "Error should be a string")
        (is (or (re-find #"parse" (str (:error result)))
                (re-find #"EOF" (str (:error result))))
            "Error message should mention parsing issue")))))

(deftest load-edn-validation-error
  (testing "load-edn! returns Result Failure for invalid check definition"
    (with-temp-dir [temp-dir "checks-test"]
      (let [check-file (str temp-dir File/separator "invalid-check.edn")
            ;; Missing required field 'kind'
            _ (spit check-file "{:name \"test\" :specimens [\"foo\"]}")
            result (check/load-edn! check-file)]

        (is (result/failure? result)
            "Should return Failure for invalid check definition")
        (is (string? (:error result))
            "Error should be a string")))))

(deftest load-edn-success
  (testing "load-edn! returns Success with Check for valid file"
    (with-temp-dir [temp-dir "checks-test"]
      (let [check-file (str temp-dir File/separator "valid-check.edn")
            check-def {:name "test-check"
                       :kind "existence"
                       :specimens ["foo" "bar"]
                       :message "Test message"}
            _ (spit check-file (pr-str check-def))
            result (check/load-edn! check-file)]

        (is (result/success? result)
            "Should return Success for valid check file")
        (is (instance? proserunner.checks.Check (:value result))
            "Success value should be a Check record")
        (is (= "test-check" (:name (:value result)))
            "Check should have correct name")))))

(deftest create-checks-success
  (testing "create returns checks when all files valid"
    (with-temp-dir [temp-dir "checks-test"]
      (let [check1 (str temp-dir File/separator "check1.edn")
            check2 (str temp-dir File/separator "check2.edn")
            _ (spit check1 (pr-str {:name "c1" :kind "existence" :specimens ["x"] :message "m1"}))
            _ (spit check2 (pr-str {:name "c2" :kind "existence" :specimens ["y"] :message "m2"}))
            options {:config {:checks [{:directory temp-dir :files ["check1" "check2"]}]}
                     :check-dir temp-dir
                     :project-ignore #{}}
            result (check/create options)]

        (is (result/success? result)
            "Should return Success when all checks load")
        (is (= 2 (count (:checks (:value result))))
            "Should return all valid checks")
        (is (empty? (:warnings (:value result)))
            "Should have no warnings when all checks load successfully")))))

(deftest create-checks-partial-failure
  (testing "create succeeds with warnings when some files fail"
    (with-temp-dir [temp-dir "checks-test"]
      (let [good-check (str temp-dir File/separator "good.edn")
            bad-check (str temp-dir File/separator "bad.edn")
            _ (spit good-check (pr-str {:name "good" :kind "existence" :specimens ["x"] :message "m"}))
            _ (spit bad-check "{:invalid}")  ; Invalid EDN
            options {:config {:checks [{:directory temp-dir :files ["good" "bad"]}]}
                     :check-dir temp-dir
                     :project-ignore #{}}
            result (check/create options)]

        (is (result/success? result)
            "Should return Success even when some checks fail to load")
        (is (= 1 (count (:checks (:value result))))
            "Should return only valid checks")
        (is (= "good" (:name (first (:checks (:value result)))))
            "Should load the valid check")
        (is (= 1 (count (:warnings (:value result))))
            "Should have one warning for the failed check")
        (let [warning (first (:warnings (:value result)))]
          (is (contains? warning :path)
              "Warning should contain :path")
          (is (contains? warning :error)
              "Warning should contain :error"))))))

(deftest create-checks-all-fail
  (testing "create returns Failure when no checks load"
    (with-temp-dir [temp-dir "checks-test"]
      (let [bad-check (str temp-dir File/separator "bad.edn")
            _ (spit bad-check "{:invalid}")
            options {:config {:checks [{:directory temp-dir :files ["bad"]}]}
                     :check-dir temp-dir
                     :project-ignore #{}}
            result (check/create options)]

        (is (result/failure? result)
            "Should return Failure when no checks load successfully")
        (is (string? (:error result))
            "Error should be a string")
        (is (re-find #"No valid checks" (:error result))
            "Error should mention no valid checks")))))

(deftest create-checks-file-not-found
  (testing "create handles missing check files gracefully"
    (with-temp-dir [temp-dir "checks-test"]
      (let [options {:config {:checks [{:directory temp-dir :files ["nonexistent"]}]}
                     :check-dir temp-dir
                     :project-ignore #{}}
            result (check/create options)]

        (is (result/failure? result)
            "Should return Failure when check files don't exist")
        (is (string? (:error result))
            "Error should be a string")))))

(deftest create-checks-with-ignore-filter
  (testing "create applies ignore filter to loaded checks"
    (with-temp-dir [temp-dir "checks-test"]
      (let [check-file (str temp-dir File/separator "test.edn")
            _ (spit check-file (pr-str {:name "test"
                                       :kind "existence"
                                       :specimens ["foo" "bar" "baz"]
                                       :message "m"}))
            options {:config {:checks [{:directory temp-dir :files ["test"]}]}
                     :check-dir temp-dir
                     :project-ignore #{"bar"}}  ; Ignore "bar"
            result (check/create options)]

        (is (result/success? result))
        (is (= 1 (count (:checks (:value result)))))
        (let [loaded-check (first (:checks (:value result)))]
          (is (= ["foo" "baz"] (:specimens loaded-check))
              "Should filter out ignored specimen"))))))
