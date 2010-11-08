# A Brief Note on How `clj-http` works

## It's not Ring, but it's close
Mark McGranaghan's Ring library is probably better known than clj-http.  It's largely for serving and responding to HTTP requests.  clj-http is the same idea on the opposite side of the protocol.  It's for making HTTP requests.

clj-http's introduction page is at http://mmcgrana.github.com/2010/08/clj-http-clojure-http-client.html.

Poking at it, trying to make sense of the code, I couldn't quite grasp what was going on.

Here's the definition of `clj-http.client/request`, featured prominently on clj-http's introduction page:

    (def request
         (-> #'core/request
             wrap-redirects
             wrap-exceptions
             wrap-decompression
             wrap-input-coercion
             wrap-output-coercion
             wrap-query-params
             wrap-basic-auth
             wrap-accept
             wrap-accept-encoding
             wrap-content-type
             wrap-method
             wrap-url))

`request` is the building block of higher-level requests that most developers would worry themselves with.  `get` and `post` and their ilk are implemented in terms of `request`.

## The code flows both ways

As to what exactly request is doing, I think it's safe to say that all of the things written above... well, happen; redirects are handled, the content is typed, input and output are compressed and/or coerced. But in what order? The `->` operator that I maligned in a [previous post](/2010/07/26/7-rules-for-writing-clojure-programs/) turns a series of backwards-lexically function calls into a sequential listing, but in this particular case, that's misleading as well, as even though `wrap-url` is called last, it's effects on the computation actually occur first.  Wha?

It clicked for me when I realized that clj-http is written in a Continuation Passing Style.  The only kind of continuation that clj-http worries itself with is the sending off of a request.  All of these wrap- methods refer to their continuation as `client`, and when they call it, they expect to get back a HTTP response.  

Let's take a look at `wrap-url`:

    (defn wrap-url [client]
      (fn [req]
        (if-let [url (:url req)]
          (client (-> req (dissoc :url) (merge (parse-url url))))
          (client req))))

It takes a client, and a client takes a request. A request is a map of various values that affect what gets included in the HTTP request.  `wrap-url` checks if it's request has a convenience key on it, `:url`, and if so, it breaks the url up into a bunch more specific parts using `parse-url` and then merges them with the given request map.  Now, the really cool and ingenious part of this is that `wrap-url` doesn't actually do any of this when called, but instead returns a fn that will.  That fn -- you guessed it -- is a "client", which means that the result of `wrap-url` can then be passed to other request-altering fns as their continuation.  All of the wrap- methods modify the client you give them to produce one with the underlying client behavior and whatever new behavior they see fit to add.

So, back to the question of when the methods in `request`'s long arrow chain actually take effect: clj-http doesn't change the semantics of the `->` operator, so `wrap-url` does indeed get called last.  It is passed the client created by `wrap-method`, who was passed the client created by [...insert your intelligence from your own explorations here..] which is passed the `clj-http.core/request` fn.  `clj-http.core/request` is the only fn in the whole lot that actually knows how to make an actual http request (and even then it has Apache's HttpClient do most of the heavy lifting for it).

So the last function in the arrow chain is the first function to get a crack at modifying the request.  Conversely, it's the last function to get a crack at modifying the response as it must get the response from the client passed to it from wrap-method, which must get it's response from [...oh god, not this again...] which gets it from `clj-http.core/request`.

It is up to each wrap- method along the way to decide whether or not it's actually concerned with the request or the response (or both).  There are plenty of fns in the stack that demonstrate each choice. `wrap-url` is a good example of a fn that modifies the request on the way down, but returns the response untouched.

I hope this helps anybody who was thinking about writing stuff on top of clj-http, but couldn't immediately figure out how to stop their code from automatically redirecting or how to gracefully check that they've logged into a website before making requests to pages behind a user account.