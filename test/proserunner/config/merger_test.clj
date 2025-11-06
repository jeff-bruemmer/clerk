(ns proserunner.config.merger-test
  (:require [clojure.test :refer [deftest is testing]]
            [proserunner.config.merger :as merger]))

(deftest merge-configs-extend-ignores
  (testing "Merges ignores in extend mode"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{"project-1" "project-2"}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= #{"global-1" "global-2" "project-1" "project-2"}
             (:ignore merged))))))

(deftest merge-configs-replace-ignores
  (testing "Replaces ignores in replace mode"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{"project-1" "project-2"}
                   :ignore-mode :replace}
          merged (merger/merge-configs global project)]
      (is (= #{"project-1" "project-2"}
             (:ignore merged))))))

(deftest merge-configs-project-only-mode
  (testing "Project-only mode ignores global config"
    (let [global {:ignore #{"global-1"}
                  :checks [{:name "global-checks"}]}
          project {:ignore #{"project-1"}
                   :config-mode :project-only
                   :checks ["default"]}
          merged (merger/merge-configs global project)]
      (is (= #{"project-1"} (:ignore merged)))
      (is (= ["default"] (:checks merged))))))

(deftest merge-configs-merged-mode
  (testing "Merged mode combines configs"
    (let [global {:ignore #{"global-1"}
                  :checks [{:name "global-checks"
                           :directory "default"
                           :files ["check1"]}]}
          project {:ignore #{"project-1"}
                   :config-mode :merged
                   :ignore-mode :extend
                   :checks ["default"]}
          merged (merger/merge-configs global project)]
      (is (= #{"global-1" "project-1"} (:ignore merged)))
      (is (= ["default"] (:checks merged))))))

(deftest merge-configs-empty-project-ignore
  (testing "Empty project ignore in extend mode preserves global"
    (let [global {:ignore #{"global-1" "global-2"}}
          project {:ignore #{}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= #{"global-1" "global-2"} (:ignore merged))))))

(deftest merge-configs-no-global-ignore
  (testing "Handles missing global ignore gracefully"
    (let [global {}
          project {:ignore #{"project-1"}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= #{"project-1"} (:ignore merged))))))

(deftest merge-configs-preserves-project-checks
  (testing "Merged mode preserves project checks for later resolution"
    (let [global {:checks [{:directory "default" :files ["global1"]}]}
          project {:checks ["default" {:directory "checks"}]
                   :config-mode :merged}
          merged (merger/merge-configs global project)]
      (is (= ["default" {:directory "checks"}] (:checks merged))))))

(deftest merge-ignore-issues-extend
  (testing "Merges ignore-issues in extend mode as set union"
    (let [global {:ignore-issues #{{:file "a.md" :specimen "x"}}}
          project {:ignore-issues #{{:file "b.md" :specimen "y"}}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= #{{:file "a.md" :specimen "x"}
               {:file "b.md" :specimen "y"}}
             (:ignore-issues merged)))))

  (testing "Handles duplicate ignore-issues entries across global and project"
    (let [issue {:file "a.md" :specimen "test"}
          global {:ignore-issues #{issue}}
          project {:ignore-issues #{issue}
                   :ignore-mode :extend}
          merged (merger/merge-configs global project)]
      (is (= 1 (count (:ignore-issues merged))))
      (is (= #{issue} (:ignore-issues merged))))))

(deftest merge-ignore-issues-replace
  (testing "Replaces ignore-issues in replace mode"
    (let [global {:ignore-issues #{{:file "a.md" :specimen "x"}}}
          project {:ignore-issues #{{:file "b.md" :specimen "y"}}
                   :ignore-mode :replace}
          merged (merger/merge-configs global project)]
      (is (= #{{:file "b.md" :specimen "y"}}
             (:ignore-issues merged))))))
