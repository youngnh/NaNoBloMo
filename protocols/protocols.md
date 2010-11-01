We have a core protocol in Spyder defined thusly:

    (defprotocol GraphAPI
      (has-resource? [graph resource-id])
      (get-resource [graph resource-id]
      		    [graph resource-id include-id])
      (find-resource [graph type]
      		     [graph type predicate value])
      (export [graph value]
      	      [graph value only-expand-generated-prefixes])
      (resource-has-type? [graph resource-id type]))

5 methods with 8 distinct signatures.  We define a record type, Graph, which extends GraphAPI:

    (defrecord Graph [prefix-map resource-map])

    (extend-type Graph
      GraphAPI
      (has-resource?
       [graph resource-id]
       (not (nil? ((get-resource-map graph) (uri-to-keyword graph resource-id)))))
      
      (get-resource
       ([graph resource-id]
        (get-resource graph resource-id true))
       ([graph resource-id include-id]
        (let [resource (get-resource-direct graph (first (lookup-uri resource-id (get-prefix-map graph))))
	      result (if include-id
	      	       resource
		       (dissoc resource :id))]
          result)))

      (find-resource
       ([graph type]
        (let [target-type (uri-to-keyword graph type)]
	  (filter #(= (get % :RDF/type) target-type) (get-resources graph))))
       ([graph type predicate value]
        (filter #(= (get % predicate) (uri-to-keyword graph value))
		(find-resource graph type))))

      (export
       ([graph value]
        (export graph value false))
       ([graph value only-expand-generated-prefixes]
        (if (or (not only-expand-generated-prefixes)
	        (and (keyword? value)
		     (generated-prefix? value)))
          (resolve-uri (get-prefix-map graph) value)
	  value)))

      (resource-has-type?
       [graph resource-id type]
       (->> resource-id
       	    (get-resource graph)
	    :RDF/type
	    to-set
	    (some #(= type %)))))

So a not-insignificant investment in code here.  If you look at the  `find-resource` method above, you'll notice it's doing a full scan of the list of triples contained in resource-map.  This method is called fairly often, so its a bit of a performance hit.  We came up with a way to scan the resources once and put all of the information in a map so that retrieval is quicker.  That concept was written as a protocol called `Indexes` to express that it's an idea that stores multiple indexes of cached data.

    (defprotocol Indexes
      (get-indexes [this] "Returns a list of all queryable indexes")
      (get-from-index [this index key] "Retrieves the value stored in index for key"))

There's also an additional function `create-index-of` that takes a `Graph` and creates an `Indexes` from it.  At any given moment in our code, a `Graph` may or may not have been indexed.  In any event, we'd like to create another extension of the `GraphAPI` protocol that behaves exactly as `Graph` does, only it uses the `Indexes` to find resources and not the slow method of filtering every resource.  This is a textbook inheritance scenario in Java, but Clojure eschews inheritance for a more ad hoc approach.  Here are 3 approaches I came up with to solve the problem.

## Approach #1 - `deftype`

Clojure's `deftype` is ideal for creating abstractions like this.  It combines fields with methods.  Sort of a combination `defrecord` & `extend-type` rolled into one.

    (deftype IndexedGraph [graph indexes]
      GraphAPI
      (has-resource?
       [_ resource-id]
       (has-resource? graph resource-id))

      (get-resource
       ([_ resource-id]
        (get-resource graph resource-id true))
       ([_ resource-id include-id]
        (get-resource graph resource-id include-id)))

      (find-resource
       ([_ type]
        (get-from-index indexes :type type))
       ([_ type predicate value]
        (get-from-index indexes :tpv [type predicate value])))

      (export
       ([_ value]
        (export graph value false))
       ([_ value only-expand-generated-prefixes]
        (export graph value only-expand-generated-prefixes)))

      (resource-has-type?
       [_ resource-id type]
       (resource-has-type? graph resource-id type)))

There's some duplication in having to re-call methods on `graph` here, but otherwise it's pretty straightforward.  Note that a method taking `graph` and `indexes` and reifying `GraphAPI` would look very, very similar but wouldn't create a named type that you could use later:

    (defn indexed-graph [graph indexes]
      (reify GraphAPI
        (has-resource? ... )
	(get-resource ... )
	...))

## Approach #2 - Pull Functionality Into Maps

This approach starts by rewriting the `Graph` implementation of `GraphAPI` as a map.

    (def graph-impl
         {:has-resource? (fn [graph resource-id]
			   (not (nil? ((get-resource-map graph) (uri-to-keyword graph resource-id)))))
	  :get-resource (fn
	  		 ([graph resource-id]
			  (get-resource graph resource-id true))
			 ([graph resource-id include-id]
			  (let [resource (get-resource-direct graph (first (lookup-uri resource-id (get-prefix-map graph))))
			        result (if include-id
				         resource
					 (dissoc resource :id))]
		            result)))
          :find-resource (fn
	  		  ([graph type]
			   (let [target-type (uri-to-keyword graph type)]
			     (filter #(= (get % :RDF/type) target-type) (get-resources graph))))
			  ([graph type predicate value]
			   (filter #(= (get % predicate) (uri-to-keyword graph value))
			   	   (find-resource graph type))))
          :export (fn
	  	   ([graph value]
		    (export graph value false))
		   ([graph value only-expand-generated-prefixes]
		    (if (or (not only-expand-generated-prefixes)
		    	    (and (keyword? value)
			    	 (generated-prefix? value)))
		      (resolve-uri (get-prefix-map graph) value)
		      value)))
          :resource-has-type? (fn [graph resource-id type]
	  		        (->> resource-id
				     (get-resource graph)
				     :RDF/type
				     to-set
				     (some #(= type %))))})

Which you can pass to `extend` directly to implement `GraphAPI`:

    (extend Graph
      GraphAPI
      graph-impl)

Then, you could create record type composed of a `Graph` and an `Indexes` and when extending that type, overwrite the parts of `graph-impl` that are new.  The twist being that all of the functions of `graph-impl` expect their first argument to be a `Graph` object, so we'll have to "unwrap" that field of our record object.

    (defrecord IndexedGraph [graph indexes])

    (let [unwrap (fn [f]
    	 	   (fn [indexed-graph & args]
		     (apply f (:graph indexed-graph) args)))
          indexed-graph-impl (zipmap (keys graph-impl) (map unwrap (vals graph-impl)))]
      (extend IndexedGraph
        GraphAPI
	(assoc indexed-graph-impl
	 :find-resource (fn
	 		 ([indexed-graph type]
			  (get-from-index (:indexes indexed-graph) :type type))
			 ([indexed-graph type predicate value]
			  (get-from-index (:indexes indexed-graph) :tpv [type predicate value]))))))

We've used Clojure to write our program for us here, first by unwrapping each `IndexedGraph` to be get at the `Graph` within, and then overwriting just the `find-resource` method via `assoc`.

