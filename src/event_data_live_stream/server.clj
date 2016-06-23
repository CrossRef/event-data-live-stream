(ns event-data-live-stream.server
  "Serve up a websocket!"
  (:require [org.httpkit.server :as server])
  (:require [clojure.data.json :as json])
  (:require [clojure.tools.logging :as l])
  (:require [event-data-live-stream.rememberizer :as rememberizer]
            [event-data-live-stream.redis :as redis])
  (:require [config.core :refer [env]])
  (:require [clj-time.core :as clj-time]
            [clj-time.format :as clj-time-format]
            [clj-time.coerce :as clj-time-coerce]
            [compojure.core :refer [defroutes GET]]
            [ring.middleware.params :as middleware-params]
            [ring.middleware.resource :as middleware-resource]))

(def channel-hub (atom {}))

(def ymd (clj-time-format/formatter "yyyy-MM-dd"))

(defn broadcast
  "Send event to all websocket listeners if it matches their filter."
  [event]
  (let [source_id (get event "source_id")]
    (doseq [[channel channel-options] @channel-hub]
      (if-let [source-filter (:source-filter channel-options)]
        (when (= source-filter source_id)
          (server/send! channel (json/write-str event)))
        (server/send! channel (json/write-str event))))))

(defn send-catchup
  [channel source-filter]
  (l/info "Send catchup to" channel "with filter" source-filter)
  (with-open [redis (redis/get-connection)]
    (let [today-key (str "live-stream__events-" (clj-time-format/unparse ymd (clj-time/now)))
          yesterday-key (str "live-stream__events-" (clj-time-format/unparse ymd (clj-time/minus (clj-time/now) (clj-time/days 1))))]

      ; the Jedis library fetches the whole lot as a big List.
      (doseq [item (.lrange redis yesterday-key 0 -1)]
        (if source-filter
         (when (= (get (json/read-str item) "source_id") source-filter) (server/send! channel (json/write-str item)))
         (server/send! channel item)))

      (doseq [item (.lrange redis today-key 0 -1)]
        (get (json/read-str item) "source_id")
        (if source-filter
         (when (= (get (json/read-str item) "source_id") source-filter) (server/send! channel (json/write-str item)))
         (server/send! channel item))))))

(defn socket-handler [request]
  (server/with-channel request channel
    (let [; source-filter is either the source id or nil for everything
          source-filter (get-in request [:query-params "source_id"])]
    
      (server/on-close channel (fn [status]
                                 (swap! channel-hub dissoc channel)))

     (server/on-receive channel (fn [data]
                                  (condp = data
                                    "catchup" (send-catchup channel source-filter)
                                    "start" (swap! channel-hub assoc channel {:source-filter source-filter})))))))

(defroutes app-routes
  ; Standlone shows the content, but from within a standalone (e.g. PDF-linked) page.
  (GET "/socket" [] socket-handler))

(def app
  (-> app-routes
     middleware-params/wrap-params
     (middleware-resource/wrap-resource "public")))

(defn run []
  (let [port (Integer/parseInt (:server-port env))]
    (l/info "Start server on " port)
    (server/run-server app {:port port}))
  ; TODO shouldn't be needed.
  (.join (Thread/currentThread)))

