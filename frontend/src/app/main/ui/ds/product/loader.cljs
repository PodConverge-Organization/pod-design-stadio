;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.ui.ds.product.loader
  (:require-macros
   [app.common.data.macros :as dm]
   [app.main.style :as stl])
  (:require
   [app.common.data :as d]
   [app.common.math :as mth]
   [app.util.i18n :as i18n :refer [tr]]
   [beicon.v2.core :as rx]
   [rumext.v2 :as mf]))

(defn- get-tips
  []
  [{:title (tr "loader.tips.01.title")
    :message (tr "loader.tips.01.message")}
   {:title (tr "loader.tips.02.title")
    :message (tr "loader.tips.02.message")}])

(mf/defc loader-icon*
  {::mf/private true}
  [{:keys [width height title] :rest props}]
  (let [class (stl/css :loader)
        props (mf/spread-props props
                               {:viewBox      "0 0 146 78"
                                :role         "status"
                                :width        width
                                :height       height
                                :xmlns        "http://www.w3.org/2000/svg"
                                :class        class})]
    [:> :svg props
     [:title title]
     [:g {:clip-path "url(#clip0_15276_77647)"}
      [:path {:opacity   "0.6"
              :fill-rule "evenodd"
              :clip-rule "evenodd"
              :d         "M79.159 50.6452C62.6288 54.0995 46.3761 60.9797 29.9924 74.9776C28.5935 76.1728 26.5078 76.141 25.1848 74.8624L15.377 65.3837C13.9235 63.979 13.9563 61.6336 15.4801 60.3054C34.8342 43.4365 54.5929 34.7433 74.9471 30.4899C89.9934 27.3456 112.957 27.432 128.706 27.4409C134.245 27.444 138.892 27.4431 141.989 27.4421C143.925 27.4414 145.494 29.0106 145.494 30.9469L145.494 43.5209C145.494 45.4572 143.925 47.0271 141.988 47.0199C120.977 46.9417 99.8848 46.314 79.159 50.6452Z"
              :fill      "#036E86"
              :class     "svg-elem-1"}]
      [:path {:opacity   "0.5"
              :fill-rule "evenodd"
              :clip-rule "evenodd"
              :d         "M143.344 47.0327C90.2046 47.1284 68.5203 47.2009 33.431 17.8809C31.9451 16.6393 31.7704 14.4133 33.0338 12.946L41.8942 2.65648C43.1577 1.1892 45.3731 1.02923 46.8554 2.27503C75.1108 26.0217 88.8113 27.4211 141.963 27.4415C143.9 27.4423 145.5 29.012 145.5 30.949V44.8713C145.5 46.0625 144.535 47.0306 143.344 47.0327Z"
              :fill      "#1A0885"
              :class     "svg-elem-2"}]
      [:rect {:opacity "0.4"
              :y       "27.4409"
              :width   "145.496"
              :height  "19.5861"
              :rx      "3.506"
              :fill    "#A916CE"
              :class   "svg-elem-3"}]
      [:path {:d     "M141.989 27.4409C143.88 27.4409 145.421 28.9377 145.492 30.811V43.6558C145.424 45.4533 144.004 46.9038 142.219 47.019C135.09 46.9939 127.952 46.9055 120.822 46.9644C94.5268 46.6035 76.7993 44.6519 58.3809 35.0513C63.8605 33.1483 69.3805 31.6526 74.9453 30.4897C88.0152 27.7585 107.059 27.4651 122.159 27.4409H141.989Z"
              :fill  "#6A097D"
              :class "svg-elem-4"}]]
     [:defs
      [:clipPath {:id "clip0_15276_77647"}
       [:rect {:width  "145.5"
               :height "77.132"
               :fill   "white"
               :class  "svg-elem-5"}]]]]))

(def ^:private schema:loader
  [:map
   [:class {:optional true} :string]
   [:width {:optional true} :int]
   [:height {:optional true} :int]
   [:title {:optional true} :string]
   [:overlay {:optional true} :boolean]
   [:file-loading {:optional true} :boolean]])

(mf/defc loader*
  {::mf/schema schema:loader}
  [{:keys [class width height title overlay children file-loading] :rest props}]
  (let [width  (or width (when (some? height) (mth/ceil (* height (/ 100 27)))) 100)
        height (or height (when (some? width) (mth/ceil (* width (/ 27 100)))) 27)

        class  (dm/str (d/nilv class "") " "
                       (stl/css-case :wrapper true
                                     :wrapper-overlay overlay
                                     :file-loading file-loading))

        title  (or title (tr "labels.loading"))
        tips   (mf/use-memo get-tips)

        tip*   (mf/use-state nil)
        tip    (deref tip*)]

    (mf/with-effect [file-loading tips]
      (when file-loading
        (let [sub (->> (rx/timer 1000 4000)
                       (rx/subs! #(reset! tip* (rand-nth tips))))]
          (partial rx/dispose! sub))))

    [:> :div {:class class}
     [:div {:class (stl/css :loader-content)}
      [:> loader-icon* {:title title
                        :width width
                        :height height}]
      (when (and file-loading tip)
        [:div {:class (stl/css :tips-container)}
         [:div {:class (stl/css :tip-title)}
          (:title tip)]
         [:div {:class (stl/css :tip-message)}
          (:message tip)]])]

     children]))
