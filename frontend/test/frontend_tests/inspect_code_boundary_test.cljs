;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns frontend-tests.inspect-code-boundary-test
  (:require
   [app.main.data.viewer.shortcuts :as shortcuts]
   [app.main.ui.viewer :as viewer]
   [app.main.ui.viewer.share-link :as share-link]
   [app.main.ui.workspace.sidebar.options :as options]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]))

(def ^:private effective-section @#'viewer/effective-section)
(def ^:private effective-options-mode @#'options/effective-options-mode)
(def ^:private options-tabs @#'options/options-tabs)
(def ^:private prepare-share-link-params @#'share-link/prepare-params)

(defn- shortcut-commands
  []
  (mapcat
   (fn [{:keys [command]}]
     (if (sequential? command)
       command
       [command]))
   (vals shortcuts/shortcuts)))

(t/deftest viewer-shortcuts-do-not-open-inspect
  (t/is (not (contains? shortcuts/shortcuts :open-inspect)))
  (t/is (not-any? #(= "g i" %) (shortcut-commands)))
  (t/is (not-any? #(and (string? %) (str/includes? % "g i"))
                  (shortcut-commands))))

(t/deftest viewer-stale-inspect-section-falls-back-to-interactions
  (t/is (= :interactions (effective-section :inspect)))
  (t/is (= :comments (effective-section :comments)))
  (t/is (= :interactions (effective-section :interactions))))

(t/deftest workspace-stale-inspect-options-mode-falls-back-to-design
  (t/is (= :design (effective-options-mode :inspect)))
  (t/is (= :design (effective-options-mode :design)))
  (t/is (= :prototype (effective-options-mode :prototype))))

(t/deftest workspace-visible-tabs-preserve-design-and-prototype-only
  (t/is (= ["design" "prototype"] (mapv :id options-tabs))))

(t/deftest share-link-params-preserve-who-inspect
  (let [params {:pages #{:page-1 :page-2}
                :who-comment "team"
                :who-inspect "all"}]
    (t/is (= {:pages #{:page-1 :page-2}
              :who-comment "team"
              :who-inspect "all"}
             (prepare-share-link-params params)))))
