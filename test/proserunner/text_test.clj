(ns proserunner.text-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [proserunner.text :as text]
            [proserunner.result :as result])
  (:import java.io.File))

(deftest curly-quotes-should-be-detected
  (testing "Quoted text detection should handle both straight and curly quotes"
    (testing "Straight double quotes are detected"
      (let [line-with-straight "She said \"hello world\" to me."]
        (is (text/contains-quoted-text? line-with-straight)
            "Straight double quotes should be detected as quoted text")))

    (testing "Curly double quotes should be detected"
      (let [line-with-curly "She said \u201Chello world\u201D to me."]
        (is (text/contains-quoted-text? line-with-curly)
            "Curly double quotes should be detected as quoted text")))

    (testing "Straight single quotes are detected"
      (let [line-with-straight "He said 'goodbye' yesterday."]
        (is (text/contains-quoted-text? line-with-straight)
            "Straight single quotes should be detected as quoted text")))

    (testing "Curly single quotes should be detected"
      (let [line-with-curly "He said \u2018goodbye\u2019 yesterday."]
        (is (text/contains-quoted-text? line-with-curly)
            "Curly single quotes should be detected as quoted text")))

    (testing "Mixed curly and straight quotes should be detected"
      (let [line-mixed "She said \u201Cit's great\u201D with excitement."]
        (is (text/contains-quoted-text? line-mixed)
            "Mixed curly quotes should be detected as quoted text")))))

(deftest file-validation-should-accept-directories
  (testing "File validation should accept both files and directories"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir")
                       File/separator
                       "proserunner-dir-test-"
                       (System/currentTimeMillis))
          temp-file (str temp-dir File/separator "test.txt")]

      (try
        (.mkdirs (io/file temp-dir))
        (spit temp-file "test content")

        (testing "Validation should accept actual files"
          (is (text/less-than-10-MB? temp-file)
              "Small files should pass validation"))

        (testing "Validation should accept directories"
          (is (text/less-than-10-MB? temp-dir)
              "Directories should pass validation"))

        (finally
          (when (.exists (io/file temp-dir))
            (doseq [f (reverse (file-seq (io/file temp-dir)))]
              (.delete f))))))))

(deftest quoted-text-stripping-should-preserve-columns
  (testing "Quoted text stripping should preserve exact column positions"
    (let [text "Hello, 'quoted text' and more."
          stripped (text/strip-quoted-text text)]

      (testing "Length should be preserved exactly"
        (is (= (count text) (count stripped))
            "Stripped text should have same length as original for accurate column positions"))

      (testing "Quoted text replaced with spaces character-for-character"
        (is (string/includes? stripped "Hello,")
            "Text before quoted text should be unchanged")
        (is (string/includes? stripped "and more.")
            "Text after quoted text should be unchanged")

        (let [expected-length (count text)]
          (is (= expected-length (count stripped))
              "Every character position should be preserved"))))))

(deftest handle-invalid-file-returns-result
  (testing "handle-invalid-file returns Result with files when valid"
    (let [files ["test1.txt" "test2.md"]
          result (text/handle-invalid-file files)]
      (is (result/success? result)
          "Should return Success when files are provided")
      (is (= files (:value result))
          "Should return the same files list")))

  (testing "handle-invalid-file returns Failure when no files"
    (let [result (text/handle-invalid-file [])]
      (is (result/failure? result)
          "Should return Failure when file list is empty")
      (is (string? (:error result))
          "Error should be a string"))))

(deftest fetch-returns-result-on-errors
  (testing "fetch! returns Failure for missing file"
    (let [result (text/fetch! false "/nonexistent/file.txt")]
      (is (result/failure? result)
          "Should return Failure for missing file")
      (is (contains? (:context result) :filepath)
          "Context should include filepath")))

  (testing "fetch! returns Success with lines for valid file"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir")
                       File/separator
                       "proserunner-fetch-test-"
                       (System/currentTimeMillis))
          temp-file (str temp-dir File/separator "test.md")
          _ (.mkdirs (io/file temp-dir))
          _ (spit temp-file "Hello world\nSecond line")]
      (try
        (let [result (text/fetch! false temp-file)]
          (is (result/success? result)
              "Should return Success for valid file")
          (is (vector? (:value result))
              "Value should be a vector of lines")
          (is (pos? (count (:value result)))
              "Should have at least one line"))
        (finally
          (when (.exists (io/file temp-dir))
            (doseq [f (reverse (file-seq (io/file temp-dir)))]
              (.delete f))))))))

(deftest quoted-text-inline-stripping
  (testing "Lines with quoted text should be kept but quoted portions stripped"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir")
                       File/separator
                       "proserunner-quoted-test-"
                       (System/currentTimeMillis))
          temp-file (str temp-dir File/separator "test.md")
          test-content "She said \"hello\" and walked to the store.\nNo quotes here.\nHe replied 'goodbye' quickly."
          _ (.mkdirs (io/file temp-dir))
          _ (spit temp-file test-content)]
      (try
        (testing "With check-quoted-text false (default), quoted text is stripped"
          (let [result (text/fetch! false temp-file false)
                lines (:value result)]
            (is (result/success? result))
            (is (= 3 (count lines))
                "All three lines should be kept")

            ;; First line: quoted text should be replaced with spaces
            (is (string/includes? (:text (first lines)) "She said")
                "Non-quoted portion before quote should remain")
            (is (string/includes? (:text (first lines)) "and walked to the store")
                "Non-quoted portion after quote should remain")
            (is (not (string/includes? (:text (first lines)) "\"hello\""))
                "Quoted text should be stripped")

            ;; Second line: no changes
            (is (= "No quotes here." (:text (second lines)))
                "Lines without quotes should be unchanged")

            ;; Third line: quoted text should be replaced with spaces
            (is (string/includes? (:text (nth lines 2)) "He replied")
                "Non-quoted portion before quote should remain")
            (is (string/includes? (:text (nth lines 2)) "quickly")
                "Non-quoted portion after quote should remain")))

        (testing "With check-quoted-text true, quoted text is kept"
          (let [result (text/fetch! false temp-file true)
                lines (:value result)]
            (is (result/success? result))
            (is (= 3 (count lines)))

            ;; All text should be preserved
            (is (string/includes? (:text (first lines)) "\"hello\"")
                "Quoted text should be kept when check-quoted-text is true")
            (is (string/includes? (:text (nth lines 2)) "'goodbye'")
                "Single-quoted text should be kept when check-quoted-text is true")))

        (finally
          (when (.exists (io/file temp-dir))
            (doseq [f (reverse (file-seq (io/file temp-dir)))]
              (.delete f))))))))

(deftest multiple-quotes-per-line
  (testing "Lines with multiple quoted portions are handled correctly"
    (let [text "\"First,\" she said, \"then second,\" he replied."
          stripped (text/strip-quoted-text text)]

      (is (= (count text) (count stripped))
          "Length should be preserved")
      (is (string/includes? stripped "she said")
          "Non-quoted text between quotes should remain")
      (is (string/includes? stripped "he replied")
          "Non-quoted text at end should remain")
      (is (not (string/includes? stripped "First"))
          "First quoted portion should be stripped")
      (is (not (string/includes? stripped "second"))
          "Second quoted portion should be stripped"))))
