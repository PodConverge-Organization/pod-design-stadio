;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns frontend-tests.plugins.core-test
  (:require
   [app.main.store :as st]
   [app.plugins.core :as pc]
   [app.plugins.register :as preg]
   [app.util.globals :refer [global]]
   [cljs.test :as t :include-macros true]
   [potok.v2.core :as ptk]))

(def ^:private plugin-id "plugin-1")

(def ^:private manifest
  {:plugin-id plugin-id
   :name "Default plugin"
   :version 7
   :description "Default plugin description"
   :host "https://plugin.podconverge.com"
   :code "https://plugin.podconverge.com/plugin.js"
   :icon "https://plugin.podconverge.com/icon.svg"
   :allow-background true
   :permissions #{"content:read"}})

(defn- emit-to-state!
  [state]
  (fn
    ([] nil)
    ([event]
     (swap! state #(ptk/update event %))
     nil)
    ([event & events]
     (doseq [event (cons event events)]
       (swap! state #(ptk/update event %)))
     nil)))

(defn- thenable?
  [value]
  (fn? (unchecked-get value "then")))

(defn- with-runtime-loader!
  [load-plugin f]
  (let [previous-loader (unchecked-get global "ɵloadPlugin")]
    (try
      (unchecked-set global "ɵloadPlugin" load-plugin)
      (f)
      (finally
        (unchecked-set global "ɵloadPlugin" previous-loader)))))

(t/deftest parse-manifest-preserves-background-runtime-field
  (let [plugin (preg/parse-manifest
                "https://plugin.podconverge.com/manifest.json"
                #js {:name "Default plugin"
                     :version 7
                     :description "Default plugin description"
                     :code "https://plugin.podconverge.com/plugin.js"
                     :icon "https://plugin.podconverge.com/icon.svg"
                     :allowBackground true
                     :permissions #js ["content:read"]})]
    (t/is (= 7 (:version plugin)))
    (t/is (true? (:allow-background plugin)))))

(t/deftest load-plugin-cleans-stale-state-when-runtime-is-unavailable
  (with-runtime-loader!
    nil
    (fn []
      (let [state (atom {:workspace-local {:open-plugins #{plugin-id}}})]
        (with-redefs [st/emit! (emit-to-state! state)]
          (t/is (thenable? (pc/load-plugin! manifest)))
          (t/is (empty? (get-in @state [:workspace-local :open-plugins]))))))))

(t/deftest load-plugin-cleans-current-state-on-async-rejection
  (t/async
    done
    (with-runtime-loader!
      (fn [_ _]
        (js/Promise.reject (js/Error. "runtime load failed")))
      (fn []
        (let [state          (atom {})
              original-emit! st/emit!
              restore!       #(set! st/emit! original-emit!)
              finish!        (fn [f]
                               (try
                                 (f)
                                 (finally
                                   (restore!)
                                   (done))))]
          (set! st/emit! (emit-to-state! state))
          (try
            (let [result (pc/load-plugin! manifest)]
              (t/is (thenable? result))
              (.then result
                     (fn [_]
                       (finish!
                        #(t/is (empty? (get-in @state [:workspace-local :open-plugins])))))
                     (fn [cause]
                       (finish!
                        #(t/is false (str "Unexpected plugin load rejection: " cause))))))
            (catch :default cause
              (finish!
               #(t/is false (str "Synchronous plugin load failure: " cause))))))))))

(t/deftest load-plugin-passes-runtime-manifest-fields
  (with-runtime-loader!
    (fn [payload close-plugin]
      (t/is (= plugin-id (unchecked-get payload "pluginId")))
      (t/is (= "Default plugin" (unchecked-get payload "name")))
      (t/is (= 7 (unchecked-get payload "version")))
      (t/is (= "Default plugin description" (unchecked-get payload "description")))
      (t/is (= "https://plugin.podconverge.com" (unchecked-get payload "host")))
      (t/is (= "https://plugin.podconverge.com/plugin.js" (unchecked-get payload "code")))
      (t/is (= "https://plugin.podconverge.com/icon.svg" (unchecked-get payload "icon")))
      (t/is (= true (unchecked-get payload "allowBackground")))
      (t/is (= ["content:read"] (js->clj (unchecked-get payload "permissions"))))
      (close-plugin)
      (js/Promise.resolve true))
    (fn []
      (let [state (atom {})]
        (with-redefs [st/emit! (emit-to-state! state)]
          (t/is (thenable? (pc/load-plugin! manifest)))
          (t/is (empty? (get-in @state [:workspace-local :open-plugins]))))))))
