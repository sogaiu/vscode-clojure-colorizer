(ns clojure-colorizer.core
  (:require
    [goog.object :as go]
    [promesa.core :as pc]
    ["path" :as path]
    ["vscode" :as vscode]
    ["web-tree-sitter" :as Parser]))

;; thanks to vscode-tree-sitter, much was adapted from there

(def language
  "clojure")

(def delim-colors
  ["red" 
   "orange" 
   "yellow"
   "aquamarine"
   "dodgerblue"
   "hotpink"
   "darkgray"])

(def syn-color-map
  {:call "#DCDCAA"
   :comment "#6A9955"
   :definition "#C586C0"
   :string "#CE9178"})

(def clj-parser
  (atom nil))

(def clj-lang
  (atom nil))

(def decos
  (atom nil))

(def trees
  (atom {}))

(defn node-seq
  [node]
  (tree-seq
    #(< 0 (.-childCount ^js %)) ; branch?
    #(.-children ^js %) ; children
    node)) ; root

(defn make-range
  [^js node]
  (new vscode/Range
    (go/get (.-startPosition node) "row")
    (go/get (.-startPosition node) "column")
    (go/get (.-endPosition node) "row")
    (go/get (.-endPosition node) "column")))

;; XXX: not used currently
(defn study-syntax
  [nodes]
  (let [ranges
        (mapv (fn [_] (vector))
          syn-color-map)]
    ;; XXX
    #_(.log js/console "start")
    (->> nodes
      (reduce
        (fn study-pairs-inner [acc ^js node]
          (let [node-type (.-type node)]
            (cond
              ;; XXX: consider query api?
              (= node-type "list")
              (let [child-count (.-childCount ^js node)]
                (if (< 3 child-count)
                  (if-let [first-elt (.child node 1)]
                    (if (and (= (.-type first-elt) "symbol")
                          (#{"declare" "def" "definterface" "defmacro"
                             "defmethod" "defmulti" "defn" "defn-"
                             "defonce" "defprotocol" "defrecord" "deftype"
                             "ns"}
                            (.-text first-elt)))
                      (loop [i 2]
                        (if (<= i (dec child-count))
                          (let [elt (.child node i)]
                            (if (= (.-type elt) "symbol")
                              (update-in 
                                (update-in acc
                                  [:ranges 4]
                                  conj #js {"range" (make-range elt)})
                                [:ranges 5]
                                conj #js {"range" (make-range first-elt)})
                              (recur (inc i))))
                          acc))
                      (update-in acc
                        [:ranges 0]
                        conj #js {"range" (make-range first-elt)}))
                    acc)
                  acc))  ; XXX: meh, acc rep
              ;; XXX: want to do this for lists too
              (= node-type "anonymous_function")
              (if (< 2 (.-childCount ^js node))
                ;; XXX: check this is not a left paren
                (if-let [first-child (.child node 1)]
                  (if (= (.-type first-child) "symbol")
                    (update-in acc
                      [:ranges 0]
                      conj #js {"range" (make-range first-child)})
                    acc)
                  acc)
                acc) ; XXX: meh, acc rep
              ;;
              (= node-type "keyword")
              (update-in acc
                [:ranges 3]
                conj #js {"range" (make-range node)})
              ;;
              (= node-type "string")
              (update-in acc
                [:ranges 1]
                conj #js {"range" (make-range node)})
              ;;
              (= node-type "comment")
              (update-in acc
                [:ranges 6]
                conj #js {"range" (make-range node)})
              ;;
              (= node-type "metadata")
              (update-in acc
                [:ranges 2]
                conj #js {"range" (make-range node)})                  
              ;;
              :else
              acc))))
        {:ranges ranges ; e.g. [[] [] [] [] [] [] []]
        })
      ;; only want the ranges
      :ranges
      clj->js))

(defn study-pairs
  [nodes]
  (let [ranges
        (mapv (fn [_] (vector))
          delim-colors)
        n-colors (count delim-colors)]
    (->> nodes
      (reduce
        (fn study-pairs-inner [acc ^js node]
          (let [node-type (.-type node)]
            (cond
              ;; skip first node, can be "program" or "ERROR"
              (:initial acc)
              (assoc acc :initial false)
              ;;
              (.hasError node)
              (reduced
                (update-in acc
                  [:ranges 0]
                  conj #js {"range" (make-range node)}))
              ;;
              :else
              (cond
                (= node-type "[")
                (let [vec-depth (:vec-depth acc)]
                  (assoc
                    (update-in acc
                      [:ranges (mod vec-depth n-colors)]
                      conj #js {"range" (make-range node)})
                    :vec-depth (inc vec-depth)))
                ;;
                (= node-type "]")
                (let [new-depth (dec (:vec-depth acc))]
                  (assoc
                    (update-in acc
                      [:ranges (mod new-depth n-colors)]
                      conj #js {"range" (make-range node)})
                    :vec-depth new-depth))
                ;;
                (or (= node-type "(")
                    (= node-type "#("))
                (let [paren-depth (:paren-depth acc)]
                  (assoc
                    (update-in acc
                      [:ranges (mod paren-depth n-colors)]
                      conj #js {"range" (make-range node)})
                    :paren-depth (inc paren-depth)))
                ;;
                (= node-type ")")
                (let [new-depth (dec (:paren-depth acc))]
                  (assoc
                    (update-in acc
                      [:ranges (mod new-depth n-colors)]
                      conj #js {"range" (make-range node)})
                    :paren-depth new-depth))
                ;;
                :else
                acc))))
        {:initial true
         :ranges ranges ; e.g. [[] [] [] [] [] [] []]
         :paren-depth 0
         :vec-depth 0})
      ;; XXX: warn if either depth is non-zero at the end?
      ;; only want the ranges
      :ranges
      clj->js)))

(defn color-editor
  [^js editor]
  (let [doc (.-document editor)
        uri (.-uri doc)]
    (when (= language (.-languageId doc))
      (when-let [^js tree (get @trees uri)]
        (let [ranges (study-pairs (node-seq (.-rootNode tree)))]
          (doseq [idx (range (count delim-colors))]
            (.setDecorations editor
              (nth @decos idx)
              (nth ranges idx))))))))

(defn color-uri
  [^js uri]
  (doseq [^js editor (.-visibleTextEditors (.-window vscode))]
    (when (= uri (.-uri (.-document editor)))
		  (color-editor editor))))

(defn open-editor
  [^js editor]
  (when-let [parser @clj-parser]
    (pc/let [^js doc (.-document editor)
             ^js tree (.parse parser (.getText doc))
             ^js uri (.-uri doc)]
      (swap! trees assoc
        uri tree)
      (color-uri uri))))

(defn make-decorations
  []
  (map (fn [color]
         (.createTextEditorDecorationType (.-window vscode)
           #js {"color" color}))
    delim-colors))

(defn update-tree
  [^js parser ^js change-event]
  (when (and parser
          (< 0 (.-length (.-contentChanges change-event))))
    (let [doc (.-document change-event)
          uri (.-uri doc)
          old-tree (get @trees uri)]
      (doseq [^js edit (.-contentChanges change-event)]
        (let [start-index (.-rangeOffset edit)
              old-end-index (+ start-index (.-rangeLength edit))
              new-end-index (+ start-index (.-length (.-text edit)))
              start-pos (.positionAt doc start-index)
              old-end-pos (.positionAt doc old-end-index)
              new-end-pos (.positionAt doc new-end-index)]
            (.edit old-tree
              #js {"startIndex" start-index
                   "oldEndIndex" old-end-index
                   "newEndIndex" new-end-index
                   "startPosition" #js {"row" (.-line start-pos)
                                        "column" (.-character start-pos)}
                   "oldEndPosition" #js {"row" (.-line old-end-pos)
                                         "column" (.-character old-end-pos)}
                   "newEndPosition" #js {"row" (.-line new-end-pos)
                                         "column" (.-character new-end-pos)}})))
      (let [new-tree
            (.parse parser
              (.getText doc) old-tree)]
        (swap! trees
          assoc uri new-tree)))))

(defn edit-doc
  [^js change-event]
  (when-let [parser @clj-parser]
    (when (= language (.-languageId (.-document change-event)))
      (update-tree parser change-event)
      (color-uri (.-uri (.-document change-event))))))

(defn color-all-open
  []
  (doseq [^js editor (.-visibleTextEditors (.-window vscode))]
    ;; XXX
    (.log js/console (.-languageId (.-document editor)))
    (pc/let [_ (open-editor editor)])))

(defn close-doc
  [^js doc]
  (swap! trees
    dissoc (.-uri doc)))

(defn activate
  [^js ctx]
  (.log js/console "activating clojure colorizer")
  (pc/let [_ (.init Parser)
           wasm-path
           (.join path
             (.-extensionPath ctx) "parsers" "tree-sitter-clojure.wasm")
           lang (.load (.-Language Parser)
                  wasm-path)
           ^js parser (new Parser)
            _ (.setLanguage parser lang)
           decorations (make-decorations)]
    (reset! clj-lang lang)
    (reset! clj-parser parser)
    (reset! decos decorations)
    ;; hooking things up and preparing for cleanup
    (let [ss (.-subscriptions ctx)
          ws (.-workspace vscode)]
      ;; for our pretty colors
      (doseq [deco decorations]
        (.push ss deco))
      (.push ss (.onDidChangeTextDocument ws edit-doc))
      (.push ss (.onDidCloseTextDocument ws close-doc))
      ;; XXX: update outline here too?
      (.push ss
        (.onDidChangeVisibleTextEditors (.-window vscode) color-all-open)))
    ;; start by coloring all open clojure things
    (pc/let [_ (color-all-open)])))

(defn deactivate
  []
  (.log js/console "deactivating clojure colorizer"))
