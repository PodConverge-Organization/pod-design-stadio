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
