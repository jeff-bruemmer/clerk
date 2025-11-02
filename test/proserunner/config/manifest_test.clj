(ns proserunner.config.manifest-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [proserunner.config.manifest :as manifest]
            [proserunner.test-helpers :refer [delete-recursively temp-dir-path silently]]
            [clojure.java.io :as io])
  (:import java.io.File))

(def test-project-root (atom nil))

(defn setup-test-env [f]
  (let [temp-project (temp-dir-path "proserunner-manifest-test")]
    (reset! test-project-root temp-project)
    (try
      (silently (f))
      (finally
        (delete-recursively (io/file temp-project))))))

(use-fixtures :each setup-test-env)

(deftest find-manifest-in-current-dir
  (testing "Finds .proserunner/config.edn in current directory"
    (let [project-root @test-project-root
          proserunner-dir (str project-root File/separator ".proserunner")
          manifest-path (str proserunner-dir File/separator "config.edn")
          _ (.mkdirs (io/file proserunner-dir))
          _ (spit manifest-path "{:checks [\"default\"]}")
          found (manifest/find project-root)]
      (is (some? found))
      (is (= manifest-path (:manifest-path found)))
      (is (= project-root (:project-root found))))))

(deftest find-manifest-walks-up-tree
  (testing "Walks up directory tree to find manifest"
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

(deftest find-manifest-stops-at-git
  (testing "Stops at .git directory boundary"
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

(deftest find-manifest-returns-nil-when-not-found
  (testing "Returns nil when no manifest exists"
    (let [project-root @test-project-root
          found (manifest/find project-root)]
      (is (nil? found)))))

(deftest manifest-path-helper
  (testing "Helper functions construct correct paths"
    (let [project-root @test-project-root
          expected (str project-root File/separator ".proserunner" File/separator "config.edn")]
      (is (= expected (manifest/project-config-path project-root))))))
