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
            [ring.middleware.resource :as middleware-resource]
            [ring.middleware.content-type :as middleware-content-type]))

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

(defn filter-events
  "Filter a seq of events in json format for those that occur after the date and optionally match source"
  [event-json-blobs filter-source filter-date]

  (filter #(let [parsed (json/read-str %)
                item-date (clj-time-coerce/from-string (get parsed "timestamp"))
                item-source (get parsed "source_id")]
              (and (clj-time/after? item-date filter-date)
                   

                   (or (nil? filter-source)
                       (= filter-source item-source))

                   )) event-json-blobs))

(defn send-catchup-since
  "Catch up since the given date."
  [channel filter-source filter-date-str]
  (l/info "Send catchup to" channel "with filter" filter-source)
  (with-open [redis (redis/get-connection)]
    (let [filter-date (clj-time-coerce/from-string filter-date-str)
          today-key (str "live-stream__events-" (clj-time-format/unparse ymd (clj-time/now)))
          yesterday-key (str "live-stream__events-" (clj-time-format/unparse ymd (clj-time/minus (clj-time/now) (clj-time/days 1))))]

      ; the Jedis library fetches the whole lot as a big List.
      (doseq [item (filter-events (.lrange redis yesterday-key 0 -1) filter-source filter-date)]
         (server/send! channel item))

      (doseq [item (filter-events (.lrange redis today-key 0 -1) filter-source filter-date)]
         (server/send! channel item)))))

(defn send-catchup-items
  "Catch up with n recent items."
  [channel filter-source num-items]
  (l/info "Send catchup to" channel "with filter" filter-source)
  (with-open [redis (redis/get-connection)]
    (let [filter-date (clj-time/date-time 0)
          today-key (str "live-stream__events-" (clj-time-format/unparse ymd (clj-time/now)))
          yesterday-key (str "live-stream__events-" (clj-time-format/unparse ymd (clj-time/minus (clj-time/now) (clj-time/days 1))))

          today-len (.llen redis today-key)
          yesterday-len (.llen redis yesterday-key)

          take-today (min today-len num-items)
          take-yesterday (max
                            0
                            (min yesterday-len (- num-items take-today)))
          ]

      (l/info "Requested" num-items "taking" take-today "/" today-len "from today," take-yesterday "/" yesterday-len "from yesterday. Source" filter-source "date" filter-date)

      ; the Jedis library fetches the whole lot as a big List, so allow each to be disposed.
      (doseq [item (take-last take-today (filter-events (.lrange redis today-key 0 -1) filter-source filter-date))]
       (server/send! channel item))

      (when (> yesterday-len 0)
        (doseq [item (take-last take-yesterday (filter-events (.lrange redis yesterday-key 0 -1) filter-source filter-date))]
          (server/send! channel item))))))


(defn socket-handler [request]
  (server/with-channel request channel
    (let [; source-filter is either the source id or nil for everything
          source-filter (get-in request [:query-params "source_id"])]
    
      (server/on-close channel (fn [status]
                                 (swap! channel-hub dissoc channel)))

     (server/on-receive channel (fn [data]
                                  (cond
                                    (.startsWith data "since ") (send-catchup-since channel source-filter (.substring data 6))
                                    (.startsWith data "items ") (send-catchup-items channel source-filter (Integer/parseInt (.substring data 6)))
                                    (= data "start") (swap! channel-hub assoc channel {:source-filter source-filter})))))))

(defroutes app-routes
  ; Standlone shows the content, but from within a standalone (e.g. PDF-linked) page.
  (GET "/socket" [] socket-handler))

(def app
  (-> app-routes
     middleware-params/wrap-params
     (middleware-resource/wrap-resource "public")
     (middleware-content-type/wrap-content-type)))

(defn run []
  (let [port (Integer/parseInt (:server-port env))]
    (l/info "Start server on " port)
    (server/run-server app {:port port}))
  ; TODO shouldn't be needed.
  (.join (Thread/currentThread)))

