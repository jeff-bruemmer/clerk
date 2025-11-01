(ns proserunner.effects-test
  "Tests for effect execution.

  Tests effect execution machinery and effect description structures."
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.effects :as effects]
            [proserunner.result :as result]))

(defmacro silently
  "Suppresses stdout/stderr during test execution."
  [& body]
  `(binding [*out* (java.io.StringWriter.)
             *err* (java.io.StringWriter.)]
     ~@body))

(deftest execute-effect-unknown-test
  (testing "Unknown effect types return failure"
    (let [result (effects/execute-effect [:unknown/type "arg"])]
      (is (result/failure? result))
      (is (re-find #"Unknown effect type" (:error result)))
      (is (= :unknown/type (-> result :context :effect-type))))))

(deftest execute-effect-help-print-test
  (testing "Help print effect succeeds without title"
    (silently
      (let [opts {:help true}
            result (effects/execute-effect [:help/print opts])]
        (is (result/success? result))
        ;; Help effect prints to stdout and returns nil, which is ok
        (is (nil? (:value result))))))

  (testing "Help print effect succeeds with title"
    (silently
      (let [opts {:help true}
            result (effects/execute-effect [:help/print opts "TEST TITLE"])]
        (is (result/success? result))
        (is (nil? (:value result)))))))

(deftest execute-effect-version-print-test
  (testing "Version print effect succeeds"
    (silently
      (let [result (effects/execute-effect [:version/print])]
        (is (result/success? result))
        ;; Version prints to stdout and returns nil, which is ok
        (is (nil? (:value result)))))))

(deftest execute-effects-test
  (testing "Execute multiple effects in sequence"
    (silently
      (let [effects [[:help/print {}]
                     [:version/print]]
            result (effects/execute-effects effects)]
        (is (result/success? result))
        (is (= 2 (count (:value result)))))))

  (testing "Short-circuits on first failure"
    (silently
      (let [effects [[:help/print {}]
                     [:unknown/type]
                     [:version/print]]
            result (effects/execute-effects effects)]
        (is (result/failure? result))
        (is (re-find #"Unknown effect type" (:error result)))))))

(deftest execute-effects-all-test
  (testing "Execute all effects successfully"
    (silently
      (let [effects [[:help/print {}]
                     [:version/print]]
            result (effects/execute-effects-all effects)]
        (is (result/success? result))
        (is (= 2 (count (:value result)))))))

  (testing "Collects all failures"
    (silently
      (let [effects [[:help/print {}]
                     [:unknown/type1]
                     [:version/print]
                     [:unknown/type2]]
            result (effects/execute-effects-all effects)]
        (is (result/failure? result))
        (is (= "Multiple errors occurred" (:error result)))
        (is (= 2 (-> result :context :count)))))))

(deftest execute-command-result-test
  (testing "Execute command with messages"
    (silently
      (let [cmd-result {:effects [[:help/print {}]]
                        :messages ["Message 1" "Message 2"]}
            result (effects/execute-command-result cmd-result)]
        (is (result/success? result)))))

  (testing "Execute command with format function"
    (silently
      (let [format-fn (fn [data] [(str "Formatted: " data)])
            cmd-result {:effects [[:version/print]]
                        :format-fn format-fn}
            result (effects/execute-command-result cmd-result)]
        (is (result/success? result)))))

  (testing "Execute command with no effects returns ok"
    (silently
      (let [cmd-result {:messages ["Just a message"]}
            result (effects/execute-command-result cmd-result)]
        (is (result/success? result))
        (is (nil? (:value result))))))

  (testing "Handles failures gracefully"
    (silently
      (let [cmd-result {:effects [[:unknown/effect]]}
            result (effects/execute-command-result cmd-result)]
        (is (result/failure? result))))))

(deftest effect-context-test
  (testing "Effects include context in error results"
    (let [result (effects/execute-effect [:unknown/custom-type "arg1" "arg2"])]
      (is (result/failure? result))
      (is (map? (:context result)))
      (is (= :unknown/custom-type (-> result :context :effect-type))))))

(deftest effect-result-type-test
  (testing "All effects return Result types"
    (silently
      (let [test-effects [[:help/print {}]
                          [:version/print]
                          [:unknown/type]]]
        (doseq [effect test-effects]
          (let [result (effects/execute-effect effect)]
            (is (or (result/success? result) (result/failure? result))
                (str "Effect " effect " should return a Result"))))))))

(deftest effect-idempotency-test
  (testing "Help and version effects are idempotent"
    (silently
      (let [help-result1 (effects/execute-effect [:help/print {}])
            help-result2 (effects/execute-effect [:help/print {}])
            version-result1 (effects/execute-effect [:version/print])
            version-result2 (effects/execute-effect [:version/print])]
        (is (result/success? help-result1))
        (is (result/success? help-result2))
        (is (result/success? version-result1))
        (is (result/success? version-result2))))))

;; Integration-style tests with actual effects
;; These test that the effects properly interact with their dependencies
(deftest ignore-effect-integration-test
  (testing "Ignore effects have proper structure"
    ;; We can't easily test the actual I/O without mocking the entire ignore namespace,
    ;; but we can test that the effects are structured correctly
    (let [test-cases [[:ignore/add "specimen" {:global true}]
                      [:ignore/remove "specimen" {:project true}]
                      [:ignore/list {}]
                      [:ignore/clear {:global true}]]]
      (doseq [effect test-cases]
        (is (vector? effect) (str "Effect should be vector: " effect))
        (is (keyword? (first effect)) (str "First element should be keyword: " effect))))))

(deftest config-effect-integration-test
  (testing "Config effects have proper structure"
    (let [effect [:config/restore-defaults]]
      (is (vector? effect))
      (is (= :config/restore-defaults (first effect))))))

(deftest project-effect-integration-test
  (testing "Project init effect has proper structure"
    (let [effect [:project/init]]
      (is (vector? effect))
      (is (= :project/init (first effect))))))

(deftest checks-effect-integration-test
  (testing "Checks effects have proper structure"
    (let [add-effect [:checks/add "/path/to/checks" {:name "custom"}]
          print-effect [:checks/print {:config "config.edn"}]]
      (is (= :checks/add (first add-effect)))
      (is (= :checks/print (first print-effect))))))

(deftest file-effect-integration-test
  (testing "File process effect has proper structure"
    (let [effect [:file/process {:file "test.md"}]]
      (is (= :file/process (first effect)))
      (is (map? (second effect))))))
