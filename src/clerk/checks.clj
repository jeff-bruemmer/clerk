(ns clerk.checks
  (:gen-class)
  (:require [clerk
             [error :as error]]
            [clojure
             [edn :as edn]
             [walk :as walk]]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defrecord Recommendation [prefer avoid])
(defrecord Expression [re message])
(defrecord Check [name specimens message kind explanation recommendations expressions])

(defn make
  "Returns a `Check`."
  [{:keys [name specimens message kind recommendations explanation expressions]}]
  (->Check name specimens message kind explanation
           (map map->Recommendation recommendations)
           (map map->Expression expressions)))

(defn path
  "Builds full path for `filename`."
  [check-dir filename]
  (str check-dir filename ".edn"))

(defn valid-config?
  "Does the `filepath` exist?"
  [filepath]
  (if (.exists (io/file filepath))
    filepath
    (error/exit (str "Clerk couldn't find " filepath))))

(defn load-edn!
  "Loads an EDN-formatted check file.
   Clerk will exit if it cannot load a check."
  [filename]
  (->> filename
       (valid-config?)
       (slurp)
       (edn/read-string)
       (walk/keywordize-keys)
       (make)))

(defn load-ignore-set!
  "Takes a checks directory and a file name for an edn file that
  lists specimens to ignore."
  [check-dir filename]
  (if (nil? filename) #{}
      (let [f (path check-dir filename)]
        (->> f
             slurp
             edn/read-string))))

(defn create
  "Takes an options ball, and loads all the specified checks."
  [options]
  (let [{:keys [config check-dir]} options
        checks (:checks config)
        all-checks (mapcat (fn
                             [{:keys [directory files]}]
                             (map #(str check-dir directory (java.io.File/separator) % ".edn") files)) checks)]
    (map load-edn! all-checks)))

