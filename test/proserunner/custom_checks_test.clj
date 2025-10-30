(ns proserunner.custom-checks-test
  (:require [proserunner.custom-checks :as custom]
            [proserunner.system :as sys]
            [clojure.test :as t :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [clojure.edn :as edn])
  (:import java.io.File))

(def original-home (atom nil))
(def test-home (atom nil))

(defn setup-test-env [f]
  ;; Save original home
  (reset! original-home (System/getProperty "user.home"))

  ;; Create temporary home directory
  (let [temp-home (str (System/getProperty "java.io.tmpdir")
                      File/separator
                      "proserunner-test-home-"
                      (System/currentTimeMillis))
        proserunner-dir (str temp-home File/separator ".proserunner")]
    (reset! test-home temp-home)

    ;; Set user.home to temp directory
    (System/setProperty "user.home" temp-home)

    ;; Create test directory structure
    (.mkdirs (io/file proserunner-dir "custom"))

    ;; Create a mock config.edn
    (spit (str proserunner-dir File/separator "config.edn")
          "{:checks []}")

    ;; Run the test
    (try
      (f)
      (finally
        ;; Restore original home
        (System/setProperty "user.home" @original-home)

        ;; Clean up temp directory
        (letfn [(delete-recursively [^File file]
                  (when (.exists file)
                    (when (.isDirectory file)
                      (doseq [child (.listFiles file)]
                        (delete-recursively child)))
                    (.delete file)))]
          (delete-recursively (io/file temp-home)))))))

(use-fixtures :each setup-test-env)

(deftest add-checks-from-directory
  (testing "Adding checks from a local directory"
    (let [;; Create source directory with test check files
          source-dir (str @test-home File/separator "source-checks")
          _ (.mkdirs (io/file source-dir))

          ;; Create test check files
          _ (spit (str source-dir File/separator "test1.edn")
                  "{:name \"test1\" :kind \"existence\" :specimens [\"word\"]}")
          _ (spit (str source-dir File/separator "test2.edn")
                  "{:name \"test2\" :kind \"recommender\" :recommendations []}")

          ;; Add checks
          result (custom/add-checks source-dir {})]

      ;; Verify result structure
      (is (= 2 (:count result)))
      (is (= #{"test1" "test2"} (set (:check-names result))))

      ;; Verify files were copied
      (is (.exists (io/file (:target-dir result) "test1.edn")))
      (is (.exists (io/file (:target-dir result) "test2.edn")))

      ;; Verify config was updated
      (let [config-path (sys/filepath ".proserunner" "config.edn")
            config (edn/read-string (slurp config-path))]
        (is (= 1 (count (:checks config))))
        (is (= "Custom checks: source-checks" (-> config :checks first :name)))
        (is (= "custom/source-checks" (-> config :checks first :directory)))
        (is (= ["test1" "test2"] (-> config :checks first :files)))))))

(deftest add-checks-with-custom-name
  (testing "Adding checks with a custom directory name"
    (let [;; Create source directory
          source-dir (str @test-home File/separator "some-checks")
          _ (.mkdirs (io/file source-dir))
          _ (spit (str source-dir File/separator "check.edn")
                  "{:name \"check\" :kind \"existence\" :specimens []}")

          ;; Add with custom name
          result (custom/add-checks source-dir {:name "my-custom-name"})]

      ;; Verify custom name was used
      (is (clojure.string/includes? (:target-dir result) "my-custom-name"))
      (is (.exists (io/file (:target-dir result) "check.edn")))

      ;; Verify config uses custom name
      (let [config-path (sys/filepath ".proserunner" "config.edn")
            config (edn/read-string (slurp config-path))]
        (is (= "custom/my-custom-name" (-> config :checks first :directory)))))))

(deftest add-checks-no-edn-files
  (testing "Error when directory has no .edn files"
    (let [;; Create empty directory
          source-dir (str @test-home File/separator "empty-dir")
          _ (.mkdirs (io/file source-dir))]

      ;; Should throw exception with improved error message
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"No .edn check files found"
           (custom/add-checks source-dir {}))))))

(deftest add-checks-updates-existing-entry
  (testing "Updating existing check directory in config"
    (let [;; Create source directory
          source-dir (str @test-home File/separator "update-test")
          _ (.mkdirs (io/file source-dir))
          _ (spit (str source-dir File/separator "new-check.edn")
                  "{:name \"new\" :kind \"existence\" :specimens []}")

          ;; Create initial config with existing entry
          config-path (sys/filepath ".proserunner" "config.edn")
          _ (spit config-path
                  "{:checks [{:name \"Custom checks: update-test\"
                              :directory \"custom/update-test\"
                              :files [\"old-check\"]}]}")

          ;; Add checks (should update existing entry)
          result (custom/add-checks source-dir {})]

      ;; Verify config was updated (not duplicated)
      (let [config (edn/read-string (slurp config-path))]
        (is (= 1 (count (:checks config))))
        (is (= ["new-check"] (-> config :checks first :files)))))))

(deftest git-url-detection
  (testing "Git URL detection"
    (is (true? (#'custom/git-url? "https://github.com/user/repo")))
    (is (true? (#'custom/git-url? "http://github.com/user/repo")))
    (is (true? (#'custom/git-url? "git@github.com:user/repo.git")))
    (is (false? (#'custom/git-url? "/local/path")))
    (is (false? (#'custom/git-url? "./relative/path")))
    (is (false? (#'custom/git-url? "~/home/path")))))

(deftest extract-name-from-source
  (testing "Extracting name from various source paths"
    (is (= "repo" (#'custom/extract-name-from-source "https://github.com/user/repo")))
    (is (= "repo" (#'custom/extract-name-from-source "https://github.com/user/repo.git")))
    (is (= "checks" (#'custom/extract-name-from-source "/path/to/checks")))
    (is (= "checks" (#'custom/extract-name-from-source "/path/to/checks/")))
    (is (= "my-checks" (#'custom/extract-name-from-source "~/my-checks")))))
