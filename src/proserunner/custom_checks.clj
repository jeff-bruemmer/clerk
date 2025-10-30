(ns proserunner.custom-checks
  "Functions for adding custom checks from external sources."
  (:gen-class)
  (:require [proserunner.system :as sys]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint])
  (:import java.io.File))

(set! *warn-on-reflection* true)

(defn- git-url?
  "Check if source is a git URL."
  [source]
  (or (string/starts-with? source "http://")
      (string/starts-with? source "https://")
      (string/starts-with? source "git@")))

(defn- extract-name-from-source
  "Extract a reasonable name from the source path or URL."
  [source]
  (let [cleaned (-> source
                    (string/replace #"\.git$" "")
                    (string/replace #"/$" ""))
        parts (string/split cleaned #"[/\\]")]
    (last parts)))

(defn- find-edn-files
  "Find all .edn files in a directory recursively."
  [dir-path]
  (let [dir (io/file dir-path)]
    (when (.exists dir)
      (->> (file-seq dir)
           (filter #(.isFile ^File %))
           (filter #(string/ends-with? (.getName ^File %) ".edn"))
           (map #(.getAbsolutePath ^File %))))))

(defn- copy-file!
  "Copy a file to destination, creating parent directories if needed."
  [^File src-file dest-path]
  (let [dest (io/file dest-path)]
    (io/make-parents dest)
    (io/copy src-file dest)))

(defn- add-checks-from-directory
  "Copy .edn files from a local directory to custom checks directory."
  [source-dir target-name]
  (let [target-dir (sys/filepath ".proserunner" "custom" target-name)
        edn-files (find-edn-files source-dir)]

    (when (empty? edn-files)
      (throw (ex-info (str "No .edn files found in " source-dir)
                      {:source source-dir})))

    ;; Create target directory
    (.mkdirs (io/file target-dir))

    ;; Copy each .edn file
    (doseq [file-path edn-files]
      (let [file (io/file file-path)
            filename (.getName file)
            dest-path (str target-dir File/separator filename)]
        (copy-file! file dest-path)))

    {:target-dir target-dir
     :check-names (map #(string/replace (.getName (io/file %)) #"\.edn$" "") edn-files)
     :count (count edn-files)}))

(defn- clone-git-repo!
  "Clone a git repository to a temporary directory.
   Returns the path to the cloned directory."
  [git-url]
  (let [temp-dir (str (System/getProperty "java.io.tmpdir")
                     File/separator
                     "proserunner-import-"
                     (System/currentTimeMillis))
        process (-> (ProcessBuilder. ["git" "clone" "--depth" "1" git-url temp-dir])
                   (.redirectErrorStream true)
                   (.start))]

    ;; Wait for clone to complete (timeout after 60 seconds)
    (when-not (.waitFor process 60 java.util.concurrent.TimeUnit/SECONDS)
      (.destroy process)
      (throw (ex-info "Git clone timed out after 60 seconds"
                      {:url git-url})))

    ;; Check exit code
    (let [exit-code (.exitValue process)]
      (when-not (zero? exit-code)
        (let [error-output (slurp (.getInputStream process))]
          (throw (ex-info (str "Git clone failed: " error-output)
                          {:url git-url
                           :exit-code exit-code})))))

    temp-dir))

(defn- delete-directory!
  "Recursively delete a directory."
  [^File dir]
  (when (.exists dir)
    (doseq [file (reverse (file-seq dir))]
      (io/delete-file file true))))

(defn- add-checks-from-git
  "Clone a git repository and copy .edn files to custom checks directory."
  [git-url target-name]
  (let [temp-dir (clone-git-repo! git-url)]
    (try
      (add-checks-from-directory temp-dir target-name)
      (finally
        ;; Clean up temp directory
        (delete-directory! (io/file temp-dir))))))

(defn- read-config
  "Read the config.edn file."
  []
  (let [config-path (sys/filepath ".proserunner" "config.edn")]
    (when (.exists (io/file config-path))
      (edn/read-string (slurp config-path)))))

(defn- write-config!
  "Write config map to config.edn file."
  [config]
  (let [config-path (sys/filepath ".proserunner" "config.edn")]
    (spit config-path (with-out-str (pprint/pprint config)))))

(defn- update-config-with-checks!
  "Add new check entry to config.edn."
  [target-name check-names]
  (let [config (or (read-config) {:checks []})
        custom-dir-name target-name
        existing-checks (:checks config)

        ;; Check if this directory already exists in config
        existing-entry (first (filter #(= (:directory %) (str "custom/" custom-dir-name))
                                      existing-checks))

        new-entry {:name (str "Custom checks: " target-name)
                   :directory (str "custom/" custom-dir-name)
                   :files (vec check-names)}

        updated-checks (if existing-entry
                        ;; Replace existing entry
                        (mapv #(if (= (:directory %) (str "custom/" custom-dir-name))
                                new-entry
                                %)
                             existing-checks)
                        ;; Add new entry
                        (conj (vec existing-checks) new-entry))]

    (write-config! (assoc config :checks updated-checks))))

(defn add-checks
  "Add checks from a source (local directory or git URL) to custom checks.

   Options:
   - :name - Custom name for the check directory (optional, defaults to source basename)"
  [source {:keys [name] :as opts}]
  (let [target-name (or name (extract-name-from-source source))
        result (if (git-url? source)
                 (do
                   (println (str "Cloning from " source "..."))
                   (add-checks-from-git source target-name))
                 (do
                   (println (str "Importing from " source "..."))
                   (add-checks-from-directory source target-name)))]

    ;; Update config.edn
    (update-config-with-checks! target-name (:check-names result))

    ;; Print success message
    (println (str "\nAdded " (:count result) " checks from " source))
    (println (str "  + " (:target-dir result)))
    (println (str "  + Updated config: " (sys/filepath ".proserunner" "config.edn")))
    (println (str "\nChecks added: " (string/join ", " (:check-names result))))

    result))
