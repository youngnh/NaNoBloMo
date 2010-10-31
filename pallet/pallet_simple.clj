(ns pallet-simple
  (:use [pallet core compute resource])
  (:use [pallet.crate.automated-admin-user])
  (:use [pallet.crate.couchdb :only (couchdb)])
  (:use [pallet.crate.java :only (java)])
  (:use [pallet.crate.jetty :only (jetty)]))

(def ec2-access-id "Your-Access-ID-Here")
(def ec2-secret-key "Your-Secret-Key-Here")

(def ec2-service (compute-service "ec2" :identity ec2-access-id :credential ec2-secret-key))

(defnode couchnode {:image-id "us-east-1/ami-da0cf8b3" :min-ram (* 7 1024) :min-cores 2}
  :bootstrap (phase (automated-admin-user))
  :configure (phase (java :openjdk)
                    (couchdb)))

(converge {couchnode 5} :compute ec2-service)

(lift couchnode :compute ec2-service
      :phase (phase (jetty)))