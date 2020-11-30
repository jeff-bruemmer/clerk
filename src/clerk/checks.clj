(ns clerk.checks
  (:gen-class)
  (:require [clerk
             [error :as error]
             [system :as sys]]
            [clojure
             [edn :as edn]
             [walk :as walk]]
            [clojure.java.io :as io]))

(defrecord Recommendation [prefer avoid])
(defrecord Check [name specimens message kind explanation recommendations])

(defn make
  "Returns a `Check`."
  [{:keys [name specimens message kind recommendations explanation]}]
  (->Check name specimens message kind explanation
           (map map->Recommendation recommendations)))

(defn path
  "Builds full path for `filename`."
  [filename]
  (->> filename
       (sys/filepath ".clerk")
       (#(str % ".edn"))))

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
       (path)
       (valid-config?)
       (slurp)
       (edn/read-string)
       (walk/keywordize-keys)
       (make)))

(defn create
  "Takes a config, and loads all the specified checks."
  [{:keys [checks]}]
  (let [all-checks (mapcat (fn
                             [{:keys [directory files]}]
                             (map #(str directory java.io.File/separator %) files)) checks)]
    (pmap load-edn! all-checks)))

