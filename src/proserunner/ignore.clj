(ns proserunner.ignore
  "Functions for managing ignored specimens."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [proserunner.context :as context]
            [proserunner.edn-utils :as edn-utils]
            [proserunner.file-utils :as file-utils]
            [proserunner.project-config :as project-config]
            [proserunner.result :as result]
            [proserunner.system :as sys]))

(set! *warn-on-reflection* true)

;;; Global Ignore File Management

(defn ignore-file-path
  "Returns the path to the ignore file."
  []
  (sys/filepath ".proserunner" "ignore.edn"))

(defn read-ignore-file
  "Reads the ignore file and returns a set of ignored specimens."
  []
  (let [ignore-path (ignore-file-path)]
    (if (.exists (io/file ignore-path))
      (let [result (edn-utils/read-edn-file ignore-path)]
        (if (result/success? result)
          (set (:value result))
          #{}))
      #{})))

(defn write-ignore-file!
  "Writes the set of ignored specimens to the ignore file atomically."
  [ignored-set]
  (let [ignore-path (ignore-file-path)]
    (file-utils/ensure-parent-dir ignore-path)
    (file-utils/atomic-spit ignore-path (pr-str (vec (sort ignored-set))))))

;;; Set Operations

(defn- add-to-set
  "Adds specimen to ignore set."
  [ignore-set specimen]
  (conj ignore-set specimen))

(defn- remove-from-set
  "Removes specimen from ignore set."
  [ignore-set specimen]
  (disj ignore-set specimen))

(defn- add-ignore-to-project!
  "Adds a specimen to project .proserunner/config.edn :ignore set."
  [specimen project-root]
  (let [config (project-config/read project-root)
        current-ignore (or (:ignore config) #{})
        updated-ignore (add-to-set current-ignore specimen)]
    (project-config/write! project-root (assoc config :ignore updated-ignore))))

(defn- remove-ignore-from-project!
  "Removes a specimen from project .proserunner/config.edn :ignore set."
  [specimen project-root]
  (let [config (project-config/read project-root)
        current-ignore (or (:ignore config) #{})
        updated-ignore (remove-from-set current-ignore specimen)]
    (project-config/write! project-root (assoc config :ignore updated-ignore))))

;;; Context-Aware Public API

(defn add-to-ignore!
  "Adds a specimen to the ignore list with context-aware targeting.

   Options:
   - :global - Force global scope (~/.proserunner/ignore.edn)
   - :project - Force project scope (.proserunner/config.edn :ignore)
   - :start-dir - Starting directory for project detection"
  ([specimen]
   (write-ignore-file! (add-to-set (read-ignore-file) specimen)))
  ([specimen options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (write-ignore-file! (add-to-set (read-ignore-file) specimen))
         ;; Add to project
         (add-ignore-to-project! specimen project-root))))))

(defn remove-from-ignore!
  "Removes a specimen from the ignore list with context-aware targeting.

   Options:
   - :global - Force global scope (~/.proserunner/ignore.edn)
   - :project - Force project scope (.proserunner/config.edn :ignore)
   - :start-dir - Starting directory for project detection"
  ([specimen]
   (write-ignore-file! (remove-from-set (read-ignore-file) specimen)))
  ([specimen options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (write-ignore-file! (remove-from-set (read-ignore-file) specimen))
         ;; Remove from project
         (remove-ignore-from-project! specimen project-root))))))

(defn list-ignored
  "Returns a sorted list of all ignored specimens with context-aware targeting.

   When in project context with :extend mode, shows both global and project ignores.
   When in project context with :replace mode, shows only project ignores.
   When in global context, shows only global ignores.

   Options:
   - :global - Force global scope
   - :project - Force project scope
   - :start-dir - Starting directory for project detection"
  ([]
   (sort (read-ignore-file)))
  ([options]
   (context/with-context options
     (fn [{:keys [target start-dir]}]
       (if (= target :global)
         (sort (read-ignore-file))
         ;; List from project context (respecting merge mode)
         (let [config (project-config/load start-dir)
               ;; The project-config loader already merges global and project ignores
               ;; based on :ignore-mode
               merged-ignore (:ignore config)]
           (sort merged-ignore)))))))

(defn clear-ignore!
  "Clears all ignored specimens."
  ([]
   (write-ignore-file! #{}))
  ([options]
   (context/with-context options
     (fn [{:keys [target project-root]}]
       (if (= target :global)
         (write-ignore-file! #{})
         ;; Clear project ignores
         (let [config (project-config/read project-root)]
           (project-config/write! project-root (assoc config :ignore #{}))))))))
