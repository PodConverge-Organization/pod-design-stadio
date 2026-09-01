;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.ui.auth
  (:require
   [rumext.v2 :as mf]))

(mf/defc auth*
  [_]
  (mf/with-effect []
    (js/window.location.replace "https://app.podconverge.com/login"))
  nil)

(mf/defc auth-page*
  {::mf/lazy-load true}
  [props]
  [:> auth* props])
