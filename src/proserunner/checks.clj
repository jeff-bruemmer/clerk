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

(defn parse-check-edn
  "Pure function: parses EDN string into a Check record.
   Throws on invalid check definition."
  [edn-string]
  (->> edn-string
       (edn/read-string)
       (walk/keywordize-keys)
       (make)))

(defn read-check-file
  "Pure I/O: reads file content as string.
   Returns {:ok content} or {:error message}."
  [filename]
  (try
    (if-not (.exists (io/file filename))
      {:error (str "Check file not found: " filename)}
      {:ok (slurp filename)})
    (catch Exception e
      {:error (str "Failed to read file '" filename "': " (.getMessage e))})))

(defn load-edn!
  "Loads an EDN-formatted check file (I/O + parsing).
   Prints warning and returns nil if file cannot be loaded.
   Composed from pure read and parse functions."
  [filename]
  (let [read-result (read-check-file filename)]
    (if-let [error (:error read-result)]
      (do
        (println (str "Error: " error))
        nil)
      (try
        (parse-check-edn (:ok read-result))
        (catch Exception e
          (println (str "Error: Failed to parse check file '" filename "': " (.getMessage e)))
          nil)))))

(defn load-ignore-set!
  "Takes a checks directory and a file name for an edn file that
  lists specimens to ignore."
  [check-dir filename]
  (if (nil? filename) #{}
      (let [f (path check-dir filename)]
        (->> f
             slurp
             edn/read-string))))

(defn- absolute-path?
  "Check if a path is absolute."
  [path]
  (or (.isAbsolute (io/file path))
      (clojure.string/starts-with? path "~")))

(defn- filter-specimens
  "Filters specimens from a check, removing any that are in the ignore set.
   Returns the check with filtered specimens. Case-insensitive matching."
  [check ignore-set]
  (if (or (nil? ignore-set) (empty? ignore-set) (nil? (:specimens check)))
    check
    (let [ignore-set-lower (set (map string/lower-case ignore-set))
          filtered-specimens (filterv
                               #(not (contains? ignore-set-lower (string/lower-case %)))
                               (:specimens check))]
      (assoc check :specimens filtered-specimens))))

(defn- filter-recommendations
  "Filters recommendations from a check, removing any where :avoid is in the ignore set.
   Returns the check with filtered recommendations. Case-insensitive matching."
  [check ignore-set]
  (if (or (nil? ignore-set) (empty? ignore-set) (nil? (:recommendations check)))
    check
    (let [ignore-set-lower (set (map string/lower-case ignore-set))
          filtered-recs (filterv
                          (fn [rec]
                            (not (contains? ignore-set-lower
                                           (string/lower-case (:avoid rec)))))
                          (:recommendations check))]
      (assoc check :recommendations filtered-recs))))

(defn- apply-ignore-filter
  "Applies ignore filtering to a check, removing ignored specimens and recommendations."
  [check ignore-set]
  (if (or (nil? ignore-set) (empty? ignore-set))
    check
    (-> check
        (filter-specimens ignore-set)
        (filter-recommendations ignore-set))))

(defn create
  "Takes an options ball, and loads all the specified checks.
   Filters out any checks that failed to load.
   Applies ignore filtering to remove specimens in the ignore set."
  [options]
  (let [{:keys [config check-dir project-ignore]} options
        checks (:checks config)
        all-checks (mapcat (fn
                             [{:keys [directory files]}]
                             (let [base-path (if (absolute-path? directory)
                                              directory
                                              (str check-dir directory))
                                   sep java.io.File/separator]
                               (map #(str base-path sep % ".edn") files))) checks)
        loaded-checks (keep load-edn! all-checks)
        filtered-checks (mapv #(apply-ignore-filter % project-ignore) loaded-checks)
        failed-count (- (count all-checks) (count loaded-checks))]
    (when (pos? failed-count)
      (println (str "\nWarning: " failed-count " check(s) failed to load and will be skipped.")))
    (when (empty? filtered-checks)
      (error/exit "No valid checks could be loaded. Please check your configuration."))
    filtered-checks))

