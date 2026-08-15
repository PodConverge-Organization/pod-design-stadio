;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns frontend-tests.print-area-protection-test
  (:require
   [app.main.data.helpers :as dsh]
   [cljs.test :as t :include-macros true]))

(def pod-ns "shared/podconverge")

(defn- plugin-data
  [m]
  {:plugin-data {pod-ns m}})

(defn- protected-board
  [id]
  (merge {:id id}
         (plugin-data {"isBoardPrintArea" "1"})))

(defn- protected-print-area
  [id]
  (merge {:id id}
         (plugin-data {"isPrintArea" "1"})))

(defn- protected-background
  [id parent-id]
  (merge {:id id
          :parent-id parent-id}
         (plugin-data {"isPrintAreaBackground" "1"})))

(defn- with-correction-marker
  [shape session-id board-id print-area-id]
  (update-in shape
             [:plugin-data pod-ns]
             merge
             {"isCorrectionEditActive" "1"
              "correctionEditSessionId" session-id
              "correctionEditTargetBoardId" board-id
              "correctionEditTargetPrintAreaId" print-area-id}))

(t/deftest correction-edit-bypass-is-target-scoped
  (let [board-id "board-1"
        print-id "print-1"
        other-board-id "board-2"
        other-print-id "print-2"
        bg-id "background-1"
        board (protected-board board-id)
        other-board (protected-board other-board-id)
        print-area (protected-print-area print-id)
        other-print-area (protected-print-area other-print-id)
        background (protected-background bg-id print-id)
        marked-board (with-correction-marker board "request-123" board-id print-id)
        objects {board-id marked-board
                 other-board-id other-board
                 print-id print-area
                 other-print-id other-print-area
                 bg-id background}]
    (t/is (dsh/shape-is-protected-print-area? board {board-id board}))
    (t/is (dsh/shape-is-print-area? marked-board))
    (t/is (not (dsh/shape-is-protected-print-area? marked-board objects)))
    (t/is (not (dsh/shape-is-protected-print-area? print-area objects)))
    (t/is (not (dsh/shape-is-protected-print-area? background objects)))
    (t/is (dsh/shape-is-protected-print-area? other-board objects))
    (t/is (dsh/shape-is-protected-print-area? other-print-area objects))
    (t/is (= [board-id print-id bg-id]
             (dsh/remove-print-area-ids [board-id other-board-id print-id other-print-id bg-id] objects)))))

(t/deftest correction-edit-bypass-requires-active-session
  (let [board-id "board-1"
        print-id "print-1"
        inactive-board (update-in (protected-board board-id)
                                  [:plugin-data pod-ns]
                                  merge
                                  {"isCorrectionEditActive" "0"
                                   "correctionEditSessionId" "request-123"
                                   "correctionEditTargetBoardId" board-id
                                   "correctionEditTargetPrintAreaId" print-id})
        empty-session-board (with-correction-marker (protected-board board-id) "" board-id print-id)]
    (t/is (dsh/shape-is-protected-print-area? inactive-board {board-id inactive-board}))
    (t/is (dsh/shape-is-protected-print-area? empty-session-board {board-id empty-session-board}))))
