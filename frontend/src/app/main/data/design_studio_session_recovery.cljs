;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.data.design-studio-session-recovery
  (:require
   [app.config :as cf]
   [app.main.data.profile :as dp]
   [app.util.globals :as g]
   [app.util.storage :as storage]
   [cuerdas.core :as str]
   [potok.v2.core :as ptk]))

(def ^:private recovery-attempted-key ::attempted)

(defn protected-route?
  [route-name]
  (let [route-name' (some-> route-name name)]
    (or (= route-name :workspace)
        (= route-name :workspace-legacy)
        (and route-name' (str/starts-with? route-name' "dashboard-"))
        (and route-name' (str/starts-with? route-name' "settings-")))))

(defn viewer-route?
  [route-name]
  (or (= route-name :viewer)
      (= route-name :viewer-legacy)))

(defn recovery-attempted?
  ([] (recovery-attempted? @storage/session))
  ([session]
   (true? (get session recovery-attempted-key))))

(defn mark-recovery-attempted
  [session]
  (assoc session recovery-attempted-key true))

(defn clear-recovery-attempted
  [session]
  (dissoc session recovery-attempted-key))

(defn valid-recovery-uri?
  [uri]
  (and
   (string? uri)
   (not (str/blank? uri))
   (try
     (let [url (js/URL. uri)]
       (and (contains? #{"http:" "https:"} (.-protocol url))
            (not (str/blank? (.-host url)))
            (str/blank? (.-username url))
            (str/blank? (.-password url))))
     (catch :default _
       false))))

(defn build-recovery-url
  [recovery-uri return-to]
  (when (valid-recovery-uri? recovery-uri)
    (let [url (js/URL. recovery-uri)]
      (.set (.-searchParams url) "returnTo" return-to)
      (str url))))

(defn local-error
  [reason route-name]
  {:type :authentication
   :code :design-studio-session-recovery-unavailable
   :reason reason
   :route route-name
   :hint "Design Studio session recovery is unavailable."})

(defn protected-recovery-decision
  [{:keys [route-name recovery-uri return-to recovery-attempted?]}]
  (cond
    (not (protected-route? route-name))
    {:type :continue}

    recovery-attempted?
    {:type :fail-closed
     :error (local-error :already-attempted route-name)}

    :else
    (if-let [href (build-recovery-url recovery-uri return-to)]
      {:type :recover
       :href href}
      {:type :fail-closed
       :error (local-error :invalid-config route-name)})))

(defn startup-decision
  [{:keys [profile route-name recovery-uri return-to recovery-attempted?]}]
  (if (dp/is-authenticated? profile)
    {:type :continue
     :clear-guard? true}
    (protected-recovery-decision
     {:route-name route-name
      :recovery-uri recovery-uri
      :return-to return-to
      :recovery-attempted? recovery-attempted?})))

(defn authentication-error-decision
  [{:keys [route-name recovery-uri return-to recovery-attempted?]}]
  (cond
    (protected-route? route-name)
    (protected-recovery-decision
     {:route-name route-name
      :recovery-uri recovery-uri
      :return-to return-to
      :recovery-attempted? recovery-attempted?})

    (viewer-route? route-name)
    {:type :local-exception}

    :else
    {:type :logout}))

(defn current-startup-decision
  [profile route-name return-to]
  (startup-decision
   {:profile profile
    :route-name route-name
    :recovery-uri cf/design-studio-recovery-uri
    :return-to return-to
    :recovery-attempted? (recovery-attempted?)}))

(defn current-authentication-error-decision
  [route-name return-to]
  (authentication-error-decision
   {:route-name route-name
    :recovery-uri cf/design-studio-recovery-uri
    :return-to return-to
    :recovery-attempted? (recovery-attempted?)}))

(defn redirect-to-recovery
  [href]
  (ptk/reify ::redirect-to-recovery
    ptk/EffectEvent
    (effect [_ _ _]
      (binding [storage/*sync* true]
        (swap! storage/session mark-recovery-attempted))
      (set! (.-href g/location) href))))

(defn clear-recovery-guard
  []
  (ptk/reify ::clear-recovery-guard
    ptk/EffectEvent
    (effect [_ _ _]
      (binding [storage/*sync* true]
        (swap! storage/session clear-recovery-attempted)))))
