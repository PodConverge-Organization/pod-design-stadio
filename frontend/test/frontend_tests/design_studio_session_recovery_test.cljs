;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns frontend-tests.design-studio-session-recovery-test
  (:require
   [app.common.uuid :as uuid]
   [app.main.data.design-studio-session-recovery :as dsr]
   [app.main.ui.routes :as routes]
   [cljs.test :as t :include-macros true]))

(def anonymous-profile
  {:id uuid/zero})

(def authenticated-profile
  {:id (uuid/uuid "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa")})

(def recovery-uri
  "https://app.example.com/auth/design-studio/recover")

(def return-to
  "https://design.example.com/#/workspace?team-id=team&file-id=file&page-id=page")

(def session-authentication-error
  {:type :authentication
   :code :authentication-required})

(def local-authentication-error
  {:type :authentication})

(defn startup-decision
  [profile route-name & {:keys [attempted? uri]
                         :or {attempted? false
                              uri recovery-uri}}]
  (dsr/startup-decision
   {:profile profile
    :route-name route-name
    :recovery-uri uri
    :return-to return-to
    :recovery-attempted? attempted?}))

(defn authentication-decision
  [route-name & {:keys [attempted? error uri]
                 :or {attempted? false
                      error session-authentication-error
                      uri recovery-uri}}]
  (dsr/authentication-error-decision
   {:error error
    :route-name route-name
    :recovery-uri uri
    :return-to return-to
    :recovery-attempted? attempted?}))

(t/deftest protected-route-classification
  (t/are [route-name] (dsr/protected-route? route-name)
    :workspace
    :workspace-legacy
    :dashboard-recent
    :dashboard-legacy-files
    :settings-profile)

  (t/are [route-name] (not (dsr/protected-route? route-name))
    :viewer
    :viewer-legacy
    :auth-login
    nil
    :frame-preview
    :debug-playground))

(t/deftest actual-route-table-path-classification
  (t/are [path route-name] (= route-name (routes/route-name-for-path path))
    "/workspace?team-id=team&file-id=file&page-id=page" :workspace
    "/workspace/project-id/file-id?page-id=page" :workspace-legacy
    "/dashboard/recent?team-id=team" :dashboard-recent
    "/settings/profile" :settings-profile
    "/view?share-id=share" :viewer
    "/view/file-id?share-id=share" :viewer-legacy))

(t/deftest anonymous-protected-startup-selects-recovery
  (let [decision (startup-decision anonymous-profile :workspace)]
    (t/is (= :recover (:type decision)))
    (t/is (= return-to
             (.get (.-searchParams (js/URL. (:href decision))) "returnTo")))))

(t/deftest authenticated-protected-startup-continues
  (let [decision (startup-decision authenticated-profile :workspace)]
    (t/is (= :continue (:type decision)))
    (t/is (true? (:clear-guard? decision)))))

(t/deftest dashboard-and-settings-startup-selects-recovery
  (t/are [route-name] (= :recover (:type (startup-decision anonymous-profile route-name)))
    :dashboard-recent
    :dashboard-legacy-files
    :settings-profile))

(t/deftest viewer-startup-continues-anonymously
  (t/are [route-name] (= :continue (:type (startup-decision anonymous-profile route-name)))
    :viewer
    :viewer-legacy))

(t/deftest loop-guard-fails-closed-after-one-attempt
  (let [decision (startup-decision anonymous-profile :workspace :attempted? true)]
    (t/is (= :fail-closed (:type decision)))
    (t/is (= :already-attempted (get-in decision [:error :reason])))))

(t/deftest guard-clearing-allows-later-recovery
  (let [marked-session (dsr/mark-recovery-attempted {})
        cleared-session (dsr/clear-recovery-attempted marked-session)]
    (t/is (dsr/recovery-attempted? marked-session))
    (t/is (not (dsr/recovery-attempted? cleared-session)))
    (t/is (= :recover
             (:type (startup-decision anonymous-profile
                                      :workspace
                                      :attempted? (dsr/recovery-attempted? cleared-session)))))))

(t/deftest missing-or-invalid-runtime-config-fails-closed
  (t/are [uri] (= :fail-closed
                  (:type (startup-decision anonymous-profile :workspace :uri uri)))
    nil
    ""
    "not a url"
    "ftp://app.example.com/auth/design-studio/recover"
    "https://user:pass@app.example.com/auth/design-studio/recover"))

(t/deftest exact-return-to-round-trips
  (let [original "https://design.podconverge.com/#/workspace?team-id=team&file-id=file&page-id=page%201"
        href     (dsr/build-recovery-url
                  "https://app.example.com/auth/design-studio/recover?source=studio"
                  original)
        url      (js/URL. href)]
    (t/is (= "https:" (.-protocol url)))
    (t/is (= "/auth/design-studio/recover" (.-pathname url)))
    (t/is (= "studio" (.get (.-searchParams url) "source")))
    (t/is (= original (.get (.-searchParams url) "returnTo")))))

(t/deftest runtime-authentication-decision
  (t/is (= :recover (:type (authentication-decision :workspace))))
  (t/is (= :recover (:type (authentication-decision :dashboard-recent))))
  (t/is (= :recover (:type (authentication-decision :settings-profile))))
  (t/is (= :local-exception
           (:type (authentication-decision :workspace :error local-authentication-error))))
  (t/is (= :local-exception
           (:type (authentication-decision :workspace
                                           :error {:type :authentication
                                                   :code :unknown-authentication-code}))))
  (t/is (= :local-exception (:type (authentication-decision :viewer))))
  (t/is (= :local-exception (:type (authentication-decision :viewer-legacy))))
  (t/is (= :logout (:type (authentication-decision :auth-login))))
  (t/is (= :fail-closed (:type (authentication-decision :workspace :attempted? true))))
  (t/is (= :fail-closed (:type (authentication-decision :workspace :uri "https://user@app.example.com/recover")))))
