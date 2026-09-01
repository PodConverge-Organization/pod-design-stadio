;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.data.design-studio-session-recovery
  (:require
   [app.util.globals :as g]
   [app.util.storage :as storage]
   [cuerdas.core :as str]
   [potok.v2.core :as ptk]))

(def ^:private recovery-attempt-key ::attempted)

(defn protected-route-name?
  [route-name]
  (let [route-name-str (some-> route-name name)]
    (or (= :workspace route-name)
        (= :workspace-legacy route-name)
        (and (string? route-name-str)
             (or (str/starts-with? route-name-str "dashboard-")
                 (str/starts-with? route-name-str "settings-"))))))

(defn viewer-route-name?
  [route-name]
  (or (= :viewer route-name)
      (= :viewer-legacy route-name)))

(defn recovery-attempted?
  []
  (true? (get @storage/session recovery-attempt-key)))

(defn mark-recovery-attempt!
  []
  (binding [storage/*sync* true]
    (swap! storage/session assoc recovery-attempt-key true)))

(defn clear-recovery-attempt!
  []
  (binding [storage/*sync* true]
    (swap! storage/session dissoc recovery-attempt-key)))

(defn- parsed-recovery-url
  [uri]
  (when (string? uri)
    (let [uri (str/trim uri)]
      (when-not (str/empty? uri)
        (try
          (let [url (js/URL. uri)]
            (when (and (#{"http:" "https:"} (.-protocol url))
                       (not (str/empty? (.-host url)))
                       (str/empty? (.-username url))
                       (str/empty? (.-password url)))
              url))
          (catch :default _
            nil))))))

(defn valid-recovery-uri?
  [uri]
  (some? (parsed-recovery-url uri)))

(defn recovery-url
  [uri return-to]
  (when-let [url (parsed-recovery-url uri)]
    (.set (.-searchParams url) "returnTo" return-to)
    (str url)))

(defn- bounded-recovery-decision
  [recovery-uri]
  (if (and (valid-recovery-uri? recovery-uri)
           (not (recovery-attempted?)))
    {:action :recover}
    {:action :fail-closed}))

(defn startup-decision
  [authenticated? route-name recovery-uri]
  (cond
    authenticated?
    {:action :continue
     :clear-guard? true}

    (protected-route-name? route-name)
    (bounded-recovery-decision recovery-uri)

    :else
    {:action :continue}))

(defn canonical-authentication-required?
  [{:keys [type code]}]
  (and (= :authentication type)
       (= :authentication-required code)))

(defn runtime-authentication-decision
  [route-name error recovery-uri]
  (cond
    (viewer-route-name? route-name)
    {:action :local-exception}

    (protected-route-name? route-name)
    (if (canonical-authentication-required? error)
      (bounded-recovery-decision recovery-uri)
      {:action :local-exception})

    :else
    {:action :logout}))

(def fail-closed-error
  {:type :authentication
   :code :design-studio-session-recovery-failed
   :hint "Design Studio session recovery is unavailable or already attempted"})

(defn redirect-to-recovery
  [recovery-uri return-to]
  (ptk/reify ::redirect-to-recovery
    ptk/EffectEvent
    (effect [_ _ _]
      (when-let [href (recovery-url recovery-uri return-to)]
        (mark-recovery-attempt!)
        (set! (.-href g/location) href)))))

(defn clear-recovery-attempt
  []
  (ptk/reify ::clear-recovery-attempt
    ptk/EffectEvent
    (effect [_ _ _]
      (clear-recovery-attempt!))))
