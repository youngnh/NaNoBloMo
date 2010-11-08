 Replacing code in Lisp has always been a bit of a black art.  I got wedged in a weird situation with multimethods today:

I started clojure via slime and my first test and implementation loaded and ran without any problems:

    (ns lambda-reductions)

    (deftest test-substitute
      (testing "variable substitution"
        (is (= 'r (substitute 'x ['x 'r])))
	(is (= 'y (substitute 'y ['x 'r])))))

    (defmulti substitute (constantly variable))

    (defmethod substitute 'variable [form [from to]]
      (if (= form from) to form))

I wrote my code and my tests in the same namespace, as I was just going for speed here.

My second test worked fine too the only real code "replacement" being that I altered my dispatch function and added another method to the multimethod:

    (testing "apply substitutions"
      (is (= '(foo r) (substitute '(foo x) ['x 'r]))))

    (defmulti substitute (fn [form _]
    	      		   (cond (list? form) 'apply)
			   	 :otherwise 'variable))

    (defmethod substitute 'apply [[f & args] replace]
      (cons (substitute f replace) (map #(substitute % replace) args)))

I hit `C-c C-k` (slime-compile-and-load-file), reran my tests from the repl and they seemed to be picked up.

Then I added number a third case, and for some reason I could not get Clojure to recognize that it had loaded it:

    (testing "lambda substitutions"
      (is (= '(fn [x] 42) (substitute '(fn [x] 42) ['x 'r])))
      (is (= '(fn [x] x) (substitute '(fn [x] x) ['x 'r])))
      (is (= '(clojure.core/fn [y] r) (substitute '(fn [y] x) ['x 'r])))
      (is (= '(clojure.core/fn [y] (foo r)) (substitute '(fn [y] (foo x)) ['x 'r]))))

    (defmulti substitute (fn [form _]
    	      		   (cond (and (list? form) (= 'fn (first form))) 'lambda
			   	 (list? form) 'apply
				 :otherwise 'variable)))

    (defmethod substitute 'lambda [[_ [arg] t :as form] [from to :as replace]]
      (if-not (= arg from)
        `(fn [~arg] ~(substitute t replace))
	form))

My tests failed.  Which is a fair enough response when I've written my code wrong, but after tweaking things enough times, I was convinced I had the impl right.  I `C-c C-k`'d a couple more times, just to be sure and then tried a bunch of things to verify that Clojure had loaded the code I asked it to.  By every indication, it had.
I used the `methods` function.  `(keys (methods substitute))` showed that my dispatch value had been registered.
I used the `get-method` function.  `((get-method substitute 'lambda) '(foo [x] (foo x)) ['x 'r]) returned the expected result.  Clojure knew all about my method and the function itself was right. For some reason the dispatcher wasn't sending my stuffs to the right place (a small lesson learned: I'm going to define my dispatch functions as top-level fns instead of anonymous fns in the `defmulti` form, to make them more testable).

I tried `remove-method` on each variation in turn, and then `C-c C-k`'d again to try and re-add them.  No joy.
I tried `remove-all-methods`, thinking perhaps I'd been too gentle before.  Another `C-c C-k` and my tests still failed.

So I did what every programmer finally does when the magic in the machine stops responding to their incantations, I killed Clojure.  `M-x slime-quit-lisp` and then restarted.  A single `C-c C-k` and re-running my tests showed them all passing.  A `remove-ns` might've saved me from having to quit my running lisp altogether, but I'm not sure. I'm still not sure what happened to cause the behavior in the first place.

If you know what I did to get on the bad side of multimethods, chime in in the comments below. Peace.