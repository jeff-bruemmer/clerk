(ns proserunner.project-config
  "Functions for loading and managing project-level configuration."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [proserunner.config :as config]
            [proserunner.system :as sys])
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
   Returns map with :manifest-path and :project-root, or nil if not found."
  [start-dir]
  (loop [current-dir (io/file start-dir)]
    (when current-dir
      (or (manifest-in-dir current-dir)
          (when-not (has-git-directory? current-dir)
            (recur (.getParentFile current-dir)))))))

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

(defn- validation-ok
  "Creates a successful validation result."
  [value]
  {:valid? true :value value :errors []})

(defn- validation-error
  "Creates a failed validation result."
  [value & error-msgs]
  {:valid? false :value value :errors (vec error-msgs)})

(defn- validate-check-sources*
  "Validates :check-sources field, returns validation result."
  [check-sources]
  (cond
    (nil? check-sources)
    (validation-error nil ":check-sources is required in project manifest")

    (not (vector? check-sources))
    (validation-error check-sources
      (str ":check-sources must be a vector, got " (type check-sources)))

    (empty? check-sources)
    (validation-error check-sources ":check-sources cannot be empty")

    :else
    (validation-ok check-sources)))

(defn- validate-ignore*
  "Validates :ignore field, returns validation result."
  [ignore-val]
  (cond
    (and ignore-val (not (set? ignore-val)))
    (validation-error ignore-val
      (str ":ignore must be a set, got " (type ignore-val)))

    :else
    (validation-ok (or ignore-val #{}))))

(defn- validate-enum*
  "Validates enum field, returns validation result."
  [field value valid-values default-val]
  (cond
    (and value (not (contains? valid-values value)))
    (validation-error value
      (str field " must be one of " (pr-str valid-values) ", got " (pr-str value)))

    :else
    (validation-ok (or value default-val))))

(defn- collect-validation-errors
  "Collects all validation errors from a map of field -> validation-result.
   Returns {:valid? bool :data map :errors [strings]}."
  [validations-map]
  (let [all-errors (mapcat :errors (vals validations-map))
        valid? (empty? all-errors)
        data (into {} (map (fn [[k v]] [k (:value v)]) validations-map))]
    {:valid? valid?
     :data data
     :errors all-errors}))

(defn parse-manifest
  "Parses and validates a project manifest map.
   Throws with ALL validation errors if invalid.
   Returns validated map with defaults applied if valid."
  [manifest]
  (let [validations {:check-sources (validate-check-sources* (:check-sources manifest))
                     :ignore (validate-ignore* (:ignore manifest))
                     :ignore-mode (validate-enum* :ignore-mode
                                                   (:ignore-mode manifest)
                                                   #{:extend :replace}
                                                   :extend)
                     :config-mode (validate-enum* :config-mode
                                                   (:config-mode manifest)
                                                   #{:merged :project-only}
                                                   :merged)}
        result (collect-validation-errors validations)]
    (if (:valid? result)
      (:data result)
      (let [error-msg (if (= 1 (count (:errors result)))
                        (first (:errors result))
                        (str "Validation errors:\n"
                             (string/join "\n"
                               (map #(str "  - " %) (:errors result)))))]
        (throw (ex-info error-msg
                        {:errors (:errors result)
                         :manifest manifest}))))))

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
     - Preserves project's :check-sources while keeping global :checks for later merging"
  [global-config project-config]
  (if (= (:config-mode project-config) :project-only)
    project-config
    ;; In merged mode, preserve project's :check-sources and merge ignores
    ;; The :check-sources will be resolved later in build-project-config
    (assoc project-config
           :ignore (merge-ignores (:ignore global-config)
                                 (:ignore project-config)
                                 (:ignore-mode project-config)))))

;;; Check Source Resolution

(defn- relative-path?
  "Checks if path is relative."
  [path]
  (not (.isAbsolute (io/file path))))

(defn- absolute-path?
  "Checks if path is already absolute."
  [path]
  (or (.isAbsolute (io/file path))
      (string/starts-with? path "~")))

(defn- resolve-local-path
  "Resolves a local path to absolute path, validating it exists."
  [source project-root]
  (let [^File source-file (if (relative-path? source)
                            (io/file project-root source)
                            (io/file source))
        absolute-path (.getAbsolutePath source-file)]
    (when-not (.exists source-file)
      (throw (ex-info (file-not-found-error source absolute-path)
                      {:source source
                       :resolved-path absolute-path
                       :type :file-not-found})))
    absolute-path))

(defn resolve-check-source
  "Resolves a check source specification to a concrete source map.

   Sources can be:
   - 'default' -> built-in default checks
   - 'checks' -> .proserunner/checks/ directory (project-local checks)
   - './path' or '/path' -> local directory

   Returns a map with :type and source-specific fields."
  [source project-root]
  (cond
    (= source "default")
    {:type :built-in :source "default"}

    (= source "checks")
    {:type :local
     :path (.getAbsolutePath (project-checks-dir-path project-root))}

    :else
    {:type :local
     :path (resolve-local-path source project-root)}))

;;; Check Source to Config Conversion

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

(defn- resolve-check-source-to-config
  "Converts a resolved check source into a check config entry.
   Returns a map with :name, :directory, and :files."
  [resolved-source]
  (case (:type resolved-source)
    :built-in
    ;; For built-in default, we'll use the global config's default checks
    nil

    :local
    (let [path (:path resolved-source)
          name (last (string/split path (re-pattern (str File/separator))))
          files (get-edn-files path)]
      (when (seq files)
        {:name (str "Project checks: " name)
         :directory path
         :files files}))))

(defn resolve-check-sources
  "Resolves all check sources from a project config into check definitions.
   Returns a vector of check configs ready for the checks system.

   Sources that resolve to nil are filtered out. This happens when:
   - Source is 'default' (handled via global config merge)
   - Source is a local directory with no .edn files"
  [check-sources project-root]
  (->> check-sources
       (map #(resolve-check-source % project-root))
       (keep resolve-check-source-to-config)  ; keep filters nils
       vec))

;;; Main Entry Point

(defn load-global-config
  "Loads the global configuration from ~/.proserunner/"
  []
  (let [config-path (sys/filepath ".proserunner" "config.edn")
        config-file (io/file config-path)]
    (if (.exists config-file)
      (let [parsed-config (config/safe-load-config config-path)
            ;; Use dynamic require to avoid circular dependency
            _ (require 'proserunner.ignore)
            read-ignore-file (resolve 'proserunner.ignore/read-ignore-file)
            global-ignore (read-ignore-file)]
        {:checks (:checks parsed-config)
         :ignore global-ignore})
      ;; No global config exists
      {:ignore #{}})))

(defn- absolutize-check-directory
  "Converts a check's directory to absolute path if needed."
  [global-check-dir check]
  (update check :directory
          (fn [dir]
            (if (absolute-path? dir)
              dir
              (str global-check-dir dir)))))

(defn- make-global-checks-absolute
  "Converts global check directories to absolute paths."
  [global-checks]
  (let [global-check-dir (sys/filepath ".proserunner" "")]
    (mapv (partial absolutize-check-directory global-check-dir) global-checks)))

(defn- includes-default?
  "Checks if 'default' is in the check sources."
  [check-sources]
  (some #(= "default" %) check-sources))

(defn- merge-checks-with-defaults
  "Merges project checks with global checks (default + custom) if 'default' is specified.
   When 'default' is in check-sources, includes ALL global checks (both default and custom).
   Returns only project checks if 'default' is not included."
  [global-checks project-checks check-sources]
  (if (includes-default? check-sources)
    (let [absolute-globals (make-global-checks-absolute global-checks)]
      (vec (concat absolute-globals project-checks)))
    (vec project-checks)))

(defn- build-project-config
  "Builds final project config from merged config and resolved checks."
  [merged-config global-config project-root]
  (let [check-sources (:check-sources merged-config)
        project-checks (resolve-check-sources check-sources project-root)
        all-checks (merge-checks-with-defaults (:checks global-config) project-checks check-sources)]
    {:checks all-checks
     :ignore (:ignore merged-config)
     :source :project}))

(defn load-project-config
  "Main entry point for loading project configuration.

   1. Searches for .proserunner/config.edn starting from start-dir
   2. If found, parses and validates it
   3. Loads global config from ~/.proserunner/
   4. Merges global and project configs based on project settings
   5. Resolves check sources into check definitions
   6. Returns the final configuration with :checks, :ignore, and :source

   If no project manifest is found, returns global config with :source :global."
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

;;; Project Config I/O

(defn read-project-config
  "Read the project .proserunner/config.edn file.
   Returns the parsed config map, or nil if file doesn't exist."
  [project-root]
  (let [config-path (project-config-path project-root)]
    (when (.exists (io/file config-path))
      (edn/read-string (slurp config-path)))))

(defn write-project-config!
  "Write config map to project .proserunner/config.edn file."
  [project-root config]
  (let [config-path (project-config-path project-root)]
    (spit config-path (with-out-str (pprint/pprint config)))))

;;; Project Initialization

(def default-manifest-template
  "Default template for a new project manifest."
  {:check-sources ["default"]
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
     ;; Write config file
     (spit config-file
           (str ";; Proserunner Project Configuration\n"
                ";; See: https://github.com/jeff-bruemmer/proserunner\n\n"
                (with-out-str (pprint/pprint manifest-template))))
     (.getAbsolutePath config-file))))
