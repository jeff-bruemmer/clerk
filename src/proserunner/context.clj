(ns proserunner.context
  "Unified context resolution utilities for project vs global scope operations.
   Provides a single source of truth for determining operation context."
  (:require [proserunner.project-config :as project-config]
            [proserunner.config.manifest :as manifest])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn with-context
  "Determines operation context and executes function with context map.

   Provides unified context resolution for operations that can target
   either global (~/.proserunner/) or project (.proserunner/) scope.

   The function f receives a map with:
   - :target - either :global or :project
   - :start-dir - resolved start directory
   - :project-root - project root directory (only when :target is :project)

   Options:
   - :global - Force global scope (~/.proserunner/)
   - :project - Force project scope (.proserunner/)
   - :start-dir - Starting directory for project detection

   Example:
     (with-context {:project true}
       (fn [{:keys [target project-root]}]
         (if (= target :project)
           (do-project-operation project-root)
           (do-global-operation))))"
  [options f]
  (let [start-dir (project-config/resolve-start-dir options)
        target (project-config/determine-target options start-dir)
        context (cond-> {:target target
                         :start-dir start-dir}
                  (= target :project)
                  (assoc :project-root (:project-root (manifest/find start-dir))))]
    (f context)))
