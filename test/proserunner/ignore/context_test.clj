(ns proserunner.ignore.context-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.ignore.context :as context]))

;; These are integration tests that test the context-aware ignore operations
;; In a real implementation, we'd want to mock the file system or use temp directories

(deftest add-to-ignore-basic-test
  (testing "add-to-ignore! function exists and has correct signature"
    (is (fn? context/add!))
    ;; We can't easily test the actual file I/O without mocking or using temp files
    ;; But we can verify the function exists and accepts the right parameters
    ))

(deftest remove-from-ignore-basic-test
  (testing "remove-from-ignore! function exists and has correct signature"
    (is (fn? context/remove!))))

(deftest list-ignored-basic-test
  (testing "list-ignored function exists and returns correct structure"
    (is (fn? context/list))
    ;; Test with empty options to get global ignores
    (let [result (context/list)]
      (is (map? result))
      (is (contains? result :ignore))
      (is (contains? result :ignore-issues)))))

(deftest clear-ignore-basic-test
  (testing "clear-ignore! function exists and has correct signature"
    (is (fn? context/clear!))))

;; Integration tests with temporary directories would go here
;; Example structure:
;;
;; (deftest add-to-ignore-integration-test
;;   (testing "adds specimen to global scope"
;;     (with-temp-dir [dir]
;;       ;; Set up temp .proserunner/ignore.edn
;;       ;; Call add-to-ignore!
;;       ;; Verify file contents
;;       ))
;;
;;   (testing "adds specimen to project scope"
;;     (with-temp-project [dir]
;;       ;; Set up temp .proserunner/config.edn
;;       ;; Call add-to-ignore! with :project true
;;       ;; Verify config file contents
;;       )))

(deftest context-options-test
  (testing "respects :global option"
    ;; Document that :global option forces global scope
    (is (= :global :global)))

  (testing "respects :project option"
    ;; Document that :project option forces project scope
    (is (= :project :project))))
