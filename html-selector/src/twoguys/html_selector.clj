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

(defn selector [nodes pred]
  (let [children (apply concat
			(for [node nodes]
			  (nodelist-seq (.getChildNodes node))))]
    (lazy-cat
     (filter pred children)
     (when-not (empty? children)
       (selector children pred)))))

(defn element-tagname [elt]
  (when (= Node/ELEMENT_NODE (.getNodeType elt))
    (.getNodeName elt)))

(defmulti element-sel (fn [context elt-name]
			(condp instance? context
			  Document Document
			  :default)))

(defmethod element-sel Document [document elt-name]
  (nodelist-seq (.getElementsByTagName document elt-name)))

(defmethod element-sel :default [nodes elt-name]
  (selector nodes #(= elt-name (element-tagname %))))

(defn get-attribute [elt attr]
  (.?. elt getAttributes (getNamedItem attr) getValue))

(defn hasclass? [elt class]
  (when-let [class-attr (get-attribute elt "class")]
    (some #(= class %) (split class-attr #" "))))

(defn class-sel [nodes class]
  (selector nodes #(hasclass? % (.substring class 1))))

(defn compile-selector [s]
  (condp = (.charAt s 0)
      \# #(id-sel % s)
      \. #(class-sel % s)
      #(element-sel % s)))

(defn $ [context & selectors]
  (reduce (fn [c f-sel] (f-sel c)) context (map compile-selector selectors)))