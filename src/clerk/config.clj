(ns clerk.config
  "Functions for creating a configuration directory."
  (:gen-class)
  (:require [clerk
             [error :as error]
             [system :as sys]]
            [clj-http.lite.client :as client]
            [clojure
             [edn :as edn]
             [string :as string]]
            [clojure.java.io :as io]
            [jsonista.core :as json])
  (:import java.io.File))

(set! *warn-on-reflection* true)

(def remote-address "https://github.com/jeff-bruemmer/clerk-default-checks/archive/main.zip")
(def remote-api "https://api.github.com/repos/jeff-bruemmer/clerk-default-checks/commits/main")

(defrecord Config [checks ignore])

(defn load-config
  [file]
  (map->Config (edn/read-string file)))

(defn default
  "If current config isn't valid, use the default."
  [options]
  (let [cur-config (:config options)
        new-config (sys/filepath ".clerk" "config.edn")]
    (if (or (nil? cur-config)
            (not (.exists (io/file cur-config))))
      (assoc options :config new-config)
      options)))

(def invalid-msg "config must be an edn file.")

(defn valid?
  "File should be a text, markdown, tex, or org file."
  [filepath]
  (contains?
   #{"edn"}
   (peek (string/split filepath #"\."))))

(defn fetch!
  "Takes a file path and returns a Config record.
   Clerk will exit if it cannot load the config."
  [config-filepath]
  (try
    (slurp config-filepath)
    (catch Exception e (error/exit
                        (str "Clerk could not load config:\n\n" (.getMessage e) "\n")))))

(defn ^:private get-remote-zip!
  "Retrieves default checks, or times out after 5 seconds."
  [address]
  (let [resp (try (client/get address {:as :byte-array
                                       :socket-timeout 5000
                                       :connection-timeout 5000
                                       :throw-exceptions false})
                  (catch Exception e (error/exit (str "Clerk couldn't reach: " (.getMessage e)
                                                      "\nAre you connected to the Internet?\n"))))]
    (:body resp)))

(defn ^:private copy!
  "Copies `stream` to `out-file`, creating parent-dir if necessary.
  Used in unzip-file!"
  [stream save-path out-file]
  (let [parent-dir (io/file (subs save-path 0 (string/last-index-of save-path File/separator)))]
    (when-not (.exists parent-dir) (.mkdirs parent-dir))
    (io/copy stream out-file)))

(defn ^:private unzip-file!
  "Uncompress zip archive.
    `input` - name of zip archive to be uncompressed.
    `output` - name of target directory"
  [input output old-name new-name]
  (with-open [stream (-> input io/input-stream java.util.zip.ZipInputStream.)]
    (loop [entry (.getNextEntry stream)]
      (when entry
        (let [save-path (str output File/separatorChar (string/replace (.getName entry) old-name new-name))
              out-file (io/file save-path)]
          (if (.isDirectory entry)
            (when-not (.exists out-file) (.mkdirs out-file))
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
  (let [version-file (sys/filepath ".clerk" ".version")]
    (when (.exists (io/file version-file))
      (try
        (string/trim (slurp version-file))
        (catch Exception _ nil)))))

(defn ^:private write-local-version!
  "Write the current version SHA to local version file."
  [sha]
  (when sha
    (let [version-file (sys/filepath ".clerk" ".version")]
      (spit version-file sha))))

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
  "Download and extract default checks from GitHub."
  []
  (println "Downloading default checks from: " remote-address ".")
  (try
    (unzip-file! (get-remote-zip! remote-address) (sys/home-dir) "clerk-default-checks-main" ".clerk")
    ;; Save the version after successful download
    (when-let [version (get-remote-version)]
      (write-local-version! version))
    (catch Exception e (error/exit (str "Couldn't unzip default checks\n" (.getMessage e))))))

(defn fetch-or-create!
  "Fetches or creates config file. Will exit on failure.
   Automatically checks for updates to default checks."
  [config-filepath]
  (let [default-config (sys/filepath ".clerk" "config.edn")
        using-default? (or (nil? config-filepath)
                           (= config-filepath default-config))
        config-exists? (.exists (io/file default-config))
        local-version (read-local-version)
        version-exists? (not (nil? local-version))]
    (cond
      ;; Use custom (non-default) config if provided
      (and (not using-default?)
           (.exists (io/file config-filepath)))
      (load-config (fetch! config-filepath))

      ;; If using default config and checks are stale, update them
      (and using-default? config-exists? (checks-stale?))
      (do
        (println "Updating default checks...")
        (download-checks!)
        (println "Checks updated.")
        (load-config (fetch! default-config)))

      ;; Config exists and is current
      config-exists?
      (do
        ;; Backfill version file for existing installations
        (when-not version-exists?
          (when-let [version (get-remote-version)]
            (write-local-version! version)))
        (load-config (fetch! default-config)))

      ;; First time setup
      :else
      (do
        (println "Initializing Clerk...")
        (download-checks!)
        (println "Created Clerk directory: " (sys/filepath ".clerk/"))
        (println "You can store custom checks in: " (sys/filepath ".clerk" "custom/"))
        (load-config (fetch! default-config))))))
