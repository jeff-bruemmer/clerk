(ns proserunner.integration-test
  "Integration tests for project configuration workflow"
  (:require [proserunner.project-config :as project-config]
            [proserunner.config :as config]
            [proserunner.custom-checks :as custom-checks]
            [proserunner.test-helpers :refer [delete-recursively temp-dir-path silently]]
            [clojure.string :as string]
            [clojure.test :as t :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import java.io.File))

(def test-project-root (atom nil))

(defn setup-test-env [f]
  (let [temp-project (temp-dir-path "proserunner-integration-test")]
    (reset! test-project-root temp-project)
    (.mkdirs (io/file temp-project))

    (try
      (silently (f))
      (finally
        (delete-recursively (io/file temp-project))))))

(use-fixtures :each setup-test-env)

(deftest project-with-local-checks
  (testing "Project with local check directory"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          checks-dir (str proserunner-dir File/separator "checks")
          _ (.mkdirs (io/file checks-dir))

          ;; Create a simple check file
          _ (spit (str checks-dir File/separator "test-check.edn")
                  (pr-str {:name "test-existence"
                          :kind "existence"
                          :specimens ["obviously" "clearly"]
                          :message "Avoid hedge words"}))

          ;; Create manifest
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (spit manifest-path
                  (pr-str {:checks [{:directory "checks"}]
                          :ignore #{"example"}
                          :ignore-mode :replace}))

          ;; Load config
          config (project-config/load-project-config project-root)]

      ;; Verify config was loaded
      (is (some? config))
      (is (= 1 (count (:checks config))))
      (is (= "Project checks: checks" (-> config :checks first :name)))
      (is (= ["test-check"] (-> config :checks first :files)))
      (is (= #{"example"} (:ignore config)))
      (is (= :project (:source config))))))

(deftest project-with-default-checks
  (testing "Project that includes default checks"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          ;; Create manifest with default checks
          _ (spit manifest-path
                  (pr-str {:checks ["default"]
                          :ignore #{"TODO"}
                          :ignore-mode :extend}))

          ;; Load config (will work even without actual default files)
          config (project-config/load-project-config project-root)]

      ;; Verify config structure
      (is (some? config))
      (is (contains? (:ignore config) "TODO"))
      (is (= :project (:source config))))))

(deftest nested-directory-discovery
  (testing "Manifest discovery from nested directory"
    (let [project-root @test-project-root
          nested-dir (str project-root File/separator "src" File/separator "docs")
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file nested-dir))
          _ (.mkdirs (io/file proserunner-dir))

          ;; Create manifest at project root
          _ (spit manifest-path
                  (pr-str {:checks ["default"]
                          :ignore #{"TODO"}}))

          ;; Load config from nested directory
          config (project-config/load-project-config nested-dir)]

      ;; Should find manifest from parent
      (is (some? config))
      (is (contains? (:ignore config) "TODO"))
      (is (= :project (:source config))))))

(deftest project-checks-directory-with-multiple-files
  (testing "Project with multiple check files in .proserunner/checks/"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          checks-dir (str proserunner-dir File/separator "checks")
          _ (.mkdirs (io/file checks-dir))

          ;; Create multiple check files
          _ (spit (str checks-dir File/separator "existence-check.edn")
                  (pr-str {:name "existence-test"
                          :kind "existence"
                          :specimens ["foo" "bar"]
                          :message "Test message"}))
          _ (spit (str checks-dir File/separator "recommender-check.edn")
                  (pr-str {:name "recommender-test"
                          :kind "recommender"
                          :specimens {"bad" "good"}
                          :message "Use better word"}))

          ;; Create manifest using "checks" shorthand
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (spit manifest-path
                  (pr-str {:checks [{:directory "checks"}]
                          :ignore #{}
                          :config-mode :project-only}))

          ;; Load config
          config (project-config/load-project-config project-root)]

      ;; Verify config was loaded
      (is (some? config))
      (is (= 1 (count (:checks config))))
      (is (= 2 (count (-> config :checks first :files))))
      (is (= :project (:source config))))))

(deftest end-to-end-add-checks-then-list
  (testing "Full workflow: init project, add checks, then --checks should show them"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")

          ;; Step 1: Initialize project (simulating --init-project)
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path
                  (pr-str {:checks ["default"]
                          :ignore #{}
                          :ignore-mode :extend
                          :config-mode :merged}))

          ;; Step 2: Create some check files to add
          source-checks-dir (str project-root File/separator "my-custom-checks")
          _ (.mkdirs (io/file source-checks-dir))
          _ (spit (str source-checks-dir File/separator "company-terms.edn")
                  (pr-str {:name "company-terms"
                          :kind "existence"
                          :specimens ["synergy" "leverage"]
                          :message "Avoid corporate jargon"
                          :explanation "Use clearer language"}))

          ;; Step 3: Add checks using custom-checks/add-checks (simulating --add-checks)
          original-dir (System/getProperty "user.dir")
          _ (System/setProperty "user.dir" project-root)
          _ (custom-checks/add-checks source-checks-dir {:start-dir project-root})
          _ (System/setProperty "user.dir" original-dir)

          ;; Step 4: Verify .proserunner/config.edn was updated with "checks"
          updated-config (edn/read-string (slurp manifest-path))]

      (is (some #(or (= "checks" %)
                     (and (map? %) (= (:directory %) "checks")))
                (:checks updated-config))
          "config.edn should include 'checks' in :checks")

      ;; Step 5: Load config using config/fetch-or-create! (simulating --checks command)
      (let [_ (System/setProperty "user.dir" project-root)
            loaded-config (config/fetch-or-create! nil)
            _ (System/setProperty "user.dir" original-dir)]

        ;; Verify the loaded config includes project checks
        (is (some? loaded-config))
        (is (some #(string/includes? (str %) "company-terms")
                  (mapcat :files (:checks loaded-config)))
            "Loaded config should include the added custom check")))))
