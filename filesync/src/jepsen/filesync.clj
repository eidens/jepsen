(ns jepsen.filesync
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen
             [checker :as checker]
             [cli :as cli]
             [control :as c]
             [client :as client]
             [db :as db]
             [generator :as gen]
             [tests :as tests]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [knossos.model :as model]))

(def dir "/csync")
(def test-file (str dir "/test"))
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
      (c/su (c/exec :mkdir :-p dir))

      (c/on nodename-server
            (c/su (c/exec :mkdir :-p dir))
            (cu/start-daemon!
             {:logfile logfile
              :pidfile pidfile
              :chdir dir}
             binary
             :--server
             :--port 22
             :--dir dir)
            (c/exec :touch test-file)))

    (teardown! [_ test node]
      (info node "tearing down csync client")
      (c/su (c/exec :rm :-rf dir)))

    db/LogFiles
    (log-files [_ test node]
            [logfile])))

(defn w [_ _] {:type :invoke, :f :write, :value (rand-int 20)})
(defn r [_ _] {:type :invoke, :f :read, :value nil})

(defrecord Client []
  client/Client
  (open! [this test node]
    this)

  (setup! [this test])

  (invoke! [_ test op]
    (let [val (:value op)]
      (case (:f op)
        :write (do (c/on nodename-client
                         ; write value to file on the client
                         (c/exec :echo val :> test-file)
                         (c/exec binary :--client :--host hostname-server
                                 :--port 22 :--dir dir))
                      (assoc op :type :ok))
        :read (assoc op :type :ok,
                     :value (c/on nodename-server
                                  (c/exec :cat test-file)))
        )))

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
          :model (model/register)
          :checker (checker/compose
                    {:perf      (checker/perf)
                     :linear    (checker/linearizable)
                     :timeline  (timeline/html)})
          :generator (->> (gen/mix [r w])
                          (gen/stagger 1)
                          (gen/nemesis nil)
                          (gen/time-limit 5))}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web
  server for browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn filesync-test})
            args))
