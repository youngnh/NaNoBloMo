(ns twoguys.html-selector
  (:require [clojure.zip :as zip])
  (:use [clojure.string :only (split)]
	[clojure.contrib.core :only (.?.)]
	[clojure.java.io :only (reader)])
  (:import [nu.validator.htmlparser.dom HtmlDocumentBuilder]
	   [org.w3c.dom Document Node]
	   [org.xml.sax InputSource]))

(defn build-document [file-name]
  (.parse (HtmlDocumentBuilder.) (InputSource. (reader file-name))))

(defn id-sel [document id]
  (let [id (.substring id 1)]
    (list (.getElementById document id))))

(defn nodelist-seq [node-list]
  (letfn [(internal [i]
	    (lazy-seq
	     (when (< i (.getLength node-list))
	       (cons (.item node-list i) (internal (inc i))))))]
    (internal 0)))

(defn dom-seq [root-node]
  (let [children (nodelist-seq (.getChildNodes root-node))]
    (lazy-cat
     children
     (when-not (empty? children)
       (mapcat dom-seq children)))))

(defn element-tagname [elt]
  (when (= Node/ELEMENT_NODE (.getNodeType elt))
    (.getNodeName elt)))

(defmulti element-sel (fn [node elt-name]
			(condp instance? node
			  Document Document
			  Node)))

(defmethod element-sel Document [document elt-name]
  (nodelist-seq (.getElementsByTagName document elt-name)))

(defmethod element-sel Node [node elt-name]
  (filter #(= elt-name (element-tagname %)) (dom-seq node)))

(defn get-attribute [elt attr]
  (.?. elt getAttributes (getNamedItem attr) getValue))

(defn hasclass? [elt class]
  (when-let [class-attr (get-attribute elt "class")]
    (some #(= class %) (split class-attr #" "))))

(defn class-sel [node class]
  (filter #(hasclass? % (.substring class 1)) (dom-seq node)))

(defmulti compile-selector type)

(defmethod compile-selector clojure.lang.IFn [f]
  f)

(defmethod compile-selector String [s]
  (condp = (.charAt s 0)
      \# #(id-sel % s)
      \. #(class-sel % s)
      #(element-sel % s)))

(defn text-sel [node]
  (list (.getTextContent node)))

(defn flip [f]
  (fn [& args]
    (apply f (reverse args))))

(defn $ [node & selectors]
  (reduce (flip mapcat) [node] (map compile-selector selectors)))