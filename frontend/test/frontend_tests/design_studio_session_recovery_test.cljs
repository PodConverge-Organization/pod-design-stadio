;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns frontend-tests.design-studio-session-recovery-test
  (:require
   [app.main.data.design-studio-session-recovery :as dsr]
   [app.main.ui.routes :as routes]
   [cljs.test :as t :include-macros true]))

(def ^:private valid-recovery-uri
  "https://bridge.example/auth/design-studio/recover?source=design-studio")

(t/use-fixtures :each
  {:before dsr/clear-recovery-attempt!
   :after  dsr/clear-recovery-attempt!})

(defn- action
  [decision]
  (:action decision))

(t/deftest protected-route-classification
  (t/testing "workspace, dashboard, and settings route families are protected"
    (t/is (true? (dsr/protected-route-name? :workspace)))
    (t/is (true? (dsr/protected-route-name? :workspace-legacy)))
    (t/is (true? (dsr/protected-route-name? :dashboard-recent)))
    (t/is (true? (dsr/protected-route-name? :settings-profile)))))

(t/deftest non-protected-route-classification
  (t/testing "viewer, auth, preview, debug, and missing routes are not protected"
    (t/is (false? (boolean (dsr/protected-route-name? :viewer))))
    (t/is (false? (boolean (dsr/protected-route-name? :viewer-legacy))))
    (t/is (false? (boolean (dsr/protected-route-name? :auth-login))))
    (t/is (false? (boolean (dsr/protected-route-name? :frame-preview))))
    (t/is (false? (boolean (dsr/protected-route-name? :debug-playground))))
    (t/is (false? (boolean (dsr/protected-route-name? nil)))))

  (t/testing "viewer routes are explicitly classified for local handling"
    (t/is (true? (dsr/viewer-route-name? :viewer)))
    (t/is (true? (dsr/viewer-route-name? :viewer-legacy)))
    (t/is (false? (boolean (dsr/viewer-route-name? :workspace))))))

(t/deftest current-route-table-path-resolution
  (t/testing "route names are derived from the canonical 2.17.1 route table"
    (t/is (= :workspace
             (routes/route-name-for-path "/workspace?team-id=team-1&file-id=file-1")))
    (t/is (= :workspace-legacy
             (routes/route-name-for-path "/workspace/project-1/file-1?team-id=team-1")))
    (t/is (= :dashboard-recent
             (routes/route-name-for-path "/dashboard/recent?team-id=team-1")))
    (t/is (= :settings-profile
             (routes/route-name-for-path "/settings/profile")))
    (t/is (= :viewer
             (routes/route-name-for-path "/view?file-id=file-1&share-id=share-1")))
    (t/is (= :viewer-legacy
             (routes/route-name-for-path "/view/file-1?share-id=share-1")))))

(t/deftest startup-decisions
  (t/testing "anonymous protected startup with valid config recovers"
    (t/is (= :recover
             (action (dsr/startup-decision false :workspace valid-recovery-uri)))))

  (t/testing "authenticated protected startup continues and clears stale guard"
    (dsr/mark-recovery-attempt!)
    (let [decision (dsr/startup-decision true :workspace valid-recovery-uri)]
      (t/is (= :continue (action decision)))
      (t/is (true? (:clear-guard? decision))))
    (dsr/clear-recovery-attempt!))

  (t/testing "dashboard and settings anonymous startup recover"
    (t/is (= :recover
             (action (dsr/startup-decision false :dashboard-recent valid-recovery-uri))))
    (t/is (= :recover
             (action (dsr/startup-decision false :settings-profile valid-recovery-uri)))))

  (t/testing "viewer anonymous startup continues without recovery"
    (t/is (= :continue
             (action (dsr/startup-decision false :viewer valid-recovery-uri))))
    (t/is (= :continue
             (action (dsr/startup-decision false :viewer-legacy valid-recovery-uri)))))

  (t/testing "already-attempted protected startup fails closed"
    (dsr/mark-recovery-attempt!)
    (t/is (= :fail-closed
             (action (dsr/startup-decision false :workspace valid-recovery-uri))))))

(t/deftest guard-mark-and-clear
  (t/testing "the per-tab guard marks one attempt and clear allows a later independent attempt"
    (t/is (false? (dsr/recovery-attempted?)))
    (dsr/mark-recovery-attempt!)
    (t/is (true? (dsr/recovery-attempted?)))
    (t/is (= :fail-closed
             (action (dsr/startup-decision false :workspace valid-recovery-uri))))
    (dsr/clear-recovery-attempt!)
    (t/is (false? (dsr/recovery-attempted?)))
    (t/is (= :recover
             (action (dsr/startup-decision false :workspace valid-recovery-uri))))))

(t/deftest invalid-config-fails-closed
  (t/testing "missing and invalid recovery configuration never selects external redirect"
    (doseq [invalid-uri [nil
                         ""
                         "   "
                         "not a url"
                         "ftp://bridge.example/recover"
                         "https://user:pass@bridge.example/recover"]]
      (t/is (false? (dsr/valid-recovery-uri? invalid-uri)))
      (t/is (= :fail-closed
               (action (dsr/startup-decision false :workspace invalid-uri)))))))

(t/deftest recovery-url-preserves-endpoint-query-and-return-to
  (t/testing "returnTo round-trips the exact current href, including query and hash specials"
    (let [return-to "https://design.example/#/workspace?file-id=file%201&page-id=page%2F1&name=a%26b#nested"
          recovery  (dsr/recovery-url
                     "https://bridge.example/recover?source=design-studio&returnTo=old"
                     return-to)
          url       (js/URL. recovery)]
      (t/is (= "https://bridge.example/recover" (str (.-origin url) (.-pathname url))))
      (t/is (= "design-studio" (.get (.-searchParams url) "source")))
      (t/is (= return-to (.get (.-searchParams url) "returnTo"))))))

(t/deftest runtime-authentication-decisions
  (t/testing "canonical protected authentication-required recovers"
    (t/is (= :recover
             (action (dsr/runtime-authentication-decision
                      :workspace
                      {:type :authentication
                       :code :authentication-required}
                      valid-recovery-uri)))))

  (t/testing "team-access-authentication-error-stays-local"
    (t/is (= :local-exception
             (action (dsr/runtime-authentication-decision
                      :workspace
                      {:type :authentication}
                      valid-recovery-uri)))))

  (t/testing "protected non-canonical authentication code stays local"
    (t/is (= :local-exception
             (action (dsr/runtime-authentication-decision
                      :workspace
                      {:type :authentication
                       :code :team-access-denied}
                      valid-recovery-uri)))))

  (t/testing "canonical protected error fails closed after one attempted recovery"
    (dsr/mark-recovery-attempt!)
    (t/is (= :fail-closed
             (action (dsr/runtime-authentication-decision
                      :workspace
                      {:type :authentication
                       :code :authentication-required}
                      valid-recovery-uri)))))

  (t/testing "viewer runtime authentication remains local"
    (dsr/clear-recovery-attempt!)
    (t/is (= :local-exception
             (action (dsr/runtime-authentication-decision
                      :viewer
                      {:type :authentication
                       :code :authentication-required}
                      valid-recovery-uri)))
          "current viewer route stays local")
    (t/is (= :local-exception
             (action (dsr/runtime-authentication-decision
                      :viewer-legacy
                      {:type :authentication
                       :code :authentication-required}
                      valid-recovery-uri)))
          "legacy viewer route stays local"))

  (t/testing "auth-login and non-protected runtime authentication keep logout behavior"
    (t/is (= :logout
             (action (dsr/runtime-authentication-decision
                      :auth-login
                      {:type :authentication
                       :code :authentication-required}
                      valid-recovery-uri))))
    (t/is (= :logout
             (action (dsr/runtime-authentication-decision
                      :frame-preview
                      {:type :authentication
                       :code :authentication-required}
                      valid-recovery-uri))))))
