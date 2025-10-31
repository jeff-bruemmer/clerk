(ns proserunner.project-config-test
  (:require [proserunner.project-config :as project-config]
            [clojure.test :as t :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
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
        temp-home (str (System/getProperty "java.io.tmpdir")
                      File/separator
                      "proserunner-test-home-"
                      (System/currentTimeMillis))
        proserunner-dir (str temp-home File/separator ".proserunner")
        temp-project (str (System/getProperty "java.io.tmpdir")
                         File/separator
                         "proserunner-test-project-"
                         (System/currentTimeMillis))]
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

    ;; Create a mock global ignore.edn
    (spit (str proserunner-dir File/separator "ignore.edn")
          (pr-str #{"global-ignore-1" "global-ignore-2"}))

    ;; Run the test with suppressed output
    (try
      (binding [*out* (java.io.StringWriter.)]
        (f))
      (finally
        ;; Restore original home and directory
        (System/setProperty "user.home" @original-home)
        (System/setProperty "user.dir" original-dir)

        ;; Clean up temp directories
        (letfn [(delete-recursively [^File file]
                  (when (.exists file)
                    (when (.isDirectory file)
                      (doseq [child (.listFiles file)]
                        (delete-recursively child)))
                    (.delete file)))]
          (delete-recursively (io/file temp-home))
          (delete-recursively (io/file temp-project)))))))

(use-fixtures :each setup-test-env)

;;; Manifest Discovery Tests

(deftest find-manifest-in-current-directory
  (testing "Finding .proserunner/config.edn in the current directory"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path "{:check-sources [\"default\"]}")
          found (project-config/find-manifest project-root)]
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
          _ (spit manifest-path "{:check-sources [\"default\"]}")
          found (project-config/find-manifest sub-dir)]
      (is (some? found))
      (is (= manifest-path (:manifest-path found)))
      (is (= project-root (:project-root found))))))

(deftest find-manifest-not-found
  (testing "Returns nil when no .proserunner/config.edn exists"
    (let [project-root @test-project-root
          found (project-config/find-manifest project-root)]
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
          _ (spit manifest-path "{:check-sources [\"default\"]}")
          found (project-config/find-manifest sub-dir)]
      (is (some? found))
      (is (= manifest-path (:manifest-path found)))
      (is (= project-root (:project-root found))))))

;;; Manifest Parsing Tests

(deftest parse-minimal-manifest
  (testing "Parsing a minimal valid manifest"
    (let [manifest {:check-sources ["default"]}
          parsed (project-config/parse-manifest manifest)]
      (is (= ["default"] (:check-sources parsed)))
      (is (= :extend (:ignore-mode parsed))) ; default
      (is (= :merged (:config-mode parsed))) ; default
      (is (empty? (:ignore parsed))))))

(deftest parse-full-manifest
  (testing "Parsing a complete manifest with all fields"
    (let [manifest {:check-sources ["default" "./local" "/absolute/path"]
                    :ignore #{"TODO" "FIXME"}
                    :ignore-mode :replace
                    :config-mode :project-only}
          parsed (project-config/parse-manifest manifest)]
      (is (= 3 (count (:check-sources parsed))))
      (is (= #{"TODO" "FIXME"} (:ignore parsed)))
      (is (= :replace (:ignore-mode parsed)))
      (is (= :project-only (:config-mode parsed))))))

(deftest parse-manifest-with-defaults
  (testing "Manifest parsing applies correct defaults"
    (let [manifest {:check-sources ["default"]
                    :ignore #{"TODO"}}
          parsed (project-config/parse-manifest manifest)]
      (is (= :extend (:ignore-mode parsed)))
      (is (= :merged (:config-mode parsed))))))

(deftest parse-manifest-validates-check-sources
  (testing "Validation fails when :check-sources is missing"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"check-sources.*required"
         (project-config/parse-manifest {}))))

  (testing "Validation fails when :check-sources is not a vector"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"check-sources.*vector"
         (project-config/parse-manifest {:check-sources "default"}))))

  (testing "Validation fails when :check-sources is empty"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"check-sources.*empty"
         (project-config/parse-manifest {:check-sources []})))))

(deftest parse-manifest-validates-ignore-mode
  (testing "Validation fails for invalid :ignore-mode"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"ignore-mode.*:extend.*:replace"
         (project-config/parse-manifest {:check-sources ["default"]
                                         :ignore-mode :invalid})))))

(deftest parse-manifest-validates-config-mode
  (testing "Validation fails for invalid :config-mode"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"config-mode.*:merged.*:project-only"
         (project-config/parse-manifest {:check-sources ["default"]
                                         :config-mode :wrong})))))

(deftest parse-manifest-validates-ignore-is-set
  (testing "Validation fails when :ignore is not a set"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"ignore.*set"
         (project-config/parse-manifest {:check-sources ["default"]
                                         :ignore ["TODO" "FIXME"]})))))

(deftest parse-manifest-multiple-errors
  (testing "Collects and reports all validation errors"
    (let [bad-manifest {:check-sources nil          ; Error 1
                        :ignore "not-a-set"         ; Error 2
                        :ignore-mode :invalid}]     ; Error 3
      ;; Should throw with a message containing multiple errors
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Validation errors"
           (project-config/parse-manifest bad-manifest)))

      ;; Verify the ex-data contains all 3 errors
      (try
        (project-config/parse-manifest bad-manifest)
        (catch Exception e
          (let [error-data (ex-data e)]
            (is (= 3 (count (:errors error-data)))
                "Should collect all 3 validation errors")
            (is (some #(re-find #":check-sources is required" %)
                      (:errors error-data))
                "Should include check-sources error")
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
          merged (project-config/merge-configs global project)]
      (is (= #{"global-1" "global-2" "project-1" "project-2"}
             (:ignore merged))))))

(deftest merge-configs-replace-mode
  (testing "Merging in :replace mode uses only project ignore"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{"project-1" "project-2"}
                   :ignore-mode :replace}
          merged (project-config/merge-configs global project)]
      (is (= #{"project-1" "project-2"}
             (:ignore merged))))))

(deftest merge-configs-project-only-mode
  (testing "Config mode :project-only ignores global config entirely"
    (let [global {:ignore #{"global-1"}
                  :checks [{:name "global-checks"}]}
          project {:ignore #{"project-1"}
                   :config-mode :project-only
                   :check-sources ["default"]}
          merged (project-config/merge-configs global project)]
      (is (= #{"project-1"} (:ignore merged)))
      (is (= ["default"] (:check-sources merged)))
      (is (nil? (:checks merged))))))

(deftest merge-configs-merged-mode
  (testing "Config mode :merged combines global and project"
    (let [global {:ignore #{"global-1"}
                  :checks [{:name "global-checks"
                           :directory "default"
                           :files ["check1"]}]}
          project {:ignore #{"project-1"}
                   :config-mode :merged
                   :ignore-mode :extend
                   :check-sources ["default"]}
          merged (project-config/merge-configs global project)]
      (is (= #{"global-1" "project-1"} (:ignore merged)))
      (is (= ["default"] (:check-sources merged)))
      ;; In merged mode, :check-sources is preserved (not :checks)
      ;; Global :checks will be merged later in build-project-config
      (is (= :merged (:config-mode merged))))))

(deftest merge-configs-empty-project-ignore
  (testing "Empty project ignore in extend mode preserves global"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{}
                   :ignore-mode :extend}
          merged (project-config/merge-configs global project)]
      (is (= #{"global-1" "global-2"} (:ignore merged))))))

(deftest merge-configs-no-global-ignore
  (testing "Handles missing global ignore gracefully"
    (let [global {}
          project {:ignore #{"project-1"}
                   :ignore-mode :extend}
          merged (project-config/merge-configs global project)]
      (is (= #{"project-1"} (:ignore merged))))))

;;; Check Source Resolution Tests

(deftest resolve-check-source-default
  (testing "Resolving 'default' check source"
    (let [result (project-config/resolve-check-source "default" nil)]
      (is (= :built-in (:type result)))
      (is (= "default" (:source result))))))

(deftest resolve-check-source-checks-shorthand
  (testing "Resolving 'checks' shorthand to .proserunner/checks/"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          checks-dir (str proserunner-dir File/separator "checks")
          _ (.mkdirs (io/file checks-dir))
          result (project-config/resolve-check-source "checks" project-root)]
      (is (= :local (:type result)))
      (is (.endsWith (:path result) (str ".proserunner" File/separator "checks"))))))

(deftest resolve-check-source-local-path
  (testing "Resolving local relative path"
    (let [project-root @test-project-root
          checks-dir (str project-root File/separator "custom-checks")
          _ (.mkdirs (io/file checks-dir))
          result (project-config/resolve-check-source
                  "./custom-checks"
                  project-root)]
      (is (= :local (:type result)))
      (is (.endsWith (:path result) "custom-checks"))))

  (testing "Resolving local absolute path"
    (let [checks-dir (str @test-project-root File/separator "abs-checks")
          _ (.mkdirs (io/file checks-dir))
          result (project-config/resolve-check-source
                  checks-dir
                  nil)]
      (is (= :local (:type result)))
      (is (= checks-dir (:path result))))))

(deftest resolve-check-source-validates-local-exists
  (testing "Validation fails when local path doesn't exist"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"does not exist"
         (project-config/resolve-check-source
          "./nonexistent"
          @test-project-root)))))

(deftest resolve-check-source-prevents-path-traversal
  (testing "Prevents directory traversal with relative paths"
    (let [project-root @test-project-root
          ;; Create a directory outside project root
          outside-dir (str (System/getProperty "java.io.tmpdir")
                          File/separator
                          "outside-project-"
                          (System/currentTimeMillis))
          _ (.mkdirs (io/file outside-dir))]
      (try
        ;; Try to reference a path outside project using ../
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Path traversal detected"
             (project-config/resolve-check-source
              "../../../outside-project"
              project-root)))
        (finally
          (.delete (io/file outside-dir))))))

  (testing "Allows absolute paths outside project root"
    (let [outside-dir (str (System/getProperty "java.io.tmpdir")
                          File/separator
                          "absolute-outside-"
                          (System/currentTimeMillis))
          _ (.mkdirs (io/file outside-dir))]
      (try
        ;; Absolute paths should be allowed (not validated against project root)
        (let [result (project-config/resolve-check-source outside-dir nil)]
          (is (= :local (:type result)))
          (is (some? (:path result))))
        (finally
          (.delete (io/file outside-dir)))))))

(deftest resolve-check-source-handles-symlinks
  (testing "Follows symlinks within project root"
    (let [project-root @test-project-root
          real-dir (str project-root File/separator "real-checks")
          symlink-path (str project-root File/separator "symlink-checks")
          _ (.mkdirs (io/file real-dir))]
      (try
        ;; Create symlink (skip test if symlinks not supported)
        (try
          (java.nio.file.Files/createSymbolicLink
           (.toPath (io/file symlink-path))
           (.toPath (io/file real-dir))
           (into-array java.nio.file.attribute.FileAttribute []))
          (let [result (project-config/resolve-check-source "./symlink-checks" project-root)]
            (is (= :local (:type result)))
            (is (some? (:path result))))
          (catch UnsupportedOperationException _
            ;; Symlinks not supported on this platform, skip test
            (is true "Symlinks not supported, skipping test")))
        (finally
          (.delete (io/file symlink-path))
          (.delete (io/file real-dir))))))

  (testing "Prevents symlink escape outside project root"
    (let [project-root @test-project-root
          outside-dir (str (System/getProperty "java.io.tmpdir")
                          File/separator
                          "outside-symlink-"
                          (System/currentTimeMillis))
          symlink-path (str project-root File/separator "escape-link")
          _ (.mkdirs (io/file outside-dir))]
      (try
        ;; Create symlink pointing outside project
        (try
          (java.nio.file.Files/createSymbolicLink
           (.toPath (io/file symlink-path))
           (.toPath (io/file outside-dir))
           (into-array java.nio.file.attribute.FileAttribute []))
          ;; Canonical path resolution should detect the escape
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Path traversal detected"
               (project-config/resolve-check-source "./escape-link" project-root)))
          (catch UnsupportedOperationException _
            ;; Symlinks not supported, skip test
            (is true "Symlinks not supported, skipping test")))
        (finally
          (.delete (io/file symlink-path))
          (letfn [(delete-recursively [^java.io.File file]
                    (when (.exists file)
                      (when (.isDirectory file)
                        (doseq [child (.listFiles file)]
                          (delete-recursively child)))
                      (.delete file)))]
            (delete-recursively (io/file outside-dir))))))))

(deftest read-manifest-handles-permissions
  (testing "Handles permission-denied when reading manifest"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path "{:check-sources [\"default\"]}")
          manifest-file (io/file manifest-path)]
      ;; Make file unreadable (may not work on all platforms)
      (when (.setReadable manifest-file false false)
        (try
          (is (thrown? Exception
                      (project-config/read-manifest manifest-path)))
          (finally
            ;; Restore permissions for cleanup
            (.setReadable manifest-file true false)))))))

;;; Integration Tests

(deftest load-project-config-full-workflow
  (testing "Complete workflow: find, parse, and merge project config"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path
                  (pr-str {:check-sources ["default"]
                          :ignore #{"TODO"}
                          :ignore-mode :extend
                          :config-mode :merged}))
          config (project-config/load-project-config project-root)]
      (is (some? config))
      ;; After resolution, check-sources becomes :checks
      (is (some? (:checks config)))
      ;; Should have both global and project ignores
      (is (contains? (:ignore config) "TODO"))
      (is (contains? (:ignore config) "global-ignore-1"))
      (is (= :project (:source config))))))

(deftest load-project-config-no-manifest
  (testing "Returns global config when no project manifest exists"
    (let [config (project-config/load-project-config @test-project-root)]
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
                  (pr-str {:check-sources ["default"]
                          :ignore #{"TODO"}
                          :config-mode :project-only}))
          config (project-config/load-project-config project-root)]
      (is (some? config))
      (is (= #{"TODO"} (:ignore config)))
      (is (not (contains? (:ignore config) "global-ignore-1")))
      (is (= :project (:source config))))))

;;; Project Initialization Tests

(deftest init-project-config-creates-manifest
  (testing "Initializing project config creates .proserunner/ directory structure"
    (let [project-root @test-project-root
          manifest-path (project-config/init-project-config! project-root)
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
          manifest-path (project-config/init-project-config! project-root)
          content (slurp manifest-path)
          manifest (edn/read-string content)]
      (is (= ["default"] (:check-sources manifest)))
      (is (= #{} (:ignore manifest)))
      (is (= :extend (:ignore-mode manifest)))
      (is (= :merged (:config-mode manifest))))))

(deftest init-project-config-fails-if-exists
  (testing "Initialization fails if manifest already exists"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit (str proserunner-dir File/separator "config.edn")
                  (pr-str {:check-sources ["default"]}))]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"already exists"
           (project-config/init-project-config! project-root))))))

(deftest init-project-config-custom-template
  (testing "Can initialize with custom template"
    (let [project-root @test-project-root
          custom-template {:check-sources ["./custom-checks"]
                          :ignore #{"TODO" "FIXME"}
                          :ignore-mode :replace
                          :config-mode :project-only}
          manifest-path (project-config/init-project-config! project-root custom-template)
          content (slurp manifest-path)
          manifest (edn/read-string content)]
      (is (= ["./custom-checks"] (:check-sources manifest)))
      (is (= #{"TODO" "FIXME"} (:ignore manifest)))
      (is (= :replace (:ignore-mode manifest)))
      (is (= :project-only (:config-mode manifest))))))

(deftest manifest-exists-check
  (testing "manifest-exists? correctly detects presence of manifest"
    (let [project-root @test-project-root]
      (is (false? (project-config/manifest-exists? project-root)))
      (project-config/init-project-config! project-root)
      (is (true? (project-config/manifest-exists? project-root))))))

;;; Nested Project Tests

(deftest nested-projects-uses-closest-manifest
  (testing "When nested .proserunner/ dirs exist, uses the closest one"
    (let [project-root @test-project-root
          ;; Create outer project
          outer-proserunner (str project-root File/separator ".proserunner")
          outer-manifest (str outer-proserunner File/separator "config.edn")
          _ (.mkdirs (io/file outer-proserunner))
          _ (spit outer-manifest
                  (pr-str {:check-sources ["default"]
                          :ignore #{"outer-ignore"}}))

          ;; Create nested subdirectory with its own project
          nested-dir (str project-root File/separator "subproject")
          nested-proserunner (str nested-dir File/separator ".proserunner")
          nested-manifest (str nested-proserunner File/separator "config.edn")
          _ (.mkdirs (io/file nested-proserunner))
          _ (spit nested-manifest
                  (pr-str {:check-sources ["default"]
                          :ignore #{"nested-ignore"}}))

          ;; Search from deep within nested project
          deep-dir (str nested-dir File/separator "src" File/separator "code")
          _ (.mkdirs (io/file deep-dir))

          ;; Find manifest from deep directory
          found (project-config/find-manifest deep-dir)]

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
                  (pr-str {:check-sources ["default"]}))

          ;; Create nested directory without its own .proserunner
          nested-dir (str project-root File/separator "subdir")
          _ (.mkdirs (io/file nested-dir))

          ;; Search from nested directory
          found (project-config/find-manifest nested-dir)]

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
                  (pr-str {:check-sources ["checks"]
                          :ignore #{}
                          :config-mode :project-only}))

          ;; Load config
          config (project-config/load-project-config project-root)]

      ;; Should load successfully with empty checks
      (is (some? config))
      ;; Checks should be empty since the directory has no .edn files
      (is (empty? (:checks config)))
      (is (= :project (:source config))))))
