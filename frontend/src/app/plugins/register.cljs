;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.plugins.register
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.schema :as sm]
   [app.common.types.plugins :as ctp]
   [app.common.uri :as u]
   [app.common.uuid :as uuid]
   [app.main.repo :as rp]
   [app.main.store :as st]
   [app.plugins.core :as pc]
   [app.util.object :as obj]
   [potok.v2.core :as ptk]
   [app.main.refs :as refs]
   [beicon.v2.core :as rx]))

;; Stores the installed plugins information
(defonce ^:private registry (atom {}))

(defn plugins-list
  "Retrieves the plugin data as an ordered list of plugin elements"
  []
  (->> (:ids @registry)
       (mapv #(dm/get-in @registry [:data %]))))

(defn get-plugin
  [id]
  (dm/get-in @registry [:data id]))

(defn parse-manifest
  "Read the manifest.json defined by the plugins definition and transforms it into an
  object that will be stored in the register."
  [plugin-url ^js manifest]
  (let [name (obj/get manifest "name")
        desc (obj/get manifest "description")
        code (obj/get manifest "code")
        icon (obj/get manifest "icon")
        vers (d/nilv (obj/get manifest "version") 1)

        permissions (into #{} (obj/get manifest "permissions" []))
        permissions
        (cond-> permissions
          (contains? permissions "content:write")
          (conj "content:read")

          (contains? permissions "library:write")
          (conj "library:read")

          (contains? permissions "comment:write")
          (conj "comment:read"))

        plugin-url
        (u/uri plugin-url)

        origin
        (if (= vers 1)
          (-> plugin-url
              (assoc :path "/")
              (str))
          (-> plugin-url
              (u/join ".")
              (str)))

        prev-plugin
        (->> (:data @registry)
             (vals)
             (d/seek (fn [plugin]
                       (and (= name (:name plugin))
                            (= origin (:host plugin))))))

        plugin-id
        (d/nilv (:plugin-id prev-plugin) (str (uuid/next)))

        manifest
        (d/without-nils
         {:plugin-id plugin-id
          :url (str plugin-url)
          :name name
          :description desc
          :host origin
          :code code
          :icon icon
          :permissions (into #{} (map str) permissions)})]
    (if (sm/validate ctp/schema:registry-entry manifest)
      manifest
      (.error js/console (clj->js (sm/explain ctp/schema:registry-entry manifest))))))

(defn save-to-store
  []
  ;; TODO: need this for the transition to the new schema. We can remove eventually
  (let [registry (update @registry :data d/update-vals d/without-nils)]
    (->> (rp/cmd! :update-profile-props {:props {:plugins registry}})
         (rx/subs! identity))))

(defn load-from-store
  []
  (reset! registry (get-in @st/state [:profile :props :plugins] {})))

(defn install-plugin!
  [plugin]
  (letfn [(update-ids [ids]
            (conj
             (->> ids (remove #(= % (:plugin-id plugin))))
             (:plugin-id plugin)))]
    (swap! registry #(-> %
                         (update :ids update-ids)
                         (update :data assoc (:plugin-id plugin) plugin)))
    (save-to-store)))

;; Define a predicate that returns true when the workspace is loaded.
(defn app-loaded? []
  ;; Adjust the selector as needed (for example, if the workspace element has id "workspace")
  (boolean (js/document.getElementById "left-sidebar-aside")))

;; Define a polling function that waits until workspace-loaded? returns true.
(defn wait-for-app [callback]
  (if (app-loaded?)
    (callback)
    (js/requestAnimationFrame #(wait-for-app callback))))

(def default-plugin-manifest-url
  "https://plugin.podconverge.com/manifest.json")

(def ^:private navigation-message-type "podconverge:navigate")
(def ^:private production-design-studio-origin
  "https://design.podconverge.com")
(def ^:private production-plugin-origin "https://plugin.podconverge.com")
(def ^:private production-plugin-origins
  #{production-plugin-origin "https://plugin-develop.podconverge.com"})
(def ^:private local-plugin-origin "http://localhost:4403")
(def ^:private local-design-studio-origins
  #{"http://localhost:3450" "https://localhost:3449"})
(def ^:private production-destination-origins
  #{"https://app.podconverge.com"})
(def ^:private local-destination-origins
  #{"https://app.podconverge.com"
    "https://pod-frontend-21ef7100c347.herokuapp.com"
    "https://localhost:3002"})
(def ^:private publish-path-pattern
  #"^/panel/design-hub/publish-to-stores/[^/]+/mockups$")
(defonce ^:private navigation-listener-installed? (atom false))

(defn- parse-absolute-url
  [value]
  (when (string? value)
    (try
      (js/URL. value)
      (catch :default _ nil))))

(defn- current-design-studio-environment
  []
  (let [origin (.-origin js/window.location)]
    (cond
      (= origin production-design-studio-origin) :production
      (contains? local-design-studio-origins origin) :local)))

(defn- approved-sender-origin?
  [environment origin]
  (case environment
    :production (contains? production-plugin-origins origin)
    :local (or (= origin production-plugin-origin)
               (= origin local-plugin-origin))
    false))

(defn- trusted-plugin-iframe?
  [^js event]
  (let [origin (.-origin event)
        source (.-source event)]
    (boolean
     (some (fn [^js modal]
             (let [shadow-root (.-shadowRoot modal)
                   iframe (some-> shadow-root (.querySelector "iframe"))
                   iframe-url (some-> iframe .-src parse-absolute-url)
                   modal-url (some-> (.getAttribute modal "iframe-src")
                                     parse-absolute-url)]
               (and (.-isConnected modal)
                    iframe
                    (.-isConnected iframe)
                    iframe-url
                    modal-url
                    (identical? source (.-contentWindow iframe))
                    (= origin (.-origin iframe-url))
                    (= origin (.-origin modal-url)))))
           (array-seq (.querySelectorAll js/document "plugin-modal"))))))

(defn- approved-destination-origin?
  [environment origin]
  (case environment
    :production (contains? production-destination-origins origin)
    :local (contains? local-destination-origins origin)
    false))

(defn- approved-destination?
  [environment ^js url]
  (and (approved-destination-origin? environment (.-origin url))
       (empty? (.-username url))
       (empty? (.-password url))
       (let [path (.-pathname url)
             params (.-searchParams url)]
         (or (and (= path "/panel/orders")
                  (.has params "cart"))
             (and (re-matches publish-path-pattern path)
                  (= "true" (.get params "selectStore")))
             (= path "/panel/billing/plans")))))

(defn- handle-navigation-message
  [^js event]
  (try
    (let [data (.-data event)
          environment (current-design-studio-environment)]
      (when (and (= navigation-message-type (obj/get data "type"))
                 environment
                 (approved-sender-origin? environment (.-origin event))
                 (trusted-plugin-iframe? event))
        (when-let [destination (parse-absolute-url (obj/get data "url"))]
          (when (approved-destination? environment destination)
            (.assign js/window.location (.-href destination))))))
    (catch :default _ nil)))

(defn- install-navigation-bridge!
  []
  (when (compare-and-set! navigation-listener-installed? false true)
    (.addEventListener js/window "message" handle-navigation-message)))

(defn auto-install-and-open-default-plugin []
  "Fetches the default plugin manifest, installs it, and triggers opening the plugin once the workspace is loaded.
   Waits `open-delay-ms` before calling pc/open-plugin!."
  (let [open-delay-ms 3000]
    (-> (js/fetch default-plugin-manifest-url)
        (.then (fn [response] (.json response)))
        (.then (fn [manifest]
                 (let [plugin (parse-manifest default-plugin-manifest-url manifest)]
                   (when plugin
                     (install-plugin! plugin)
                     ;; Emit event to signal plugin start (optional)
                     (st/emit! (ptk/event :app.main.data.event/event
                                          {:app.main.data.event/name "start-plugin"
                                           :name (:name plugin)
                                           :host (:host plugin)}))
                     ;; Wait until the workspace is loaded, then delay before opening
                     (wait-for-app
                      (fn []
                        (let [user-can-edit? (:can-edit (deref refs/permissions))]
                          (js/setTimeout
                           (fn []
                             (when user-can-edit?
                               (pc/open-plugin! plugin)))
                           open-delay-ms))))))))
        (.catch (fn [err]
                  (js/console.error "Failed to install default plugin:" err))))))

(defn init
  "Loads stored plugins and auto-installs & opens the default plugin."
  []
  (install-navigation-bridge!)
  (load-from-store)
  (auto-install-and-open-default-plugin))

(defn remove-plugin!
  [{:keys [plugin-id]}]
  (letfn [(update-ids [ids]
            (->> ids
                 (remove #(= % plugin-id))))]
    (swap! registry #(-> %
                         (update :ids update-ids)
                         (update :data dissoc plugin-id)))
    (save-to-store)))

(defn check-permission
  [plugin-id permission]
  (or (= plugin-id "TEST")
      (let [{:keys [permissions]} (dm/get-in @registry [:data plugin-id])]
        (contains? permissions permission))))
