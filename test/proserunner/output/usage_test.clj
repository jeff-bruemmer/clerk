(ns proserunner.output.usage-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.output.usage :as usage]))

(deftest print-version-test
  (testing "print-version outputs version information"
    (let [output (with-out-str (usage/version))]
      (is (string? output))
      (is (re-find #"Proserunner version" output)))))

(deftest print-usage-test
  (testing "print-usage outputs help information"
    (let [opts {:summary [] :config "test-config"}
          output (with-out-str (usage/print opts))]
      (is (string? output))
      (is (re-find #"P R O S E R U N N E R" output))
      (is (re-find #"USAGE" output)))))

(deftest categorize-options-test
  (testing "categorizes command-line options into groups"
    (let [summary [{:option "--file" :desc "File to check"}
                   {:option "--help" :desc "Print help"}
                   {:option "--output" :desc "Output format"}]
          result (usage/categorize-options summary)]
      (is (seq? result))
      (is (every? (fn [[category opts]] (and (string? category) (sequential? opts))) result)))))
