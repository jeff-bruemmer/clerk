(ns clerk.version
  (:require [clojure.string :as string]
            [clojure.java.io :as io])
  (:gen-class))

(def number
  (string/trim
   (slurp (io/resource "CLERK_VERSION"))))

