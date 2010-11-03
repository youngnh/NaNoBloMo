# CSS Selectors, Scraping and Clojure

## Building a DOM

Parsing HTML can be tricky, most of my naive attempts to parse real-world pages produced a lot of stack traces.  The [Validator.nu HTML parser](http://about.validator.nu/htmlparser/) has so far cleared those low hurdles.  It's implemented in Java and it has a maven artifact, which makes it easy to include in a leiningen project, so it's my current weapon of choice.

    :dependencies [[org.clojure/clojure "1.2.0"]
    		   [org.clojure/clojure-contrib "1.2.0"]
		   [nu.validator.htmlparser/htmlparser "1.2.1"]]

It's easy to get a DOM from a webpage using Validator.nu ([api docs here](http://about.validator.nu/htmlparser/apidocs/)), feed `HtmlDocumentBuilder` an `InputSource` which you feed a `java.io.Reader`, which is easily created via the `reader` fn from `clojure.java.io`:

    (defn build-document [file-name]
      (.parse (HtmlDocumentBuilder.) (InputSource. (reader file-name))))

## Converting the DOM to a seq

Clojure comes with a few very nice tree walking facilities.  We can't use them until we convert a dom with nodes of type, well, `Node` and branches of `NodeList` into seqs that Clojure is more adept at manipulating.

`NodeList` has two methods on it, `getLength()` and `item(int index)`.  One approach is to close over an index binding and recursively create the seq:

    (defn nodelist-seq [node-list]
      (letfn [(internal [i]
                (lazy-seq
  	          (when (< i (.getLength node-list))
	            (cons (.item node-list i) (internal (inc i))))))]
      (internal 0)))

Another is to keep the current index in an atom, and implement `Iterator` with it, which Clojure can make into a seq for you:

    (defn nodelist-seq [node-list]
      (iterator-seq
       (let [i (atom 0)]
         (reify Iterator
           (hasNext [_]
             (< @i (.getLength node-list)))
           (next [_]
             (try
  	       (.item node-list @i)]
               (finally
	        (swap! i inc)))))))

Where I'm using `try/finally` as a replacement for Common Lisp's `prog1`.

With that in place, it's not hard to turn a DOM into a nested seq, which either zippers found in `clojure.zip` or Stuart Sierra's `clojure.walk` should be able to navigate for you quite adeptly.

## Selectors

I'd like to be able to select a node by:

* id: `#statTable1`
* tag name: `table`
* class attribute: `.class`

And I'd like selectors to work from any node I give it.  This way I can write a selector that will work at multiple places in a tree, making them more reusable.  Being able to turn a DOM into a seq suggests that filtering it on a predicate would be a quick way to write the above selectors, here are supporting functions to inspect the nodes themselves:

    (defn element-tagname [elt]
      (when (= Node/ELEMENT_NODE (.getNodeType elt))
        (.getNodeName elt)))

    (defn get-attribute [elt attr]
      (.?. elt getAttributes (getNamedItem attr) getValue))

    (defn hasclass? [elt class]
      (when-let [class-attr (get-attribute elt "class")]
        (some #(= class %) (split class-attr #" "))))

The `.?.` method in `get-attribute` is remarkably useful.  It's analogous to the `..` operator in `clojure.core` for chaining method invocations on objects. As not all `Node` objects have attributes on them, and not all attributes have the one we're looking for, in both cases, a null value is returned by the method invoked. Trying to invoke any other method returns an NPE. `.?.` does the grunt-work of handling that and short-circuiting to return nil.

The `Document` object has two methods on it that are just too good to pass up, though. `getElementById` and `getElementsByTagName` might give better performance than scanning the entire tree, so if we're selecting from the root, then we'd like to use them.  Multimethods solve our dilemma nicely.

    (defn doc-or-node [node & _]
      (if (instance? Document node)
        Document
        Node)))

    (defmulti id-sel doc-or-node)

    (defmulti element-sel doc-or-node)
    
    (defmethod id-sel Document [document id]
      (.getElementById document (.substring id 1)))
    
    (defmethod id-sel Node [node id]
      (filter #(= (.substring id 1) (get-attribute % "id")) (dom-seq node)))
    
    (defmethod element-sel Document [document elt-name]
      (.getElementsByTagname document elt-name))
    
    (defmethod element-sel Node [node elt-name]
      (filter #(= elt-name (element-tagname %)) (dom-seq node)))

## Uniformity

Finally, if each selector takes a single `Node` and returns a list of `Node`s, then I'll note that you can "chain" selectors together with `mapcat`.

    (->> (element-sel document "body")
         (mapcat #(element-sel % "table"))
         (mapcat #(element-sel % "tr"))
         (mapcat #(class-sel % ".odd")))

With this property, we'd need to make sure that `Document` version of `id-sel` above wraps it's single `Node` in a list.  This sort of chaining ability, of taking a bunch of things, and applying them in sequence to get a single thing throws up the _use reduce_ flags in my head.  My first attempt nearly works out of the gate:

    (defn $ [node & selectors]
      (reduce mapcat node selectors))

The problems with it being that `mapcat` takes it's function argument first, while we're passing our selector functions in second, and that `mapcat` takes a list, not a single item.  Here's how I fixed it:

    (defn flip [f]
      (fn [& args]
        (apply f (reverse args))))

    (defn $ [node & selectors]
      (reduce (flip mapcat) [node] selectors))

So now we have a new selector that composes the behavior of a bunch of selectors.

## The 'M' Word

By now, you may have realized that this approach is the same as that suddenly ubiquitous and hip mathematical notion, the List monad. I won't expound any further on the point, you're either interested in monads or you're not. I'm of the mind that they're a remarkably useful construct, but a bit obtuse when approached from the narrow description of only their mathematical properties.

You can find a larger working example expanding upon all the code in this post on my github account.