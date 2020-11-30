(ns clerk.system
  (:gen-class)
  (:require [clojure.string :as string]))

(def home-dir (System/getProperty "user.home"))

(defn filepath
  "Builds filepath with using the home directory."
  ([& args]
   (str (string/join (java.io.File/separator) (concat [(System/getProperty "user.home")] args)))))
