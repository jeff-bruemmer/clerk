(ns proserunner.result-test
  "Tests for the Result type."
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.result :as result]))

(deftest success-creation-test
  (testing "Creating success results"
    (let [r (result/ok 42)]
      (is (result/success? r))
      (is (not (result/failure? r)))
      (is (= 42 (:value r))))))

(deftest failure-creation-test
  (testing "Creating failure results"
    (let [r (result/err "Something went wrong" {:code 404})]
      (is (result/failure? r))
      (is (not (result/success? r)))
      (is (= "Something went wrong" (:error r)))
      (is (= {:code 404} (:context r))))))

(deftest bind-test
  (testing "Bind with successful results"
    (let [r1 (result/ok 5)
          r2 (result/bind r1 (fn [x] (result/ok (* x 2))))]
      (is (result/success? r2))
      (is (= 10 (:value r2)))))

  (testing "Bind short-circuits on failure"
    (let [r1 (result/err "Failed" {})
          r2 (result/bind r1 (fn [x] (result/ok (* x 2))))]
      (is (result/failure? r2))
      (is (= "Failed" (:error r2)))))

  (testing "Bind propagates failure through chain"
    (let [r (-> (result/ok 5)
                (result/bind (fn [x] (result/ok (* x 2))))
                (result/bind (fn [_] (result/err "Chain broken" {})))
                (result/bind (fn [x] (result/ok (* x 3)))))]
      (is (result/failure? r))
      (is (= "Chain broken" (:error r))))))

(deftest fmap-test
  (testing "fmap applies function to success value"
    (let [r1 (result/ok 5)
          r2 (result/fmap r1 #(* % 2))]
      (is (result/success? r2))
      (is (= 10 (:value r2)))))

  (testing "fmap short-circuits on failure"
    (let [r1 (result/err "Failed" {})
          r2 (result/fmap r1 #(* % 2))]
      (is (result/failure? r2))
      (is (= "Failed" (:error r2))))))

(deftest or-else-test
  (testing "or-else returns first success"
    (let [r1 (result/err "Fail 1" {})
          r2 (result/err "Fail 2" {})
          r3 (result/ok 42)
          r4 (result/ok 100)
          result (result/or-else r1 r2 r3 r4)]
      (is (result/success? result))
      (is (= 42 (:value result)))))

  (testing "or-else returns last failure when all fail"
    (let [r1 (result/err "Fail 1" {})
          r2 (result/err "Fail 2" {})
          r3 (result/err "Fail 3" {})
          result (result/or-else r1 r2 r3)]
      (is (result/failure? result))
      (is (= "Fail 3" (:error result))))))

(deftest combine-test
  (testing "combine succeeds when all succeed"
    (let [results [(result/ok 1) (result/ok 2) (result/ok 3)]
          combined (result/combine results)]
      (is (result/success? combined))
      (is (= [1 2 3] (:value combined)))))

  (testing "combine fails on first failure"
    (let [results [(result/ok 1)
                   (result/err "Failed" {})
                   (result/ok 3)]
          combined (result/combine results)]
      (is (result/failure? combined))
      (is (= "Failed" (:error combined))))))

(deftest combine-all-errors-test
  (testing "combine-all-errors collects all failures"
    (let [results [(result/ok 1)
                   (result/err "Fail 1" {})
                   (result/ok 3)
                   (result/err "Fail 2" {})]
          combined (result/combine-all-errors results)]
      (is (result/failure? combined))
      (is (= "Multiple errors occurred" (:error combined)))
      (is (= 2 (-> combined :context :count)))))

  (testing "combine-all-errors succeeds when all succeed"
    (let [results [(result/ok 1) (result/ok 2) (result/ok 3)]
          combined (result/combine-all-errors results)]
      (is (result/success? combined))
      (is (= [1 2 3] (:value combined))))))

(deftest try-result-test
  (testing "try-result wraps successful function"
    (let [r (result/try-result #(+ 1 2))]
      (is (result/success? r))
      (is (= 3 (:value r)))))

  (testing "try-result catches exceptions"
    (let [r (result/try-result #(throw (Exception. "Boom!")))]
      (is (result/failure? r))
      (is (instance? Exception (:error r)))))

  (testing "try-result with custom error function"
    (let [r (result/try-result
             #(throw (Exception. "Boom!"))
             (fn [e] (str "Custom: " (.getMessage e))))]
      (is (result/failure? r))
      (is (= "Custom: Boom!" (:error r))))))

(deftest try-result-with-context-test
  (testing "try-result-with-context adds context to errors"
    (let [r (result/try-result-with-context
             #(throw (Exception. "File not found"))
             {:file "/tmp/test.txt" :operation :read})]
      (is (result/failure? r))
      (is (= "File not found" (:error r)))
      (is (= "/tmp/test.txt" (-> r :context :file)))
      (is (= :read (-> r :context :operation))))))

(deftest when-result-test
  (testing "when-result returns success when condition is true"
    (let [r (result/when-result true 42 "Should not fail")]
      (is (result/success? r))
      (is (= 42 (:value r)))))

  (testing "when-result returns failure when condition is false"
    (let [r (result/when-result false 42 "Condition failed")]
      (is (result/failure? r))
      (is (= "Condition failed" (:error r))))))

(deftest validate-test
  (testing "validate returns success for valid value"
    (let [r (result/validate 10 pos? "Must be positive")]
      (is (result/success? r))
      (is (= 10 (:value r)))))

  (testing "validate returns failure for invalid value"
    (let [r (result/validate -5 pos? "Must be positive")]
      (is (result/failure? r))
      (is (= "Must be positive" (:error r)))
      (is (= -5 (-> r :context :value))))))

(deftest chaining-test
  (testing "Complex chaining scenario"
    (let [parse-int (fn [s]
                      (try
                        (result/ok (Integer/parseInt s))
                        (catch Exception _
                          (result/err "Not a number" {:input s}))))
          validate-positive (fn [n]
                              (if (pos? n)
                                (result/ok n)
                                (result/err "Not positive" {:value n})))
          double-it (fn [n] (result/ok (* 2 n)))

          result (-> (parse-int "42")
                     (result/bind validate-positive)
                     (result/bind double-it))]
      (is (result/success? result))
      (is (= 84 (:value result)))))

  (testing "Chaining with early failure"
    (let [parse-int (fn [s]
                      (try
                        (result/ok (Integer/parseInt s))
                        (catch Exception _
                          (result/err "Not a number" {:input s}))))
          validate-positive (fn [n]
                              (if (pos? n)
                                (result/ok n)
                                (result/err "Not positive" {:value n})))
          double-it (fn [n] (result/ok (* 2 n)))

          result (-> (parse-int "not-a-number")
                     (result/bind validate-positive)
                     (result/bind double-it))]
      (is (result/failure? result))
      (is (= "Not a number" (:error result))))))

;; Property tests for Result composition behavior
;; Verifies that bind/fmap follow expected algebraic properties

(deftest monad-law-left-identity-test
  (testing "Left Identity: bind (ok x) f === f x"
    (let [f (fn [x] (result/ok (* x 2)))
          test-values [0 1 42 -5 1000]]
      (doseq [x test-values]
        (let [left (result/bind (result/ok x) f)
              right (f x)]
          (is (= left right)
              (str "Left identity failed for value: " x))))))

  (testing "Left Identity with failure-producing function"
    (let [f (fn [x] (if (pos? x)
                      (result/ok x)
                      (result/err "negative" {})))
          test-values [-5 -1 0 1 5]]
      (doseq [x test-values]
        (let [left (result/bind (result/ok x) f)
              right (f x)]
          (is (= left right)
              (str "Left identity failed for value: " x)))))))

(deftest monad-law-right-identity-test
  (testing "Right Identity: bind m ok === m"
    (let [test-results [(result/ok 42)
                        (result/ok 0)
                        (result/ok "hello")
                        (result/ok [1 2 3])
                        (result/err "error" {})
                        (result/err "failed" {:code 404})]]
      (doseq [m test-results]
        (let [left (result/bind m result/ok)
              right m]
          (is (= left right)
              (str "Right identity failed for: " m)))))))

(deftest monad-law-associativity-test
  (testing "Associativity: bind (bind m f) g === bind m (λx. bind (f x) g)"
    (let [f (fn [x] (result/ok (* x 2)))
          g (fn [x] (result/ok (+ x 10)))
          test-values [0 1 5 42 -3]]
      (doseq [x test-values]
        (let [m (result/ok x)
              ;; Left side: bind (bind m f) g
              left (result/bind (result/bind m f) g)
              ;; Right side: bind m (λx. bind (f x) g)
              right (result/bind m (fn [y] (result/bind (f y) g)))]
          (is (= left right)
              (str "Associativity failed for value: " x))))))

  (testing "Associativity with mixed success/failure"
    (let [f (fn [x] (if (even? x)
                      (result/ok (/ x 2))
                      (result/err "odd number" {:value x})))
          g (fn [x] (result/ok (* x 3)))
          test-values [2 4 6 3 5 7]]
      (doseq [x test-values]
        (let [m (result/ok x)
              left (result/bind (result/bind m f) g)
              right (result/bind m (fn [y] (result/bind (f y) g)))]
          (is (= left right)
              (str "Associativity failed for value: " x))))))

  (testing "Associativity with initial failure"
    (let [f (fn [x] (result/ok (* x 2)))
          g (fn [x] (result/ok (+ x 10)))
          m (result/err "initial failure" {})
          left (result/bind (result/bind m f) g)
          right (result/bind m (fn [y] (result/bind (f y) g)))]
      (is (= left right)
          "Associativity should hold even with initial failure"))))

(deftest monad-law-functor-composition-test
  (testing "Functor law: fmap (f ∘ g) === fmap f ∘ fmap g"
    (let [f (fn [x] (* x 2))
          g (fn [x] (+ x 10))
          test-values [0 1 5 42 -3]]
      (doseq [x test-values]
        (let [m (result/ok x)
              ;; Left side: fmap (f ∘ g)
              left (result/fmap m (comp f g))
              ;; Right side: (fmap f) ∘ (fmap g)
              right (-> m (result/fmap g) (result/fmap f))]
          (is (= left right)
              (str "Functor composition failed for value: " x))))))

  (testing "Functor preserves identity"
    (let [test-values [(result/ok 42)
                       (result/ok "hello")
                       (result/err "error" {})]]
      (doseq [m test-values]
        (let [left (result/fmap m identity)
              right m]
          (is (= left right)
              (str "Functor identity failed for: " m)))))))

(deftest monad-consistency-with-functor-test
  (testing "fmap f m === bind m (ok ∘ f)"
    (let [f (fn [x] (* x 2))
          test-values [0 1 5 42 -3]]
      (doseq [x test-values]
        (let [m (result/ok x)
              left (result/fmap m f)
              right (result/bind m (comp result/ok f))]
          (is (= left right)
              (str "Monad-functor consistency failed for value: " x)))))))
