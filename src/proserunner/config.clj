(ns proserunner.config
  "Functions for creating a configuration directory."
  (:gen-class)
  (:require [proserunner
             [error :as error]
             [file-utils :as file-utils]
             [result :as result]
             [system :as sys]]
            [clj-http.lite.client :as client]
            [clojure
             [edn :as edn]
             [string :as string]]
            [clojure.java.io :as io]
            [jsonista.core :as json])
  (:import java.io.File))

(set! *warn-on-reflection* true)

(def remote-address "https://github.com/jeff-bruemmer/proserunner-default-checks/archive/main.zip")
(def remote-api "https://api.github.com/repos/jeff-bruemmer/proserunner-default-checks/commits/main")

(defrecord Config [checks ignore])

(defn load-config-from-file
  "Loads and parses a config file, returning Result<Config>.

  This is the primary config loading function that handles all error cases:
  - File not found
  - File read errors
  - EDN parse errors

  Returns Success<Config> or Failure with error details."
  [filepath]
  (result/try-result-with-context
   (fn []
     (let [content (slurp filepath)
           parsed (edn/read-string content)
           ;; Provide default value for :ignore if not present
           with-defaults (if (contains? parsed :ignore)
                           parsed
                           (assoc parsed :ignore "ignore"))]
       (map->Config with-defaults)))
   {:filepath filepath :operation :load-config}))

(defn safe-load-config
  "Loads and parses config file, exiting on error (for backwards compatibility).

  Prefer using load-config-from-file for better error handling."
  [filepath]
  (let [result (load-config-from-file filepath)]
    (if (result/success? result)
      (:value result)
      (error/exit (str "Proserunner could not load config file '" filepath "':\n"
                      (:error result) "\n")))))

(defn load-config
  "Parse config EDN string and return Config record.
  Throws exception on parse error."
  [edn-string]
  (try
    (let [parsed (edn/read-string edn-string)
          ;; Provide default value for :ignore if not present
          with-defaults (if (contains? parsed :ignore)
                          parsed
                          (assoc parsed :ignore "ignore"))]
      (map->Config with-defaults))
    (catch Exception e
      (throw (ex-info "Failed to parse config EDN"
                      {:error (.getMessage e)})))))

(defn default
  "If current config isn't valid, use the default."
  [options]
  (let [cur-config (:config options)
        new-config (sys/filepath ".proserunner" "config.edn")]
    (if (or (nil? cur-config)
            (not (.exists (io/file cur-config))))
      (assoc options :config new-config)
      options)))

(def invalid-msg "config must be an edn file.")

(defn valid?
  "File should be an edn file."
  [filepath]
  (contains?
   #{"edn"}
   (peek (string/split filepath #"\."))))

(defn ^:private get-remote-zip!
  "Retrieves default checks, or times out after 5 seconds.

  Returns Result<byte-array> - Success with ZIP bytes, or Failure on error."
  [address]
  (result/try-result-with-context
   (fn []
     (let [resp (client/get address {:as :byte-array
                                     :socket-timeout 5000
                                     :connection-timeout 5000
                                     :throw-exceptions false})]
       (if (= 200 (:status resp))
         (:body resp)
         (throw (ex-info "Failed to fetch remote checks"
                        {:status (:status resp)
                         :address address})))))
   {:operation :get-remote-zip :address address}))

(defn ^:private copy!
  "Copies `stream` to `out-file`, creating parent-dir if necessary.
  Used in unzip-file!"
  [stream save-path out-file]
  (file-utils/ensure-parent-dir save-path)
  (io/copy stream out-file))

(defn ^:private unzip-file!
  "Uncompress zip archive.
    `input` - name of zip archive to be uncompressed.
    `output` - name of target directory"
  [input output old-name new-name]
  (with-open [stream (-> input io/input-stream java.util.zip.ZipInputStream.)]
    (loop [entry (.getNextEntry stream)]
      (when entry
        (let [entry-name (string/replace (.getName entry) old-name new-name)
              save-path (file-utils/join-path output entry-name)
              out-file (io/file save-path)]
          (if (.isDirectory entry)
            (file-utils/mkdirs-if-missing save-path)
            (copy! stream save-path out-file))
          (recur (.getNextEntry stream)))))))

(defn ^:private get-remote-version
  "Get the latest commit SHA from GitHub API.
   Returns nil if unable to fetch (offline, rate limit, etc)."
  []
  (try
    (let [resp (client/get remote-api {:socket-timeout 3000
                                       :connection-timeout 3000
                                       :throw-exceptions false})]
      (when (= 200 (:status resp))
        (let [body (json/read-value (:body resp) json/keyword-keys-object-mapper)]
          (:sha body))))
    (catch Exception _ nil)))

(defn ^:private read-local-version
  "Read the local version file if it exists."
  []
  (let [version-file (sys/filepath ".proserunner" ".version")]
    (when (.exists (io/file version-file))
      (try
        (string/trim (slurp version-file))
        (catch Exception _ nil)))))

(defn ^:private write-local-version!
  "Write the current version SHA to local version file atomically."
  [sha]
  (when sha
    (let [version-file (sys/filepath ".proserunner" ".version")]
      (file-utils/atomic-spit version-file sha))))

(defn ^:private checks-stale?
  "Check if local checks are outdated compared to remote.
   Returns true if checks should be updated."
  []
  (let [local-version (read-local-version)
        remote-version (get-remote-version)]
    (cond
      ;; No version file exists, checks need download
      (nil? local-version) true
      ;; Couldn't reach GitHub, assume checks are current
      (nil? remote-version) false
      ;; Compare versions
      :else (not= local-version remote-version))))

(defn ^:private download-checks!
  "Download and extract default checks from GitHub.

  Returns Result<nil> - Success when complete, Failure on error."
  []
  (println "Downloading default checks from: " remote-address ".")
  (result/try-result-with-context
   (fn []
     (let [zip-result (get-remote-zip! remote-address)]
       (if (result/failure? zip-result)
         (throw (ex-info (:error zip-result) (:context zip-result)))
         (do
           (unzip-file! (:value zip-result) (sys/home-dir) "proserunner-default-checks-main" ".proserunner")
           ;; Save the version after successful download
           (when-let [version (get-remote-version)]
             (write-local-version! version))
           nil))))
   {:operation :download-checks}))

(defn ^:private backup-directory!
  "Create a timestamped backup of a directory."
  [dir-path _backup-name]
  (let [timestamp (.format (java.text.SimpleDateFormat. "yyyyMMdd-HHmmss")
                           (java.util.Date.))
        backup-dir (file-utils/join-path (sys/home-dir) (str ".proserunner-backup-" timestamp))]
    (when (.exists (io/file dir-path))
      (file-utils/mkdirs-if-missing backup-dir)
      (doseq [^java.io.File file (file-seq (io/file dir-path))]
        (when (.isFile file)
          (let [rel-path (subs (.getPath file) (count dir-path))
                target (io/file (str backup-dir rel-path))]
            (file-utils/ensure-parent-dir (.getPath target))
            (io/copy file target))))
      (println "Created backup at:" backup-dir)
      backup-dir)))

(defn restore-defaults!
  "Restore default checks from GitHub, backing up existing checks first.

  Returns Result<nil> - Success when complete, Failure on error."
  []
  (println "\n=== Restoring Default Checks ===\n")
  (result/try-result-with-context
   (fn []
     (let [proserunner-dir (sys/filepath ".proserunner")
           default-dir (sys/filepath ".proserunner" "default")
           config-file (sys/filepath ".proserunner" "config.edn")
           ignore-file (sys/filepath ".proserunner" "ignore.edn")]

       ;; Check if .proserunner directory exists
       (if-not (.exists (io/file proserunner-dir))
         (do
           (println "No .proserunner directory found. Creating fresh installation...")
           (let [dl-result (download-checks!)]
             (if (result/failure? dl-result)
               (throw (ex-info (:error dl-result) (:context dl-result)))
               (println "\nDefault checks installed."))))
         ;; Otherwise, backup and restore
         (do
           ;; Backup existing checks
           (println "Backing up existing checks...")
           (backup-directory! default-dir "default")

           ;; Preserve config and ignore files
           (let [config-backup (when (.exists (io/file config-file))
                                 (slurp config-file))
                 ignore-backup (when (.exists (io/file ignore-file))
                                 (slurp ignore-file))]

             ;; Download fresh checks
             (println "\nDownloading fresh default checks...")
             (let [dl-result (download-checks!)]
               (when (result/failure? dl-result)
                 (throw (ex-info (:error dl-result) (:context dl-result)))))

             ;; Restore preserved files atomically if they existed
             (when config-backup
               (file-utils/atomic-spit config-file config-backup)
               (println "Preserved your config.edn"))

             (when ignore-backup
               (file-utils/atomic-spit ignore-file ignore-backup)
               (println "Preserved your ignore.edn"))

             (println "\nDefault checks restored successfully.")
             (println "\nYour custom checks in ~/.proserunner/custom/ were not modified."))))
       nil))
   {:operation :restore-defaults}))

(defn using-default-config?
  "Determines if automatic check updates should be performed."
  [config-filepath default-config]
  (or (nil? config-filepath)
      (= config-filepath default-config)))

(defn backfill-version-if-needed
  "Supports upgrades from older Proserunner versions that lacked version tracking."
  [version-exists?]
  (when-not version-exists?
    (when-let [version (get-remote-version)]
      (write-local-version! version))))

(defn initialize-proserunner
  "Sets up ~/.proserunner directory and downloads default checks for first-time users."
  [default-config]
  (println "Initializing Proserunner...")
  (let [dl-result (download-checks!)]
    (if (result/failure? dl-result)
      (error/exit (str "Failed to download default checks: " (:error dl-result)))
      (do
        (println "Created Proserunner directory: " (sys/filepath ".proserunner/"))
        (println "You can store custom checks in: " (sys/filepath ".proserunner" "custom/"))
        (safe-load-config default-config)))))

(defn update-default-checks
  "Refreshes default checks when remote version is newer than local cache."
  [default-config]
  (println "Updating default checks...")
  (let [dl-result (download-checks!)]
    (if (result/failure? dl-result)
      (do
        (println "Warning: Failed to update checks:" (:error dl-result))
        (println "Continuing with existing checks...")
        (safe-load-config default-config))
      (do
        (println "Checks updated.")
        (safe-load-config default-config)))))

(defn- load-custom-config
  "Loads a custom config file specified via -c flag."
  [config-filepath]
  (safe-load-config config-filepath))

(defn- load-project-based-config
  "Loads project configuration, merging with global config as appropriate."
  [current-dir]
  (let [_ (require 'proserunner.project-config)
        load-project-config (resolve 'proserunner.project-config/load-project-config)]
    (if-let [project-cfg (load-project-config current-dir)]
      (map->Config {:checks (:checks project-cfg)
                    :ignore (:ignore project-cfg)})
      (error/exit "Failed to load project configuration. Check .proserunner/config.edn for errors.\n"))))

(defn- load-existing-global-config
  "Loads existing global config, backfilling version if needed."
  [default-config version-exists?]
  (backfill-version-if-needed version-exists?)
  (safe-load-config default-config))

(defn fetch-or-create!
  "Fetches or creates config file. Will exit on failure.
   Automatically checks for updates to default checks.

   If in a project directory, loads project config which may include
   project-specific checks and ignores merged with global config."
  [config-filepath]
  (when (and config-filepath (not (string? config-filepath)))
    (throw (ex-info (str "fetch-or-create! expects a string filepath, got: " (type config-filepath))
                    {:config-filepath config-filepath})))
  (let [default-config (sys/filepath ".proserunner" "config.edn")
        using-default? (using-default-config? config-filepath default-config)
        config-exists? (.exists (io/file default-config))
        version-exists? (not (nil? (read-local-version)))

        ;; Check if we're in a project directory
        _ (require 'proserunner.project-config)
        find-manifest (resolve 'proserunner.project-config/find-manifest)
        current-dir (System/getProperty "user.dir")
        in-project? (find-manifest current-dir)]

    (cond
      ;; Custom config file specified via -c flag
      (and (not using-default?) (.exists (io/file config-filepath)))
      (load-custom-config config-filepath)

      ;; In a project directory - load project config (merges with global)
      ;; This must come BEFORE the checks-stale? and config-exists? checks
      (and using-default? in-project?)
      (load-project-based-config current-dir)

      ;; Using default config and checks need updating
      (and using-default? config-exists? (checks-stale?))
      (update-default-checks default-config)

      ;; Global config exists
      config-exists?
      (load-existing-global-config default-config version-exists?)

      ;; First-time initialization
      :else
      (initialize-proserunner default-config))))
