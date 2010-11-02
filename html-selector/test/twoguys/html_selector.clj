(ns twoguys.html-selector
  (:use [twoguys.html-selector] :reload-all)
  (:use [clojure.test])
  (:import [org.w3c.dom Document]))

(deftest test-build-document
  (is (instance? Document (build-document "data/matchup_mid1_mid2_week1.html"))))

(deftest test-element-tagname
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")
	table-elts ($ document "table")]
    (is (= "table" (element-tagname (first table-elts))))))

(deftest test-element-sel
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")
	[matchup-summary] ($ document "#matchup-summary")
	[matchup] ($ document "#matchup")]

    (testing "given a Document, the context is the whole DOM"
      (is (= 13 (count (element-sel document "table")))))

    (testing "given a seq of nodes, they are the context"
      (is (= 1 (count (element-sel matchup-summary "table"))))
      (is (= 4 (count (element-sel matchup "table"))))
      (is (= 5 (count (mapcat #(element-sel % "table") [matchup matchup-summary])))))))

(deftest test-class-sel
  (let [document (build-document "data/matchup_mid1_mid2_week1.html")
	selector #(class-sel % ".pos")]
    (is (= ["C" "1B" "2B" "3B" "SS" "LF" "CF" "RF" "Util" "BN" "BN" "BN" "BN" "BN" "BN" "--" "--" "--"]
	   ($ document "#statTable1" "tbody" "tr" selector text-sel)))))
