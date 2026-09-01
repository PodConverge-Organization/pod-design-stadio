(ns app.plugins.core
  (:require
   [app.main.store :as st]
   [app.util.globals :refer [global]]
   [potok.v2.core :as ptk]
   [promesa.core :as p]))

(defn- save-current-plugin
  [plugin-id]
  (ptk/reify ::save-current-plugin
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:workspace-local :open-plugins] (fnil conj #{}) plugin-id))))

(defn- remove-current-plugin
  [plugin-id]
  (ptk/reify ::remove-current-plugin
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:workspace-local :open-plugins] (fnil disj #{}) plugin-id))))

(defn load-plugin!
  "Calls the underlying JavaScript function to load a plugin.
   This function wraps the native call to .ɵloadPlugin."
  [{:keys [plugin-id name version description host code icon allow-background permissions]}]
  (let [load-plugin (unchecked-get global "ɵloadPlugin")]
    (if (fn? load-plugin)
      (try
        (st/emit! (save-current-plugin plugin-id))
        (-> (load-plugin
             #js {:pluginId plugin-id
                  :name name
                  :version version
                  :description description
                  :host host
                  :code code
                  :icon icon
                  :allowBackground (boolean allow-background)
                  :permissions (apply array permissions)}
             (fn []
               (st/emit! (remove-current-plugin plugin-id))))
            (p/catch
             (fn [cause]
               (st/emit! (remove-current-plugin plugin-id))
               (.error js/console "Plugin runtime failed to load plugin:" cause))))
        (catch :default cause
          (st/emit! (remove-current-plugin plugin-id))
          (.error js/console "Error in load-plugin!:" cause)
          (p/resolved nil)))
      (do
        (st/emit! (remove-current-plugin plugin-id))
        (.warn js/console "Plugin runtime not initialized yet" plugin-id)
        (p/resolved nil)))))

(defn open-plugin!
  [manifest]
  (load-plugin! manifest))
