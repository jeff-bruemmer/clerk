(ns editors.registry
  "Dynamic editor registry for extensible check processing.

   Editors are functions that apply checks to lines of text. Each editor
   handles a specific check 'kind' (e.g., 'existence', 'regex', 'repetition').

   Registration lifecycle:
   1. Editor function created with signature: [line check] -> line
   2. Registered via register-editor! with unique kind string
   3. Dispatched at runtime via dispatch function based on check's :kind

   Thread safety:
   - Uses atom for concurrent read/write access
   - Safe to register editors and dispatch simultaneously
   - Typically editors registered at startup (see proserunner.vet)

   Extensibility:
   - Add new check types by registering new editors
   - No changes to core processing logic required
   - See existing editors in editors.* namespaces for examples"
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
