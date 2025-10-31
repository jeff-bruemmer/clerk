(ns proserunner.commands-test
  "Tests for command handlers."
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.commands :as cmd]
            [proserunner.result :as result]))

(deftest determine-command-test
  (testing "Determines add-ignore command"
    (is (= :add-ignore (cmd/determine-command {:add-ignore "foo"}))))

  (testing "Determines remove-ignore command"
    (is (= :remove-ignore (cmd/determine-command {:remove-ignore "bar"}))))

  (testing "Determines list-ignored command"
    (is (= :list-ignored (cmd/determine-command {:list-ignored true}))))

  (testing "Determines clear-ignored command"
    (is (= :clear-ignored (cmd/determine-command {:clear-ignored true}))))

  (testing "Determines restore-defaults command"
    (is (= :restore-defaults (cmd/determine-command {:restore-defaults true}))))

  (testing "Determines init-project command"
    (is (= :init-project (cmd/determine-command {:init-project true}))))

  (testing "Determines add-checks command"
    (is (= :add-checks (cmd/determine-command {:add-checks "/path/to/checks"}))))

  (testing "Determines file command"
    (is (= :file (cmd/determine-command {:file "/path/to/file.md"}))))

  (testing "Determines checks command"
    (is (= :checks (cmd/determine-command {:checks true}))))

  (testing "Determines help command"
    (is (= :help (cmd/determine-command {:help true}))))

  (testing "Determines version command"
    (is (= :version (cmd/determine-command {:version true}))))

  (testing "Defaults to :default command"
    (is (= :default (cmd/determine-command {})))))

(deftest validate-options-test
  (testing "Valid options pass validation"
    (let [opts {:file "test.md"}
          result (cmd/validate-options opts)]
      (is (result/success? result))
      (is (= opts (:value result)))))

  (testing "Parallel files without sequential lines fails"
    (let [opts {:parallel-files true}
          result (cmd/validate-options opts)]
      (is (result/failure? result))
      (is (re-find #"parallel" (:error result)))))

  (testing "Both global and project flags fails"
    (let [opts {:global true :project true}
          result (cmd/validate-options opts)]
      (is (result/failure? result))
      (is (re-find #"global.*project" (:error result)))))

  (testing "Parallel files with sequential lines is valid"
    (let [opts {:parallel-files true :sequential-lines true}
          result (cmd/validate-options opts)]
      (is (result/success? result)))))

(deftest handle-add-ignore-test
  (testing "Handle add-ignore produces correct effects"
    (let [opts {:add-ignore "specimen1" :global true}
          result (cmd/handle-add-ignore opts)]
      (is (= [[:ignore/add "specimen1" opts]] (:effects result)))
      (is (some? (:messages result)))
      (is (re-find #"global" (first (:messages result))))))

  (testing "Handle add-ignore for project"
    (let [opts {:add-ignore "specimen2" :project true}
          result (cmd/handle-add-ignore opts)]
      (is (= [[:ignore/add "specimen2" opts]] (:effects result)))
      (is (re-find #"project" (first (:messages result)))))))

(deftest handle-remove-ignore-test
  (testing "Handle remove-ignore produces correct effects"
    (let [opts {:remove-ignore "specimen1" :global true}
          result (cmd/handle-remove-ignore opts)]
      (is (= [[:ignore/remove "specimen1" opts]] (:effects result)))
      (is (some? (:messages result))))))

(deftest handle-list-ignored-test
  (testing "Handle list-ignored produces correct effects"
    (let [opts {:list-ignored true}
          result (cmd/handle-list-ignored opts)]
      (is (= [[:ignore/list opts]] (:effects result)))
      (is (fn? (:format-fn result))))))

(deftest handle-clear-ignored-test
  (testing "Handle clear-ignored produces correct effects"
    (let [opts {:clear-ignored true :global true}
          result (cmd/handle-clear-ignored opts)]
      (is (= [[:ignore/clear opts]] (:effects result)))
      (is (some? (:messages result))))))

(deftest handle-restore-defaults-test
  (testing "Handle restore-defaults produces correct effects"
    (let [result (cmd/handle-restore-defaults {})]
      (is (= [[:config/restore-defaults]] (:effects result)))
      (is (some? (:messages result))))))

(deftest handle-init-project-test
  (testing "Handle init-project produces correct effects"
    (let [result (cmd/handle-init-project {})]
      (is (= [[:project/init]] (:effects result)))
      (is (fn? (:format-fn result))))))

(deftest handle-add-checks-test
  (testing "Handle add-checks produces correct effects"
    (let [opts {:add-checks "/path/to/checks" :name "custom" :global true}
          result (cmd/handle-add-checks opts)]
      (is (= [[:checks/add "/path/to/checks" {:name "custom" :global true}]]
             (:effects result)))
      (is (some? (:messages result))))))

(deftest handle-file-test
  (testing "Handle file produces correct effects"
    (let [opts {:file "/path/to/file.md"}
          result (cmd/handle-file opts)]
      (is (= [[:file/process opts]] (:effects result))))))

(deftest handle-checks-test
  (testing "Handle checks produces correct effects"
    (let [opts {:checks true :config "config.edn"}
          result (cmd/handle-checks opts)]
      (is (= [[:checks/print "config.edn"]] (:effects result))))))

(deftest handle-help-test
  (testing "Handle help produces correct effects"
    (let [opts {:help true}
          result (cmd/handle-help opts)]
      (is (= [[:help/print opts]] (:effects result))))))

(deftest handle-version-test
  (testing "Handle version produces correct effects"
    (let [result (cmd/handle-version {})]
      (is (= [[:version/print]] (:effects result))))))

(deftest handle-default-test
  (testing "Handle default produces correct effects"
    (let [opts {:summary "..."}
          result (cmd/handle-default opts)]
      (is (= [[:help/print opts "P R O S E R U N N E R"]] (:effects result))))))

(deftest dispatch-command-test
  (testing "Dispatch command returns effect description"
    (let [opts {:add-ignore "test-specimen" :global true}
          result (cmd/dispatch-command opts)]
      (is (= :add-ignore (:command result)))
      (is (some? (:effects result)))
      (is (= [[:ignore/add "test-specimen" opts]] (:effects result)))))

  (testing "Dispatch handles multiple command types"
    (let [test-cases [{:opts {:help true}
                       :expected-cmd :help
                       :expected-effect-type :help/print}
                      {:opts {:version true}
                       :expected-cmd :version
                       :expected-effect-type :version/print}
                      {:opts {:list-ignored true}
                       :expected-cmd :list-ignored
                       :expected-effect-type :ignore/list}]]
      (doseq [{:keys [opts expected-cmd expected-effect-type]} test-cases]
        (let [result (cmd/dispatch-command opts)]
          (is (= expected-cmd (:command result)))
          (is (= expected-effect-type (-> result :effects first first))))))))

(deftest pure-handler-testability
  (testing "Pure handlers can be tested without I/O"
    (let [opts {:add-ignore "test" :global true}
          result (cmd/handle-add-ignore opts)]
      ;; No side effects - just data
      (is (map? result))
      (is (vector? (:effects result)))
      (is (every? vector? (:effects result)))
      ;; Can inspect effects without executing them
      (let [[effect-type specimen effect-opts] (first (:effects result))]
        (is (= :ignore/add effect-type))
        (is (= "test" specimen))
        (is (= opts effect-opts))))))

(deftest handler-registry-test
  (testing "All command types have handlers"
    (let [commands [:add-ignore :remove-ignore :list-ignored :clear-ignored
                    :restore-defaults :init-project :add-checks
                    :file :checks :help :version :default]]
      (doseq [cmd commands]
        (is (contains? cmd/handlers cmd)
            (str "Missing handler for: " cmd))
        (is (fn? (get cmd/handlers cmd))
            (str "Handler for " cmd " is not a function"))))))

(deftest command-effect-structure-test
  (testing "All handlers return consistent structure"
    (let [test-cases [{:handler cmd/handle-add-ignore
                       :opts {:add-ignore "test" :global true}}
                      {:handler cmd/handle-remove-ignore
                       :opts {:remove-ignore "test" :global true}}
                      {:handler cmd/handle-file
                       :opts {:file "test.md"}}]]
      (doseq [{:keys [handler opts]} test-cases]
        (let [result (handler opts)]
          (is (map? result))
          (is (or (:effects result) (:messages result) (:format-fn result))
              "Handler should return at least one of: effects, messages, or format-fn"))))))
