(ns proserunner.vet.cache-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.vet.cache :as cache]
            [proserunner.storage :as storage]
            [proserunner.config.types :as types]))

(deftest valid-result-all-unchanged
  (testing "valid-result? returns true when nothing changed"
    (let [lines [{:text "Hello world" :line-num 1}
                 {:text "Goodbye" :line-num 2}]
          cfg (types/map->Config {:checks [] :ignore "ignore"})
          checks []
          cached-result (storage/map->Result
                         {:lines lines
                          :lines-hash (cache/stable-hash lines)
                          :config cfg
                          :config-hash (cache/stable-hash cfg)
                          :check-hash (cache/stable-hash checks)
                          :results []})
          input {:cached-result cached-result
                 :lines lines
                 :config cfg
                 :checks checks}]
      (is (true? (cache/valid-result? input))))))

(deftest valid-result-lines-changed
  (testing "valid-result? returns false when lines changed"
    (let [original-lines [{:text "Hello world" :line-num 1}]
          new-lines [{:text "Hello universe" :line-num 1}]
          cfg (types/map->Config {:checks [] :ignore "ignore"})
          checks []
          cached-result (storage/map->Result
                         {:lines original-lines
                          :lines-hash (cache/stable-hash original-lines)
                          :config cfg
                          :config-hash (cache/stable-hash cfg)
                          :check-hash (cache/stable-hash checks)
                          :results []})
          input {:cached-result cached-result
                 :lines new-lines
                 :config cfg
                 :checks checks}]
      (is (false? (cache/valid-result? input))))))

(deftest valid-result-config-changed
  (testing "valid-result? returns false when config changed"
    (let [lines [{:text "Hello world" :line-num 1}]
          old-cfg (types/map->Config {:checks [] :ignore "old"})
          new-cfg (types/map->Config {:checks [] :ignore "new"})
          checks []
          cached-result (storage/map->Result
                         {:lines lines
                          :lines-hash (cache/stable-hash lines)
                          :config old-cfg
                          :config-hash (cache/stable-hash old-cfg)
                          :check-hash (cache/stable-hash checks)
                          :results []})
          input {:cached-result cached-result
                 :lines lines
                 :config new-cfg
                 :checks checks}]
      (is (false? (cache/valid-result? input))))))

(deftest valid-result-checks-changed
  (testing "valid-result? returns false when checks changed"
    (let [lines [{:text "Hello world" :line-num 1}]
          cfg (types/map->Config {:checks [] :ignore "ignore"})
          old-checks []
          new-checks [{:name "test" :kind "existence"}]
          cached-result (storage/map->Result
                         {:lines lines
                          :lines-hash (cache/stable-hash lines)
                          :config cfg
                          :config-hash (cache/stable-hash cfg)
                          :check-hash (cache/stable-hash old-checks)
                          :results []})
          input {:cached-result cached-result
                 :lines lines
                 :config cfg
                 :checks new-checks}]
      (is (false? (cache/valid-result? input))))))

(deftest valid-checks-unchanged
  (testing "valid-checks? returns true when config and checks unchanged"
    (let [lines [{:text "Hello world" :line-num 1}]
          cfg (types/map->Config {:checks [] :ignore "ignore"})
          checks []
          cached-result (storage/map->Result
                         {:lines lines
                          :lines-hash (cache/stable-hash lines)
                          :config cfg
                          :config-hash (cache/stable-hash cfg)
                          :check-hash (cache/stable-hash checks)
                          :results []})
          input {:cached-result cached-result
                 :lines [{:text "Different line" :line-num 1}]
                 :config cfg
                 :checks checks}]
      (is (true? (cache/valid-checks? input))))))

(deftest valid-checks-config-changed
  (testing "valid-checks? returns false when config changed"
    (let [lines [{:text "Hello world" :line-num 1}]
          old-cfg (types/map->Config {:checks [] :ignore "old"})
          new-cfg (types/map->Config {:checks [] :ignore "new"})
          checks []
          cached-result (storage/map->Result
                         {:lines lines
                          :lines-hash (cache/stable-hash lines)
                          :config old-cfg
                          :config-hash (cache/stable-hash old-cfg)
                          :check-hash (cache/stable-hash checks)
                          :results []})
          input {:cached-result cached-result
                 :lines lines
                 :config new-cfg
                 :checks checks}]
      (is (false? (cache/valid-checks? input))))))

(deftest stable-hash-consistency
  (testing "stable-hash produces consistent results across calls"
    (let [data {:a 1 :b 2 :c [1 2 3]}
          hash1 (cache/stable-hash data)
          hash2 (cache/stable-hash data)]
      (is (= hash1 hash2)))))

(deftest stable-hash-different-data
  (testing "stable-hash produces different results for different data"
    (let [data1 {:a 1 :b 2}
          data2 {:a 1 :b 3}
          hash1 (cache/stable-hash data1)
          hash2 (cache/stable-hash data2)]
      (is (not= hash1 hash2)))))
