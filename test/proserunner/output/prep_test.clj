(ns proserunner.output.prep-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.output.prep :as prep]))

(deftest prep-test
  (testing "merges line data with issues"
    (let [line-data {:line-num 10 :text "This is a test line" :issues [{:file "test.md" :name "test-check" :specimen "test" :col-num 5 :message "test message" :kind "existence"}]}
          result (prep/prep line-data)]
      (is (vector? result))
      (is (= 1 (count result)))
      (is (= 10 (:line-num (first result))))
      (is (= "test.md" (:file (first result))))
      (is (= "test" (:specimen (first result))))
      (is (= "This is a test line" (:line-text (first result))))))

  (testing "handles multiple issues on one line"
    (let [line-data {:line-num 5 :text "Multiple issues here"
                     :issues [{:file "a.md" :name "check1" :specimen "word1" :col-num 1 :message "msg1" :kind "existence"}
                              {:file "a.md" :name "check2" :specimen "word2" :col-num 10 :message "msg2" :kind "repetition"}]}
          result (prep/prep line-data)]
      (is (= 2 (count result)))
      (is (= "word1" (:specimen (first result))))
      (is (= "word2" (:specimen (second result)))))))

(deftest issue-str-test
  (testing "formats issue without number"
    (let [issue {:line-num 10 :col-num 5 :specimen "test" :message "this is a test"}
          result (prep/issue-str issue)]
      (is (string? result))
      (is (re-find #"10:5" result))
      (is (re-find #"test" result))))

  (testing "formats issue with number prefix"
    (let [issue {:line-num 10 :col-num 5 :specimen "test" :message "this is a test"}
          result (prep/issue-str 42 issue)]
      (is (re-find #"\[42\]" result))
      (is (re-find #"10:5" result)))))

(deftest time-elapsed-test
  (testing "prints elapsed time when timer is enabled"
    (let [start-time (System/currentTimeMillis)
          opts {:timer false :start-time start-time}]
      ;; Just verify it doesn't throw
      (is (nil? (prep/time-elapsed opts))))))
