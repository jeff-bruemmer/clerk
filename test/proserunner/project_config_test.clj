(ns proserunner.project-config-test
  (:require [proserunner.project-config :as project-config]
            [proserunner.config.manifest :as manifest]
            [proserunner.config.merger :as merger]
            [proserunner.test-helpers :refer [delete-recursively temp-dir-path silently]]
            [clojure.test :as t :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn])
  (:import java.io.File))

(def original-home (atom nil))
(def test-home (atom nil))
(def test-project-root (atom nil))

(defn setup-test-env [f]
  ;; Save original home and working directory
  (reset! original-home (System/getProperty "user.home"))
  (let [original-dir (System/getProperty "user.dir")
        ;; Create temporary home directory
        temp-home (temp-dir-path "proserunner-test-home")
        proserunner-dir (str temp-home File/separator ".proserunner")
        temp-project (temp-dir-path "proserunner-test-project")]
    (reset! test-home temp-home)
    (reset! test-project-root temp-project)

    ;; Set user.home to temp directory
    (System/setProperty "user.home" temp-home)

    ;; Create test directory structures
    (.mkdirs (io/file proserunner-dir "custom"))
    (.mkdirs (io/file proserunner-dir "default"))
    (.mkdirs (io/file temp-project))

    ;; Create a mock global config.edn
    (spit (str proserunner-dir File/separator "config.edn")
          (pr-str {:checks [{:name "default"
                            :directory "default"
                            :files ["cliches" "redundancies"]}]}))

    ;; Create a mock global ignore.edn with new format
    (spit (str proserunner-dir File/separator "ignore.edn")
          (pr-str {:ignore #{"global-ignore-1" "global-ignore-2"}
                   :ignore-issues []}))

    ;; Run the test with suppressed output
    (try
      (silently (f))
      (finally
        ;; Restore original home and directory
        (System/setProperty "user.home" @original-home)
        (System/setProperty "user.dir" original-dir)

        ;; Clean up temp directories
        (delete-recursively (io/file temp-home))
        (delete-recursively (io/file temp-project))))))

(use-fixtures :each setup-test-env)

;;; Manifest Discovery Tests

(deftest find-manifest-in-current-directory
  (testing "Finding .proserunner/config.edn in the current directory"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path "{:checks [\"default\"]}")
          found (manifest/find project-root)]
      (is (some? found))
      (is (= manifest-path (:manifest-path found)))
      (is (= project-root (:project-root found))))))

(deftest find-manifest-in-parent-directory
  (testing "Walking up directory tree to find .proserunner/config.edn"
    (let [project-root @test-project-root
          sub-dir (str project-root File/separator "src" File/separator "nested")
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file sub-dir))
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path "{:checks [\"default\"]}")
          found (manifest/find sub-dir)]
      (is (some? found))
      (is (= manifest-path (:manifest-path found)))
      (is (= project-root (:project-root found))))))

(deftest find-manifest-not-found
  (testing "Returns nil when no .proserunner/config.edn exists"
    (let [project-root @test-project-root
          found (manifest/find project-root)]
      (is (nil? found)))))

(deftest find-manifest-stops-at-git-root
  (testing "Stops searching at .git directory boundary"
    (let [project-root @test-project-root
          git-dir (str project-root File/separator ".git")
          sub-dir (str project-root File/separator "src")
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file git-dir))
          _ (.mkdirs (io/file sub-dir))
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path "{:checks [\"default\"]}")
          found (manifest/find sub-dir)]
      (is (some? found))
      (is (= manifest-path (:manifest-path found)))
      (is (= project-root (:project-root found))))))

;;; Manifest Parsing Tests

(deftest parse-minimal-manifest
  (testing "Parsing a minimal valid manifest"
    (let [manifest {:checks ["default"]}
          parsed (manifest/parse manifest)]
      (is (= ["default"] (:checks parsed)))
      (is (= :extend (:ignore-mode parsed))) ; default
      (is (= :merged (:config-mode parsed))) ; default
      (is (empty? (:ignore parsed))))))

(deftest parse-full-manifest
  (testing "Parsing a complete manifest with all fields"
    (let [manifest {:checks ["default" "./local" "/absolute/path"]
                    :ignore #{"TODO" "FIXME"}
                    :ignore-mode :replace
                    :config-mode :project-only}
          parsed (manifest/parse manifest)]
      (is (= 3 (count (:checks parsed))))
      (is (= #{"TODO" "FIXME"} (:ignore parsed)))
      (is (= :replace (:ignore-mode parsed)))
      (is (= :project-only (:config-mode parsed))))))

(deftest parse-manifest-with-defaults
  (testing "Manifest parsing applies correct defaults"
    (let [manifest {:checks ["default"]
                    :ignore #{"TODO"}}
          parsed (manifest/parse manifest)]
      (is (= :extend (:ignore-mode parsed)))
      (is (= :merged (:config-mode parsed))))))

(deftest parse-manifest-validates-checks
  (testing "Validation fails when :checks is missing"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"checks.*required"
         (manifest/parse {}))))

  (testing "Validation fails when :checks is not a vector"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"checks.*vector"
         (manifest/parse {:checks "default"}))))

  (testing "Validation fails when :checks is empty"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"checks.*empty"
         (manifest/parse {:checks []})))))

(deftest parse-manifest-validates-ignore-mode
  (testing "Validation fails for invalid :ignore-mode"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"ignore-mode.*:extend.*:replace"
         (manifest/parse {:checks ["default"]
                                         :ignore-mode :invalid})))))

(deftest parse-manifest-validates-config-mode
  (testing "Validation fails for invalid :config-mode"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"config-mode.*:merged.*:project-only"
         (manifest/parse {:checks ["default"]
                                         :config-mode :wrong})))))

(deftest parse-manifest-validates-ignore-is-set
  (testing "Validation fails when :ignore is not a set"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"ignore.*set"
         (manifest/parse {:checks ["default"]
                                         :ignore ["TODO" "FIXME"]})))))

(deftest parse-manifest-multiple-errors
  (testing "Collects and reports all validation errors"
    (let [bad-manifest {:checks nil          ; Error 1
                        :ignore "not-a-set"         ; Error 2
                        :ignore-mode :invalid}]     ; Error 3
      ;; Should throw with a message containing multiple errors
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Validation errors"
           (manifest/parse bad-manifest)))

      ;; Verify the ex-data contains all 3 errors
      (try
        (manifest/parse bad-manifest)
        (catch Exception e
          (let [error-data (ex-data e)]
            (is (= 3 (count (:errors error-data)))
                "Should collect all 3 validation errors")
            (is (some #(re-find #":checks is required" %)
                      (:errors error-data))
                "Should include checks error")
            (is (some #(re-find #":ignore must be a set" %)
                      (:errors error-data))
                "Should include ignore error")
            (is (some #(re-find #":ignore-mode must be one of" %)
                      (:errors error-data))
                "Should include ignore-mode error")))))))

;;; Config Merging Tests

(deftest merge-configs-extend-mode
  (testing "Merging in :extend mode combines ignore sets"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{"project-1" "project-2"}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= #{"global-1" "global-2" "project-1" "project-2"}
             (:ignore merged))))))

(deftest merge-configs-replace-mode
  (testing "Merging in :replace mode uses only project ignore"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{"project-1" "project-2"}
                   :ignore-mode :replace}
          merged (merger/merge-configs global project)]
      (is (= #{"project-1" "project-2"}
             (:ignore merged))))))

(deftest merge-configs-project-only-mode
  (testing "Config mode :project-only ignores global config entirely"
    (let [global {:ignore #{"global-1"}
                  :checks [{:name "global-checks"}]}
          project {:ignore #{"project-1"}
                   :config-mode :project-only
                   :checks ["default"]}
          merged (merger/merge-configs global project)]
      (is (= #{"project-1"} (:ignore merged)))
      (is (= ["default"] (:checks merged))))))

(deftest merge-configs-merged-mode
  (testing "Config mode :merged combines global and project"
    (let [global {:ignore #{"global-1"}
                  :checks [{:name "global-checks"
                           :directory "default"
                           :files ["check1"]}]}
          project {:ignore #{"project-1"}
                   :config-mode :merged
                   :ignore-mode :extend
                   :checks ["default"]}
          merged (merger/merge-configs global project)]
      (is (= #{"global-1" "project-1"} (:ignore merged)))
      (is (= ["default"] (:checks merged)))
      ;; In merged mode, :checks is preserved (not :checks)
      ;; Global :checks will be merged later in build-project-config
      (is (= :merged (:config-mode merged))))))

(deftest merge-configs-empty-project-ignore
  (testing "Empty project ignore in extend mode preserves global"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= #{"global-1" "global-2"} (:ignore merged))))))

(deftest merge-configs-no-global-ignore
  (testing "Handles missing global ignore gracefully"
    (let [global {}
          project {:ignore #{"project-1"}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= #{"project-1"} (:ignore merged))))))

;;; Integration Tests

(deftest load-project-config-full-workflow
  (testing "Complete workflow: find, parse, and merge project config"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path
                  (pr-str {:checks ["default"]
                          :ignore #{"TODO"}
                          :ignore-mode :extend
                          :config-mode :merged}))
          config (project-config/load project-root)]
      (is (some? config))
      ;; After resolution, checks becomes :checks
      (is (some? (:checks config)))
      ;; Should have both global and project ignores
      (is (contains? (:ignore config) "TODO"))
      (is (contains? (:ignore config) "global-ignore-1"))
      (is (= :project (:source config))))))

(deftest load-project-config-no-manifest
  (testing "Returns global config when no project manifest exists"
    (let [config (project-config/load @test-project-root)]
      ;; Should return global config
      (is (some? (:checks config)))
      (is (= #{"global-ignore-1" "global-ignore-2"} (:ignore config)))
      (is (= :global (:source config))))))

(deftest load-project-config-project-only-mode
  (testing "Project-only mode ignores global configuration"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path
                  (pr-str {:checks ["default"]
                          :ignore #{"TODO"}
                          :config-mode :project-only}))
          config (project-config/load project-root)]
      (is (some? config))
      (is (= #{"TODO"} (:ignore config)))
      (is (not (contains? (:ignore config) "global-ignore-1")))
      (is (= :project (:source config))))))

;;; Project Initialization Tests

(deftest init-project-config-creates-manifest
  (testing "Initializing project config creates .proserunner/ directory structure"
    (let [project-root @test-project-root
          manifest-path (project-config/init! project-root)
          proserunner-dir (str project-root File/separator ".proserunner")
          checks-dir (str proserunner-dir File/separator "checks")]
      (is (some? manifest-path))
      (is (.exists (io/file manifest-path)))
      (is (= (str proserunner-dir File/separator "config.edn") manifest-path))
      (is (.exists (io/file proserunner-dir)))
      (is (.isDirectory (io/file proserunner-dir)))
      (is (.exists (io/file checks-dir)))
      (is (.isDirectory (io/file checks-dir))))))

(deftest init-project-config-default-template
  (testing "Default template has correct structure"
    (let [project-root @test-project-root
          manifest-path (project-config/init! project-root)
          content (slurp manifest-path)
          manifest (edn/read-string content)]
      (is (= ["default"] (:checks manifest)))
      (is (= #{} (:ignore manifest)))
      (is (= :extend (:ignore-mode manifest)))
      (is (= :merged (:config-mode manifest))))))

(deftest init-project-config-fails-if-exists
  (testing "Initialization fails if manifest already exists"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit (str proserunner-dir File/separator "config.edn")
                  (pr-str {:checks ["default"]}))]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"already exists"
           (project-config/init! project-root))))))

(deftest init-project-config-custom-template
  (testing "Can initialize with custom template"
    (let [project-root @test-project-root
          custom-template {:checks ["./custom-checks"]
                          :ignore #{"TODO" "FIXME"}
                          :ignore-mode :replace
                          :config-mode :project-only}
          manifest-path (project-config/init! project-root custom-template)
          content (slurp manifest-path)
          manifest (edn/read-string content)]
      (is (= ["./custom-checks"] (:checks manifest)))
      (is (= #{"TODO" "FIXME"} (:ignore manifest)))
      (is (= :replace (:ignore-mode manifest)))
      (is (= :project-only (:config-mode manifest))))))

(deftest manifest-exists-check
  (testing "manifest-exists? correctly detects presence of manifest"
    (let [project-root @test-project-root]
      (is (false? (manifest/exists? project-root)))
      (project-config/init! project-root)
      (is (true? (manifest/exists? project-root))))))

;;; Nested Project Tests

(deftest nested-projects-uses-closest-manifest
  (testing "When nested .proserunner/ dirs exist, uses the closest one"
    (let [project-root @test-project-root
          ;; Create outer project
          outer-proserunner (str project-root File/separator ".proserunner")
          outer-manifest (str outer-proserunner File/separator "config.edn")
          _ (.mkdirs (io/file outer-proserunner))
          _ (spit outer-manifest
                  (pr-str {:checks ["default"]
                          :ignore #{"outer-ignore"}}))

          ;; Create nested subdirectory with its own project
          nested-dir (str project-root File/separator "subproject")
          nested-proserunner (str nested-dir File/separator ".proserunner")
          nested-manifest (str nested-proserunner File/separator "config.edn")
          _ (.mkdirs (io/file nested-proserunner))
          _ (spit nested-manifest
                  (pr-str {:checks ["default"]
                          :ignore #{"nested-ignore"}}))

          ;; Search from deep within nested project
          deep-dir (str nested-dir File/separator "src" File/separator "code")
          _ (.mkdirs (io/file deep-dir))

          ;; Find manifest from deep directory
          found (manifest/find deep-dir)]

      ;; Should find the nested manifest, not the outer one
      (is (some? found))
      (is (= nested-manifest (:manifest-path found)))
      (is (= nested-dir (:project-root found))))))

(deftest nested-projects-stops-at-git-boundary
  (testing "Nested project search stops at .git directory"
    (let [project-root @test-project-root
          ;; Create outer project with .git
          git-dir (str project-root File/separator ".git")
          _ (.mkdirs (io/file git-dir))
          outer-proserunner (str project-root File/separator ".proserunner")
          outer-manifest (str outer-proserunner File/separator "config.edn")
          _ (.mkdirs (io/file outer-proserunner))
          _ (spit outer-manifest
                  (pr-str {:checks ["default"]}))

          ;; Create nested directory without its own .proserunner
          nested-dir (str project-root File/separator "subdir")
          _ (.mkdirs (io/file nested-dir))

          ;; Search from nested directory
          found (manifest/find nested-dir)]

      ;; Should still find the outer manifest
      (is (some? found))
      (is (= outer-manifest (:manifest-path found)))
      (is (= project-root (:project-root found))))))

(deftest nested-projects-with-empty-checks-dir
  (testing "Project with empty .proserunner/checks/ directory"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          checks-dir (str proserunner-dir File/separator "checks")
          manifest-path (str proserunner-dir File/separator "config.edn")
          ;; Create empty checks directory
          _ (.mkdirs (io/file checks-dir))
          ;; Create manifest that references the empty checks dir
          _ (spit manifest-path
                  (pr-str {:checks ["checks"]
                          :ignore #{}
                          :config-mode :project-only}))

          ;; Load config
          config (project-config/load project-root)]

      ;; Should load successfully with empty checks
      (is (some? config))
      ;; Checks should be empty since the directory has no .edn files
      (is (empty? (:checks config)))
      (is (= :project (:source config))))))

;;; Unified Schema Tests (Post-Migration)
;;;
;;; These tests verify the unified :checks schema that replaced :checks

(deftest find-manifest-skips-home-directory
  (testing "find-manifest skips home directory to avoid treating global config as project config"
    (let [home-dir @test-home
          proserunner-dir (str home-dir File/separator ".proserunner")
          home-manifest (str proserunner-dir File/separator "config.edn")
          ;; Global config already exists in home from setup-test-env
          _ (is (.exists (io/file home-manifest)))
          ;; Search from home directory should NOT find the global config
          found (manifest/find home-dir)]
      (is (nil? found) "Should not find manifest in home directory"))))

(deftest find-manifest-finds-project-in-subdirectory
  (testing "find-manifest finds project config in subdirectory but not home"
    (let [home-dir @test-home
          project-dir (str home-dir File/separator "projects" File/separator "myproject")
          proserunner-dir (str project-dir File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path (pr-str {:checks ["default"]}))
          found (manifest/find project-dir)]
      (is (some? found) "Should find project manifest in subdirectory")
      (is (= manifest-path (:manifest-path found)))
      (is (= project-dir (:project-root found))))))

(deftest parse-manifest-with-string-references
  (testing "Parsing manifest with string references in :checks"
    (let [manifest {:checks ["default" "custom"]}
          parsed (manifest/parse manifest)]
      (is (= ["default" "custom"] (:checks parsed)))
      (is (= :extend (:ignore-mode parsed)))
      (is (= :merged (:config-mode parsed))))))

(deftest parse-manifest-with-map-entries
  (testing "Parsing manifest with map entries in :checks"
    (let [manifest {:checks [{:directory "checks"}
                            {:directory "other" :files ["style"]}]}
          parsed (manifest/parse manifest)]
      (is (= 2 (count (:checks parsed))))
      (is (map? (first (:checks parsed))))
      (is (= "checks" (:directory (first (:checks parsed)))))
      (is (= ["style"] (:files (second (:checks parsed))))))))

(deftest parse-manifest-with-mixed-entries
  (testing "Parsing manifest with mixed string and map entries"
    (let [manifest {:checks ["default"
                            {:directory "checks"}
                            {:directory "./custom" :files ["style" "grammar"]}]}
          parsed (manifest/parse manifest)]
      (is (= 3 (count (:checks parsed))))
      (is (string? (first (:checks parsed))))
      (is (map? (second (:checks parsed))))
      (is (map? (nth (:checks parsed) 2))))))

(deftest parse-manifest-validates-checks-required
  (testing "Validation fails when :checks is missing"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #":checks.*required"
         (manifest/parse {})))))

(deftest parse-manifest-validates-checks-is-vector
  (testing "Validation fails when :checks is not a vector"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #":checks.*vector"
         (manifest/parse {:checks "default"})))))

(deftest parse-manifest-validates-checks-not-empty
  (testing "Validation fails when :checks is empty"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #":checks.*empty"
         (manifest/parse {:checks []})))))

(deftest parse-manifest-validates-check-entry-format
  (testing "Validation fails for invalid check entry format"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"invalid entries"
         (manifest/parse {:checks [123]})))))

(deftest parse-manifest-validates-map-entry-has-directory
  (testing "Validation fails when map entry missing :directory"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"invalid entries"
         (manifest/parse {:checks [{:files ["style"]}]})))))

(deftest parse-manifest-validates-map-entry-files-is-vector
  (testing "Validation fails when :files is not a vector"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"invalid entries"
         (manifest/parse {:checks [{:directory "checks" :files "style"}]})))))

(deftest load-project-config-resolves-string-references
  (testing "String references in :checks resolve to global check definitions"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path (pr-str {:checks ["default"]}))
          config (project-config/load project-root)]
      (is (some? config))
      (is (= 1 (count (:checks config))))
      ;; Should have resolved "default" to global check definition
      (let [check (first (:checks config))]
        (is (map? check))
        (is (contains? check :directory))
        (is (contains? check :files))
        (is (= ["cliches" "redundancies"] (:files check)))))))

(deftest load-project-config-with-auto-discovery
  (testing "Map entries without :files auto-discover .edn files"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          checks-dir (str proserunner-dir File/separator "checks")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file checks-dir))
          ;; Create some test check files
          _ (spit (str checks-dir File/separator "style.edn")
                  (pr-str {:name "style" :message "test"}))
          _ (spit (str checks-dir File/separator "grammar.edn")
                  (pr-str {:name "grammar" :message "test"}))
          _ (spit manifest-path (pr-str {:checks [{:directory "checks"}]
                                        :config-mode :project-only}))
          config (project-config/load project-root)]
      (is (some? config))
      (is (= 1 (count (:checks config))))
      (let [check (first (:checks config))]
        (is (= 2 (count (:files check))))
        (is (= ["grammar" "style"] (:files check)))))))  ; sorted alphabetically

(deftest load-project-config-cascading-disabled-checks
  (testing "Disabled checks in global config cascade to projects via string references"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          ;; Global config has :files ["cliches" "redundancies"] (set in setup-test-env)
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path (pr-str {:checks ["default"]}))
          config (project-config/load project-root)]
      (is (some? config))
      ;; Project should inherit the global's file list (with disabled checks removed)
      (let [check (first (:checks config))]
        (is (= ["cliches" "redundancies"] (:files check)))
        ;; If global had more checks disabled, they wouldn't appear here
        ))))

(deftest load-project-config-mixed-checks
  (testing "Projects can mix string references and map definitions"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          checks-dir (str proserunner-dir File/separator "checks")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file checks-dir))
          _ (spit (str checks-dir File/separator "custom.edn")
                  (pr-str {:name "custom" :message "test"}))
          _ (spit manifest-path
                  (pr-str {:checks ["default" {:directory "checks"}]}))
          config (project-config/load project-root)]
      (is (some? config))
      ;; Should have both global default and project checks
      (is (= 2 (count (:checks config))))
      ;; First should be the resolved global check
      (is (= ["cliches" "redundancies"] (:files (first (:checks config)))))
      ;; Second should be the project check
      (is (= ["custom"] (:files (second (:checks config))))))))

(deftest path-traversal-should-be-prevented
  (testing "Path validation should prevent traversal outside project root"
    (let [temp-project (str (System/getProperty "java.io.tmpdir")
                           File/separator
                           "proserunner-path-test-"
                           (System/currentTimeMillis))
          malicious-config-content (pr-str {:checks [{:directory "/etc/passwd"}]
                                           :config-mode :merged})]

      (try
        (.mkdirs (io/file temp-project ".git"))
        (.mkdirs (io/file temp-project ".proserunner"))

        (spit (str temp-project File/separator ".proserunner" File/separator "config.edn")
              malicious-config-content)

        (testing "Loading config with absolute path outside project should fail"
          (try
            (let [loaded-config (project-config/load temp-project)]
              (is (or (empty? (:checks loaded-config))
                     (not-any? #(string/includes? (str (:directory %)) "/etc")
                              (:checks loaded-config)))
                  "Config should reject absolute paths outside project root"))
            (catch Exception e
              (is (or (string/includes? (.getMessage e) "traversal")
                     (string/includes? (.getMessage e) "not found"))
                  "Should throw exception for path traversal or file not found"))))

        (finally
          (when (.exists (io/file temp-project))
            (doseq [f (reverse (file-seq (io/file temp-project)))]
              (.delete f))))))))
