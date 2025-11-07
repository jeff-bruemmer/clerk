(ns proserunner.config.types
  "Shared configuration types used across config and project-config namespaces."
  (:gen-class))

(set! *warn-on-reflection* true)

(defrecord Config
  [checks                     ; Vector of Check records defining prose rules to apply
   ignore])                   ; Set of ignore patterns (strings or contextual maps with :file, :line-num, :specimen)
