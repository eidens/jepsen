(defproject jepsen.csync "0.1.0-SNAPSHOT"
  :description "a jepsen test for a file sync protocol"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [jepsen "0.1.8"]]
  :main jepsen.csync)
