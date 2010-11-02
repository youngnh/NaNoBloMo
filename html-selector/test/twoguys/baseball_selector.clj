(ns twoguys.baseball-selector
  (:use [twoguys.baseball-selector] :reload-all)
  (:use [clojure.test]))

(deftest test-player-line-selector
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")
	$batter-rows ($ document "#statTable1")
	$pitcher-rows ($ document "#statTable3")]
    (testing "batters"
     (is (= ["C" "Yadier Molina" "-" "-" "-" "-" "-"]
	      (map #(.getTextContent %) (first (player-line-sel $batter-rows))))))
    (testing "pitchers"
      (is (= ["P" "Jonathan Broxton" "0" "0.00" "3.00" "16.20" ".286"]
	     (map #(.getTextContent %) (first (player-line-sel $pitcher-rows))))))))

(deftest test-matchup-line-selector
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")
	$table ($ document "#matchup-summary-table")]
    (is (= ["Takeit2thebank" "31" "32" "21" "91" ".345" "5" "2.61" "2.64" "6.87" ".284" "3"]
	   (map #(.getTextContent %) (first (matchup-line-sel $table)))))))

(deftest test-minimum-innings-sel
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")]
    (is (= "Takeit2thebank did not meet minimum requirements."
	   (.getTextContent (first (minimum-innings-sel document)))))))