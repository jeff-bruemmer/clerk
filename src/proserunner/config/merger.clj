(ns proserunner.config.merger
  "Config merging logic for global + project configs."
  (:gen-class))

(set! *warn-on-reflection* true)

(defn- merge-ignores
  "Merges ignore sets based on ignore-mode."
  [global-ignore project-ignore ignore-mode]
  (if (= ignore-mode :extend)
    (into (or global-ignore #{}) (or project-ignore #{}))
    (or project-ignore #{})))

(defn- merge-ignore-issues
  "Merges ignore-issues vectors based on ignore-mode."
  [global-ignore-issues project-ignore-issues ignore-mode]
  (if (= ignore-mode :extend)
    (vec (concat (or global-ignore-issues []) (or project-ignore-issues [])))
    (or project-ignore-issues [])))

(defn merge-configs
  "Merges global and project configurations based on project settings.

   - If project :config-mode is :project-only, returns only project config
   - If :config-mode is :merged:
     - If :ignore-mode is :extend, unions global and project ignores/ignore-issues
     - If :ignore-mode is :replace, uses only project ignores/ignore-issues
     - Preserves project's :checks while keeping global :checks for later reference resolution"
  [global-config project-config]
  (if (= (:config-mode project-config) :project-only)
    project-config
    (assoc project-config
           :ignore (merge-ignores (:ignore global-config)
                                 (:ignore project-config)
                                 (:ignore-mode project-config))
           :ignore-issues (merge-ignore-issues (:ignore-issues global-config)
                                               (:ignore-issues project-config)
                                               (:ignore-mode project-config)))))
