;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.data.workspace.text-defaults
  (:require
   [app.common.math :as mth]
   [app.common.types.text :as txt]
   [cuerdas.core :as str]))

(def new-text-baseline
  {:font-size "400"})

(def ^:private min-font-size 3)
(def ^:private max-font-size 1000)
(def ^:private complete-decimal-pattern
  #"^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$")

(defn- parse-complete-number
  [value]
  (cond
    (number? value)
    (when (mth/finite? value) value)

    (string? value)
    (let [trimmed (str/trim value)]
      (when (re-matches complete-decimal-pattern trimmed)
        (let [parsed (js/Number trimmed)]
          (when (mth/finite? parsed)
            parsed))))

    :else
    nil))

(defn- valid-font-size
  [value]
  (let [parsed (parse-complete-number value)]
    (when (and (some? parsed)
               (<= min-font-size parsed max-font-size))
      parsed)))

(defn- canonical-number-string
  [value]
  (str value))

(defn- normalize-font-size
  [value]
  (if-let [font-size (valid-font-size value)]
    (canonical-number-string font-size)
    (:font-size new-text-baseline)))

(defn- parse-positive-number
  [value fallback]
  (let [parsed (parse-complete-number value)]
    (if (and (some? parsed) (pos? parsed))
      parsed
      fallback)))

(defn ensure-valid-font-size
  [attrs]
  (assoc attrs :font-size (normalize-font-size (:font-size attrs))))

(defn new-text-attrs
  ([] (new-text-attrs nil))
  ([default-font]
   (-> (merge (txt/get-default-text-attrs)
              new-text-baseline
              default-font)
       (ensure-valid-font-size))))

(defn line-box-height
  [attrs]
  (let [safe-attrs  (ensure-valid-font-size attrs)
        font-size   (parse-positive-number (:font-size safe-attrs)
                                           (parse-positive-number (:font-size new-text-baseline) 1))
        line-height (parse-positive-number (:line-height safe-attrs)
                                           (parse-positive-number (:line-height txt/default-text-attrs) 1.2))]
    (mth/ceil (* font-size line-height))))

(defn new-text-line-box-height
  [default-font]
  (line-box-height (new-text-attrs default-font)))
