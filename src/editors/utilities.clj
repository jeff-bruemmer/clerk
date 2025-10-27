(ns editors.utilities
  (:require [clerk.text :as text]
            [clojure.string :as string])
  (:gen-class))

;; LRU cache configuration
(def ^:private max-cache-size 1000)

;; Cache for compiled regex patterns with LRU eviction
(def ^:private pattern-cache (atom {:entries {} :access-order []}))
(def ^:private re-core-cache (atom {:entries {} :access-order []}))

(defn- lru-get
  "Get value from LRU cache and update access order."
  [cache-atom key]
  (let [cache @cache-atom
        value (get-in cache [:entries key])]
    (when value
      ;; Update access order - move key to end
      (swap! cache-atom update :access-order
             (fn [order]
               (conj (vec (remove #{key} order)) key))))
    value))

(defn- lru-put
  "Put value in LRU cache with eviction if needed."
  [cache-atom key value]
  (swap! cache-atom
         (fn [cache]
           (let [entries (:entries cache)
                 access-order (:access-order cache)
                 new-entries (if (>= (count entries) max-cache-size)
                               ;; Evict least recently used
                               (dissoc entries (first access-order))
                               entries)
                 new-order (if (>= (count entries) max-cache-size)
                             (vec (rest access-order))
                             access-order)]
             {:entries (assoc new-entries key value)
              :access-order (conj new-order key)}))))

(defn make-pattern
  "Used to concat regex pattern to search for multiple specimens at once."
  [re-payload case-sensitive?]
  (let [cache-key [re-payload case-sensitive?]]
    (if-let [cached-pattern (lru-get pattern-cache cache-key)]
      cached-pattern
      (let [ignore-chars "[^\\[\\#-_]"
            boundary "\\b("
            leftb (if case-sensitive?
                    ;; Ignore matches in markdown/org links
                    (str ignore-chars boundary)
                    (str "(?i)" ignore-chars boundary))
            pattern (->> re-payload
                        (#(str leftb % ")\\b"))
                        (re-pattern))]
        ;; Cache the compiled pattern with LRU eviction
        (lru-put pattern-cache cache-key pattern)
        pattern))))

(defn seek
  "Given a text, regex, and case sensitivity, returns a vector of regex matches."
  [text re-payload case-sensitive?]
  (let [p (make-pattern re-payload case-sensitive?)
        matches (re-seq p text)]
    (if matches
      (into [] (map first matches))
      [])))

(defn add-issue
  "Adds an issue to a text/Line's issues."
  [{:keys [line specimen name kind message]}]
  (let [{:keys [file text]} line
        ;; Only convert to lowercase if needed
        col (if (string/includes? text specimen)
              (string/index-of text specimen)
              (when-let [idx (string/index-of (string/lower-case text) 
                                            (string/lower-case specimen))]
                idx))]
    ;; If the string is not in the line, there is no issue.
    ;; Guards against legitimate repetition hits, hits that are valid.
    (if (nil? col)
      line
      (-> line
          (assoc :issue? true)
          (update :issues conj (text/->Issue
                               (text/home-path file)
                               name
                               kind
                               specimen
                               col
                               message))))))

(defn create-issue-collector
  "Samples lines for each specimen in check, and adds any issues to the line."
  [case-sensitive?]
  (fn
    [line
     {:keys [file kind specimens message name]}]
    (if (empty? specimens)
      line
      (let [re-core (if-let [cached-core (lru-get re-core-cache specimens)]
                      cached-core
                      (let [new-core (string/join "|" specimens)]
                        (lru-put re-core-cache specimens new-core)
                        new-core))
            matches (seek (:text line) re-core case-sensitive?)]
        (if (empty? matches)
          line
          (reduce
           (fn [l match] (add-issue {:file file
                                    :line l
                                    :specimen match
                                    :name name
                                    :kind kind
                                    :message message}))
           line
           matches))))))

(defn create-recommender
  "Returns a function that accepts a boolean to determine whether the
  recommender is case sensitive."
  [case-sensitive?]
  (fn [line check]
    (let [{:keys [recommendations name kind]} check]
      (if (empty? recommendations)
        line
        (reduce (fn [line {:keys [prefer avoid]}]
                  (let [{:keys [file text]} line
                        matches (seek text avoid case-sensitive?)]
                    (if (empty? matches)
                      line
                      (reduce (fn [l match] (add-issue {:file file
                                                       :line l
                                                       :specimen match
                                                       :name name
                                                       :kind kind
                                                       :message (str "Prefer: " prefer)}))
                              line
                              matches))))
                line
                recommendations)))))
