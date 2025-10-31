(ns proserunner.effects
  "Executes effect descriptions using multimethods.

  Effects are vectors [effect-type & args] dispatched to handlers.
  All effects return Results (Success/Failure) for error handling.

  To add effects: define a keyword, add defmethod for execute-effect."
  (:gen-class)
  (:require [proserunner
             [config :as conf]
             [custom-checks :as custom]
             [ignore :as ignore]
             [project-config :as project-conf]
             [result :as result]
             [shipping :as ship]
             [version :as ver]]))

(set! *warn-on-reflection* true)

(defmulti execute-effect
  "Executes an effect and returns a Result.

  Effect format: [effect-type & args]
  Returns: Success or Failure"
  (fn [effect] (first effect)))

;; Ignore management effects
(defmethod execute-effect :ignore/add
  [[_ specimen opts]]
  (result/try-result-with-context
   #(do
      (ignore/add-to-ignore! specimen opts)
      {:specimen specimen :target (if (:project opts) :project :global)})
   {:effect :ignore/add :specimen specimen}))

(defmethod execute-effect :ignore/remove
  [[_ specimen opts]]
  (result/try-result-with-context
   #(do
      (ignore/remove-from-ignore! specimen opts)
      {:specimen specimen :target (if (:project opts) :project :global)})
   {:effect :ignore/remove :specimen specimen}))

(defmethod execute-effect :ignore/list
  [[_ opts]]
  (result/try-result-with-context
   #(ignore/list-ignored opts)
   {:effect :ignore/list}))

(defmethod execute-effect :ignore/clear
  [[_ opts]]
  (result/try-result-with-context
   #(do
      (ignore/clear-ignore! opts)
      {:target (if (:project opts) :project :global)})
   {:effect :ignore/clear}))

;; Configuration effects
(defmethod execute-effect :config/restore-defaults
  [[_]]
  (result/try-result-with-context
   #(do
      (conf/restore-defaults!)
      {:restored true})
   {:effect :config/restore-defaults}))

;; Project initialization effects
(defmethod execute-effect :project/init
  [[_]]
  (result/try-result-with-context
   #(let [cwd (System/getProperty "user.dir")
          result (project-conf/init-project-config! cwd)]
      {:cwd cwd :result result})
   {:effect :project/init}))

;; Custom checks effects
(defmethod execute-effect :checks/add
  [[_ source opts]]
  (result/try-result-with-context
   #(do
      (custom/add-checks source opts)
      {:source source :opts opts})
   {:effect :checks/add :source source}))

(defmethod execute-effect :checks/print
  [[_ config]]
  (result/try-result-with-context
   #(do
      (ship/print-checks config)
      {:config config})
   {:effect :checks/print}))

;; File processing effects
(defmethod execute-effect :file/process
  [[_ opts]]
  (result/try-result-with-context
   #(let [process-fn (requiring-resolve 'proserunner.process/proserunner)]
      (process-fn opts))
   {:effect :file/process}))

;; Help and version effects
(defmethod execute-effect :help/print
  [[_ opts & [title]]]
  (result/ok
   (if title
     (ship/print-usage opts title)
     (ship/print-usage opts))))

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

  Takes command result map with :effects, :messages, :format-fn
  Executes effects and prints messages or formatted output.

  Returns the last effect result (or nil if no effects)."
  [{:keys [effects messages format-fn] :as _cmd-result}]
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
        effect-result))))
