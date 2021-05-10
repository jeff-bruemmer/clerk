(ns clerk.shipping
  (:gen-class)
  (:require [clerk
             [version :as ver]
             [checks :as checks]
             [config :as conf]]
            [clojure
             [pprint :as pp]
             [set :refer [rename-keys]]
             [string :as string]]
            [jsonista.core :as json]))

(defn print-version
  "Prints version number."
  []
  (println "Clerk version: " ver/number))

(defn format-summary
  "Function supplied to cli/parse-opts to format map of command line options.
   Map produced sent to ship/print-options."
  [summary]
  (->> summary
       ;; combine short and long option
       (map (fn [m] (assoc m :option (str (:short-opt m) ", " (:long-opt m)))))
       (map #(dissoc % :short-opt :long-opt :id :validate-fn :validate-msg))))

(defn capitalize-first-char
  "Like string/capitalize, only it leaves the rest of the string in tact
  to retain case-sensitive recommendations."
  [s]
    (if (< (count s) 2)
      (string/upper-case s)
      (str (string/upper-case (subs s 0 1))
           (subs s 1))))
(defn prep
  "Prepares results for printing by merging line data with each issue."
  [{:keys [line-num issues]}]
  (reduce (fn [l issue]
            (let [{:keys [name specimen col-num message]} issue]
              (conj l {:line-num line-num
                       :col-num col-num
                       :specimen specimen
                       :name (string/capitalize (string/replace name "-" " "))
                       :message (str (capitalize-first-char message) ".")})))
          []
          issues))

(defn make-key-printer
  "Used for printing in a table: makes for sentence-case column headings."
  [km]
  (fn [m] (rename-keys m km)))

(defn ^:private make-row-formatter
  [ks fmts]
  (fn
    [leader divider trailer row]
    (str leader
         (apply str (interpose divider
                               (for [[col fmt] (map vector (map #(get row %) ks) fmts)]
                                 (format fmt (str col)))))
         trailer)))

(defn ^:private col-widths
  "Determines column widths for spacing tables."
  [ks rows]
  (map
   (fn [k]
     (apply max (count (str k))
            (map #(count (str (get % k))) rows)))
   ks))

(defn print-table
  "Modified from pprint library to left-justify columns in table. Prints a
  collection of maps in a textual table. Prints table headings ks, and then a
  line of output for each row, corresponding to the keys in ks."
  ([ks rows]
   (when (seq rows)
     (let [widths (col-widths ks rows)
           spacers (map #(apply str (repeat % "-")) widths)
           fmts (map #(str "%-" % "s") widths) ;; modified to justify left
           fmt-row (make-row-formatter ks fmts)]
       (println)
       (println (fmt-row "| " " | " " |" (zipmap ks ks)))
       (println (fmt-row "|-" "-+-" "-|" (zipmap ks spacers)))
       (doseq [row rows]
         (println (fmt-row "| " " | " " |" row))))))
  ([rows] (print-table (keys (first rows)) rows)))

(defn print-options
  "Modified from pprint library to left-justify columns in table
  and remove borders. Prints a collection of maps in a textual table."
  ([ks rows]
   (when (seq rows)
     (let [widths (col-widths ks rows)
           fmts (map #(str "%-" % "s") widths) ;; modified to justify left
           fmt-row (make-row-formatter ks fmts)]
       (println)
       (doseq [row rows]
         (println (fmt-row "  " "   " "  " row))))))
  ([rows] (print-table (keys (first rows)) rows)))

(defn ^:private add-period
  "Ensures a `phrase` ends with a period."
  [phrase]
  (if (string/ends-with? phrase ".")
    phrase
    (str phrase ".")))

(defn print-checks
  "Prints a table of the enabled checks: names, kind, and description."
  [c]
  (println "Enabled checks:")
  (->> c
       (conf/fetch-or-create!)
       (checks/create)
       (map (fn [{:keys [name kind explanation]}]
              {:name (string/capitalize name)
               :kind (string/capitalize kind)
               :explanation (add-period explanation)}))
       (sort-by :name)
       (map (make-key-printer {:name "Name" :kind "Kind" :explanation "Explanation"}))
       (print-table)))

(defn print-opts
  "Utiltiy for printing usage."
  [summary title]
  (println title)
  (print-options [:option :required :desc] summary)
  (println "\nConfig file: " (:default (first (filter #(= "CONFIG" (:required %)) summary))) "\n"))

(defn print-usage
  "Prints usage, optionally with a message."

  ([{:keys [summary]}]
   (println "\nClerk vets a text with the supplied checks.\n")
   (print-opts summary "USAGE:"))

  ([opts message]
   (let [{:keys [summary]} opts]
     (println (str "\n" message "\n"))
     (print-opts summary "USAGE:"))))

(defn results-table
  "Takes results and prints them as a table."
  [results]
  (->> results
       (map (make-key-printer {:line-num "Line"
                               :col-num "Col"
                               :specimen "Specimen"
                               :name "Name"
                               :message "Message"}))
       (print-table)))

(defn out
  "Takes results, preps them, and prints them in the supplied output format."
  [{:keys [results output]}]
  (cond
    (and (empty? results)) (println "Flawless victory.")
    (some? results) (let [r (sort-by (juxt :line-num :col-num) (mapcat prep results))]
                      (case (string/lower-case output)
                        "edn" (pp/pprint r)
                        "json" (json/write-value *out* r)
                        (results-table r)))
    :else nil))

;;;; Utilities for generating checks README

(defn print-explanation
  "Prints line of table with check and explanation."
  [{:keys [name explanation]}]
  (let [heading (string/capitalize name)]
    (str "| **" heading "** | " (add-period explanation) " |")))

(defn generate-checks-readme
  "Creates markdown table with checks and their descriptions."
  [config]
  (->> config
       (conf/fetch!)
       (conf/make-config)
       (checks/create)
       (sort-by :name)
       (map print-explanation)
       (string/join \newline)
       (str "| **Check** | **Description** |" \newline "|-|-|" \newline)))
