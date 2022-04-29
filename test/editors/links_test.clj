(ns editors.links-test
  (:require [clerk
             [checks :as checks]
             [text :as text]]
            [editors.links :as l]
            [clojure.test :as t :refer [deftest is]])
  (:use clj-http.fake))

(def handsome-line (text/->Line
                    "resources"
                    "This [link](http://good.com) is working."
                    42
                    false
                    []))

(def error-line (text/->Line
                 "resources"
                 "This [link](http://bad.com) is broken"
                 42
                 false
                 []))

(def linkless-line (text/->Line
                    "resources"
                    "This [link](not-even-a-link) is broken"
                    42
                    false
                    []))

(def link-check (checks/map->Check {:name "Links"
                                    :message "Broken link"
                                    :kind "links"
                                    :explanation "Link does not return a 200-299 status code."}))

(with-fake-routes {"http://good.com" (fn [request] {:status 200 :headers {} :body "Okay."})
                   "http://bad.com" (fn [request] {:status 500 :headers {} :body "Broken."})}

  ;; Your tests with requests here
  (deftest links
    (is true? (:issue? (l/proofread error-line link-check)))
    (is false? (:issue? (l/proofread handsome-line link-check)))
    (is true? (:issue? (l/proofread linkless-line link-check)))))



