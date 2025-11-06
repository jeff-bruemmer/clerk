(ns proserunner.effects
  "Executes effect descriptions using multimethods.

  Effects are vectors [effect-type & args] dispatched to handlers.
  All effects return Results (Success/Failure) for error handling.

  To add effects: define a keyword, add defmethod for execute-effect."
  (:gen-class)
  (:require [proserunner
             [commands :as cmd]
             [config :as conf]
             [custom-checks :as custom]
             [process :as process]
             [project-config :as project-conf]
             [result :as result]
             [scope :as scope]
             [version :as ver]
             [vet :as vet]]
            [proserunner.ignore.audit :as ignore-audit]
            [proserunner.ignore.context :as ignore-context]
            [proserunner.ignore.core :as ignore-core]
            [proserunner.ignore.file :as ignore-file]
            [proserunner.output.checks :as output-checks]
            [proserunner.output.prep :as prep]
            [proserunner.output.usage :as output-usage]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

;;; Generic helper functions

(defn effect-wrapper
  "Wraps a function with try-result-with-context for effect execution.

  Reduces boilerplate by providing a standard way to wrap effect functions.

  Args:
    f - Zero-arg function to execute
    effect-type - Keyword identifying the effect (e.g., :ignore/add)
    context-map - Map of context data to include in errors

  Returns: Result (Success with function return value, or Failure with context)

  Example:
    (effect-wrapper
      #(do-something specimen opts)
      :ignore/add
      {:specimen specimen})"
  [f effect-type context-map]
  (result/try-result-with-context
   f
   (assoc context-map :effect effect-type)))

;;; Helper functions for ignore effects

(defn- get-project-root
  "Returns project root directory from opts or system property."
  [opts]
  (or (:start-dir opts) (System/getProperty "user.dir")))

(defn- run-vet-and-get-issues
  "Runs vetting and returns flat list of prepped issues.
   Returns Result with vector of issues or propagates vet failure."
  [opts]
  (let [vet-result (vet/compute-or-cached opts)]
    (if (result/failure? vet-result)
      vet-result
      (let [payload (:value vet-result)
            results-record (:results payload)
            lines-with-issues (:results results-record)
            all-prepped-issues (mapcat prep/prep lines-with-issues)]
        (result/ok (vec all-prepped-issues))))))

(defn- read-ignores-by-scope
  "Reads current ignores from project or global scope.
   Returns map with :ignore (set) and :ignore-issues (vector)."
  [opts]
  (if (:project opts)
    (let [project-root (get-project-root opts)
          config (project-conf/read project-root)]
      {:ignore (or (:ignore config) #{})
       :ignore-issues (or (:ignore-issues config) [])})
    (ignore-file/read)))

(defn- write-ignores-by-scope!
  "Writes ignores map to project or global scope.
   Takes map with :ignore (set) and :ignore-issues (vector)."
  [ignores opts]
  (if (:project opts)
    (let [project-root (get-project-root opts)
          config (project-conf/read project-root)]
      (project-conf/write! project-root (assoc config
                                                :ignore (:ignore ignores)
                                                :ignore-issues (:ignore-issues ignores))))
    (ignore-file/write! ignores)))

(defn update-ignores-by-scope!
  "Applies an update function to ignores in project or global scope.

  Reduces the read-modify-write boilerplate for ignore operations.

  Args:
    update-fn - Function taking ignores map and returning updated ignores map
    opts - Options map with :project key to determine scope

  Returns: Updated ignores map (same format as returned by read-ignores-by-scope)

  Example:
    (update-ignores-by-scope!
      #(update % :ignore conj \"new-pattern\")
      {:project false})"
  [update-fn opts]
  (let [current-ignores (read-ignores-by-scope opts)
        updated-ignores (update-fn current-ignores)]
    (write-ignores-by-scope! updated-ignores opts)
    updated-ignores))


(defn- extract-prepped-issues
  "Extracts and preps issues from vet result payload."
  [payload]
  (let [results-record (:results payload)
        lines-with-issues (:results results-record)]
    (mapcat prep/prep lines-with-issues)))

(defn- load-ignore-map-for-filtering
  "Loads ignore map for filtering, matching what the user saw in output.
  Returns map with :ignore (set) and :ignore-issues (vector)."
  [payload opts]
  (if (:skip-ignore opts)
    {:ignore #{} :ignore-issues []}
    {:ignore (or (:project-ignore payload) #{})
     :ignore-issues (or (:project-ignore-issues payload) [])}))

(defn- select-and-validate-issue-numbers
  "Filters and validates issues by requested numbers.
  Returns map with :selected-issues, :valid-nums, and :invalid-nums."
  [all-issues ignore-map issue-nums]
  (let [filtered-issues (if (and (empty? (:ignore ignore-map)) (empty? (:ignore-issues ignore-map)))
                          all-issues
                          (ignore-core/filter-issues all-issues ignore-map))
        sorted-issues (sort-by (juxt :file :line-num :col-num) filtered-issues)
        total-issues (count sorted-issues)
        selected-issues (cmd/filter-issues-by-numbers sorted-issues issue-nums)
        valid-nums (set (range 1 (inc total-issues)))
        invalid-nums (remove valid-nums issue-nums)]
    {:selected-issues selected-issues
     :total-issues total-issues
     :valid-nums valid-nums
     :invalid-nums invalid-nums}))

(defmulti execute-effect
  "Executes an effect and returns a Result.

  Effect format: [effect-type & args]
  Returns: Success or Failure"
  (fn [effect] (first effect)))

;; Ignore management effects
(defmethod execute-effect :ignore/add
  [[_ specimen opts]]
  (effect-wrapper
   #(do
      (ignore-context/add! specimen opts)
      (merge {:specimen specimen} (scope/get-target-info opts)))
   :ignore/add
   {:specimen specimen}))

(defmethod execute-effect :ignore/remove
  [[_ specimen opts]]
  (effect-wrapper
   #(do
      (ignore-context/remove! specimen opts)
      (merge {:specimen specimen} (scope/get-target-info opts)))
   :ignore/remove
   {:specimen specimen}))

(defmethod execute-effect :ignore/list
  [[_ opts]]
  (effect-wrapper
   #(ignore-context/list opts)
   :ignore/list
   {}))

(defmethod execute-effect :ignore/clear
  [[_ opts]]
  (effect-wrapper
   #(do
      (ignore-context/clear! opts)
      (scope/get-target-info opts))
   :ignore/clear
   {}))

(defmethod execute-effect :ignore/add-all
  [[_ opts]]
  (effect-wrapper
   (fn []
     (let [issues-result (run-vet-and-get-issues opts)]
       (if (result/failure? issues-result)
         issues-result
         (let [issues (:value issues-result)
               ignore-entries (ignore-core/issues->entries issues {:granularity :line})
               _ (update-ignores-by-scope!
                  (fn [ignores]
                    (update ignores :ignore-issues
                            #(vec (concat % ignore-entries))))
                  opts)
               {:keys [msg-context] :as target-info} (scope/get-target-info opts)]
           (println (format "Added %d contextual ignore(s) to %s ignore list."
                           (count ignore-entries)
                           msg-context))
           (merge {:count (count ignore-entries)} target-info)))))
   :ignore/add-all
   {}))

(defmethod execute-effect :ignore/add-issues
  [[_ issue-nums opts]]
  (effect-wrapper
   (fn []
     (let [vet-result (vet/compute-or-cached opts)]
       (if (result/failure? vet-result)
         vet-result
         (let [payload (:value vet-result)
               all-prepped-issues (extract-prepped-issues payload)
               ignore-map (load-ignore-map-for-filtering payload opts)
               {:keys [selected-issues total-issues valid-nums invalid-nums]}
               (select-and-validate-issue-numbers all-prepped-issues ignore-map issue-nums)
               ignore-entries (ignore-core/issues->entries selected-issues {:granularity :line})
               _ (update-ignores-by-scope!
                  (fn [ignores]
                    (update ignores :ignore-issues
                            #(vec (concat % ignore-entries))))
                  opts)
               {:keys [msg-context] :as target-info} (scope/get-target-info opts)]
           ;; Provide feedback
           (when (seq invalid-nums)
             (println (format "Warning: Issue numbers out of range (1-%d): %s"
                             total-issues
                             (str/join ", " invalid-nums))))
           (if (empty? selected-issues)
             (println "No valid issue numbers provided. Nothing was ignored.")
             (println (format "Added %d contextual ignore(s) for issues %s to %s ignore list."
                             (count ignore-entries)
                             (str/join ", " (filter valid-nums issue-nums))
                             msg-context)))
           (merge {:count (count ignore-entries)
                   :issues issue-nums
                   :selected (count selected-issues)
                   :invalid (vec invalid-nums)}
                  target-info)))))
   :ignore/add-issues
   {}))



(defmethod execute-effect :ignore/audit
  [[_ opts]]
  (effect-wrapper
   #(let [ignores (read-ignores-by-scope opts)
          audit-result (ignore-audit/audit ignores)]
      audit-result)
   :ignore/audit
   {}))

(defmethod execute-effect :ignore/clean
  [[_ opts]]
  (effect-wrapper
   #(let [ignores (read-ignores-by-scope opts)
          cleaned-ignores (ignore-audit/remove-stale ignores)
          removed-count (- (count (:ignore-issues ignores))
                           (count (:ignore-issues cleaned-ignores)))
          {:keys [msg-context] :as target-info} (scope/get-target-info opts)]
      ;; Write cleaned ignores
      (write-ignores-by-scope! cleaned-ignores opts)
      (println (format "Removed %d stale ignore(s) from %s ignore list."
                      removed-count
                      msg-context))
      (merge {:removed removed-count} target-info))
   :ignore/clean
   {}))

;; Configuration effects
(defmethod execute-effect :config/restore-defaults
  [[_]]
  (let [restore-result (conf/restore-defaults!)]
    (if (result/success? restore-result)
      (result/ok {:restored true})
      restore-result)))

;; Project initialization effects
(defmethod execute-effect :project/init
  [[_]]
  (effect-wrapper
   #(let [cwd (System/getProperty "user.dir")
          result (project-conf/init! cwd)]
      {:cwd cwd :result result})
   :project/init
   {}))

;; Custom checks effects
(defmethod execute-effect :checks/add
  [[_ source opts]]
  (effect-wrapper
   #(do
      (custom/add-checks source opts)
      {:source source :opts opts})
   :checks/add
   {:source source}))

(defmethod execute-effect :checks/print
  [[_ config]]
  (effect-wrapper
   #(do
      (output-checks/print config)
      {:config config})
   :checks/print
   {}))

;; File processing effects
(defmethod execute-effect :file/process
  [[_ opts]]
  (effect-wrapper
   #(process/proserunner opts)
   :file/process
   {}))

;; Help and version effects
(defmethod execute-effect :help/print
  [[_ opts & [title]]]
  (result/ok
   (if title
     (output-usage/print opts title)
     (output-usage/print opts))))

(defmethod execute-effect :version/print
  [[_]]
  (result/ok
   (println "Proserunner version: " ver/number)))

;; Default handler for unknown effects
(defmethod execute-effect :default
  [[effect-type & _]]
  (result/err
   (str "Unknown effect type: " effect-type)
   {:effect-type effect-type}))

;; Effect execution orchestration
(defn execute-effects
  "Executes a sequence of effects, short-circuiting on first failure.

  Returns Result containing vector of effect results, or first Failure."
  [effects]
  (reduce
   (fn [acc effect]
     (result/bind acc
                  (fn [results]
                    (let [result (execute-effect effect)]
                      (if (result/success? result)
                        (result/ok (conj results (:value result)))
                        result)))))
   (result/ok [])
   effects))

(defn execute-effects-all
  "Executes all effects, collecting all failures.

  Returns Success with all results if all succeed,
  or Failure with collected errors if any fail."
  [effects]
  (let [results (mapv execute-effect effects)]
    (result/combine-all-errors results)))

(defn execute-command-result
  "Executes a command result from proserunner.commands/dispatch-command.

  Takes command result map with :effects, :messages, :format-fn, :error
  Executes effects and prints messages or formatted output.

  Returns the last effect result (or nil if no effects).
  If :error is present in command result, returns failure immediately."
  [{:keys [effects messages format-fn error] :as _cmd-result}]
  (if error
    ;; Command returned an error - return failure immediately
    (result/err error)
    ;; No error - proceed with effects
    (let [effect-result (if (seq effects)
                          (execute-effects effects)
                          (result/ok nil))]
      (if (result/success? effect-result)
        (do
          ;; Print messages if provided
          (when (seq messages)
            (doseq [msg messages]
              (println msg)))
          ;; Or use format-fn if provided
          (when (and format-fn (seq (:value effect-result)))
            (doseq [msg (format-fn (last (:value effect-result)))]
              (println msg)))
          effect-result)
        ;; Print error and return failure
        (do
          (result/print-failure effect-result)
          effect-result)))))
