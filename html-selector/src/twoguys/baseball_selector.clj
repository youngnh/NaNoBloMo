(ns twoguys.baseball-selector
  (:use twoguys.html-selector))

(defn player-line-sel [$table]
  (for [$row ($ $table "tbody" "tr")]
    (let [$row (list $row)
	  pos ($ $row ".pos")
	  name ($ $row ".player" ".name")
	  stats ($ $row ".stat")]
      (concat pos name stats))))

(defn matchup-line-sel [$table] ;; runs over a single ($ #matchup-summary-table tbody tr)
  (for [$row ($ $table "tbody" "tr")]
    (let [$row (list $row)]
      ($ $row "td"))))

(defn minimum-innings-sel [document]
  ($ document "#minreached" "p"))