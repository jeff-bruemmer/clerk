(ns proserunner.output.format-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.output.format :as format]))

(deftest format-ignored-list-test
  (testing "formats empty ignore list"
    (let [ignores {:ignore #{} :ignore-issues []}
          result (format/ignored-list ignores)]
      (is (vector? result))
      (is (some #(re-find #"No ignored" %) result))))

  (testing "formats simple ignores"
    (let [ignores {:ignore #{"word1" "word2"} :ignore-issues []}
          result (format/ignored-list ignores)]
      (is (some #(re-find #"Simple ignores" %) result))
      (is (some #(re-find #"word1" %) result))))

  (testing "formats contextual ignores"
    (let [ignores {:ignore #{} :ignore-issues [{:file "test.md" :line 10 :specimen "test"}]}
          result (format/ignored-list ignores)]
      (is (some #(re-find #"Contextual ignores" %) result))
      (is (some #(re-find #"test.md" %) result)))))

(deftest format-init-project-test
  (testing "returns vector of strings"
    (let [result (format/init-project {})]
      (is (vector? result))
      (is (every? string? result))
      (is (some #(re-find #"Created project configuration" %) result)))))

(deftest group-results-numbered-test
  (testing "groups results by file"
    ;; This is primarily an integration test - just verify it doesn't throw
    (let [results [{:file "a.md" :line-num 1 :col-num 1 :specimen "test1" :message "msg1"}
                   {:file "a.md" :line-num 2 :col-num 1 :specimen "test2" :message "msg2"}
                   {:file "b.md" :line-num 1 :col-num 1 :specimen "test3" :message "msg3"}]
          output (with-out-str (format/group-numbered results))]
      ;; Verify it produces output
      (is (string? output))
      (is (re-find #"a\.md" output))
      (is (re-find #"b\.md" output)))))
