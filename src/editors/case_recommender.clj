(ns editors.case-recommender
  (:gen-class)
  (:require [editors.utilities :as util]))

(def proofread (util/create-recommender true))
