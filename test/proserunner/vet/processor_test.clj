(ns proserunner.vet.processor-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [proserunner.vet.processor :as processor]
            [proserunner.checks :as checks]
            [proserunner.text :as text]
            [proserunner.test-helpers :refer [silently]]
            [editors.registry :as registry]
            [editors.utilities :as util]
            [editors.repetition :as repetition]
            [editors.re :as re]))

(defn setup-editors [f]
  (doseq [editor-type (util/standard-editor-types)]
    (registry/register-editor! editor-type (util/create-editor editor-type)))
  (registry/register-editor! "repetition" repetition/proofread)
  (registry/register-editor! "regex" re/proofread)
  (f))

(use-fixtures :each setup-editors)

(deftest process-checks-sequential
  (testing "Processes checks sequentially when parallel? is false"
    (let [lines [(text/->Line "test.txt" "very unique" 1 false false false [])]
          check (checks/map->Check {:name "test" :kind "existence" :specimens ["unique"] :message "Avoid"})
          results (processor/process [check] lines false)]
      (is (seq? results))
      (is (= 1 (count results)))
      (is (:issue? (first results))))))

(deftest process-checks-parallel
  (testing "Processes checks in parallel when parallel? is true"
    (let [lines [(text/->Line "test.txt" "very unique" 1 false false false [])
                 (text/->Line "test.txt" "another unique line" 2 false false false [])]
          check (checks/map->Check {:name "test" :kind "existence" :specimens ["unique"] :message "Avoid"})
          results (processor/process [check] lines true)]
      (is (= 2 (count results)))
      (is (every? :issue? results)))))

(deftest process-filters-issues-only
  (testing "Only returns lines with issues"
    (let [lines [(text/->Line "test.txt" "clean line" 1 false false false [])
                 (text/->Line "test.txt" "has unique word" 2 false false false [])
                 (text/->Line "test.txt" "another clean line" 3 false false false [])]
          check (checks/map->Check {:name "test" :kind "existence" :specimens ["unique"] :message "Avoid"})
          results (processor/process [check] lines false)]
      (is (= 1 (count results)))
      (is (= "has unique word" (:text (first results)))))))

(deftest process-multiple-checks
  (testing "Applies multiple checks to each line"
    (let [lines [(text/->Line "test.txt" "very unique and really special" 1 false false false [])]
          chks [(checks/map->Check {:name "test1" :kind "existence" :specimens ["unique"] :message "Avoid unique"})
                (checks/map->Check {:name "test2" :kind "existence" :specimens ["special"] :message "Avoid special"})]
          results (processor/process chks lines false)]
      (is (= 1 (count results)))
      (is (:issue? (first results)))
      (is (= 2 (count (:issues (first results))))))))

(deftest dispatch-to-correct-editor
  (testing "Dispatches to correct editor based on check kind"
    (let [line (text/->Line "test.txt" "very unique" 1 false false false [])
          check (checks/map->Check {:name "test" :kind "existence" :specimens ["unique"] :message "Avoid"})
          result (processor/dispatch line check)]
      (is (map? result))
      (is (:issue? result))
      (is (seq (:issues result))))))

(deftest safe-dispatch-catches-errors
  (testing "safe-dispatch catches errors and returns line unchanged"
    (let [line (text/->Line "test.txt" "test" 1 false false false [])
          bad-check (checks/map->Check {:name "bad" :kind "nonexistent-editor" :specimens ["test"]})
          result (silently (processor/safe-dispatch line bad-check))]
      (is (= line result)))))

(deftest process-empty-lines
  (testing "Handles empty line list"
    (let [lines []
          check (checks/map->Check {:name "test" :kind "existence" :specimens ["unique"] :message "Avoid"})
          results (processor/process [check] lines false)]
      (is (empty? results)))))

(deftest process-empty-checks
  (testing "Handles empty checks list"
    (let [lines [(text/->Line "test.txt" "some text" 1 false false false [])]
          chks []
          results (processor/process chks lines false)]
      (is (empty? results)))))
