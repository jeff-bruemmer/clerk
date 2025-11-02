(ns proserunner.config.types
  "Shared configuration types used across config and project-config namespaces."
  (:gen-class))

(set! *warn-on-reflection* true)

(defrecord Config
  [checks ignore])

;; Configuration record for proserunner checks and ignore patterns.
;; Fields:
;; - checks: Vector of check definitions (maps with :name, :directory, :files)
;; - ignore: Set of ignore patterns or string path to ignore file
