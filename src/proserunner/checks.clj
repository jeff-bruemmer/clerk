(ns proserunner.checks
  (:gen-class)
  (:require [proserunner
             [error :as error]]
            [clojure
             [edn :as edn]
             [string :as string]
             [walk :as walk]]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defrecord Recommendation [prefer avoid])
(defrecord Expression [re message])
(defrecord Check [name specimens message kind explanation recommendations expressions])

(defn validate-check
  "Validates that a check definition has required fields.
   Returns {:valid? true} or {:valid? false :errors [...]}"
  [check-def]
  (let [kind (:kind check-def)
        ;; Message is optional for recommender types (generated from recommendations)
        message-required? (not (contains? #{"recommender" "case-recommender"} kind))
        errors (cond-> []
                 (not (:name check-def))
                 (conj "Check must have a :name field")

                 (not kind)
                 (conj "Check must have a :kind field")

                 (and message-required? (not (:message check-def)))
                 (conj "Check must have a :message field")

                 (and (= (:kind check-def) "existence")
                      (or (nil? (:specimens check-def))
                          (empty? (:specimens check-def))))
                 (conj "Check of kind 'existence' must have non-empty :specimens")

                 (and (= (:kind check-def) "case")
                      (or (nil? (:specimens check-def))
                          (empty? (:specimens check-def))))
                 (conj "Check of kind 'case' must have non-empty :specimens")

                 (and (contains? #{"recommender" "case-recommender"} (:kind check-def))
                      (or (nil? (:recommendations check-def))
                          (empty? (:recommendations check-def))))
                 (conj (str "Check of kind '" (:kind check-def) "' must have non-empty :recommendations"))

                 (and (= (:kind check-def) "regex")
                      (or (nil? (:expressions check-def))
                          (empty? (:expressions check-def))))
                 (conj "Check of kind 'regex' must have non-empty :expressions"))]
    (if (empty? errors)
      {:valid? true}
      {:valid? false :errors errors})))

(defn make
  "Returns a `Check` after validation."
  [{:keys [name specimens message kind recommendations explanation expressions] :as check-def}]
  (let [validation (validate-check check-def)]
    (when-not (:valid? validation)
      (error/exit (str "Invalid check definition for '" name "':\n"
                      (clojure.string/join "\n" (map #(str "  - " %) (:errors validation))))))
    (->Check name specimens message kind explanation
             (map map->Recommendation recommendations)
             (map map->Expression expressions))))

(defn path
  "Builds full path for `filename`."
  [check-dir filename]
  (str check-dir filename ".edn"))

(defn valid-config?
  "Does the `filepath` exist?"
  [filepath]
  (if (.exists (io/file filepath))
    filepath
    (error/exit (str "Proserunner couldn't find " filepath))))

(defn load-edn!
  "Loads an EDN-formatted check file.
   Proserunner will exit if it cannot load a check."
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

