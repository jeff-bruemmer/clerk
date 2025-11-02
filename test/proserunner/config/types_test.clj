(ns proserunner.config.types-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.config.types :as types]))

(deftest config-record-creation
  (testing "Creating a Config record with all fields"
    (let [cfg (types/map->Config {:checks [{:name "test"}]
                                  :ignore "ignore"})]
      (is (= [{:name "test"}] (:checks cfg)))
      (is (= "ignore" (:ignore cfg))))))

(deftest config-record-defaults
  (testing "Config record should support default values"
    (let [cfg (types/map->Config {:checks []})]
      (is (= [] (:checks cfg)))
      (is (nil? (:ignore cfg))))))

(deftest config-equality
  (testing "Two configs with same data are equal"
    (let [cfg1 (types/map->Config {:checks [{:name "a"}] :ignore "ignore"})
          cfg2 (types/map->Config {:checks [{:name "a"}] :ignore "ignore"})]
      (is (= cfg1 cfg2)))))

(deftest config-with-different-data
  (testing "Configs with different data are not equal"
    (let [cfg1 (types/map->Config {:checks [{:name "a"}] :ignore "ignore"})
          cfg2 (types/map->Config {:checks [{:name "b"}] :ignore "ignore"})]
      (is (not= cfg1 cfg2)))))
