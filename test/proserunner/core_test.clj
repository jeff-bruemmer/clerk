(ns proserunner.core-test
  "Tests for core CLI entry point and command dispatch."
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.core :as core]
            [proserunner.effects]
            [proserunner.result :as result]
            [proserunner.test-helpers :refer [silently]]))

;; Note: Many error paths in core/reception call System/exit directly
;; and cannot be easily tested until we refactor to use result/result-or-exit.
;; These tests focus on successful execution paths.

(deftest reception-version-command-test
  (testing "reception returns options for --version command"
    (silently
      (let [options (core/reception ["--version"])]
        (is (some? options))
        (is (contains? options :version))
        (is (true? (:version options)))))))

(deftest reception-help-command-test
  (testing "reception handles --help command"
    (silently
      (let [options (core/reception ["--help"])]
        (is (some? options))
        (is (true? (:help options)))))))

(deftest reception-checks-command-test
  (testing "reception handles --checks command"
    (silently
      (let [options (core/reception ["--checks"])]
        (is (some? options))
        (is (true? (:checks options)))))))

(deftest reception-with-valid-file-test
  (testing "reception processes file argument when valid"
    ;; This tests that reception doesn't exit when given valid options
    ;; We mock the effect execution to avoid actually processing files
    (with-redefs [proserunner.effects/execute-command-result
                  (fn [cmd]
                    ;; Return success for test
                    (result/ok {:command (:command cmd)}))]
      (silently
        (let [temp-file (java.io.File/createTempFile "test" ".md")]
          (try
            (spit (.getPath temp-file) "# Test file")
            (let [options (core/reception ["--file" (.getPath temp-file)])]
              (is (some? options))
              (is (= (.getPath temp-file) (:file options))))
            (finally
              (.delete temp-file))))))))

(deftest reception-returns-expanded-options-test
  (testing "reception returns expanded options with defaults"
    (with-redefs [proserunner.effects/execute-command-result
                  (fn [_] (result/ok {}))]
      (silently
        (let [options (core/reception ["--version"])]
          ;; Should have default values expanded
          (is (contains? options :code-blocks))
          (is (contains? options :quoted-text))
          (is (contains? options :output))
          (is (false? (:code-blocks options)))
          (is (false? (:quoted-text options)))
          (is (= "group" (:output options))))))))

(deftest reception-effect-execution-failure-documents-exit-test
  (testing "DOCUMENTATION: reception calls System/exit 1 when effect execution fails"
    ;; Current implementation at core.clj:86 calls System/exit 1 directly
    ;; After refactoring, this will use result/result-or-exit which can be tested
    ;; with binding [result/*exit-fn* mock-fn]
    (is true "Documented behavior - will be testable after refactoring")))

(deftest reception-validation-failure-documents-exit-test
  (testing "DOCUMENTATION: reception calls error/inferior-input (exits 0) on validation failure"
    ;; Examples of validation failures that cause System/exit 0:
    ;; - ["--parallel-files"] without --sequential-lines
    ;; - ["--global" "--project"] (both flags)
    ;; - ["--ignore-issues" "1,2,3"] without --file
    ;; - ["--ignore-all"] without --file
    ;; - ["--invalid-flag"] (unknown option)
    ;; After refactoring, these will be testable
    (is true "Documented behavior - will be testable after refactoring")))

(deftest reception-with-parallel-and-sequential-test
  (testing "reception accepts --parallel-files with --sequential-lines (valid combination)"
    (with-redefs [proserunner.effects/execute-command-result
                  (fn [_] (result/ok {}))]
      (silently
        (let [temp-file (java.io.File/createTempFile "test" ".md")]
          (try
            (spit (.getPath temp-file) "# Test")
            (let [options (core/reception ["--file" (.getPath temp-file)
                                           "--parallel-files"
                                           "--sequential-lines"])]
              (is (some? options))
              (is (true? (:parallel-files options)))
              (is (true? (:sequential-lines options))))
            (finally
              (.delete temp-file))))))))

(deftest reception-with-output-format-test
  (testing "reception accepts custom output format"
    (with-redefs [proserunner.effects/execute-command-result
                  (fn [_] (result/ok {}))]
      (silently
        (let [options (core/reception ["--output" "json" "--version"])]
          (is (some? options))
          (is (= "json" (:output options))))))))
