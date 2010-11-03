# CSS Selectors, Scraping and Clojure

## Building a DOM

Parsing HTML can be tricky, most of my naive attempts to parse real-world pages produced a lot of stack traces.  The [Validator.nu HTML parser](http://about.validator.nu/htmlparser/) has so far cleared those low hurdles.  It's implemented in Java and it has a maven artifact, which makes it easy to include in a leiningen project, so it's my current weapon of choice.

    :dependencies [[org.clojure/clojure "1.2.0"]
    		   [org.clojure/clojure-contrib "1.2.0"]
		   [nu.validator.htmlparser/htmlparser "1.2.1"]]

It's easy to get a DOM from a webpage using Validator.nu ([api docs here](http://about.validator.nu/htmlparser/apidocs/)), feed `HtmlDocumentBuilder` an `InputSource` which you feed a `java.io.Reader`, which is easily created via the `reader` fn from `clojure.java.io`:

    (defn build-document [file-name]
      (.parse (HtmlDocumentBuilder.) (InputSource. (reader file-name))))

## Selectors

To start, I'd like to be able to select a node by:

* id: `#statTable1`
* tag name: `table`
* class attribute: `.class`

Selection by id and tagname is easy, there are already methods on `getElementById` method on `Document`.

    (defn id-sel [document id]
      (let [id (.substring id 1)]
        (.getElementById document id)))

A DOM is already a tree, but not in a Clojure data structure that we can walk with the lbs the language already gives us, like Stuart Sierra's `clojure.walk`.  The Java interface for walking the DOM returns a node's children as `NodeList` (which does not implement `Iterable`), so converting that into a seq is a useful first step.  After that, `filter` can be used to 

    (defn selector [node pred]
      (let [children (nodelist-seq (.getChildNodes node))]
        (lazy-cat
         (filter pred children)
         (when-not (empty? children)
           (mapcat #(selector % pred) children)))))

    (defn element-tagname [elt]
      (when (= Node/ELEMENT_NODE (.getNodeType elt))
        (.getNodeName elt)))

    (defn get-attribute [elt attr]
      (.?. elt getAttributes (getNamedItem attr) getValue))

    (defn hasclass? [elt class]
      (when-let [class-attr (get-attribute elt "class")]
        (some #(= class %) (split class-attr #" "))))


    (defn class-sel [node class]
      (selector node #(hasclass? % (.substring class 1))))

Is there a better way to write `selector` here?  I'd love to hear in the comments.  Zippers from `clojure.zip` and prewalk/postwalk from Stuart Sierra's `clojure.walk` might be faster/cleaner/more elegant.

The `.?.` method in `get-attribute` is remarkably useful.  It's analogous to the `..` operator in `clojure.core` for chaining method invocations on objects. As not all `Node` objects have attributes on them, and not all attributes have the one we're looking for, in both cases, a null value is returned by the method invoked. Trying to invoke any other method returns an NPE. `.?.` does the grunt-work of handling that and short-circuiting to return nil, which is a perfectly reasonable and usable return value for those cases.

## Composing Selectors

I wanted selectors to be composable.  I wanted to be able to feed the results of one selector to another selector to produce further refined results.  This would allow me to declare a selector and use it under multiple contexts.

Selectors take a single node and produce a list of "selected" nodes.  To run a second selector over a list of selected nodes, the `mapcat` operator executes it for each selection and combines the individual result lists back into a flat list of "selected" nodes.

## The 'M' Word

By now, you may have realized that this approach is the same as that ubiquitous and hip mathematical notion, the List monad.  