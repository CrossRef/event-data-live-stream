(ns event-data-live-stream.rememberizer
  "Remember the events we've seen."
  (:require [event-data-live-stream.redis :as redis])
  (:require [clojure.tools.logging :as l])
  (:require [clj-time.core :as clj-time]
            [clj-time.format :as clj-time-format]
            [clj-time.coerce :as clj-time-coerce]))

(defn is-event-id-new?
  "Is this event ID new to us?"
  [redis-connection event]
  (let [id (get event "id")
        timestamp-iso8601 (get event "timestamp")
        timestamp-yyyy-mm-dd (.substring timestamp-iso8601 0 10)
        timestamp-key (str "live-stream__id-set-" timestamp-yyyy-mm-dd)
        month-hence (quot
                      (clj-time-coerce/to-long
                              (clj-time/plus (clj-time-coerce/from-string timestamp-iso8601)
                                             (clj-time/months 1)))
                      1000)
        already-exists? (.sismember redis-connection timestamp-key id)]

    ; Maintain a set of IDs that occurred in the given day.
    (.sadd redis-connection timestamp-key (into-array [id]))

    ; Make sure that month's worth of things expires in a month.
    (.expireAt redis-connection timestamp-key month-hence)

    (not already-exists?)))

(defn filter-new-events
  "For a seq of objects with an id and timestamp string key, return only those that haven't been seen before."
  [events]
  (with-open [connection (redis/get-connection)]
    (filter (partial is-event-id-new? connection) events)))
