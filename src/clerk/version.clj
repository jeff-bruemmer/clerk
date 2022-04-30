(ns clerk.version
  "Puny namespace used just to get a somewhat meaningless number."
  (:require [clojure.string :as string]
            [clojure.java.io :as io])
  (:gen-class))

(def number
  (string/trim
   (slurp (io/resource "CLERK_VERSION"))))

