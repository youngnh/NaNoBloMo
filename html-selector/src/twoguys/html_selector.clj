(ns twoguys.html-selector
  (:use [clojure.java.io])
  (:import [nu.validator.htmlparser.dom HtmlDocumentBuilder]
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

(defn element-sel [document elt-name]
  (nodelist-seq (.getElementsByTagName document elt-name)))