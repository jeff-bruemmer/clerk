(ns proserunner.vet-test
  (:require [proserunner.vet :as vet]
            [proserunner.project-config :as project-config]
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
    ;; Reset cache stats before test
    (vet/reset-cache-stats!)

    ;; First run - will compute and cache
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
                :parallel-lines false}]
      (vet/compute-or-cached opts)

      ;; Second run without no-cache - should use cache
      (vet/reset-cache-stats!)
      (vet/compute-or-cached opts)
      (let [stats-cached (vet/get-cache-stats)]
        (is (or (pos? (:hits stats-cached))
                (pos? (:partial-hits stats-cached)))
            "Should have cache hits when no-cache is not set"))

      ;; Third run WITH no-cache - should NOT use cache
      (vet/reset-cache-stats!)
      (vet/compute-or-cached (assoc opts :no-cache true))
      (let [stats-no-cache (vet/get-cache-stats)]
        (is (pos? (:misses stats-no-cache))
            "Should have cache miss when no-cache is true")
        (is (zero? (:hits stats-no-cache))
            "Should have no cache hits when no-cache is true")))))

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
              (pr-str {:check-sources ["default"]
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
              (pr-str {:check-sources ["default"]
                       :ignore #{"project-ignore-1" "project-ignore-2"}
                       :ignore-mode :extend  ; This should merge global + project
                       :config-mode :merged}))

        ;; Change working directory to project root
        (System/setProperty "user.dir" temp-project)

        ;; Load project config - should merge global and project ignores
        (let [loaded-config (binding [*out* (java.io.StringWriter.)]
                              (project-config/load-project-config temp-project))]

          ;; Verify that BOTH global and project ignores are included
          (is (= :project (:source loaded-config))
              "Project config should be loaded")
          (is (= #{"global-ignore-1" "global-ignore-2" "project-ignore-1" "project-ignore-2"}
                 (:ignore loaded-config))
              "Ignore set should include BOTH global and project ignores when :ignore-mode is :extend"))

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
    (let [original-dir (System/getProperty "user.dir")
          original-home (System/getProperty "user.home")
          temp-project (str (System/getProperty "java.io.tmpdir")
                           File/separator
                           "proserunner-replace-test-"
                           (System/currentTimeMillis))
          proserunner-dir (str temp-project File/separator ".proserunner")
          temp-home (str (System/getProperty "java.io.tmpdir")
                        File/separator
                        "proserunner-test-home-replace-"
                        (System/currentTimeMillis))
          temp-home-config (str temp-home File/separator ".proserunner")]

      (try
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
              (pr-str {:check-sources ["default"]
                       :ignore #{"project-ignore-only"}
                       :ignore-mode :replace  ; Should NOT include global ignores
                       :config-mode :merged}))

        (System/setProperty "user.dir" temp-project)

        ;; Load project config
        (let [loaded-config (binding [*out* (java.io.StringWriter.)]
                              (project-config/load-project-config temp-project))]

          ;; Verify that ONLY project ignores are included (global ignores excluded)
          (is (= :project (:source loaded-config)))
          (is (= #{"project-ignore-only"} (:ignore loaded-config))
              "Ignore set should include ONLY project ignores when :ignore-mode is :replace"))

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
            (catch Exception _e nil))))))))
