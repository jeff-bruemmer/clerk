(ns proserunner.result
  "Result type for error handling.

  A Result is either Success (contains value) or Failure (contains error and context).
  Enables error composition through bind/fmap without exceptions or System/exit.

  Key operations:
  - Constructors: ok, err
  - Predicates: success?, failure?
  - Composition: bind, fmap, combine, or-else
  - Utilities: try-result, unwrap, validate"
  (:gen-class))

(set! *warn-on-reflection* true)

;; Result type definitions
(defrecord Success [value])
(defrecord Failure [error context])

;; Constructor functions
(defn ok
  "Creates a Success result with the given value."
  [v]
  (->Success v))

(defn err
  "Creates a Failure result with an error message and optional context map."
  ([error-msg]
   (->Failure error-msg {}))
  ([error-msg context]
   (->Failure error-msg context)))

;; Type predicates
(defn success?
  "Returns true if result is a Success."
  [result]
  (instance? Success result))

(defn failure?
  "Returns true if result is a Failure."
  [result]
  (instance? Failure result))

;; Value extraction
(defn get-value
  "Extracts value from Success, returns nil for Failure."
  [result]
  (when (success? result)
    (:value result)))

(defn unwrap
  "Unwraps a Success value or throws on Failure."
  [result]
  (if (success? result)
    (:value result)
    (throw (ex-info (:error result) (:context result)))))

(defn bind
  "Applies function f to Success value, short-circuits on Failure.

  f takes a value and returns a Result.
  Example: (bind (ok 5) (fn [x] (ok (* x 2)))) => (ok 10)"
  [result f]
  (if (success? result)
    (f (:value result))
    result))

(defn fmap
  "Maps function over a Success value, wrapping result in ok.

  f takes a value and returns a plain value.
  Example: (fmap (ok 5) (fn [x] (* x 2))) => (ok 10)"
  [result f]
  (if (success? result)
    (ok (f (:value result)))
    result))
(defmacro result->
  "Thread-first macro for Result values. Short-circuits on first Failure.

  Example:
  (result-> (ok 5)
            (bind inc-if-valid)
            (fmap (* 2)))"
  [val & forms]
  (reduce (fn [acc form]
            (if (seq? form)
              `(bind ~acc (fn [v#] (-> v# ~form)))
              `(bind ~acc (fn [v#] (~form v#)))))
          val
          forms))

(defmacro result->>
  "Thread-last macro for Result values. Short-circuits on first Failure."
  [val & forms]
  (reduce (fn [acc form]
            (if (seq? form)
              `(bind ~acc (fn [v#] (->> v# ~form)))
              `(bind ~acc (fn [v#] (~form v#)))))
          val
          forms))

;; Error recovery and composition
(defn or-else
  "Returns first successful result, or last failure if all fail.

  Example: (or-else (err \"fail 1\") (err \"fail 2\") (ok 3)) => (ok 3)"
  [& results]
  (or (first (filter success? results))
      (last results)))

(defn combine
  "Combines multiple results into a single result containing a vector of values.
  Returns Failure on first error."
  [results]
  (reduce (fn [acc result]
            (if (and (success? acc) (success? result))
              (ok (conj (:value acc) (:value result)))
              (or (when (failure? acc) acc)
                  result)))
          (ok [])
          results))

(defn combine-all-errors
  "Combines multiple results, collecting all errors.
  Returns Success with vector of values if all succeed,
  or Failure with all error messages if any fail."
  [results]
  (let [failures (filter failure? results)
        successes (filter success? results)]
    (if (empty? failures)
      (ok (mapv :value successes))
      (err "Multiple errors occurred"
           {:errors (mapv #(select-keys % [:error :context]) failures)
            :count (count failures)}))))

;; Try/catch wrappers
(defn try-result
  "Wraps a potentially throwing function in a Result.

  Returns Success with function result, or Failure with exception message.
  Optional error-fn transforms exception to custom error message."
  ([f]
   (try-result f identity))
  ([f error-fn]
   (try
     (ok (f))
     (catch Exception e
       (err (error-fn e) {:exception e :message (.getMessage e)})))))

(defn try-result-with-context
  "Like try-result but with custom context for errors."
  [f context-map]
  (try
    (ok (f))
    (catch Exception e
      (err (.getMessage e)
           (merge context-map
                  {:exception e
                   :exception-type (class e)})))))

;; Conditional construction
(defn when-result
  "Returns Success if condition is true, Failure otherwise."
  [condition value error-msg]
  (if condition
    (ok value)
    (err error-msg)))

(defn validate
  "Validates a value with a predicate.
  Returns Success if valid, Failure with error-msg if invalid."
  [value pred error-msg]
  (if (pred value)
    (ok value)
    (err error-msg {:value value})))

;; Legacy compatibility helpers
(defn exit-on-failure
  "Exits with status 1 if result is Failure, returns value if Success."
  [result]
  (if (failure? result)
    (do
      (println "Error:" (:error result))
      (when (seq (:context result))
        (println "Context:" (:context result)))
      (System/exit 1))
    (:value result)))

(defn print-failure
  "Prints failure details without exiting."
  [result]
  (when (failure? result)
    (println "Error:" (:error result))
    (when (seq (:context result))
      (println "Details:" (:context result)))))
