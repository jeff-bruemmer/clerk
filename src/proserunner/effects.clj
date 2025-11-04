(ns proserunner.effects
  "Executes effect descriptions using multimethods.

  Effects are vectors [effect-type & args] dispatched to handlers.
  All effects return Results (Success/Failure) for error handling.

  To add effects: define a keyword, add defmethod for execute-effect."
  (:gen-class)
  (:require [proserunner
             [checks :as checks]
             [commands :as cmd]
             [config :as conf]
             [custom-checks :as custom]
             [ignore :as ignore]
             #_{:clj-kondo/ignore [:unused-namespace]}
             [process :as process]  ; Required for AOT compilation (loaded via requiring-resolve at line 97)
             [project-config :as project-conf]
             [result :as result]
             [shipping :as ship]
             [version :as ver]]
            [clojure.string :as str]))

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

(defmethod execute-effect :ignore/add-issues
  [[_ issue-nums opts]]
  (result/try-result-with-context
   #(let [;; Import vet namespace
          vet (requiring-resolve 'proserunner.vet/compute-or-cached)
          ship-prep (requiring-resolve 'proserunner.shipping/prep)
          ;; Re-run proserunner to get the issues with same numbering
          vet-result (vet opts)]
      (if (result/failure? vet-result)
        vet-result
        (let [;; Extract and process results exactly like the output pipeline does
              payload (:value vet-result)
              results-record (:results payload)
              lines-with-issues (:results results-record)
              ;; Flatten, prep, filter, and sort - same as shipping/process-results
              all-prepped-issues (mapcat ship-prep lines-with-issues)
              ;; Load ignore set for filtering (to match what user saw)
              ignore-set (if (:skip-ignore opts)
                           #{}
                           (if-let [project-ignore (:project-ignore payload)]
                             project-ignore
                             (set (checks/load-ignore-set! (:check-dir payload) 
                                                           (:ignore (:config payload))))))
              ;; Filter and sort to get exact same issues user saw
              filtered-issues (if (empty? ignore-set)
                                all-prepped-issues
                                (ignore/filter-issues all-prepped-issues ignore-set))
              sorted-issues (sort-by (juxt :file :line-num :col-num) filtered-issues)
              total-issues (count sorted-issues)
              ;; Filter to only the requested issue numbers
              selected-issues (cmd/filter-issues-by-numbers sorted-issues issue-nums)
              ;; Check for out-of-range issue numbers
              valid-nums (set (range 1 (inc total-issues)))
              invalid-nums (remove valid-nums issue-nums)
              ;; Convert to contextual ignore entries
              ignore-entries (ignore/issues->ignore-entries selected-issues {:granularity :line})
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
                            (if (:project opts) "project" "global"))))
          {:count (count ignore-entries)
           :issues issue-nums
           :selected (count selected-issues)
           :invalid (vec invalid-nums)
           :target (if (:project opts) :project :global)})))
   {:effect :ignore/add-issues}))



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
