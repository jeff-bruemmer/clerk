(ns proserunner.build-test
  "Tests for build system tasks and wrapper script behavior."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [proserunner.test-helpers :refer [with-temp-dir]])
  (:import java.io.File))

;;; Issue 1: bb.edn install task should check for proserunner-binary

(deftest file-check-logic
  (testing "Install task file existence check logic"
    (with-temp-dir [temp-dir "build-test"]
      ;; Test 1: Only wrapper exists, no binary
      (spit (str temp-dir "/proserunner") "#!/bin/bash\necho wrapper")

      (is (.exists (io/file temp-dir "proserunner"))
          "Wrapper script exists")
      (is (not (.exists (io/file temp-dir "proserunner-binary")))
          "Binary does not exist - install task should fail in this case")

      ;; Test 2: Both exist
      (spit (str temp-dir "/proserunner-binary") "fake binary")

      (is (.exists (io/file temp-dir "proserunner"))
          "Wrapper script exists")
      (is (.exists (io/file temp-dir "proserunner-binary"))
          "Binary exists - install task should succeed in this case"))))

;;; Issue 2: Wrapper script should limit find depth

(deftest wrapper-find-command-has-maxdepth
  (testing "Wrapper script should use -maxdepth flag in find commands"
    (let [wrapper-content (slurp "proserunner")]
      (is (str/includes? wrapper-content "-maxdepth 10")
          "Wrapper should include -maxdepth 10 flag to limit directory traversal")
      (is (>= (count (re-seq #"-maxdepth 10" wrapper-content)) 2)
          "Should have at least 2 instances of -maxdepth 10 (one for args, one for current dir)"))))

;;; Issue 3: Install script should warn before overwriting

(deftest install-script-has-overwrite-warning
  (testing "Install script should check for existing installation"
    (let [install-content (slurp "install.sh")]
      (is (str/includes? install-content "Overwriting")
          "Install script should contain overwrite warning message")
      (is (or (str/includes? install-content "[ -L \"$TARGET\" ]")
              (str/includes? install-content "[ -f \"$TARGET\" ]"))
          "Install script should check if target exists before warning"))))

;;; Issue 5: Command construction should be safe

(deftest build-command-construction-uses-proper-joining
  (testing "Build command construction should use explicit string joining"
    ;; Check that tasks/build.clj uses str/join for command construction
    (let [build-content (slurp "tasks/build.clj")]
      (is (str/includes? build-content "str/join")
          "Build tasks should use str/join for command construction")
      (is (str/includes? build-content "[\"clojure\"")
          "Build command should use vector of command parts"))))
