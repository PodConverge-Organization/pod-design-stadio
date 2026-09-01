;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns app.main.data.helpers
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.files.helpers :as cfh]
   [app.common.geom.matrix :as gmt]
   [app.common.geom.point :as gpt]
   [app.common.geom.shapes :as gsh]
   [app.common.types.path :as path]
   [clojure.string :as str]))

(defn lookup-profile
  ([state]
   (:profile state))
  ([state profile-id]
   (dm/get-in state [:profiles profile-id])))

(defn lookup-libraries
  "Retrieve all libraries, including the local file."
  [state]
  (:files state))

(defn lookup-file
  ([state]
   (lookup-file state (:current-file-id state)))
  ([state file-id]
   (dm/get-in state [:files file-id])))

(defn lookup-file-data
  ([state]
   (lookup-file-data state (:current-file-id state)))
  ([state file-id]
   (dm/get-in state [:files file-id :data])))

(defn get-page
  [fdata page-id]
  (dm/get-in fdata [:pages-index page-id]))

(defn lookup-page
  ([state]
   (let [file-id (:current-file-id state)
         page-id (:current-page-id state)]
     (lookup-page state file-id page-id)))
  ([state page-id]
   (let [file-id (:current-file-id state)]
     (lookup-page state file-id page-id)))
  ([state file-id page-id]
   (dm/get-in state [:files file-id :data :pages-index page-id])))

(defn lookup-page-objects
  ([state]
   (lookup-page-objects state
                        (:current-file-id state)
                        (:current-page-id state)))
  ([state page-id]
   (lookup-page-objects state
                        (:current-file-id state)
                        page-id))
  ([state file-id page-id]
   (-> (lookup-page state file-id page-id)
       (get :objects))))

(defn process-selected
  ([objects selected]
   (process-selected objects selected nil))

  ([objects selected {:keys [omit-blocked?] :or {omit-blocked? false}}]
   (let [selectable?
         (fn [id]
           (and (contains? objects id)
                (or (not omit-blocked?)
                    (not (dm/get-in objects [id :blocked] false)))))

         selected
         (cfh/clean-loops objects selected)]

     (into (d/ordered-set)
           (filter selectable?)
           selected))))

(defn split-text-shapes
  "Split text shapes from non-text shapes"
  [objects ids]
  (loop [ids (seq ids)
         text-ids []
         shape-ids []]
    (if-let [id (first ids)]
      (let [shape (get objects id)]
        (if (cfh/text-shape? shape)
          (recur (rest ids)
                 (conj text-ids id)
                 shape-ids)
          (recur (rest ids)
                 text-ids
                 (conj shape-ids id))))
      [text-ids shape-ids])))

;; DEPRECATED
(defn lookup-selected-raw
  [state]
  (dm/get-in state [:workspace-local :selected]))

(defn get-selected-ids
  [state]
  (dm/get-in state [:workspace-local :selected]))

(defn lookup-selected
  ([state]
   (lookup-selected state (:current-page-id state) nil))
  ([state options]
   (lookup-selected state (:current-page-id state) options))
  ([state page-id options]
   (let [objects  (lookup-page-objects state page-id)
         selected (dm/get-in state [:workspace-local :selected])]
     (process-selected objects selected options))))

(defn lookup-shape
  ([state id]
   (lookup-shape state (:current-page-id state) id))

  ([state page-id id]
   (let [objects (lookup-page-objects state page-id)]
     (get objects id))))

(defn lookup-shapes
  ([state ids]
   (lookup-shapes state (:current-page-id state) ids))
  ([state page-id ids]
   (let [objects (lookup-page-objects state page-id)]
     (into [] (keep (d/getf objects)) ids))))

(defn update-file
  ([state f]
   (update-file state (:current-file-id state) f))
  ([state file-id f]
   (d/update-in-when state [:files file-id] f)))

(defn update-page
  ([state f]
   (update-page state
                (:current-file-id state)
                (:current-page-id state)
                f))
  ([state page-id f]
   (update-page state
                (:current-file-id state)
                page-id
                f))
  ([state file-id page-id f]
   (d/update-in-when state [:files file-id :data :pages-index page-id] f)))

(defn filter-shapes
  ([state filter-fn]
   (filter-shapes state (:current-page-id state) filter-fn))
  ([state page-id filter-fn]
   (let [objects (lookup-page-objects state page-id)]
     (into [] (filter filter-fn) (vals objects)))))

(defn select-bool-children
  [state parent-id]
  (let [objects (lookup-page-objects state)

        shape-modifiers
        (:workspace-modifiers state)

        content-modifiers
        (dm/get-in state [:workspace-local :edit-path])]

    (reduce (fn [result id]
              (if-let [shape (get objects id)]
                (let [modifiers (dm/get-in shape-modifiers [id :modifiers])
                      shape     (if (some? modifiers)
                                  (gsh/transform-shape shape modifiers)
                                  shape)
                      modifiers (dm/get-in content-modifiers [id :content-modifiers])
                      shape     (if (some? modifiers)
                                  (update shape :content path/apply-content-modifiers modifiers)
                                  shape)]
                  (assoc result id shape))
                result))
            {}
            (cfh/get-children-ids objects parent-id))))

(defn get-viewport-center
  [state]
  (when-let [{:keys [x y width height]} (get-in state [:workspace-local :vbox])]
    (gpt/point (+ x (/ width 2)) (+ y (/ height 2)))))

(defn lookup-team-files
  ([state]
   (lookup-team-files state (:current-team-id state)))
  ([state team-id]
   (->> state
        :files
        (filter #(= team-id (:team-id (val %))))
        (into {}))))

(defn lookup-team-projects
  ([state]
   (lookup-team-projects (:current-team-id state)))
  ([state team-id]
   (->> state
        :projects
        (filter #(= team-id (:team-id (val %))))
        (into {}))))

(defn- ->clj-if-js
  "Convert JS object to CLJ map only when necessary. Keep string keys (no keywordize)."
  [x]
  (cond
    (map? x) x
    (nil? x) nil
    :else
    (try
      (js->clj x :keywordize-keys false)
      (catch :default _
        x))))

(defn- get-from-js-or-map
  "Fetch key `kstr` from `m` that can be either a CLJ map (with string or keyword keys)
   or a JS object. Returns nil if not found. Tries multiple variants:
   string, keyword, underscore/kebab variants."
  [m kstr]
  (when (some? m)
    (let [alt1 (str/replace kstr "_" "-")
          alt2 (str/replace kstr "-" "_")
          try-keys [kstr (keyword kstr) alt1 alt2 (keyword alt1) (keyword alt2)]]
      (cond
        (map? m)
        (some (fn [k] (when (contains? m k) (get m k))) try-keys)

        :else
        ;; JS object: try aget with string keys (js objects don't respond to contains?)
        (some (fn [k]
                (let [ks (if (keyword? k) (name k) (str k))
                      v  (try (aget m ks) (catch :default _ nil))]
                  (when (some? v) v)))
              try-keys)))))

(defn- lookup-plugin-namespace
  "Given plugin-data `pd` (map or JS object) and namespace `ns-str`, return the ns map/object."
  [pd ns-str]
  (when (some? pd)
    (let [pd-clj (->clj-if-js pd)
          ns-val (when (map? pd-clj) (get-from-js-or-map pd-clj ns-str))]
      (if (some? ns-val)
        (->clj-if-js ns-val)
        ;; fallback: try reading directly from original pd (JS object case)
        (let [v (get-from-js-or-map pd ns-str)]
          (when (some? v) (->clj-if-js v)))))))

(defn get-shape-plugin-data
  "Return the plugin-data value for `key` on `shape`."
  ([shape key] (get-shape-plugin-data shape nil key))
  ([shape ns key]
   (let [kstr (if (keyword? key) (name key) (str key))

         ;; 1) Try direct JS path first (works when shape and plugin-data are native JS objects)
         pd-js    (aget shape "plugin-data")
         ns-js    (when (and ns pd-js) (aget pd-js ns))
         val-js   (cond
                    (and ns-js (some? ns-js)) (or (aget ns-js kstr) (aget ns-js (name (keyword kstr))))
                    (some? pd-js)            (or (aget pd-js kstr) (aget pd-js (name (keyword kstr))))
                    :else                   nil)]

     (if (some? val-js)
       val-js
       ;; 2) Fallback: robust CLJ/js->clj handling
       (let [pd-raw (or (get shape :plugin-data)
                        (get shape "plugin-data"))
             pd     (->clj-if-js pd-raw)]
         (if ns
           (let [nsmap (lookup-plugin-namespace pd ns)]
             (when (some? nsmap)
               (or (get-from-js-or-map nsmap kstr)
                   (get-from-js-or-map nsmap (str/replace kstr "_" "-"))
                   (get-from-js-or-map nsmap (str/replace kstr "-" "_")))))
           (get-from-js-or-map pd kstr)))))))

(defn- truthy-plugin-value?
  "Return true for common truthy plugin-data values."
  [v]
  (when (some? v)
    (let [s (-> (str v) str/trim str/lower-case)]
      (or (= s "1")
          (= s "true")
          (= s "yes")
          (= s "on")
          (= s "t")
          (= v 1)))))

(defn shape-is-print-area?
  "Return true if shape plugin-data marks it as a print area."
  [shape]
  (let [val (or (get-shape-plugin-data shape "shared/podconverge" "isPrintArea")
                (get-shape-plugin-data shape "shared/podconverge" "isPrintAreaBackground")
                (get-shape-plugin-data shape "shared/podconverge" "isBoardPrintArea"))]
    (boolean (truthy-plugin-value? val))))

(def ^:private podconverge-plugin-ns
  "shared/podconverge")

(def ^:private correction-edit-active-key
  "isCorrectionEditActive")

(def ^:private correction-edit-session-id-key
  "correctionEditSessionId")

(def ^:private correction-edit-target-board-id-key
  "correctionEditTargetBoardId")

(def ^:private correction-edit-target-print-area-id-key
  "correctionEditTargetPrintAreaId")

(defn- plugin-id=?
  [id plugin-id]
  (and (some? id)
       (some? plugin-id)
       (= (str id) (str plugin-id))))

(defn- shape-is-print-area-background?
  [shape]
  (truthy-plugin-value?
   (get-shape-plugin-data shape podconverge-plugin-ns "isPrintAreaBackground")))

(defn- shape-is-board-print-area?
  [shape]
  (truthy-plugin-value?
   (get-shape-plugin-data shape podconverge-plugin-ns "isBoardPrintArea")))

(defn- correction-edit-marker
  [shape]
  (when (truthy-plugin-value?
         (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-active-key))
    (let [session-id (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-session-id-key)
          board-id   (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-target-board-id-key)
          print-id   (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-target-print-area-id-key)]
      (when (and (some? session-id)
                 (not (str/blank? (str session-id)))
                 (some? board-id)
                 (some? print-id))
        {:session-id session-id
         :board-id board-id
         :print-area-id print-id}))))

(defn- shape-matches-correction-marker?
  [shape marker]
  (let [id (:id shape)
        parent-id (:parent-id shape)
        frame-id (:frame-id shape)
        board-id (:board-id marker)
        print-id (:print-area-id marker)]
    (or (plugin-id=? id board-id)
        (plugin-id=? id print-id)
        (and (shape-is-print-area-background? shape)
             (or (plugin-id=? parent-id print-id)
                 (plugin-id=? frame-id print-id)
                 (plugin-id=? parent-id board-id)
                 (plugin-id=? frame-id board-id))))))

(defn- active-correction-edit-marker-for-shape
  [shape objects]
  (or (when-let [marker (correction-edit-marker shape)]
        (when (shape-matches-correction-marker? shape marker)
          marker))
      (some (fn [[_ candidate]]
              (when-let [marker (correction-edit-marker candidate)]
                (when (shape-matches-correction-marker? shape marker)
                  marker)))
            objects)))

(defn shape-is-correction-edit-target?
  "Return true when shape is the target board, print area, or background for
  an active correction edit marker.

  The marker is intentionally explicit and target scoped:
  shared/podconverge.isCorrectionEditActive = \"1\"
  shared/podconverge.correctionEditSessionId = non-empty request/session id
  shared/podconverge.correctionEditTargetBoardId = target board id
  shared/podconverge.correctionEditTargetPrintAreaId = target print-area id"
  [shape objects]
  (boolean (and shape
                (active-correction-edit-marker-for-shape shape objects))))

(defn shape-protection-bypassed-for-correction?
  "Return true only for the currently marked correction edit target."
  [shape objects]
  (shape-is-correction-edit-target? shape objects))

(defn shape-is-protected-print-area?
  "Return true when shape is print-area protected after applying the scoped
  correction edit bypass."
  [shape objects]
  (and (shape-is-print-area? shape)
       (not (shape-protection-bypassed-for-correction? shape objects))))

(defn print-area-protection-diagnostics
  "Return focused diagnostics for print-area protection decisions."
  [shape objects guard-site]
  (let [session-id (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-session-id-key)]
    {:guard-site guard-site
     :shape-id (:id shape)
     :is-board-print-area (get-shape-plugin-data shape podconverge-plugin-ns "isBoardPrintArea")
     :is-print-area (get-shape-plugin-data shape podconverge-plugin-ns "isPrintArea")
     :is-print-area-background (get-shape-plugin-data shape podconverge-plugin-ns "isPrintAreaBackground")
     :is-correction-edit-active (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-active-key)
     :correction-edit-session-id-present? (and (some? session-id)
                                               (not (str/blank? (str session-id))))
     :correction-edit-target-board-id (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-target-board-id-key)
     :correction-edit-target-print-area-id (get-shape-plugin-data shape podconverge-plugin-ns correction-edit-target-print-area-id-key)
     :bypass? (shape-protection-bypassed-for-correction? shape objects)
     :protected? (shape-is-protected-print-area? shape objects)}))

(defn log-print-area-protection-blocked!
  "Log focused diagnostics when a board-level print-area guard blocks mutation."
  [shape objects guard-site]
  (when (shape-is-board-print-area? shape)
    (js/console.debug "print-area board transform blocked"
                      (clj->js (print-area-protection-diagnostics shape objects guard-site)))))

(defn remove-print-area-ids
  "Filter out protected print-area ids from `ids` using `objects` map."
  [ids objects]
  (->> ids
       (remove (fn [id]
                 (let [shape (get objects id)]
                   (and shape (shape-is-protected-print-area? shape objects)))))
       (into [])))

(defn get-selrect
  [selrect-transform shape]
  (if (some? selrect-transform)
    (let [{:keys [center width height transform]} selrect-transform]
      [(gsh/center->rect center width height)
       (gmt/transform-in center transform)])
    [(dm/get-prop shape :selrect)
     (gsh/transform-matrix shape)]))
