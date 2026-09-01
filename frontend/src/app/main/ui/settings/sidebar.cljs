;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.ui.settings.sidebar
  (:require-macros [app.main.style :as stl])
  (:require
   [app.config :as cf]
   [app.main.data.common :as dcm]
   [app.main.data.team :as dtm]
   [app.main.router :as rt]
   [app.main.store :as st]
   [app.main.ui.dashboard.sidebar :refer [profile-section*]]
   [app.main.ui.icons :as deprecated-icon]
   [app.util.i18n :as i18n :refer [tr]]
   [rumext.v2 :as mf]))

(def ^:private arrow-icon
  (deprecated-icon/icon-xref :arrow (stl/css :arrow-icon)))

;; FIXME: move to common
(def ^:private go-settings-integrations
  #(st/emit! (rt/nav :settings-integrations)))

(mf/defc sidebar-content*
  [{:keys [profile section]}]
  (let [integrations? (= section :settings-integrations)
        team-id       (or (dtm/get-last-team-id)
                          (:default-team-id profile))

        go-dashboard
        (mf/use-fn
         (mf/deps team-id)
         #(st/emit! (dcm/go-to-dashboard-recent :team-id team-id)))]

    [:div {:class (stl/css :sidebar-content)}
     [:div {:class (stl/css :sidebar-content-section)}
      [:button {:class (stl/css :back-to-dashboard)
                :on-click go-dashboard}
       arrow-icon
       [:span {:class (stl/css :back-text)} (tr "labels.dashboard")]]]

     [:hr {:class (stl/css :sidebar-separator)}]

     [:div {:class (stl/css :sidebar-content-section)}
      [:ul {:class (stl/css :sidebar-nav-settings)}
       (when (or (contains? cf/flags :access-tokens)
                 (contains? cf/flags :mcp))
         [:li {:class (stl/css-case :current integrations?
                                    :settings-item true)
               :on-click go-settings-integrations
               :data-testid "settings-integrations"}
          [:span {:class (stl/css :element-title)} (tr "labels.integrations")]])]]]))

(mf/defc sidebar*
  {::mf/wrap [mf/memo]}
  [{:keys [profile section]}]
  [:div {:class (stl/css :dashboard-sidebar :settings)}
   [:> sidebar-content* {:profile profile
                         :section section}]
   [:> profile-section* {:profile profile}]])
