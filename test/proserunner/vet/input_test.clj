(ns proserunner.vet.input-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [proserunner.vet.input :as input]
            [proserunner.result :as result]
            [proserunner.test-helpers :refer [delete-recursively temp-dir-path silently with-system-property]]
            [clojure.java.io :as io]
            [clojure.string]
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

(deftest make-input-with-multiple-exclude-patterns
  (testing "make-input respects multiple exclude patterns"
    (let [temp-dir @test-temp-dir
          config-path (str (System/getProperty "user.home")
                          File/separator ".proserunner"
                          File/separator "config.edn")
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "include.md") "Include this")
          _ (spit (str temp-dir File/separator "exclude1.md") "Exclude this")
          _ (spit (str temp-dir File/separator "exclude2.md") "Exclude this too")
          _ (spit (str temp-dir File/separator "skip.txt") "Skip this")
          opts {:file temp-dir
                :config config-path
                :output "table"
                :code-blocks false
                :quoted-text false
                :exclude ["exclude1.md" "exclude2.md" "skip.txt"]
                :parallel-files false}
          input-result (input/make opts)]
      (is (result/success? input-result))
      (let [input (:value input-result)
            lines (:lines input)]
        ;; Should only contain lines from include.md
        (is (pos? (count lines)))
        (is (every? #(clojure.string/includes? (:file %) "include.md") lines))))))

(deftest make-input-with-directory-and-file-excludes
  (testing "make-input can exclude both directories and files"
    (let [temp-dir @test-temp-dir
          subdir (str temp-dir File/separator "drafts")
          _ (.mkdirs (io/file subdir))
          _ (spit (str temp-dir File/separator "keep.md") "Keep this")
          _ (spit (str temp-dir File/separator "skip.md") "Skip this")
          _ (spit (str subdir File/separator "draft.md") "Draft content")
          opts {:file temp-dir
                :config (str (System/getProperty "user.home")
                            File/separator ".proserunner"
                            File/separator "config.edn")
                :output "table"
                :code-blocks false
                :quoted-text false
                :exclude ["drafts/*" "skip.md"]
                :parallel-files false}
          input-result (input/make opts)]
      (is (result/success? input-result))
      (let [input (:value input-result)
            lines (:lines input)]
        ;; Should only contain lines from keep.md
        (is (pos? (count lines)))
        (is (every? #(clojure.string/includes? (:file %) "keep.md") lines))))))

(deftest make-input-with-empty-exclude
  (testing "make-input handles empty exclude list"
    (let [temp-dir @test-temp-dir
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "file.md") "Content")
          opts {:file temp-dir
                :config (str (System/getProperty "user.home")
                            File/separator ".proserunner"
                            File/separator "config.edn")
                :output "table"
                :code-blocks false
                :quoted-text false
                :exclude []
                :parallel-files false}
          input-result (input/make opts)]
      (is (result/success? input-result))
      (let [input (:value input-result)]
        (is (seq (:lines input)))))))

(deftest make-input-with-wildcard-excludes
  (testing "make-input handles wildcard patterns in excludes"
    (let [temp-dir @test-temp-dir
          _ (.mkdirs (io/file temp-dir))
          _ (spit (str temp-dir File/separator "keep.md") "Keep this")
          _ (spit (str temp-dir File/separator "temp.backup") "Backup")
          _ (spit (str temp-dir File/separator "draft.backup") "Draft backup")
          opts {:file temp-dir
                :config (str (System/getProperty "user.home")
                            File/separator ".proserunner"
                            File/separator "config.edn")
                :output "table"
                :code-blocks false
                :quoted-text false
                :exclude ["*.backup"]
                :parallel-files false}
          input-result (input/make opts)]
      (is (result/success? input-result))
      (let [input (:value input-result)
            lines (:lines input)]
        (is (pos? (count lines)))
        (is (every? #(clojure.string/includes? (:file %) "keep.md") lines))))))

(deftest make-input-loads-project-ignore-patterns
  (testing "Project config ignore patterns are loaded into Input"
    (with-system-property "user.dir" @test-temp-dir
      (fn []
        (let [temp-dir @test-temp-dir
              proserunner-dir (io/file temp-dir ".proserunner")
              _ (.mkdirs proserunner-dir)
              ;; Create project config with ignore patterns - use "default" checks from global
              _ (spit (str proserunner-dir File/separator "config.edn")
                      (pr-str {:checks ["default"]
                               :ignore #{"adverb" "cliche"}
                               :ignore-mode :extend}))
              _ (spit (str temp-dir File/separator "test.md") "# Test")
              opts {:file temp-dir
                    :config (str (System/getProperty "user.home")
                                File/separator ".proserunner"
                                File/separator "config.edn")
                    :output "table"
                    :parallel-files false}
              input-result (input/make opts)]
          (is (result/success? input-result))
          (let [input (:value input-result)]
            ;; Verify ignore is a set, not a string
            (is (set? (-> input :config :ignore)))
            ;; Verify correct ignore patterns loaded
            (is (= #{"adverb" "cliche"} (-> input :config :ignore)))
            ;; Verify project-ignore also populated
            (is (= #{"adverb" "cliche"} (:project-ignore input)))))))))
