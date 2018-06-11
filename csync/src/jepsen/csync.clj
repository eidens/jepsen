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
             [nemesis :as nemesis]
             [net :as net]
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
(def initial-value "")

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

(defn start-csync-client
  "Start the csync client."
  [remote-host remote-port dir local-host local-port]
  (info "starting csync server")
  (c/exec binary
          :-c
          :--host remote-host
          :--port remote-port
          :--dir dir
          :--local_host local-host
          :--local_port local-port
          :--no_file_watching))

(defn start-csync-server
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

(defn stop-csync-server
  "Stop a csync server."
  []
  (info "stopping csync server")
  (cu/stop-daemon! binary pidfile))

(defn db
  "A csync server for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "setting up csync" version)

      ; Create the directory where 1) the csync server will store the
      ; files it receives and 2) the csync client will look for files
      ; to upload to the server.
      ;
      ; The csync server currently saves the files it receives at
      ; exactly the same path as on the client. Therefore this
      ; directory has to be available on both client and server node.
      ;
      ; In the current test setup only one file (test-file) is
      ; used. This file is also created here.
      (setup-test-dir dir test-file)

      ; reset the log file
      (c/exec :echo "" :> logfile)

      ; start the csync server on its dedicated node
      (if (= node nodename-server)
        (start-csync-server hostname-server port-server dir)))

    (teardown! [_ test node]
      (info node "tearing down csync node")
      (c/su (c/exec :rm :-rf dir))
      (if (= node nodename-server)
        (stop-csync-server)))

    db/LogFiles
    (log-files [_ test node]
            [logfile])))

(defn w [_ _] {:type :invoke, :f :write, :value (str (rand-int 20))})
(defn r [_ _] {:type :invoke, :f :read, :value nil})

(def client-lock (Object.))
(defrecord Client []
  client/Client
  (open! [this test node]
    this)

  (setup! [this test])

  (invoke! [_ test op]
    (let [val (:value op)]
      (case (:f op)

        ; Write the given value to the test file on the client, then
        ; start the csync client and let it upload the file to the
        ; server.
        ;
        ; Multiple :write operations can be invoked concurrently. But,
        ; since the implementation always operates on the same file
        ; and always starts a new csync client, they may not be
        ; executed concurrently.
        :write (locking client-lock
                 (c/on nodename-client
                       ; write the given value to the test file on the client
                       (c/exec :echo val :> test-file)
                       ; let the client upload that file to the server
                       (start-csync-client hostname-server
                                           port-server
                                           dir
                                           hostname-client
                                           port-client))
                 (assoc op :type :ok))

        ; read the value currently in the test file on the server
        :read (assoc op :type :ok,
                     :value (c/on nodename-server
                                  (c/exec :cat test-file :2>/dev/null
                                          :|| :echo initial-value))))))

  (teardown! [this test])

  (close! [_ test]))

(defn csync-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :nconcurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "csync"

          :net net/iptables
          :os debian/os

          :client (Client.)
          :db (db"v0.1.0")
          :model (model/register initial-value)
          :nemesis nemesis/noop

          :checker (checker/compose
                    {:perf      (checker/perf)
                     :linear    (checker/linearizable)
                     :timeline  (timeline/html)})

          :generator (->> (gen/mix [r w])
                          (gen/stagger 1)
                          (gen/nemesis nil)
                          (gen/time-limit 5))}
         opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web
  server for browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn csync-test})
            args))
