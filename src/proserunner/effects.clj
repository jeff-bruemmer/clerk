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
             #_{:clj-kondo/ignore [:unused-namespace]}
             [process :as process]  ; Required for AOT compilation (loaded via requiring-resolve at line 97)
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

(defmethod execute-effect :ignore/add-all
  [[_ opts]]
  (result/try-result-with-context
   #(let [;; Import vet namespace
          vet (requiring-resolve 'proserunner.vet/compute-or-cached)
          ship (requiring-resolve 'proserunner.shipping/prep)
          ;; Process the file to get all current issues  
          vet-result (vet opts)]
      (if (result/failure? vet-result)
        vet-result
        (let [;; Extract results from the vet result
              payload (:value vet-result)
              results-record (:results payload)
              lines-with-issues (:results results-record)
              ;; Flatten all issues from all lines and prep them (adds line-num)
              all-prepped-issues (mapcat ship lines-with-issues)
              ;; Convert to contextual ignore entries
              ignore-entries (ignore/issues->ignore-entries all-prepped-issues {:granularity :line})
              ;; Read current ignores
              current-ignores (if (:project opts)
                               (let [project-root (or (:start-dir opts) (System/getProperty "user.dir"))
                                     config (project-conf/read project-root)]
                                 (or (:ignore config) #{}))
                               (ignore/read-ignore-file))
              ;; Merge new entries with existing
              updated-ignores (into (or current-ignores #{}) ignore-entries)]
          ;; Write updated ignores
          (if (:project opts)
            (let [project-root (or (:start-dir opts) (System/getProperty "user.dir"))
                  config (project-conf/read project-root)]
              (project-conf/write! project-root (assoc config :ignore updated-ignores)))
            (ignore/write-ignore-file! updated-ignores))
          (println (format "Added %d contextual ignore(s) to %s ignore list."
                          (count ignore-entries)
                          (if (:project opts) "project" "global")))
          {:count (count ignore-entries)
           :target (if (:project opts) :project :global)})))
   {:effect :ignore/add-all}))

(defmethod execute-effect :ignore/audit
  [[_ opts]]
  (result/try-result-with-context
   #(let [ignores (if (:project opts)
                   (let [project-root (or (:start-dir opts) (System/getProperty "user.dir"))
                         config (project-conf/read project-root)]
                     (or (:ignore config) #{}))
                   (ignore/read-ignore-file))
          audit-result (ignore/audit-ignores ignores)]
      audit-result)
   {:effect :ignore/audit}))

(defmethod execute-effect :ignore/clean
  [[_ opts]]
  (result/try-result-with-context
   #(let [ignores (if (:project opts)
                   (let [project-root (or (:start-dir opts) (System/getProperty "user.dir"))
                         config (project-conf/read project-root)]
                     (or (:ignore config) #{}))
                   (ignore/read-ignore-file))
          cleaned-ignores (ignore/remove-stale-ignores ignores)
          removed-count (- (count ignores) (count cleaned-ignores))]
      ;; Write cleaned ignores
      (if (:project opts)
        (let [project-root (or (:start-dir opts) (System/getProperty "user.dir"))
              config (project-conf/read project-root)]
          (project-conf/write! project-root (assoc config :ignore (set cleaned-ignores))))
        (ignore/write-ignore-file! cleaned-ignores))
      (println (format "Removed %d stale ignore(s) from %s ignore list."
                      removed-count
                      (if (:project opts) "project" "global")))
      {:removed removed-count
       :target (if (:project opts) :project :global)})
   {:effect :ignore/clean}))

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
  (result/try-result-with-context
   #(let [cwd (System/getProperty "user.dir")
          result (project-conf/init! cwd)]
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
