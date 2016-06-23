(ns event-data-live-stream.core
  (:require [clojure.tools.logging :as l])
  (:require [event-data-live-stream.fetch :as fetch]
            [event-data-live-stream.server :as server])
  (:gen-class))

(defn main-run
  "Run fetcher and server in two threads. If either terminates, bring down everything."
  []
  (l/info "Start server...")
  (let [server-thread (new Thread (fn []
                                    (server/run)
                                    (System/exit 1)))
        fetch-thread (new Thread (fn []
                                  (fetch/run)
                                  (System/exit 1)))]

    (l/info "Blocking.")
    (.start server-thread)
    (.start fetch-thread)
    

    ; They shouldn't ever stop.
    (.join server-thread)
    (l/info "Server thread exited.")
    
    (.join fetch-thread)
    (l/info "Fetch thread exited.")
  
  (l/info "Exiting (not a good thing!)")))

(defn main-unrecognised-action
  [command]
  (l/fatal "ERROR didn't recognise " command))

; todo test config

(defn -main
  [& args]
  (let [command (first args)]
    (condp = command
      "run" (main-run)
      (main-unrecognised-action command))))
