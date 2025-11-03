(ns proserunner.test-helpers
  "Common test utilities and helpers.

  Provides shared functionality for test fixtures, temp directories,
  and output suppression."
  (:require [clojure.java.io :as io])
  (:import java.io.File))

;;; Output Suppression

(defmacro silently
  "Suppresses stdout/stderr during test execution.

  Test assertions will still fail loudly - only println/print output is suppressed.

  Example:
    (silently
      (println \"This won't appear\")
      (is (= 1 1)))  ; Assertion passes silently

    (silently
      (println \"This won't appear\")
      (is (= 1 2)))  ; Assertion WILL fail loudly"
  [& body]
  `(binding [*out* (java.io.StringWriter.)
             *err* (java.io.StringWriter.)]
     ~@body))

;;; File System Utilities

(defn delete-recursively
  "Recursively delete a directory and all its contents.

  Safe to call on non-existent files (no-op)."
  [^File file]
  (when (.exists file)
    (if (.isDirectory file)
      (do
        (doseq [child (.listFiles file)]
          (delete-recursively child))
        (.delete file))
      (.delete file))))

(defn temp-dir-path
  "Generate a unique temporary directory path with the given prefix.

  Does not create the directory - just returns the normalized path string.

  Example:
    (temp-dir-path \"test-project\")
    ;; => \"/tmp/test-project-1234567890\""
  [prefix]
  (.getAbsolutePath
    (io/file (System/getProperty "java.io.tmpdir")
             (str prefix "-" (System/currentTimeMillis)))))

(defn create-temp-dir!
  "Create a temporary directory with the given prefix.

  Returns the File object for the created directory.

  Example:
    (create-temp-dir! \"my-test\")
    ;; => #object[java.io.File ...]"
  [prefix]
  (let [dir (io/file (temp-dir-path prefix))]
    (.mkdirs dir)
    dir))

;;; Test Fixtures

(defmacro with-temp-dir
  "Execute body with a temporary directory that gets cleaned up afterward.

  The temp directory path is bound to the given binding name.

  Example:
    (with-temp-dir [temp-path \"my-test\"]
      (spit (str temp-path \"/file.txt\") \"content\")
      (is (.exists (io/file temp-path \"file.txt\"))))
    ;; Directory is automatically deleted after body executes"
  [[binding-name prefix] & body]
  `(let [~binding-name (temp-dir-path ~prefix)
         temp-dir# (io/file ~binding-name)]
     (try
       (.mkdirs temp-dir#)
       ~@body
       (finally
         (delete-recursively temp-dir#)))))

(defmacro with-temp-dirs
  "Execute body with multiple temporary directories that get cleaned up afterward.

  Takes a vector of [binding-name prefix] pairs.

  Example:
    (with-temp-dirs [[home \"test-home\"]
                     [project \"test-project\"]]
      (is (.exists (io/file home)))
      (is (.exists (io/file project))))
    ;; Both directories are automatically deleted after body executes"
  [bindings & body]
  (if (empty? bindings)
    `(do ~@body)
    (let [[binding-name prefix] (first bindings)
          rest-bindings (rest bindings)]
      `(with-temp-dir [~binding-name ~prefix]
         (with-temp-dirs [~@rest-bindings]
           ~@body)))))

;;; Environment Stubbing

(defn with-system-property
  "Execute function f with a system property temporarily set.

  Restores the original value (or removes it) after f completes.

  Example:
    (with-system-property \"user.home\" \"/tmp/fake-home\"
      (fn [] (System/getProperty \"user.home\")))
    ;; Returns \"/tmp/fake-home\" but doesn't affect system after"
  [property-name value f]
  (let [original (System/getProperty property-name)]
    (try
      (System/setProperty property-name value)
      (f)
      (finally
        (if original
          (System/setProperty property-name original)
          (System/clearProperty property-name))))))

(defmacro with-user-home
  "Execute body with user.home system property temporarily set.

  Example:
    (with-user-home \"/tmp/fake-home\"
      (is (= \"/tmp/fake-home\" (System/getProperty \"user.home\"))))"
  [home-path & body]
  `(with-system-property "user.home" ~home-path
     (fn [] ~@body)))
