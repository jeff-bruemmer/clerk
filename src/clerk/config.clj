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
            [clojure.java.io :as io])
  (:import java.io.File))

(set! *warn-on-reflection* true)

(def remote-address "https://github.com/jeff-bruemmer/clerk-default-checks/archive/main.zip")

(defrecord Config [checks ignore])

(defn make-config
  [file]
  (map->Config (edn/read-string file)))

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

(defn fetch-or-create!
  "Fetches or creates config file. Will exit on failure."
  [config-filepath]
  (if (.exists (io/file config-filepath))
    (make-config (fetch! config-filepath))
    (do
      (println "Initializing Clerk...")
      (println "Downloading default checks from: " remote-address ".")
      (try
        (unzip-file! (get-remote-zip! remote-address) (sys/home-dir) "clerk-default-checks-main" ".clerk")
        (catch Exception e (error/exit (str "Couldn't unzip default checks\n" (.getMessage e)))))
      (println "Created Clerk directory: " (sys/filepath ".clerk"))
      (println "You can store custom checks in: " (sys/filepath ".clerk" "custom"))
      (make-config (fetch! config-filepath)))))
