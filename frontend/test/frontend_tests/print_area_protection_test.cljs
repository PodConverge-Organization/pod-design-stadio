;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC Sucursal en España SL

(ns frontend-tests.print-area-protection-test
  (:require
   [app.main.data.helpers :as dsh]
   [cljs.test :as t :include-macros true]))

(def ^:private plugin-ns "shared/podconverge")

(defn- plugin-data
  [data]
  {:plugin-data {plugin-ns data}})

(defn- shape
  [id data & {:keys [parent-id frame-id]}]
  (cond-> {:id id}
    parent-id (assoc :parent-id parent-id)
    frame-id (assoc :frame-id frame-id)
    true (merge (plugin-data data))))

(defn- correction-data
  [& {:keys [active? session-id board-id print-area-id]
      :or {active? true session-id "correction-session"}}]
  {"isCorrectionEditActive" (if active? "1" "0")
   "correctionEditSessionId" session-id
   "correctionEditTargetBoardId" board-id
   "correctionEditTargetPrintAreaId" print-area-id})

(defn- fixture
  [& {:keys [active? session-id]
      :or {active? true session-id "correction-session"}}]
  (let [board-id "board-1"
        print-id "print-area-1"
        background-id "print-area-background-1"
        other-board-id "board-2"
        other-print-id "print-area-2"
        board (shape board-id
                     (merge {"isBoardPrintArea" "1"}
                            (correction-data :active? active?
                                             :session-id session-id
                                             :board-id board-id
                                             :print-area-id print-id)))
        print-area (shape print-id
                          {"isPrintArea" "1"}
                          :parent-id board-id
                          :frame-id board-id)
        background (shape background-id
                          {"isPrintAreaBackground" "1"}
                          :parent-id print-id
                          :frame-id print-id)
        other-board (shape other-board-id
                           {"isBoardPrintArea" "1"})
        other-print-area (shape other-print-id
                                {"isPrintArea" "1"}
                                :parent-id other-board-id
                                :frame-id other-board-id)
        plain (shape "plain-1" {})]
    {:board board
     :print-area print-area
     :background background
     :other-board other-board
     :other-print-area other-print-area
     :plain plain
     :objects {board-id board
               print-id print-area
               background-id background
               other-board-id other-board
               other-print-id other-print-area
               "plain-1" plain}}))

(t/deftest print-area-markers-are-absolute-identity
  (let [{:keys [board print-area background other-print-area plain]} (fixture)]
    (t/is (true? (dsh/shape-is-print-area? board)))
    (t/is (true? (dsh/shape-is-print-area? print-area)))
    (t/is (true? (dsh/shape-is-print-area? background)))
    (t/is (true? (dsh/shape-is-print-area? other-print-area)))
    (t/is (false? (dsh/shape-is-print-area? plain)))))

(t/deftest active-correction-marker-bypasses-only-targeted-protection
  (let [{:keys [board print-area background other-board other-print-area objects]} (fixture)]
    (t/is (true? (dsh/shape-protection-bypassed-for-correction? board objects)))
    (t/is (true? (dsh/shape-protection-bypassed-for-correction? print-area objects)))
    (t/is (true? (dsh/shape-protection-bypassed-for-correction? background objects)))
    (t/is (false? (dsh/shape-protection-bypassed-for-correction? other-board objects)))
    (t/is (false? (dsh/shape-protection-bypassed-for-correction? other-print-area objects)))
    (t/is (false? (dsh/shape-is-protected-print-area? board objects)))
    (t/is (false? (dsh/shape-is-protected-print-area? print-area objects)))
    (t/is (false? (dsh/shape-is-protected-print-area? background objects)))
    (t/is (true? (dsh/shape-is-protected-print-area? other-board objects)))
    (t/is (true? (dsh/shape-is-protected-print-area? other-print-area objects)))))

(t/deftest inactive-or-blank-correction-marker-does-not-bypass-protection
  (let [{inactive-board :board inactive-objects :objects} (fixture :active? false)
        {blank-board :board blank-objects :objects} (fixture :session-id "")]
    (t/is (false? (dsh/shape-protection-bypassed-for-correction?
                   inactive-board
                   inactive-objects)))
    (t/is (true? (dsh/shape-is-protected-print-area?
                  inactive-board
                  inactive-objects)))
    (t/is (false? (dsh/shape-protection-bypassed-for-correction?
                   blank-board
                   blank-objects)))
    (t/is (true? (dsh/shape-is-protected-print-area?
                  blank-board
                  blank-objects)))))

(t/deftest correction-target-remains-print-area-identity
  (let [{:keys [board print-area background objects]} (fixture)]
    (t/is (true? (dsh/shape-is-print-area? board)))
    (t/is (true? (dsh/shape-is-print-area? print-area)))
    (t/is (true? (dsh/shape-is-print-area? background)))
    (t/is (false? (dsh/shape-is-protected-print-area? board objects)))
    (t/is (false? (dsh/shape-is-protected-print-area? print-area objects)))
    (t/is (false? (dsh/shape-is-protected-print-area? background objects)))))

(t/deftest remove-print-area-ids-filters-only-protected-ids
  (let [{:keys [objects]} (fixture)]
    (t/is (= ["board-1"
              "print-area-1"
              "print-area-background-1"
              "plain-1"]
             (dsh/remove-print-area-ids
              ["board-1"
               "print-area-1"
               "print-area-background-1"
               "board-2"
               "print-area-2"
               "plain-1"]
              objects)))))
