# HTML Selectors for Scraping Webpages with Clojure

1. starting with a webpage we've got on disk
2. DOM is a nice format to parse and HTML on the web these days is tough to parse
3. validator.nu (already on maven - easy to add to project.clj)
4. docs at http://about.validator.nu/htmlparser/apidocs/
5. feed HtmlDocumentBuilder an InputSource (feed the InputSource a Reader, which Clojure has a great fn for)
6. decide what information we want off the page and what we'd write in jQuery to get it (sizzle selectors)

($ "#statTable1")
($ "tbody" "tr")

($ ".pos")
($ ".player" ".name")
($ ".stat")

($ "#matchup-summary-table" "tr")
($ "td")

We'd like to be able to create these snippets, which then merely need to be fed the contexts (a list of nodes) from which they will select their work and return a flat list.  That way the results can serve as contexts for other selectors.

nodelist-seq aside (this is why you need `letfn`, won't work with a let and an anonymous fn)
