;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns frontend-tests.data.workspace-text-defaults-test
  (:require
   [app.common.test-helpers.files :as cthf]
   [app.common.types.shape :as cts]
   [app.common.types.text :as txt]
   [app.common.uuid :as uuid]
   [app.main.data.workspace.drawing.common :as drawing.common]
   [app.main.data.workspace.text-defaults :as text-defaults]
   [app.main.data.workspace.texts :as dwt]
   [app.main.ui.workspace.shapes.text.v2-editor :as v2-editor]
   [app.util.text-editor :as ted]
   [cljs.test :as t :include-macros true]
   [frontend-tests.helpers.state :as ths]
   [potok.v2.core :as ptk]))

(defn- set-default-font
  [default-font]
  (ptk/reify ::set-default-font
    ptk/UpdateEvent
    (update [_ state]
      (assoc-in state [:workspace-global :default-font] default-font))))

(defn- text-shape
  ([]
   (cts/setup-shape {:id (uuid/next)
                     :type :text
                     :x 0
                     :y 0
                     :width 4
                     :height 480
                     :grow-type :auto-width}))
  ([content]
   (assoc (text-shape) :content content)))

(t/deftest new-text-attrs-use-podconverge-baseline
  (t/is (= "400" (:font-size (text-defaults/new-text-attrs))))
  (t/is (= "72" (:font-size (text-defaults/new-text-attrs {:font-size "72"}))))
  (t/is (= "400" (:font-size (text-defaults/new-text-attrs {:font-size "1001"})))))

(t/deftest shared-text-defaults-remain-compatible
  (t/is (= "14" (:font-size txt/default-text-attrs)))
  (t/is (= "14" (:font-size txt/default-typography))))

(t/deftest click-text-geometry-uses-effective-line-box
  (t/is (= {:height 480 :width 4 :grow-type :auto-width}
           (drawing.common/click-text-geometry nil)))
  (t/is (= {:height 87 :width 4 :grow-type :auto-width}
           (drawing.common/click-text-geometry {:font-size "72"})))
  (t/is (= {:height 480 :width 4 :grow-type :auto-width}
           (drawing.common/click-text-geometry {:font-size "not-a-number"})))
  (t/is (= {:height 480 :width 4 :grow-type :auto-width}
           (drawing.common/click-text-geometry {:font-size "-1"})))
  (t/is (= {:height 480 :width 4 :grow-type :auto-width}
           (drawing.common/click-text-geometry {:font-size "1001"})))
  (t/is (= {:height 480 :width 4 :grow-type :auto-width}
           (drawing.common/click-text-geometry {:line-height "bad"}))))

(t/deftest legacy-editor-initialization-uses-effective-baseline
  (t/async
    done
    (let [shape (text-shape nil)
          store (ths/setup-store (cthf/sample-file :file1 :page-label :page1))]
      (ths/run-store
       store done [(dwt/initialize-editor-state shape nil)]
       (fn [new-state]
         (let [editor (get-in new-state [:workspace-editor-state (:id shape)])
               attrs  (ted/get-editor-current-block-data editor)]
           (t/is (= "400" (:font-size attrs)))))))))

(t/deftest legacy-editor-initialization-respects-saved-font-size
  (t/async
    done
    (let [shape (text-shape nil)
          store (ths/setup-store (cthf/sample-file :file1 :page-label :page1))]
      (ths/run-store
       store done [(set-default-font {:font-size "72"})
                   (dwt/initialize-editor-state shape nil)]
       (fn [new-state]
         (let [editor (get-in new-state [:workspace-editor-state (:id shape)])
               attrs  (ted/get-editor-current-block-data editor)]
           (t/is (= "72" (:font-size attrs)))))))))

(t/deftest legacy-editor-initialization-keeps-existing-explicit-font-size
  (t/async
    done
    (let [shape (-> (text-shape)
                    (update :content txt/change-text "Existing")
                    (assoc-in [:content :children 0 :children 0 :font-size] "32"))
          store (ths/setup-store (cthf/sample-file :file1 :page-label :page1))]
      (ths/run-store
       store done [(dwt/initialize-editor-state shape nil)]
       (fn [new-state]
         (let [editor  (get-in new-state [:workspace-editor-state (:id shape)])
               content (ted/export-content (ted/get-editor-current-content editor))
               attrs   (txt/get-first-paragraph-text-attrs content)]
           (t/is (= "32" (:font-size attrs)))))))))

(t/deftest v2-editor-style-defaults-use-effective-baseline
  (let [defaults         (js->clj (v2-editor/new-text-style-defaults nil "#000000"))
        saved-defaults   (js->clj (v2-editor/new-text-style-defaults {:font-size "72"} "#000000"))
        invalid-defaults (js->clj (v2-editor/new-text-style-defaults {:font-size "nope"} "#000000"))]
    (t/is (= "400px" (get defaults "font-size")))
    (t/is (= "72px" (get saved-defaults "font-size")))
    (t/is (= "400px" (get invalid-defaults "font-size")))))
