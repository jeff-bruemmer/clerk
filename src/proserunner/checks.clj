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

(defn missing-or-empty?
  "Used during check validation to ensure required fields have content."
  [check-def field]
  (or (nil? (get check-def field))
      (empty? (get check-def field))))

(defn validate-required-field
  "Accumulates validation errors for fields that all checks must have."
  [errors check-def field message]
  (if (not (get check-def field))
    (conj errors message)
    errors))

(defn validate-kind-field
  "Enforces kind-specific requirements (e.g., 'existence' needs :specimens)."
  [errors check-def kinds field message-template]
  (if (and (contains? (set kinds) (:kind check-def))
           (missing-or-empty? check-def field))
    (conj errors (if (fn? message-template)
                   (message-template (:kind check-def))
                   message-template))
    errors))

(defn validate-check
  "Validates that a check definition has required fields.
   Returns {:valid? true} or {:valid? false :errors [...]}"
  [check-def]
  (let [kind (:kind check-def)
        message-required? (not (contains? #{"recommender" "case-recommender"} kind))
        errors (-> []
                   (validate-required-field check-def :name "Check must have a :name field")
                   (validate-required-field check-def :kind "Check must have a :kind field")
                   ((fn [e] (if (and message-required? (not (:message check-def)))
                             (conj e "Check must have a :message field")
                             e)))
                   (validate-kind-field check-def ["existence" "case"] :specimens
                                       (fn [k] (str "Check of kind '" k "' must have non-empty :specimens")))
                   (validate-kind-field check-def ["recommender" "case-recommender"] :recommendations
                                       (fn [k] (str "Check of kind '" k "' must have non-empty :recommendations")))
                   (validate-kind-field check-def ["regex"] :expressions
                                       "Check of kind 'regex' must have non-empty :expressions"))]
    (if (empty? errors)
      {:valid? true}
      {:valid? false :errors errors})))

(defn make
  "Returns a `Check` after validation. Throws exception with details if invalid."
  [{:keys [name specimens message kind recommendations explanation expressions] :as check-def}]
  (let [validation (validate-check check-def)]
    (when-not (:valid? validation)
      (throw (ex-info (str "Invalid check definition for '" name "'")
                      {:check-name name
                       :errors (:errors validation)
                       :message (str "Invalid check definition for '" name "':\n"
                                    (clojure.string/join "\n" (map #(str "  - " %) (:errors validation))))})))
    (->Check name specimens message kind explanation
             (map map->Recommendation recommendations)
             (map map->Expression expressions))))

(defn path
  "Builds full path for `filename`."
  [check-dir filename]
  (str check-dir filename ".edn"))

(defn load-edn!
  "Loads an EDN-formatted check file.
   Prints warning and returns nil if file cannot be loaded."
  [filename]
  (try
    (if-not (.exists (io/file filename))
      (do
        (println (str "Error: Check file not found: " filename))
        nil)
      (->> filename
           (slurp)
           (edn/read-string)
           (walk/keywordize-keys)
           (make)))
    (catch Exception e
      (println (str "Error: Failed to load check file '" filename "': " (.getMessage e)))
      nil)))

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
  "Takes an options ball, and loads all the specified checks.
   Filters out any checks that failed to load."
  [options]
  (let [{:keys [config check-dir]} options
        checks (:checks config)
        all-checks (mapcat (fn
                             [{:keys [directory files]}]
                             (map #(str check-dir directory (java.io.File/separator) % ".edn") files)) checks)
        loaded-checks (keep load-edn! all-checks)
        failed-count (- (count all-checks) (count loaded-checks))]
    (when (pos? failed-count)
      (println (str "\nWarning: " failed-count " check(s) failed to load and will be skipped.")))
    (when (empty? loaded-checks)
      (error/exit "No valid checks could be loaded. Please check your configuration."))
    loaded-checks))

