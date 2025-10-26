(ns editors.registry
  "Dynamic editor registry for extensibility."
  (:gen-class))

(set! *warn-on-reflection* true)

(def editors
  "Registry of editor functions by kind."
  (atom {}))

(defn register-editor!
  "Register an editor function for a given kind.
  The editor-fn should have signature: [line check] -> line"
  [kind editor-fn]
  (swap! editors assoc kind editor-fn))

(defn unregister-editor!
  "Unregister an editor (useful for testing and customization)."
  [kind]
  (swap! editors dissoc kind))

(defn get-editor
  "Get an editor function by kind."
  [kind]
  (get @editors kind))

(defn dispatch
  "Dispatch to an editor using the registry.
  Throws an exception with helpful error if editor kind is unknown."
  [line check]
  (let [{:keys [kind]} check
        editor (get-editor kind)]
    (if editor
      (editor line check)
      (throw (ex-info (str "Unknown editor type: " kind)
                      {:kind kind :available (keys @editors)})))))
