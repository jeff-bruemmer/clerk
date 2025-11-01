(ns proserunner.vet-test
  (:require [proserunner.vet :as vet]
            [proserunner.project-config :as project-config]
            [proserunner.storage :as storage]
            [clojure.test :as t :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [editors.registry :as registry]
            [editors.utilities :as util]
            [editors.repetition :as repetition]
            [editors.re :as re])
  (:import java.io.File))

(defn setup-editors [f]
  ;; Register editors before each test
  (doseq [editor-type (util/standard-editor-types)]
    (registry/register-editor! editor-type (util/create-editor editor-type)))
  (registry/register-editor! "repetition" repetition/proofread)
  (registry/register-editor! "regex" re/proofread)
  (f))

(use-fixtures :each setup-editors)

(deftest compute
  (let [config-path (str (System/getProperty "user.home")
                         java.io.File/separator
                         ".proserunner"
                         java.io.File/separator
                         "config.edn")
        input (vet/make-input {:file "resources"
                               :config config-path
                               :output "table"
                               :code-blocks false
                               :parallel-files false
                               :parallel-lines false})
        results (vet/compute input)]
    (is (false? (empty? results)))
    ;; Check that we have results (instead of hardcoding count)
    (is (pos? (count (:results results))))
    ;; Verify structure of results
    (is (every? :file (:results results)))
    (is (every? :line-num (:results results)))
    (is (every? :issues (:results results)))))

(deftest no-cache-option
  (testing "no-cache option bypasses cache and forces recomputation"
    (let [config-path (str (System/getProperty "user.home")
                           java.io.File/separator
                           ".proserunner"
                           java.io.File/separator
                           "config.edn")
          opts {:file "resources"
                :config config-path
                :output "table"
                :code-blocks false
                :parallel-files false
                :parallel-lines false}
          file "resources"]

      ;; First run - will compute and cache
      (vet/compute-or-cached opts)
      ;; Verify cache file exists after first run
      (is (not (false? (storage/inventory file)))
          "Cache should be created after first run")

      ;; Second run without no-cache - should use cache
      (let [cached-data (storage/inventory file)
            result2 (vet/compute-or-cached opts)]
        (is (not (false? cached-data))
            "Cache should exist for second run")
        ;; Results should be consistent between cached and fresh runs
        (is (= (count (:results (:results result2)))
               (count (:results cached-data)))
            "Cached results should match fresh results"))

      ;; Third run WITH no-cache - results should still be correct but recomputed
      (let [result3 (vet/compute-or-cached (assoc opts :no-cache true))]
        (is (pos? (count (:results (:results result3))))
            "No-cache run should still produce results")))))

(deftest project-config-loaded-from-working-directory
  (testing "Project config is loaded from current working directory, not file directory"
    ;; This regression test ensures that when processing files in subdirectories,
    ;; the project config (.proserunner/config.edn) is loaded from the current
    ;; working directory (project root), not from the file's parent directory.
    ;; Bug fixed: vet.clj was passing file-path to load-project-config instead of user.dir
    (let [original-dir (System/getProperty "user.dir")
          original-home (System/getProperty "user.home")
          ;; Create temp project structure
          temp-project (str (System/getProperty "java.io.tmpdir")
                           File/separator
                           "proserunner-regression-test-"
                           (System/currentTimeMillis))
          proserunner-dir (str temp-project File/separator ".proserunner")
          temp-home (str (System/getProperty "java.io.tmpdir")
                        File/separator
                        "proserunner-test-home-"
                        (System/currentTimeMillis))
          temp-home-config (str temp-home File/separator ".proserunner")]

      (try
        ;; Set up temp home with global config
        (System/setProperty "user.home" temp-home)
        (.mkdirs (io/file temp-home-config "default"))
        (.mkdirs (io/file temp-home-config "custom"))
        (spit (str temp-home-config File/separator "config.edn")
              (pr-str {:checks [{:name "default" :directory "default" :files []}]}))
        (spit (str temp-home-config File/separator "ignore.edn")
              (pr-str #{}))

        ;; Create project structure with .git directory (marks project boundary)
        (.mkdirs (io/file temp-project ".git"))
        (.mkdirs (io/file proserunner-dir "checks"))

        ;; Create project config with specific ignore set
        (spit (str proserunner-dir File/separator "config.edn")
              (pr-str {:checks ["default"]
                       :ignore #{"test-ignore-specimen" "another-ignored-word"}
                       :ignore-mode :extend
                       :config-mode :merged}))

        ;; Change working directory to project root
        (System/setProperty "user.dir" temp-project)

        ;; Load project config - should use user.dir (temp-project), not a file path
        (let [loaded-config (binding [*out* (java.io.StringWriter.)]
                              (project-config/load-project-config temp-project))]

          ;; Verify that project config was found and ignore set was loaded
          (is (= :project (:source loaded-config))
              "Project config should be loaded (not falling back to global)")
          (is (= #{"test-ignore-specimen" "another-ignored-word"} (:ignore loaded-config))
              "Project ignore set should be loaded from .proserunner/config.edn in project root"))

        (finally
          ;; Restore original directories
          (System/setProperty "user.dir" original-dir)
          (System/setProperty "user.home" original-home)
          ;; Clean up temp directories
          (try
            (when (.exists (io/file temp-project))
              (doseq [f (reverse (file-seq (io/file temp-project)))]
                (.delete f)))
            (when (.exists (io/file temp-home))
              (doseq [f (reverse (file-seq (io/file temp-home)))]
                (.delete f)))
            (catch Exception _e
              ;; Ignore cleanup errors
              nil)))))))

(defn- verify-project-config
  "Helper to verify project config ignore settings."
  [temp-project expected-source expected-ignore msg]
  (let [loaded-config (binding [*out* (java.io.StringWriter.)]
                        (project-config/load-project-config temp-project))]
    (is (= expected-source (:source loaded-config)))
    (is (= expected-ignore (:ignore loaded-config)) msg)))

(defn- with-temp-project-env
  "Execute test-fn with temporary project and home directories set up."
  [test-name-suffix test-fn]
  (let [original-dir (System/getProperty "user.dir")
        original-home (System/getProperty "user.home")
        temp-project (str (System/getProperty "java.io.tmpdir")
                         File/separator
                         "proserunner-" test-name-suffix "-"
                         (System/currentTimeMillis))
        proserunner-dir (str temp-project File/separator ".proserunner")
        temp-home (str (System/getProperty "java.io.tmpdir")
                      File/separator
                      "proserunner-test-home-" test-name-suffix "-"
                      (System/currentTimeMillis))
        temp-home-config (str temp-home File/separator ".proserunner")]
    (try
      (test-fn temp-project proserunner-dir temp-home temp-home-config)
      (finally
        (System/setProperty "user.dir" original-dir)
        (System/setProperty "user.home" original-home)
        (try
          (when (.exists (io/file temp-project))
            (doseq [f (reverse (file-seq (io/file temp-project)))]
              (.delete f)))
          (when (.exists (io/file temp-home))
            (doseq [f (reverse (file-seq (io/file temp-home)))]
              (.delete f)))
          (catch Exception _e nil))))))

(deftest global-and-project-ignores-are-both-used
  (testing "Both global and project ignores are used when ignore-mode is :extend"
    ;; This test verifies that specimens in BOTH the global ignore list
    ;; and the project ignore list are filtered out when :ignore-mode is :extend
    (let [original-dir (System/getProperty "user.dir")
          original-home (System/getProperty "user.home")
          ;; Create temp project structure
          temp-project (str (System/getProperty "java.io.tmpdir")
                           File/separator
                           "proserunner-global-test-"
                           (System/currentTimeMillis))
          proserunner-dir (str temp-project File/separator ".proserunner")
          temp-home (str (System/getProperty "java.io.tmpdir")
                        File/separator
                        "proserunner-test-home-global-"
                        (System/currentTimeMillis))
          temp-home-config (str temp-home File/separator ".proserunner")]

      (try
        ;; Set up temp home with global config and global ignores
        (System/setProperty "user.home" temp-home)
        (.mkdirs (io/file temp-home-config "default"))
        (.mkdirs (io/file temp-home-config "custom"))
        (spit (str temp-home-config File/separator "config.edn")
              (pr-str {:checks [{:name "default" :directory "default" :files []}]}))
        ;; IMPORTANT: Add global ignores
        (spit (str temp-home-config File/separator "ignore.edn")
              (pr-str #{"global-ignore-1" "global-ignore-2"}))

        ;; Create project structure with .git directory
        (.mkdirs (io/file temp-project ".git"))
        (.mkdirs (io/file proserunner-dir "checks"))

        ;; Create project config with project ignores and :ignore-mode :extend
        (spit (str proserunner-dir File/separator "config.edn")
              (pr-str {:checks ["default"]
                       :ignore #{"project-ignore-1" "project-ignore-2"}
                       :ignore-mode :extend  ; This should merge global + project
                       :config-mode :merged}))

        ;; Change working directory to project root
        (System/setProperty "user.dir" temp-project)

        ;; Load project config - should merge global and project ignores
        (verify-project-config temp-project
                              :project
                              #{"global-ignore-1" "global-ignore-2" "project-ignore-1" "project-ignore-2"}
                              "Ignore set should include BOTH global and project ignores when :ignore-mode is :extend")

        (finally
          ;; Restore original directories
          (System/setProperty "user.dir" original-dir)
          (System/setProperty "user.home" original-home)
          ;; Clean up temp directories
          (try
            (when (.exists (io/file temp-project))
              (doseq [f (reverse (file-seq (io/file temp-project)))]
                (.delete f)))
            (when (.exists (io/file temp-home))
              (doseq [f (reverse (file-seq (io/file temp-home)))]
                (.delete f)))
            (catch Exception _e
              ;; Ignore cleanup errors
              nil))))))

(deftest project-ignores-replace-global-when-replace-mode
  (testing "Only project ignores are used when ignore-mode is :replace"
    ;; This test verifies that when :ignore-mode is :replace,
    ;; only the project ignores are used (global ignores are ignored)
    (with-temp-project-env "replace-test"
      (fn [temp-project proserunner-dir temp-home temp-home-config]
        ;; Set up temp home with global ignores
        (System/setProperty "user.home" temp-home)
        (.mkdirs (io/file temp-home-config "default"))
        (.mkdirs (io/file temp-home-config "custom"))
        (spit (str temp-home-config File/separator "config.edn")
              (pr-str {:checks [{:name "default" :directory "default" :files []}]}))
        (spit (str temp-home-config File/separator "ignore.edn")
              (pr-str #{"global-ignore-1" "global-ignore-2"}))

        ;; Create project with :ignore-mode :replace
        (.mkdirs (io/file temp-project ".git"))
        (.mkdirs (io/file proserunner-dir "checks"))
        (spit (str proserunner-dir File/separator "config.edn")
              (pr-str {:checks ["default"]
                       :ignore #{"project-ignore-only"}
                       :ignore-mode :replace  ; Should NOT include global ignores
                       :config-mode :merged}))

        (System/setProperty "user.dir" temp-project)

        ;; Load project config and verify ONLY project ignores are included
        (verify-project-config temp-project
                              :project
                              #{"project-ignore-only"}
                              "Ignore set should include ONLY project ignores when :ignore-mode is :replace"))))))

(deftest skip-ignore-flag-skips-all-ignores
  (testing "--skip-ignore flag bypasses both global and project ignores"
    (let [original-dir (System/getProperty "user.dir")
          original-home (System/getProperty "user.home")
          temp-project (str (System/getProperty "java.io.tmpdir")
                           File/separator
                           "proserunner-skip-ignore-test-"
                           (System/currentTimeMillis))
          proserunner-dir (str temp-project File/separator ".proserunner")
          temp-home (str (System/getProperty "java.io.tmpdir")
                        File/separator
                        "proserunner-test-home-skip-"
                        (System/currentTimeMillis))
          temp-home-config (str temp-home File/separator ".proserunner")]

      (try
        ;; Set up temp home with global ignores and a dummy check file
        (System/setProperty "user.home" temp-home)
        (.mkdirs (io/file temp-home-config "default"))
        (spit (str temp-home-config File/separator "config.edn")
              (pr-str {:checks [{:name "default" :directory "default" :files ["dummy"]}]}))
        (spit (str temp-home-config File/separator "ignore.edn")
              (pr-str #{"global-ignore"}))
        ;; Create a dummy check file
        (spit (str temp-home-config File/separator "default" File/separator "dummy.edn")
              (pr-str {:name "dummy-check"
                       :kind "existence"
                       :message "Test message"
                       :explanation "Test explanation"
                       :specimens ["test-specimen"]}))

        ;; Create project with project ignores
        (.mkdirs (io/file temp-project ".git"))
        (.mkdirs (io/file proserunner-dir "checks"))
        (spit (str proserunner-dir File/separator "config.edn")
              (pr-str {:checks ["default"]
                       :ignore #{"project-ignore"}
                       :ignore-mode :extend
                       :config-mode :merged}))

        (System/setProperty "user.dir" temp-project)

        ;; Test with skip-ignore flag
        (let [input-with-skip (vet/make-input {:file "resources"
                                               :config (str temp-home-config File/separator "config.edn")
                                               :skip-ignore true})]
          (is (= #{} (:project-ignore input-with-skip))
              "--skip-ignore should result in empty ignore set"))

        ;; Test without skip-ignore flag (should use merged ignores)
        (let [input-without-skip (vet/make-input {:file "resources"
                                                  :config (str temp-home-config File/separator "config.edn")
                                                  :skip-ignore false})]
          (is (= #{"global-ignore" "project-ignore"} (:project-ignore input-without-skip))
              "Without --skip-ignore, both global and project ignores should be used"))

        (finally
          (System/setProperty "user.dir" original-dir)
          (System/setProperty "user.home" original-home)
          (try
            (when (.exists (io/file temp-project))
              (doseq [f (reverse (file-seq (io/file temp-project)))]
                (.delete f)))
            (when (.exists (io/file temp-home))
              (doseq [f (reverse (file-seq (io/file temp-home)))]
                (.delete f)))
            (catch Exception _e nil)))))))
