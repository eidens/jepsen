(ns jepsen.filesync
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen
             [cli :as cli]
             [control :as c]
             [client :as client]
             [db :as db]
             [generator :as gen]
             [tests :as tests]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]))

(def dir "/csync")
(def pidfile "/csync.pid")
(def logfile "/csync.log")
(def binary "/filesync/csync")
(def nodename-client "csync_client")
(def nodename-server "csync_server")
(def hostname-server nodename-server)

(defn db
  "A filesync client for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing csync" version)
      (Thread/sleep 1000)
      (c/su (c/exec :mkdir dir))
      (cu/start-daemon!
       {:logfile logfile
        :pidfile pidfile
        :chdir dir}
       binary
       (case node
         nodename-client :--client
         nodename-server :--server)
       :--host hostname-server
       :--port 22
       :--dir dir)

      (Thread/sleep 1000))

    (teardown! [_ test node]
      (info node "tearing down csync client")
      (cu/stop-daemon! binary pidfile)
     ;(c/su (c/exec :rm :-rf dir))
      )

    db/LogFiles
    (log-files [_ test node]
            [logfile])))

(defn w [_ _] {:type :invoke, :f :add-file, :value (rand-int 20)})

(defrecord Client []
  client/Client
  (open! [this test node]
    this)

  (setup! [this test])

  (invoke! [this test op]
    (let [val (:value op)]
      (case (:f op)
        :add-file (do (c/on nodename-client (c/exec :echo val :> (str dir "/" val)))
                      (assoc op :type, :ok)))))

  (teardown! [this test])

    (close! [_ test]))

(defn filesync-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :nconcurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "csync"
          :os debian/os
          :db (db"v0.1.0")
          :client (Client.)
          :generator (->> w
                          (gen/stagger 1)
                          (gen/nemesis nil)
                          (gen/time-limit 2))}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web
  server for browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn filesync-test})
            args))
