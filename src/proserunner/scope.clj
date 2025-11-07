(ns proserunner.scope
  "Shared utilities for determining project vs global scope.

  Provides basic scope resolution used by both commands and effects."
  (:gen-class))

(set! *warn-on-reflection* true)

(defn determine-target-keyword
  "Returns :project or :global based on opts.

  Looks at the :project key in opts map. Returns :project if true, :global otherwise."
  [opts]
  (if (:project opts) :project :global))

(defn get-target-info
  "Returns map with :target keyword and :msg-context string for display.

  Used by effects for basic scope determination.
  Returns:
  - :target - either :project or :global keyword
  - :msg-context - string for display (\"project\" or \"global\")"
  [opts]
  (let [target (determine-target-keyword opts)]
    {:target target
     :msg-context (if (= target :project) "project" "global")}))
