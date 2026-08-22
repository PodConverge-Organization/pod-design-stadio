;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns backend-tests.http-session-test
  (:require
   [app.common.time :as ct]
   [app.config :as cf]
   [app.http.session :as session]
   [clojure.test :as t]
   [yetti.response :as yres]))

(t/deftest session-cookie-without-configured-domain
  (with-redefs [app.config/config (dissoc cf/config :auth-token-cookie-domain)]
    (let [cname    (cf/get :auth-token-cookie-name)
          response (#'session/assign-session-cookie
                    {}
                    {:token "foobar"
                     :modified-at (ct/now)})
          cookie   (get-in response [::yres/cookies cname])]
      (t/is (= "/" (:path cookie)))
      (t/is (= "foobar" (:value cookie)))
      (t/is (not (contains? cookie :domain))))))

(t/deftest session-cookie-with-configured-domain
  (with-redefs [app.config/config (assoc cf/config :auth-token-cookie-domain ".podconverge.com")]
    (let [cname    (cf/get :auth-token-cookie-name)
          response (#'session/assign-session-cookie
                    {}
                    {:token "foobar"
                     :modified-at (ct/now)})
          cookie   (get-in response [::yres/cookies cname])]
      (t/is (= "/" (:path cookie)))
      (t/is (= "foobar" (:value cookie)))
      (t/is (= ".podconverge.com" (:domain cookie))))))

(t/deftest clear-session-cookie-with-configured-domain
  (with-redefs [app.config/config (assoc cf/config :auth-token-cookie-domain ".podconverge.com")]
    (let [cname    (cf/get :auth-token-cookie-name)
          response (#'session/clear-session-cookie {})
          cookie   (get-in response [::yres/cookies cname])]
      (t/is (= {:path "/"
                :domain ".podconverge.com"
                :value ""
                :max-age 0}
               cookie)))))

(t/deftest legacy-cookie-cleanup-disabled
  (with-redefs [app.config/config (dissoc cf/config :auth-token-legacy-cookie-domain)]
    (let [response (#'session/assign-session-cookie
                    {}
                    {:token "foobar"
                     :modified-at (ct/now)})]
      (t/is (not (contains? (::yres/headers response) "set-cookie"))))))

(t/deftest legacy-cookie-cleanup-without-canonical-domain
  (with-redefs [app.config/config (-> cf/config
                                      (dissoc :auth-token-cookie-domain)
                                      (assoc :auth-token-legacy-cookie-domain "design.podconverge.com"))]
    (let [response (#'session/assign-session-cookie
                    {}
                    {:token "foobar"
                     :modified-at (ct/now)})]
      (t/is (not (contains? (::yres/headers response) "set-cookie"))))))

(t/deftest legacy-cookie-cleanup-enabled
  (with-redefs [app.config/config (-> cf/config
                                      (assoc :auth-token-cookie-domain ".podconverge.com")
                                      (assoc :auth-token-legacy-cookie-domain "design.podconverge.com"))]
    (let [cname    (cf/get :auth-token-cookie-name)
          response (#'session/assign-session-cookie
                    {::yres/headers {"set-cookie" "other-cookie=other-value"}}
                    {:token "foobar"
                     :modified-at (ct/now)})
          cookie   (get-in response [::yres/cookies cname])]
      (t/is (= ".podconverge.com" (:domain cookie)))
      (t/is (= ["other-cookie=other-value"
                "auth-token=; Path=/; Max-Age=0; Domain=design.podconverge.com"]
               (get-in response [::yres/headers "set-cookie"]))))))

(t/deftest legacy-cookie-cleanup-appends-to-collection-header
  (with-redefs [app.config/config (-> cf/config
                                      (assoc :auth-token-cookie-domain ".podconverge.com")
                                      (assoc :auth-token-legacy-cookie-domain "design.podconverge.com"))]
    (let [response (#'session/assign-session-cookie
                    {::yres/headers
                     {"set-cookie" ["first-cookie=value" "second-cookie=value"]}}
                    {:token "foobar"
                     :modified-at (ct/now)})]
      (t/is (= ["first-cookie=value"
                "second-cookie=value"
                "auth-token=; Path=/; Max-Age=0; Domain=design.podconverge.com"]
               (get-in response [::yres/headers "set-cookie"]))))))

(t/deftest clear-session-cookie-with-legacy-cookie-cleanup
  (with-redefs [app.config/config (-> cf/config
                                      (assoc :auth-token-cookie-domain ".podconverge.com")
                                      (assoc :auth-token-legacy-cookie-domain "design.podconverge.com"))]
    (let [cname    (cf/get :auth-token-cookie-name)
          response (#'session/clear-session-cookie {})
          cookie   (get-in response [::yres/cookies cname])]
      (t/is (= {:path "/"
                :domain ".podconverge.com"
                :value ""
                :max-age 0}
               cookie))
      (t/is (= ["auth-token=; Path=/; Max-Age=0; Domain=design.podconverge.com"]
               (get-in response [::yres/headers "set-cookie"]))))))
