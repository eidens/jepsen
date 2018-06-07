(ns jepsen.csync
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

(def dir "/csync-files")
(def test-file (str dir "/test"))
(def pidfile "/csync.pid")
(def logfile "/csync.log")
(def binary "/csync/bin/csync")

(def nodename-client "csync_client")
(def hostname-client nodename-client)
(def port-client 0)

(def nodename-server "csync_server")
(def hostname-server nodename-server)
(def port-server 2000)

(defn setup-test-dir
  "Sets up the directory in which csync saves its files."
  [dir test-file]
  (info "creating csync directory")

  (c/su (c/exec :mkdir :-p dir)
        (c/exec :touch test-file)))

(defn start-client
  "Start the csync client."
  [remote-host remote-port dir local-host local-port]
  (info "starting csync server")
  (c/exec binary
          :-c
          :--host remote-host
          :--port remote-port
          :--dir dir
          :--local_host local-host
          :--local_port local-port))

(defn start-server
  "Start the csync server."
  [host port dir]
  (info "starting csync server")

  (c/exec :cd dir)
  (cu/start-daemon!
   {:logfile logfile
    :pidfile pidfile
    :chdir dir}
   binary
   :-s
   :--host host
   :--port port))

(defn db
  "A csync server for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing csync" version)

      (setup-test-dir dir test-file)
      (c/exec :echo "" :> logfile)

      (if (= node nodename-server)
        (start-server hostname-server port-server dir)))

    (teardown! [_ test node]
      (info node "tearing down csync node")
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
                         ; write the given value to the test file on
                         ; the client
                         (c/exec :echo val :> test-file)
                         ; let the client upload that file to the
                         ; server
                         (start-client hostname-server
                                       port-server
                                       dir
                                       hostname-client
                                       port-client))
                      (assoc op :type :ok))

        ; read the value currently in the test file on the server
        :read (assoc op :type :ok,
                     :value (c/on nodename-server
                                  (c/exec :cat test-file))))))

  (teardown! [this test])

  (close! [_ test]))

(defn csync-test
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
  (cli/run! (cli/single-test-cmd {:test-fn csync-test})
            args))
