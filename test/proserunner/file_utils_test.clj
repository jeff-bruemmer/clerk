(ns proserunner.file-utils-test
  (:require [clojure.test :as t :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [proserunner.file-utils :as file-utils]
            [proserunner.result :as result])
  (:import java.io.File))

(def test-dir (atom nil))

(defn setup-test-dir [f]
  (let [tmp-dir (str (System/getProperty "java.io.tmpdir")
                    File/separator
                    "proserunner-file-utils-test-"
                    (System/currentTimeMillis))]
    (.mkdirs (io/file tmp-dir))
    (reset! test-dir tmp-dir)
    (try
      (f)
      (finally
        (doseq [file (reverse (file-seq (io/file tmp-dir)))]
          (.delete file))))))

(use-fixtures :each setup-test-dir)

(deftest join-path-basic
  (testing "join-path combines path components"
    (let [result (file-utils/join-path "home" "user" "file.txt")]
      (is (string? result))
      (is (.contains result "home"))
      (is (.contains result "user"))
      (is (.contains result "file.txt")))))

(deftest ensure-parent-dir-creates-directory
  (testing "ensure-parent-dir creates parent directories if they don't exist"
    (let [nested-path (file-utils/join-path @test-dir "a" "b" "c" "file.txt")
          parent-dir (file-utils/join-path @test-dir "a" "b" "c")]

      (is (not (.exists (io/file parent-dir)))
          "Parent directory should not exist initially")

      (file-utils/ensure-parent-dir nested-path)

      (is (.exists (io/file parent-dir))
          "Parent directory should be created")
      (is (.isDirectory (io/file parent-dir))
          "Parent should be a directory"))))

(deftest ensure-parent-dir-idempotent
  (testing "ensure-parent-dir is idempotent"
    (let [filepath (file-utils/join-path @test-dir "x" "file.txt")]

      (file-utils/ensure-parent-dir filepath)
      (file-utils/ensure-parent-dir filepath)

      (is (.exists (io/file @test-dir "x"))
          "Should work when called multiple times"))))

(deftest write-edn-file-success
  (testing "write-edn-file writes EDN data to file"
    (let [filepath (file-utils/join-path @test-dir "test.edn")
          test-data {:foo "bar" :baz [1 2 3]}
          result (file-utils/write-edn-file filepath test-data)]

      (is (result/success? result)
          "Should return Success")
      (is (.exists (io/file filepath))
          "File should be created")

      (let [content (slurp filepath)
            parsed (clojure.edn/read-string content)]
        (is (= test-data parsed)
            "Written data should match input")))))

(deftest write-edn-file-creates-parents
  (testing "write-edn-file creates parent directories"
    (let [nested-path (file-utils/join-path @test-dir "nested" "dir" "config.edn")
          test-data {:test true}
          result (file-utils/write-edn-file nested-path test-data)]

      (is (result/success? result)
          "Should return Success even with nested path")
      (is (.exists (io/file nested-path))
          "Nested file should be created"))))

(deftest write-edn-file-overwrites
  (testing "write-edn-file overwrites existing file"
    (let [filepath (file-utils/join-path @test-dir "overwrite.edn")
          original {:version 1}
          updated {:version 2}]

      (file-utils/write-edn-file filepath original)
      (file-utils/write-edn-file filepath updated)

      (let [parsed (clojure.edn/read-string (slurp filepath))]
        (is (= updated parsed)
            "File should be overwritten with new data")))))

(deftest mkdirs-if-missing-creates-directory
  (testing "mkdirs-if-missing creates directory structure"
    (let [dirpath (file-utils/join-path @test-dir "new" "nested" "dir")]

      (is (not (.exists (io/file dirpath)))
          "Directory should not exist initially")

      (file-utils/mkdirs-if-missing dirpath)

      (is (.exists (io/file dirpath))
          "Directory should be created")
      (is (.isDirectory (io/file dirpath))
          "Path should be a directory"))))

(deftest mkdirs-if-missing-idempotent
  (testing "mkdirs-if-missing is idempotent"
    (let [dirpath (file-utils/join-path @test-dir "idempotent")]

      (file-utils/mkdirs-if-missing dirpath)
      (file-utils/mkdirs-if-missing dirpath)
      (file-utils/mkdirs-if-missing dirpath)

      (is (.exists (io/file dirpath))
          "Should work when called multiple times")
      (is (.isDirectory (io/file dirpath))
          "Should remain a directory"))))
