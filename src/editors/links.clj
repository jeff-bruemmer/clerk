(ns editors.links
  (:gen-class)
  (:import (org.apache.commons.validator.routines UrlValidator))
  (:require
   [editors.utilities :as util]
   [clj-http.lite.client :as client]
   [clojure.string :as string]))

(defn ^:private valid-url?
  "Is the `url-str` a potential internet address?"
  [url-str]
  (let [validator (UrlValidator.)]
    (.isValid validator url-str)))

(defn ^:private request-url
  "Requests a `url`'s headers."
  [url]
  (let [resp (try (client/head url)
                  (catch Exception _ {:status 404}))]
    {:url url
     :status (:status resp)}))

(defn ^:private ping
  "Pings each url in `urls`."
  [urls]
  (cond
    (empty? urls) urls
    (= (count urls) 1) (map request-url urls)
    :else (pmap request-url urls)))

(defn ^:private broken?
  "Does the response have an ok `status`?"
  [{:keys [status]}]
    (or
     (> status 299)
     (< status 200)))

(defn proofread
  "Checks a `line` for broken links.
  If a link does not return a 200-299 status code,
  proofread adds an issue to the `line`'s issues vector."
  [line check]
  (let [urls (filter valid-url? (string/split (:text line) #"(\[|\]|\(|\))"))
        broken-links (filter broken? (ping urls))
        {:keys [name message kind]} check]
    (if (empty? broken-links)
      line
      (reduce (fn [line bl] (util/add-issue {:line line
                                             :specimen (:url bl)
                                             :name name
                                             :kind kind
                                             :message message}))
              line
              broken-links))))


