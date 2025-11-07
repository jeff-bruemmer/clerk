(ns proserunner.check-validator
  "Validation for check configuration structures."
  (:require [clojure.set :as set]))

(set! *warn-on-reflection* true)

(def valid-kinds
  "Set of valid check kind values."
  #{"existence" "case" "recommender" "case-recommender" "repetition" "regex"})

(def kind-requirements
  "Map of kind to required fields beyond base :name and :kind."
  {"existence" #{:specimens :message}
   "case" #{:specimens :message}
   "recommender" #{:specimens}
   "case-recommender" #{:specimens}
   "regex" #{:pattern :message}
   "repetition" #{}})

(defn check-name
  "Extract check name for error messages, with fallback."
  [check]
  (or (:name check) "unnamed-check"))

(defn missing-fields
  "Returns set of missing required fields."
  [required-fields check]
  (set/difference required-fields (set (keys check))))

(defn validate-has-fields
  "Validates all required fields are present.

   Returns: {:valid? true}
        or {:valid? false :error string :missing #{:field} :check-name string}"
  [required-fields check]
  (let [missing (missing-fields required-fields check)]
    (if (empty? missing)
      {:valid? true}
      {:valid? false
       :error (str "Missing required fields: " (pr-str (vec missing)))
       :missing missing
       :check-name (check-name check)})))

(defn validate-kind
  "Validates :kind field is a recognized check type.

   Returns: {:valid? true}
        or {:valid? false :error string :check-name string}"
  [check]
  (let [kind (:kind check)]
    (if (contains? valid-kinds kind)
      {:valid? true}
      {:valid? false
       :error (str "Invalid kind '" kind "'. Valid kinds: " (pr-str (sort valid-kinds)))
       :check-name (check-name check)
       :invalid-kind kind})))

(defn validate-specimens-type
  "Validates specimens field matches expected type for check kind.

   Existence/case checks need specimens as vector.
   Recommender checks need specimens as map.

   Returns: {:valid? true}
        or {:valid? false :error string :check-name string}"
  [check]
  (let [kind (:kind check)
        specimens (:specimens check)]
    (cond
      (not (contains? check :specimens))
      {:valid? true}

      (and (#{"recommender" "case-recommender"} kind)
           (not (map? specimens)))
      {:valid? false
       :error "Recommender checks require specimens as map: {\"bad\" \"good\"}"
       :check-name (check-name check)
       :specimens-type (type specimens)}

      (and (#{"existence" "case"} kind)
           (not (or (vector? specimens) (list? specimens) (seq? specimens))))
      {:valid? false
       :error "Existence/case checks require specimens as vector: [\"word1\" \"word2\"]"
       :check-name (check-name check)
       :specimens-type (type specimens)}

      :else
      {:valid? true})))

(defn first-invalid
  "Returns first invalid result from sequence, or {:valid? true}."
  [validation-results]
  (or (first (remove :valid? validation-results))
      {:valid? true}))

(defn validate-check
  "Validates a check structure.

   Checks:
   1. Base fields present (:name :kind)
   2. Kind value is recognized
   3. Kind-specific fields present
   4. Specimens type matches kind

   Returns: {:valid? true}
        or {:valid? false :error string :check-name string ...}"
  [check]
  (let [base-fields #{:name :kind}
        kind (:kind check)
        specific-fields (get kind-requirements kind #{})
        all-required (set/union base-fields specific-fields)]

    (first-invalid
      [(validate-has-fields base-fields check)
       (validate-kind check)
       (validate-has-fields all-required check)
       (validate-specimens-type check)])))

(defn validate-checks
  "Validates a collection of checks.

   Returns: {:valid? true}
        or {:valid? false :errors [{...}] :count N}"
  [checks]
  (let [results (map validate-check checks)
        errors (remove :valid? results)]
    (if (empty? errors)
      {:valid? true}
      {:valid? false
       :errors (vec errors)
       :count (count errors)})))
