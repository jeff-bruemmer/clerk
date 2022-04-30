(ns clerk.system
  "Utilites for clerk to figure out what OS it's on so it can follow system mores."
  (:gen-class)
  (:require [clojure.string :as string]))

(set! *warn-on-reflection* true)

(def home-dir (System/getProperty "user.home"))

(defn filepath
  "Builds filepath using the home directory."
  ([& args]
   (str (string/join (java.io.File/separator) (concat [home-dir] args)))))
