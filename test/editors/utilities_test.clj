(ns editors.utilities-test
  "Tests for editor utilities including LRU cache."
  (:require [editors.utilities :as util]
            [proserunner.text :as text]
            [clojure.test :as t :refer [deftest is testing]]))

(deftest test-make-pattern
  (testing "Pattern creation with case sensitivity"
    (let [pattern (util/make-pattern "test|example" false)]
      (is (instance? java.util.regex.Pattern pattern)))

    (let [pattern (util/make-pattern "test|example" true)]
      (is (instance? java.util.regex.Pattern pattern)))))

(deftest test-seek
  (testing "Finding specimens in text"
    (let [text "This is a test of the test function."
          matches (util/seek text "test" false)]
      (is (vector? matches))
      (is (= 2 (count matches)))
      ;; Matches include boundary characters (space before word)
      (is (every? #(re-find #"test" %) matches))))

  (testing "Case-insensitive matching"
    (let [text "Test this TEST and test."
          matches (util/seek text "test" false)]
      ;; Should find at least 2 instances
      (is (>= (count matches) 2))))

  (testing "Case-sensitive matching"
    (let [text "Test this test."
          matches (util/seek text "test" true)]
      (is (= 1 (count matches)))))

  (testing "No matches returns empty vector"
    (let [text "This has no specimens."
          matches (util/seek text "hopefully" false)]
      (is (empty? matches)))))

(deftest test-add-issue
  (testing "Adding issue to line"
    (let [line (text/map->Line {:file "test.md"
                                :text "This is hopefully a test."
                                :line-num 1
                                :code? false
                                :quoted? false
                                :issue? false
                                :issues []})
          result (util/add-issue {:line line
                                  :specimen "hopefully"
                                  :name "test-check"
                                  :kind "existence"
                                  :message "Test message"})]
      (is (:issue? result))
      (is (= 1 (count (:issues result))))
      (is (= "hopefully" (:specimen (first (:issues result)))))
      (is (number? (:col-num (first (:issues result)))))))

  (testing "No issue added when specimen not in text"
    (let [line (text/map->Line {:file "test.md"
                                :text "This is a test."
                                :line-num 1
                                :code? false
                                :quoted? false
                                :issue? false
                                :issues []})
          result (util/add-issue {:line line
                                  :specimen "hopefully"
                                  :name "test-check"
                                  :kind "existence"
                                  :message "Test message"})]
      (is (false? (:issue? result)))
      (is (empty? (:issues result))))))

(deftest test-create-issue-collector
  (testing "Issue collector finds specimens"
    (let [collector (util/create-issue-collector false)
          line (text/map->Line {:file "test.md"
                                :text "This is hopefully a test."
                                :line-num 1
                                :code? false
                                :quoted? false
                                :issue? false
                                :issues []})
          check {:file "test.md"
                 :kind "existence"
                 :specimens ["hopefully" "test"]
                 :message "Test message"
                 :name "test-check"}
          result (collector line check)]
      (is (:issue? result))
      (is (pos? (count (:issues result))))))

  (testing "Issue collector with empty specimens"
    (let [collector (util/create-issue-collector false)
          line (text/map->Line {:file "test.md"
                                :text "This is a test."
                                :line-num 1
                                :code? false
                                :quoted? false
                                :issue? false
                                :issues []})
          check {:file "test.md"
                 :kind "existence"
                 :specimens []
                 :message "Test message"
                 :name "test-check"}
          result (collector line check)]
      (is (false? (:issue? result)))
      (is (empty? (:issues result))))))

(deftest test-create-recommender
  (testing "Recommender finds and suggests replacements"
    (let [recommender (util/create-recommender false)
          line (text/map->Line {:file "test.md"
                                :text "I would utilize this tool."
                                :line-num 1
                                :code? false
                                :quoted? false
                                :issue? false
                                :issues []})
          check {:name "test-recommender"
                 :kind "recommender"
                 :recommendations [{:avoid "utilize"
                                    :prefer "use"}]}
          result (recommender line check)]
      (is (:issue? result))
      (is (pos? (count (:issues result))))
      (let [issue (first (:issues result))]
        ;; Specimen may include boundary characters
        (is (re-find #"utilize" (:specimen issue)))
        (is (re-find #"Prefer: use" (:message issue))))))

  (testing "Recommender with no matches"
    (let [recommender (util/create-recommender false)
          line (text/map->Line {:file "test.md"
                                :text "This is fine."
                                :line-num 1
                                :code? false
                                :quoted? false
                                :issue? false
                                :issues []})
          check {:name "test-recommender"
                 :kind "recommender"
                 :recommendations [{:avoid "utilize"
                                    :prefer "use"}]}
          result (recommender line check)]
      (is (false? (:issue? result)))
      (is (empty? (:issues result))))))
