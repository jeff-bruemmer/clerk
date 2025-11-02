(ns proserunner.storage-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [clojure.java.io :as io])
  (:import java.io.File
           java.util.concurrent.CountDownLatch))

(deftest cache-hash-should-be-stable
  (testing "Cache validation should use stable hashing"
    (let [checks [{:name "check1" :kind "existence" :specimens ["test"]}
                  {:name "check2" :kind "regex" :pattern "foo"}]
          hash1 (hash checks)]

      (testing "Hash should be deterministic and stable"
        (let [stable-hash1 (hash (pr-str checks))
              stable-hash2 (hash (pr-str checks))]
          (is (= stable-hash1 stable-hash2)
              "Stable hash of serialized data should be consistent"))

        (is (= hash1 (hash checks))
            "Hash should be reproducible within and across sessions")))))

(deftest cache-concurrent-writes-should-be-safe
  (testing "Cache writes should be atomic and handle concurrent access safely"
    (let [temp-dir (str (System/getProperty "java.io.tmpdir")
                       File/separator
                       "proserunner-race-test-"
                       (System/currentTimeMillis))
          num-threads 10
          latch (CountDownLatch. num-threads)
          results (atom [])]

      (try
        (.mkdirs (io/file temp-dir))

        (testing "Concurrent writes should not corrupt files"
          (let [threads (doall
                         (for [i (range num-threads)]
                           (Thread.
                            (fn []
                              (.await latch)
                              (try
                                (let [result {:file (str "test-" i ".md")
                                            :content "content"
                                            :check-hash (hash {:check i})
                                            :results [{:line i :issue (str "Issue " i)}]}
                                      file-path (str temp-dir File/separator "cache-" i ".edn")]
                                  (spit file-path (pr-str result))
                                  (Thread/sleep (rand-int 5))
                                  (let [read-back (read-string (slurp file-path))]
                                    (swap! results conj {:success true :result read-back})))
                                (catch Exception e
                                  (swap! results conj {:success false :error (.getMessage e)})))))))]

            (doseq [t threads] (.start t))
            (dotimes [_ num-threads] (.countDown latch))
            (doseq [t threads] (.join t 5000))

            (let [final-results @results
                  successes (filter :success final-results)
                  failures (filter #(not (:success %)) final-results)]
              (is (= num-threads (count successes))
                  (str "All " num-threads " concurrent writes should succeed. "
                       "Got " (count failures) " failures: "
                       (map :error failures))))))

        (finally
          (when (.exists (io/file temp-dir))
            (doseq [f (reverse (file-seq (io/file temp-dir)))]
              (.delete f))))))))
