(ns clerk.system
  "Utilites for clerk to figure out what OS it's on so it can follow system mores."
  (:gen-class)
  (:require [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defn home-dir
  "Gets home directory for storing config directory."
  []
  (System/getProperty "user.home"))

(defn filepath
  "Builds filepath using the home directory."
  ([& args]
   (str (string/join (java.io.File/separator) (concat [(home-dir)] args)))))

(defn check-dir
  "Infer the directory when supplied a config filepath."
  [config]
  (let [dd (filepath ".clerk/")]
    (if (nil? config)
      (do (println "Using default directory: " dd)
          dd)
      (-> config
          (string/split (re-pattern (java.io.File/separator)))
          drop-last
          (#(string/join "/" %))
          (str (java.io.File/separator))))))

