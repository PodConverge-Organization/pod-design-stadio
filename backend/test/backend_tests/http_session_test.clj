;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns backend-tests.http-session-test
  (:require
   [app.common.time :as ct]
   [app.config :as cf]
   [app.http.session :as session]
   [clojure.test :as t]
   [yetti.response :as-alias yres]))

(defn- config-get
  [config]
  (fn
    ([key]
     (get config key))
    ([key default]
     (get config key default))))

(defn- with-session-config
  [config f]
  (with-redefs [cf/get (config-get (merge cf/config config))
                cf/flags #{}]
    (f)))

(defn- assign-session-cookie
  [response]
  (#'session/assign-session-cookie
   response
   {:token "session-token"
    :modified-at (ct/now)}))

(defn- clear-session-cookie
  [response]
  (#'session/clear-session-cookie response))

(defn- auth-cookie-name
  []
  (cf/get :auth-token-cookie-name))

(t/use-fixtures :each
  (fn [f]
    (with-session-config
      {:auth-token-cookie-domain nil
       :auth-token-legacy-cookie-domain nil}
      f)))

(t/deftest assign-session-cookie-keeps-host-only-cookie-when-domain-is-absent
  (let [response (assign-session-cookie {})
        cookie   (get-in response [::yres/cookies (auth-cookie-name)])]
    (t/is (= "/" (:path cookie)))
    (t/is (= "session-token" (:value cookie)))
    (t/is (not (contains? cookie :domain)))
    (t/is (nil? (get-in response [::yres/headers "set-cookie"])))))

(t/deftest assign-session-cookie-uses-configured-canonical-domain
  (with-session-config
    {:auth-token-cookie-domain ".podconverge.com"}
    (fn []
      (let [response (assign-session-cookie {})
            cookie   (get-in response [::yres/cookies (auth-cookie-name)])]
        (t/is (= ".podconverge.com" (:domain cookie)))
        (t/is (= "/" (:path cookie)))
        (t/is (= "session-token" (:value cookie)))
        (t/is (true? (:http-only cookie)))
        (t/is (= :lax (:same-site cookie)))
        (t/is (false? (:secure cookie)))))))

(t/deftest clear-session-cookie-uses-configured-canonical-domain
  (with-session-config
    {:auth-token-cookie-domain ".podconverge.com"}
    (fn []
      (let [response (clear-session-cookie {})
            cookie   (get-in response [::yres/cookies (auth-cookie-name)])]
        (t/is (= {:path "/"
                  :domain ".podconverge.com"
                  :value ""
                  :max-age 0}
                 cookie))))))

(t/deftest clear-session-cookie-with-both-domains-clears-canonical-and-legacy-cookies
  (with-session-config
    {:auth-token-cookie-domain ".podconverge.com"
     :auth-token-legacy-cookie-domain "design.podconverge.com"}
    (fn []
      (let [response (clear-session-cookie {})
            name     (auth-cookie-name)
            cookie   (get-in response [::yres/cookies name])]
        (t/is (= {:path "/"
                  :domain ".podconverge.com"
                  :value ""
                  :max-age 0}
                 cookie))
        (t/is (= [(str name "=; Path=/; Max-Age=0; Domain=design.podconverge.com")]
                 (get-in response [::yres/headers "set-cookie"])))))))

(t/deftest legacy-cookie-cleanup-is-not-emitted-without-canonical-domain
  (with-session-config
    {:auth-token-cookie-domain nil
     :auth-token-legacy-cookie-domain "design.podconverge.com"}
    (fn []
      (let [response (assign-session-cookie {})]
        (t/is (nil? (get-in response [::yres/headers "set-cookie"])))))))

(t/deftest legacy-cookie-cleanup-is-emitted-as-new-header-collection
  (with-session-config
    {:auth-token-cookie-domain ".podconverge.com"
     :auth-token-legacy-cookie-domain "design.podconverge.com"}
    (fn []
      (let [response (assign-session-cookie {})]
        (t/is (= [(str (auth-cookie-name) "=; Path=/; Max-Age=0; Domain=design.podconverge.com")]
                 (get-in response [::yres/headers "set-cookie"])))))))

(t/deftest legacy-cookie-cleanup-is-appended-when-both-domains-are-configured
  (with-session-config
    {:auth-token-cookie-domain ".podconverge.com"
     :auth-token-legacy-cookie-domain "design.podconverge.com"}
    (fn []
      (let [response (assign-session-cookie {::yres/headers {"set-cookie" "other=value"}})]
        (t/is (= ["other=value"
                  (str (auth-cookie-name) "=; Path=/; Max-Age=0; Domain=design.podconverge.com")]
                 (get-in response [::yres/headers "set-cookie"])))))))

(t/deftest legacy-cookie-cleanup-preserves-collection-valued-set-cookie-headers
  (with-session-config
    {:auth-token-cookie-domain ".podconverge.com"
     :auth-token-legacy-cookie-domain "design.podconverge.com"}
    (fn []
      (let [response (clear-session-cookie {::yres/headers {"set-cookie" ["one=1" "two=2"]}})]
        (t/is (= ["one=1"
                  "two=2"
                  (str (auth-cookie-name) "=; Path=/; Max-Age=0; Domain=design.podconverge.com")]
                 (get-in response [::yres/headers "set-cookie"])))))))
