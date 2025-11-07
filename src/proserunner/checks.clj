(ns proserunner.checks
  "Loading, validating, and filtering check definitions from EDN files."
  (:gen-class)
  (:require [proserunner
             [edn-utils :as edn-utils]
             [file-utils :as file-utils]
             [result :as result]]
            [clojure
             [edn :as edn]
             [string :as string]
             [walk :as walk]]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defrecord Recommendation
  [^String prefer             ; Preferred alternative text to use
   ^String avoid])            ; Text pattern to avoid

(defrecord Expression
  [^String re                 ; Regular expression pattern to match
   ^String message])          ; Message to display when pattern matches

(defrecord Check
  [^String name               ; Unique identifier for this check (e.g., "passive-voice")
   specimens                  ; Vector of strings to match (for "existence" and "case" kinds)
   ^String message            ; Error message to display when check fails (optional for recommenders)
   ^String kind               ; Check type: "existence", "case", "recommender", "case-recommender", "regex", "repetition"
   ^String explanation        ; Optional detailed explanation of why this is an issue
   recommendations            ; Vector of Recommendation records (for "recommender" kinds)
   expressions])              ; Vector of Expression records (for "regex" kind)

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
  "Parses EDN string into a Check record.
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

  Returns Result<Check> - Success with parsed Check, or Failure with error details."
  [filename]
  (let [read-result (read-check-file filename)]
    (if-let [error (:error read-result)]
      (result/err error {:filepath filename :operation :load-check-file})
      (result/try-result-with-context
        (fn [] (parse-check-edn (:ok read-result)))
        {:filepath filename :operation :parse-check-edn}))))

(defn load-ignore-set!
  "Takes a checks directory and a file name for an edn file that
  lists specimens to ignore."
  [check-dir filename]
  (if (nil? filename) #{}
      (let [f (path check-dir filename)
            read-result (edn-utils/read-edn-file f)]
        (if (result/success? read-result)
          (:value read-result)
          (throw (ex-info (str "Failed to load ignore set from file: " f)
                          {:error (:error read-result)
                           :context (:context read-result)}))))))

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

  Returns Result<map> with:
    - Success: {:checks [Check...], :warnings [{:path str :error str}...]}
    - Failure: If no checks load successfully
  Returns warnings as data for partially failed loads."
  [options]
  (let [{:keys [config check-dir project-ignore]} options
        checks (:checks config)
        all-check-paths (mapcat (fn
                                  [{:keys [directory files]}]
                                  (let [base-path (if (file-utils/absolute-path? directory)
                                                   directory
                                                   (str check-dir directory))
                                        sep java.io.File/separator]
                                    (map #(str base-path sep % ".edn") files))) checks)
        ;; Load all checks, collecting Results
        load-results (map load-edn! all-check-paths)
        ;; Extract successful checks and failed ones
        loaded-checks (keep (fn [r] (when (result/success? r) (:value r))) load-results)
        failed-checks (keep-indexed (fn [idx r]
                                       (when (result/failure? r)
                                         {:path (nth all-check-paths idx)
                                          :error (:error r)}))
                                     load-results)
        filtered-checks (mapv #(apply-ignore-filter % project-ignore) loaded-checks)
        failed-count (count failed-checks)]

    ;; Return Failure if no checks loaded successfully
    (if (empty? filtered-checks)
      (result/err "No valid checks could be loaded. Please check your configuration."
                  {:operation :create-checks
                   :total-attempts (count all-check-paths)
                   :successful 0
                   :failed failed-count})
      (result/ok {:checks filtered-checks
                  :warnings failed-checks}))))

