(ns proserunner.project-config
  "Functions for loading and managing project-level configuration."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [proserunner.config :as config]
            [proserunner.file-utils :as file-utils]
            [proserunner.system :as sys]
            [proserunner.validation :as v])
  (:import java.io.File))

(set! *warn-on-reflection* true)

;;; Constants

(def ^:private proserunner-dir-name ".proserunner")
(def ^:private config-file-name "config.edn")
(def ^:private checks-dir-name "checks")

;;; Path Helpers

(defn- manifest-path
  "Returns path to manifest file within a directory."
  ^File [dir]
  (io/file dir proserunner-dir-name config-file-name))

(defn- project-checks-dir-path
  "Returns path to .proserunner/checks/ directory within project root."
  ^File [project-root]
  (io/file project-root proserunner-dir-name checks-dir-name))

(defn project-config-path
  "Returns path string to project config.edn file.
   Public helper for building consistent paths to project config."
  ^String [project-root]
  (str project-root File/separator proserunner-dir-name File/separator config-file-name))

(defn project-proserunner-dir
  "Returns path string to project .proserunner directory.
   Public helper for building consistent paths."
  ^String [project-root]
  (str project-root File/separator proserunner-dir-name))

(defn project-checks-dir
  "Returns path string to project .proserunner/checks directory.
   Public helper for building consistent paths."
  ^String [project-root]
  (str project-root File/separator proserunner-dir-name File/separator checks-dir-name))

;;; Manifest Discovery

(defn- has-git-directory?
  "Checks if the given directory contains a .git folder."
  [^File dir]
  (.exists (io/file dir ".git")))

(defn- manifest-in-dir
  "Returns map with :manifest-path and :project-root if manifest exists in dir, nil otherwise."
  [^File dir]
  (let [manifest-file (manifest-path dir)]
    (when (.exists manifest-file)
      {:manifest-path (.getAbsolutePath manifest-file)
       :project-root (.getAbsolutePath dir)})))

(defn find-manifest
  "Walks up the directory tree from start-dir to find .proserunner/config.edn.
   Stops at the first .git directory encountered (project boundary).
   Skips the home directory (~/.proserunner/ is reserved for global config).
   Returns map with :manifest-path and :project-root, or nil if not found."
  [start-dir]
  (let [home-dir (sys/home-dir)]
    (loop [current-dir (io/file start-dir)]
      (when (and current-dir
                 (not= (.getAbsolutePath current-dir) home-dir))
        (or (manifest-in-dir current-dir)
            (when-not (has-git-directory? current-dir)
              (recur (.getParentFile current-dir))))))))

;;; Error Message Builders

(defn- file-not-found-error
  "Builds error for missing check source files."
  [source resolved-path]
  (str "Local check source does not exist: " source
       "\nResolved to: " resolved-path
       "\nPlease verify the path is correct."))

(defn- no-project-config-error
  "Builds error when --project flag used but no project exists."
  [start-dir]
  (str "No project configuration found"
       "\nSearched from: " start-dir
       "\nTo create a project configuration, run: proserunner init"))

(defn- conflicting-flags-error
  "Builds error for conflicting --global and --project flags."
  []
  "Cannot specify both --global and --project flags. Choose one.")

;;; Manifest Parsing and Validation

(defn check-entry-references-checks?
  "Returns true if entry references the 'checks' directory (string 'checks' or map with :directory 'checks').
   Public helper for use in custom-checks namespace."
  [entry]
  (or (= entry "checks")
      (and (map? entry) (= (:directory entry) "checks"))))

(defn- valid-check-entry?
  "Validates a single check entry (string reference or map definition)."
  [entry]
  (or (string? entry)
      (and (map? entry)
           (contains? entry :directory)
           (string? (:directory entry))
           (or (nil? (:files entry))
               (and (vector? (:files entry))
                    (every? string? (:files entry))))
           (or (nil? (:name entry))
               (string? (:name entry))))))

(defn- validate-entries-format
  "Validates that all entries in a check list are valid."
  [field-name]
  (fn [value]
    (let [invalid-entries (remove valid-check-entry? value)]
      (if (seq invalid-entries)
        (v/validation-error value
          (str field-name " contains invalid entries. Each entry must be either:\n"
               "  - A string (reference to global check)\n"
               "  - A map with :directory (required), and optional :files (vector) and :name (string)\n"
               "Invalid entries: " (pr-str invalid-entries)))
        (v/validation-ok value)))))

(defn- validate-check-entries
  "Custom validator for :checks field that validates all entries."
  [field-name]
  (v/chain
    (v/required field-name)
    (v/type-check field-name vector? "a vector")
    (v/not-empty-check field-name)
    (validate-entries-format field-name)))

(def ^:private manifest-validators
  "Declarative validator specifications for project manifest fields."
  {:checks
   (validate-check-entries ":checks")

   :ignore
   (v/with-default #{}
     (v/type-check ":ignore" set? "a set"))

   :ignore-mode
   (v/with-default :extend
     (v/enum-check ":ignore-mode" #{:extend :replace}))

   :config-mode
   (v/with-default :merged
     (v/enum-check ":config-mode" #{:merged :project-only}))})

(defn parse-manifest
  "Parses and validates a project manifest map.
   Throws with ALL validation errors if invalid.
   Returns validated map with defaults applied if valid."
  [manifest]
  (v/validate! manifest-validators manifest))

(defn read-manifest
  "Reads and parses a manifest file from the given path."
  [manifest-path]
  (try
    (let [content (slurp manifest-path)
          edn-data (edn/read-string content)]
      (parse-manifest edn-data))
    (catch Exception e
      (throw (ex-info (str "Failed to read manifest file: " manifest-path)
                      {:path manifest-path
                       :error (.getMessage e)}
                      e)))))

;;; Config Merging
;;;
;;; The config merge pipeline combines global (~/.proserunner/) and project (.proserunner/)
;;; configurations based on project settings. This section handles the complex merging logic.
;;;
;;; MERGE PIPELINE OVERVIEW:
;;;
;;; 1. Load Global Config (load-global-config)
;;;    - Reads ~/.proserunner/config.edn → :checks (list of check definitions)
;;;    - Reads ~/.proserunner/ignore.edn → :ignore (set of ignored specimens)
;;;    - Normalizes :checks entries (auto-discovers :files if missing)
;;;    - Global :checks contain directory paths relative to ~/.proserunner/
;;;
;;; 2. Parse Project Manifest (read-manifest → parse-manifest)
;;;    - Reads .proserunner/config.edn in project directory
;;;    - Contains :checks (vector of string references or map definitions)
;;;    - Contains :ignore (set), :ignore-mode (:extend/:replace), :config-mode (:merged/:project-only)
;;;    - Validates all fields with helpful error messages
;;;
;;; 3. Merge Configs (merge-configs)
;;;    - If :config-mode is :project-only → use only project config
;;;    - If :config-mode is :merged:
;;;      - Merge :ignore sets based on :ignore-mode (:extend unions, :replace uses only project)
;;;      - Preserve project's :checks for later resolution
;;;      - Keep global :checks available for reference resolution
;;;
;;; 4. Resolve Check Entries (resolve-check-entries)
;;;    - Converts check entries into concrete check definitions
;;;    - String reference (e.g., "default") → looks up in global :checks by :directory name
;;;    - Map definition → normalizes paths and auto-discovers :files if missing
;;;    - Special: {:directory "checks"} → resolves to .proserunner/checks/ in project root
;;;    - Path traversal protection for local directories
;;;
;;; 5. Build Final Config (build-project-config)
;;;    - Resolves all check entries from project config
;;;    - String references are resolved to their global definitions (preserving disabled checks)
;;;    - Returns final config with :checks, :ignore, and :source metadata
;;;
;;; EXAMPLE FLOWS:
;;;
;;; Example 1: Project references global checks
;;;   Global:  {:checks [{:directory "default" :files ["cliches"]}] :ignore #{"foo"}}
;;;   Project: {:checks ["default" {:directory "checks"}] :ignore #{"bar"} :ignore-mode :extend}
;;;   Result:  {:checks [global-default-check... project-checks...] :ignore #{"foo" "bar"}}
;;;
;;; Example 2: Project-only configuration
;;;   Global:  {:checks [...] :ignore #{"foo"}}
;;;   Project: {:checks [{:directory "checks"}] :ignore #{"bar"} :config-mode :project-only}
;;;   Result:  {:checks [project-checks-only] :ignore #{"bar"}}
;;;
;;; Example 3: Local check directory with explicit files
;;;   Project: {:checks [{:directory "./custom-checks" :files ["style"]}]}
;;;   Result:  Resolves ./custom-checks relative to project root, uses only "style" check

(defn- merge-ignores
  "Merges ignore sets based on ignore-mode."
  [global-ignore project-ignore ignore-mode]
  (if (= ignore-mode :extend)
    (into (or global-ignore #{}) (or project-ignore #{}))
    (or project-ignore #{})))

(defn merge-configs
  "Merges global and project configurations based on project settings.

   - If project :config-mode is :project-only, returns only project config
   - If :config-mode is :merged:
     - If :ignore-mode is :extend, unions global and project ignores
     - If :ignore-mode is :replace, uses only project ignores
     - Preserves project's :checks while keeping global :checks for later reference resolution"
  [global-config project-config]
  (if (= (:config-mode project-config) :project-only)
    project-config
    ;; In merged mode, preserve project's :checks and merge ignores
    ;; The :checks entries will be resolved later in build-project-config
    (assoc project-config
           :ignore (merge-ignores (:ignore global-config)
                                 (:ignore project-config)
                                 (:ignore-mode project-config)))))

;;; Check Entry Resolution

(defn- relative-path?
  "Checks if path is relative."
  [path]
  (not (.isAbsolute (io/file path))))

(defn- absolute-path?
  "Checks if path is already absolute."
  [path]
  (or (.isAbsolute (io/file path))
      (string/starts-with? path "~")))

(defn- get-edn-files
  "Gets all .edn files in a directory, returning their names without extension."
  [directory]
  (let [dir-file (io/file directory)]
    (when (.exists dir-file)
      (->> (.listFiles dir-file)
           (filter #(.isFile ^File %))
           (map #(.getName ^File %))
           (filter #(string/ends-with? % ".edn"))
           (map #(string/replace % #"\.edn$" ""))
           sort
           vec))))

(defn- resolve-local-path
  "Resolves a local path to absolute path, validating it exists and is safe.
   For relative paths, ensures the resolved path is within project-root to prevent directory traversal."
  [source project-root]
  (let [source-file (if (relative-path? source)
                      (io/file project-root source)
                      (io/file source))
        canonical-path (.getCanonicalPath source-file)]

    ;; Check path traversal for relative paths
    (when (and (relative-path? source) project-root)
      (let [canonical-root (.getCanonicalPath (io/file project-root))]
        (when-not (.startsWith canonical-path canonical-root)
          (throw (ex-info "Path traversal detected"
                         {:source source
                          :resolved-path canonical-path
                          :project-root canonical-root
                          :type :path-traversal})))))

    ;; Check existence
    (when-not (.exists source-file)
      (throw (ex-info (file-not-found-error source canonical-path)
                     {:source source
                      :resolved-path canonical-path
                      :type :file-not-found})))

    canonical-path))

(defn- make-directory-absolute
  "Converts a directory path to absolute, relative to base-dir if needed."
  [dir base-dir]
  (if (absolute-path? dir)
    dir
    (str (io/file base-dir dir))))

(defn- find-global-check-by-directory
  "Finds a global check entry that matches the given directory name.
   Returns the check entry with absolute paths, or nil if not found."
  [dir-name global-checks]
  (let [global-proserunner-dir (sys/filepath ".proserunner" "")]
    (when-let [check (first (filter #(= (:directory %) dir-name) global-checks))]
      (update check :directory #(make-directory-absolute % global-proserunner-dir)))))

(defn- generate-check-name
  "Generates a check name from a directory path."
  [directory]
  (str "Project checks: " (last (string/split directory (re-pattern (str File/separator))))))

(defn- resolve-check-directory
  "Resolves a check directory path, handling the special 'checks' shorthand."
  [directory project-root]
  (if (= directory "checks")
    (.getAbsolutePath (project-checks-dir-path project-root))
    (resolve-local-path directory project-root)))

(defn- normalize-project-check-entry
  "Normalizes a project check map entry.
   - Resolves :directory to absolute path
   - Auto-discovers :files if not present
   - Generates :name if not present"
  [check-entry project-root]
  (let [directory (resolve-check-directory (:directory check-entry) project-root)
        files (if (contains? check-entry :files)
               (:files check-entry)
               (get-edn-files directory))
        name (or (:name check-entry)
                (generate-check-name directory))]
    (when (seq files)
      {:name name
       :directory directory
       :files files})))

(defn- resolve-check-entry
  "Resolves a check entry (string reference or map) to concrete check config.
   - String: looks up in global-checks by directory name
   - Map: normalizes and resolves paths
   Returns check config map or nil if resolution fails."
  [entry global-checks project-root]
  (if (string? entry)
    ;; String reference - find matching global check
    (find-global-check-by-directory entry global-checks)
    ;; Map - normalize and resolve
    (normalize-project-check-entry entry project-root)))

(defn resolve-check-entries
  "Resolves all check entries from a project config into check definitions.
   Returns a vector of check configs ready for the checks system.

   Entries that resolve to nil are filtered out (e.g., empty directories)."
  [check-entries global-checks project-root]
  (->> check-entries
       (map #(resolve-check-entry % global-checks project-root))
       (remove nil?)
       vec))

;;; Main Entry Point

(defn- normalize-check-entry
  "Adds :files to check entry if missing by auto-discovering .edn files.
   For global config, resolves directory relative to ~/.proserunner/"
  [global-proserunner-dir check-entry]
  (if (contains? check-entry :files)
    check-entry
    (let [dir-path (io/file global-proserunner-dir (:directory check-entry))
          files (get-edn-files (.getAbsolutePath dir-path))]
      (assoc check-entry :files (or files [])))))

(defn load-global-config
  "Loads the global configuration from ~/.proserunner/"
  []
  (let [config-path (sys/filepath ".proserunner" "config.edn")
        config-file (io/file config-path)]
    (if (.exists config-file)
      (let [parsed-config (config/safe-load-config config-path)
            global-proserunner-dir (sys/filepath ".proserunner" "")
            ;; Normalize check entries (auto-discover :files if missing)
            normalized-checks (mapv #(normalize-check-entry global-proserunner-dir %)
                                   (:checks parsed-config))
            ;; Use dynamic require to avoid circular dependency
            _ (require 'proserunner.ignore)
            read-ignore-file (resolve 'proserunner.ignore/read-ignore-file)
            global-ignore (read-ignore-file)]
        {:checks normalized-checks
         :ignore global-ignore})
      ;; No global config exists
      {:ignore #{}})))

(defn- build-project-config
  "Builds final project config from merged config and resolved checks."
  [merged-config global-config project-root]
  (let [check-entries (:checks merged-config)
        ;; Resolve all check entries (string references and map definitions)
        resolved-checks (resolve-check-entries check-entries (:checks global-config) project-root)]
    {:checks resolved-checks
     :ignore (:ignore merged-config)
     :source :project}))

(defn load-project-config
  "Main entry point for loading project configuration.

   Orchestrates the entire config merge pipeline (see 'Config Merging' section above):

   1. Searches for .proserunner/config.edn starting from start-dir (skips home directory)
   2. If found, parses and validates it (parse-manifest)
   3. Loads global config from ~/.proserunner/ (load-global-config)
   4. Merges global and project configs based on project settings (merge-configs)
   5. Resolves check entries into check definitions (resolve-check-entries)
   6. Builds final configuration with resolved checks (build-project-config)
   7. Returns the final configuration with :checks, :ignore, and :source

   If no project manifest is found, returns global config with :source :global.

   See the 'Config Merging' section documentation for detailed examples and flow diagrams."
  [start-dir]
  (if-let [{:keys [manifest-path project-root]} (find-manifest start-dir)]
    (let [project-config (read-manifest manifest-path)
          global-config (load-global-config)
          merged (merge-configs global-config project-config)]
      (build-project-config merged global-config project-root))
    (assoc (load-global-config) :source :global)))

;;; Context-Aware Target Determination

(defn resolve-start-dir
  "Resolves the start directory from options or current working directory.
   Public helper to provide consistent start-dir resolution."
  [options]
  (or (:start-dir options) (System/getProperty "user.dir")))

(defn determine-target
  "Determines whether to add checks/ignores to :global or :project scope.

   Options map may contain:
   - :global - Force global scope (~/.proserunner/)
   - :project - Force project scope (.proserunner/), fails if no project
   - neither - Auto-detect based on presence of .proserunner/ in start-dir

   Returns:
   - :global - Add to ~/.proserunner/
   - :project - Add to .proserunner/ in project root

   Throws:
   - ExceptionInfo if both :global and :project specified
   - ExceptionInfo if :project specified but no project found"
  [options start-dir]
  (cond
    ;; Both flags specified is an error
    (and (:global options) (:project options))
    (throw (ex-info (conflicting-flags-error)
                    {:options options
                     :type :conflicting-flags}))

    ;; Explicit global
    (:global options)
    :global

    ;; Explicit project - verify it exists
    (:project options)
    (if (find-manifest start-dir)
      :project
      (throw (ex-info (no-project-config-error start-dir)
                      {:start-dir start-dir
                       :type :no-project-found})))

    ;; Auto-detect: if project exists, use it; otherwise use global
    :else
    (if (find-manifest start-dir)
      :project
      :global)))

(defn read-project-config
  "Read the project .proserunner/config.edn file.
   Returns the parsed config map, or nil if file doesn't exist."
  [project-root]
  (let [config-path (project-config-path project-root)]
    (when (.exists (io/file config-path))
      (edn/read-string (slurp config-path)))))

(defn write-project-config!
  "Write config map to project .proserunner/config.edn file atomically."
  [project-root config]
  (let [config-path (project-config-path project-root)]
    (file-utils/atomic-spit config-path (with-out-str (pprint/pprint config)))))

;;; Project Initialization

(def default-manifest-template
  "Default template for a new project manifest."
  {:checks ["default"]
   :ignore #{}
   :ignore-mode :extend
   :config-mode :merged})

(defn manifest-exists?
  "Checks if a .proserunner/config.edn file exists in the given directory."
  [dir]
  (.exists (manifest-path dir)))

(defn init-project-config!
  "Creates a .proserunner/ directory structure in the specified directory.
   Creates .proserunner/config.edn and .proserunner/checks/ subdirectory.
   Returns the path to the created config file, or throws if file already exists."
  ([dir] (init-project-config! dir default-manifest-template))
  ([dir manifest-template]
   (when (manifest-exists? dir)
     (throw (ex-info "Project manifest already exists"
                     {:path (.getAbsolutePath (manifest-path dir))})))
   (let [checks-dir (project-checks-dir-path dir)
         config-file (manifest-path dir)]
     ;; Create directories
     (.mkdirs checks-dir)
     ;; Write config file atomically
     (file-utils/atomic-spit (.getAbsolutePath config-file)
                             (str ";; Proserunner Project Configuration\n"
                                  ";; See: https://github.com/jeff-bruemmer/proserunner\n\n"
                                  (with-out-str (pprint/pprint manifest-template))))
     (.getAbsolutePath config-file))))
