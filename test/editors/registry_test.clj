(ns editors.registry-test
  "Tests for dynamic editor registry system."
  (:require [clojure.test :as t :refer [deftest is testing]]
            [proserunner.text :as text]
            [editors.registry :as registry]))

;; Test setup/teardown helpers
(defn reset-registry! []
  (reset! registry/editors {}))

;; Mock editor functions for testing
(defn mock-existence-editor
  [line check]
  (assoc line :issue? true :issues [{:kind "existence" :name (:name check)}]))

(defn mock-custom-editor
  [line check]
  (assoc line :issue? true :issues [{:kind "custom" :name (:name check)}]))

(deftest test-register-and-retrieve-editor
  (testing "Can register and retrieve an editor"
    (reset-registry!)
    (registry/register-editor! "existence" mock-existence-editor)
    (is (= mock-existence-editor (registry/get-editor "existence"))
        "Should retrieve registered editor")))

(deftest test-register-multiple-editors
  (testing "Can register multiple editors"
    (reset-registry!)
    (registry/register-editor! "existence" mock-existence-editor)
    (registry/register-editor! "custom" mock-custom-editor)
    (is (= 2 (count @registry/editors))
        "Should have two registered editors")
    (is (= mock-existence-editor (registry/get-editor "existence")))
    (is (= mock-custom-editor (registry/get-editor "custom")))))

(deftest test-unregister-editor
  (testing "Can manually remove an editor from registry"
    (reset-registry!)
    (registry/register-editor! "existence" mock-existence-editor)
    (swap! registry/editors dissoc "existence")
    (is (nil? (registry/get-editor "existence"))
        "Should not find removed editor")))

(deftest test-dispatch-to-registered-editor
  (testing "Dispatch works with registered editor"
    (reset-registry!)
    (registry/register-editor! "existence" mock-existence-editor)
    (let [line (text/->Line "test.txt" "Test line" 1 false false false [])
          check {:kind "existence" :name "test-check"}
          result (registry/dispatch line check)]
      (is (:issue? result)
          "Should mark line as having issue")
      (is (= "existence" (-> result :issues first :kind))
          "Should have correct issue kind"))))

(deftest test-dispatch-to-custom-editor
  (testing "Dispatch works with custom editor"
    (reset-registry!)
    (registry/register-editor! "custom" mock-custom-editor)
    (let [line (text/->Line "test.txt" "Test line" 1 false false false [])
          check {:kind "custom" :name "my-custom-check"}
          result (registry/dispatch line check)]
      (is (:issue? result)
          "Should mark line as having issue")
      (is (= "custom" (-> result :issues first :kind))
          "Should have correct issue kind"))))

(deftest test-dispatch-unknown-editor-throws
  (testing "Dispatch throws for unknown editor type"
    (reset-registry!)
    (let [line (text/->Line "test.txt" "Test line" 1 false false false [])
          check {:kind "nonexistent" :name "test"}]
      (is (thrown? clojure.lang.ExceptionInfo
                   (registry/dispatch line check))
          "Should throw exception for unknown editor"))))

(deftest test-dispatch-error-includes-available-editors
  (testing "Error message includes available editor types"
    (reset-registry!)
    (registry/register-editor! "existence" mock-existence-editor)
    (registry/register-editor! "custom" mock-custom-editor)
    (let [line (text/->Line "test.txt" "Test line" 1 false false false [])
          check {:kind "nonexistent" :name "test"}]
      (try
        (registry/dispatch line check)
        (is false "Should have thrown exception")
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (is (= "nonexistent" (:kind data))
                "Should include requested kind")
            (is (contains? (set (:available data)) "existence")
                "Should list available editors")
            (is (contains? (set (:available data)) "custom")
                "Should list all available editors")))))))

(deftest test-override-existing-editor
  (testing "Can override an existing editor"
    (reset-registry!)
    (registry/register-editor! "existence" mock-existence-editor)
    (let [new-editor (fn [line _] (assoc line :custom-field true))]
      (registry/register-editor! "existence" new-editor)
      (is (= new-editor (registry/get-editor "existence"))
          "Should use new editor after override"))))

(deftest test-registry-thread-safety
  (testing "Registry is thread-safe"
    (reset-registry!)
    (let [threads (doall
                   (for [i (range 10)]
                     (Thread.
                      (fn []
                        (registry/register-editor! (str "editor-" i)
                                                   (fn [line _] line))))))]
      (doseq [t threads] (.start t))
      (doseq [t threads] (.join t))
      (is (= 10 (count @registry/editors))
          "All editors should be registered despite concurrent access"))))

(deftest test-backward-compatibility
  (testing "Registry can support all existing editor types"
    (reset-registry!)
    (let [standard-types ["existence" "case" "recommender"
                          "case-recommender" "repetition" "regex"]]
      (doseq [kind standard-types]
        (registry/register-editor! kind (fn [line _] line)))
      (is (= (count standard-types) (count @registry/editors))
          "Should register all standard types")
      (doseq [kind standard-types]
        (is (some? (registry/get-editor kind))
            (str "Should find editor for " kind))))))
