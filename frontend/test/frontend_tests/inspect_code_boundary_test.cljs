;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns frontend-tests.inspect-code-boundary-test
  (:require
   [app.main.data.viewer.shortcuts :as shortcuts]
   [app.main.ui.viewer :as viewer]
   [app.main.ui.viewer.share-link :as share-link]
   [app.main.ui.workspace.sidebar.options :as options]
   [cljs.test :as t :include-macros true]))

(defn- command?
  [expected command]
  (if (sequential? command)
    (some #(= expected %) command)
    (= expected command)))

(t/deftest viewer-shortcuts-do-not-open-inspect
  (t/is (not (contains? shortcuts/shortcuts :open-inspect)))
  (t/is (not-any? #(command? "g i" (:command %))
                  (vals shortcuts/shortcuts))))

(t/deftest viewer-inspect-section-falls-back-to-interactions
  (t/is (= :interactions (#'viewer/effective-section :inspect)))
  (t/is (= :comments (#'viewer/effective-section :comments)))
  (t/is (= :interactions (#'viewer/effective-section :interactions))))

(t/deftest workspace-inspect-options-mode-falls-back-to-design
  (t/is (= :design (#'options/effective-options-mode :inspect)))
  (t/is (= :design (#'options/effective-options-mode :design)))
  (t/is (= :prototype (#'options/effective-options-mode :prototype))))

(t/deftest share-link-keeps-who-inspect-contract
  (let [params (#'share-link/prepare-params {:pages #{"page-id"}
                                             :who-comment "team"
                                             :who-inspect "team"})]
    (t/is (= "team" (:who-inspect params)))
    (t/is (= {:pages #{"page-id"}
              :who-comment "team"
              :who-inspect "team"}
             params))))
