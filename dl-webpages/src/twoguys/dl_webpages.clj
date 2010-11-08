(ns twoguys.dl-webpages
  (:require [clj-http.client :as http])
  (:require [clj-http.core :as http.core]))

(defn dl-webpage [folder week mid1 mid2]
  (let [matchup-url "http://baseball.fantasysports.yahoo.com/b1/161295/matchup"
	params {"week" week
		"mid1" mid1
		"mid2" mid2}
	filename (format "matchup_week%d_mid%d_mid%d.html" week mid1 mid2)
	body (:body (http/get matchup-url {:query-params params}))]
    (spit (str folder filename) body)))



(def request2
     (-> #'http.core/request
	 http/wrap-exceptions
	 http/wrap-decompression
	 http/wrap-input-coercion
	 http/wrap-output-coercion
	 http/wrap-query-params
	 http/wrap-basic-auth
	 http/wrap-accept
	 http/wrap-accept-encoding
	 http/wrap-content-type
	 http/wrap-method
	 http/wrap-url))