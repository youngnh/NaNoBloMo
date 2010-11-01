(ns twoguys.html-selector
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
    [(.getElementById document id)]))

(defn nodelist-seq [node-list]
  (letfn [(internal [i]
	    (lazy-seq
	     (when (< i (.getLength node-list))
	       (cons (.item node-list i) (internal (inc i))))))]
    (internal 0)))

(defmulti element-sel (fn [context elt-name]
			(condp instance? context
			  Document Document
			  :default)))

(defmethod element-sel Document [document elt-name]
  (nodelist-seq (.getElementsByTagName document elt-name)))

(defmethod element-sel :default [nodes elt-name]
  (let [children (apply concat
			(for [node nodes]
			  (nodelist-seq (.getChildNodes node))))]
    (lazy-cat
     (filter #(= elt-name (element-tagname %)) children)
     (when-not (empty? children)
       (element-sel children elt-name)))))

(defn element-tagname [elt]
  (when (= Node/ELEMENT_NODE (.getNodeType elt))
    (.getNodeName elt)))

(defn get-attribute [elt attr]
  (.?. elt getAttributes (getNamedItem attr) getValue))

(defn hasclass? [elt class]
  (when-let [class-attr (get-attribute elt "class")]
    (some #(= class %) (split class-attr #" "))))

(defn class-sel [nodes class]
  (let [class-name (.substring class 1)
	children (apply concat
			(for [node nodes]
			  (nodelist-seq (.getChildNodes node))))]
    (lazy-cat
     (filter #(hasclass? % class-name) children)
     (when-not (empty? children)
       (class-sel children class)))))