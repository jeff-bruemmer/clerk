(ns proserunner.vet.input-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [proserunner.vet.input :as input]
            [proserunner.result :as result]
            [proserunner.test-helpers :refer [delete-recursively temp-dir-path silently]]
            [clojure.java.io :as io]
            [editors.registry :as registry]
            [editors.utilities :as util]
            [editors.repetition :as repetition]
            [editors.re :as re])
  (:import java.io.File))

(def test-temp-dir (atom nil))

(defn setup-test-env [f]
  (let [temp-dir (temp-dir-path "proserunner-input-test")]
    (reset! test-temp-dir temp-dir)
    (doseq [editor-type (util/standard-editor-types)]
      (registry/register-editor! editor-type (util/create-editor editor-type)))
    (registry/register-editor! "repetition" repetition/proofread)
    (registry/register-editor! "regex" re/proofread)
    (try
      (silently (f))
      (finally
        (delete-recursively (io/file temp-dir))))))

(use-fixtures :each setup-test-env)

(deftest build-ignore-patterns-test
  (testing "Builds ignore patterns from file directory"
    (let [temp-dir @test-temp-dir
          _ (.mkdirs (io/file temp-dir))
          ignore-info (#'input/build-ignore-patterns temp-dir [])]
      (is (map? ignore-info))
      (is (contains? ignore-info :base-dir))
      (is (contains? ignore-info :patterns)))))

(deftest filter-valid-files-test
  (testing "Filters for supported file types"
    (let [temp-dir @test-temp-dir
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "test.md") "# Test")
          _ (spit (str temp-dir File/separator "test.txt") "Test")
          _ (spit (str temp-dir File/separator "test.jpg") "binary")
          ignore-info {:base-dir (.getAbsolutePath (io/file temp-dir))
                       :patterns []}
          files-result (#'input/filter-valid-files temp-dir ignore-info)]
      (is (result/success? files-result))
      (let [files (:value files-result)]
        (is (seq files))
        (is (every? #(or (re-find #"\.md$" %)
                        (re-find #"\.txt$" %))
                   files))))))

(deftest determine-parallel-settings-test
  (testing "Determines parallel settings from options"
    (is (= {:parallel-files? true :parallel-lines? true}
           (#'input/determine-parallel-settings {:parallel-files true})))
    (is (= {:parallel-files? false :parallel-lines? false}
           (#'input/determine-parallel-settings {:sequential-lines true})))
    (is (= {:parallel-files? false :parallel-lines? true}
           (#'input/determine-parallel-settings {})))))

(deftest make-input-test
  (testing "make-input creates Input record from options"
    (let [temp-dir @test-temp-dir
          config-path (str (System/getProperty "user.home")
                          File/separator ".proserunner"
                          File/separator "config.edn")
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "test.md") "# Test file\n\nSome content.")
          opts {:file temp-dir
                :config config-path
                :output "table"
                :code-blocks false
                :quoted-text false
                :parallel-files false}
          input-result (input/make opts)]
      (is (result/success? input-result))
      (let [input (:value input-result)]
        (is (= (:file input) temp-dir))
        (is (seq (:lines input)))
        (is (some? (:config input)))
        (is (some? (:checks input)))))))

(deftest get-lines-from-files-parallel
  (testing "Gets lines from files in parallel"
    (let [temp-dir @test-temp-dir
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "file1.md") "Line 1")
          _ (spit (str temp-dir File/separator "file2.md") "Line 2")
          lines-result (#'input/get-lines-from-all-files false false temp-dir [] true)]
      (is (result/success? lines-result))
      (let [lines (:value lines-result)]
        (is (= 2 (count lines)))))))

(deftest get-lines-from-files-sequential
  (testing "Gets lines from files sequentially"
    (let [temp-dir @test-temp-dir
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "file1.md") "Line 1")
          lines-result (#'input/get-lines-from-all-files false false temp-dir [] false)]
      (is (result/success? lines-result))
      (let [lines (:value lines-result)]
        (is (= 1 (count lines)))))))

(deftest make-input-with-exclude-patterns
  (testing "make-input respects exclude patterns"
    (let [temp-dir @test-temp-dir
          config-path (str (System/getProperty "user.home")
                          File/separator ".proserunner"
                          File/separator "config.edn")
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "include.md") "Include this")
          _ (spit (str temp-dir File/separator "exclude.md") "Exclude this")
          opts {:file temp-dir
                :config config-path
                :output "table"
                :code-blocks false
                :quoted-text false
                :exclude "exclude.md"
                :parallel-files false}
          input-result (input/make opts)]
      (is (result/success? input-result)))))
