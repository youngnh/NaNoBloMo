(ns twoguys.baseball-selector
  (:use [twoguys.baseball-selector] :reload-all)
  (:use [clojure.test]))

(deftest test-player-line-selector
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")
	[batter-table] ($ document "#statTable1")
	[pitcher-table] ($ document "#statTable3")]
    (testing "batters"
     (is (= ["C" "Yadier Molina" "-" "-" "-" "-" "-"]
	    (first (player-line-sel batter-table)))))
    (testing "pitchers"
      (is (= ["P" "Jonathan Broxton" "0" "0.00" "3.00" "16.20" ".286"]
	     (first (player-line-sel pitcher-table)))))))

(deftest test-matchup-line-selector
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")
	[matchup-summary-table] ($ document "#matchup-summary-table")]
    (is (= ["Takeit2thebank" "31" "32" "21" "91" ".345" "5" "2.61" "2.64" "6.87" ".284" "3"]
	   (first (matchup-line-sel matchup-summary-table))))))

(deftest test-minimum-innings-sel
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")]
    (is (= "Takeit2thebank did not meet minimum requirements."
	   (first (minimum-innings-sel document))))))