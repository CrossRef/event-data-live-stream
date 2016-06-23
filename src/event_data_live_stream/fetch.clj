(ns event-data-live-stream.fetch
  "Fetch events for broadcast."
  (:require [clojure.data.json :as json])
  (:require [clojure.tools.logging :as l])
  (:require [event-data-live-stream.rememberizer :as rememberizer]
            [event-data-live-stream.redis :as redis]
            [event-data-live-stream.server :as server])
  (:require [config.core :refer [env]])
  (:require [clj-time.core :as clj-time]
            [clj-time.format :as clj-time-format]
            [clj-time.coerce :as clj-time-coerce])
  (:require [org.httpkit.client :as http-client])
  
  
  (:require [robert.bruce :refer [try-try-again]])
  (:gen-class))

(def ymd (clj-time-format/formatter "yyyy-MM-dd"))

(defn tomorrow-ymd []
  "What day would it be if I asked you what day it is tomorrow?"
  (clj-time/plus (clj-time/now) (clj-time/days 1)))

(def per-page
  "Number of items to fetch from Lagotto API. This must be greater than the number of events we will ever get in the polling interval.
  This is set up to poll every minute. We currently get 1 or 2 per minute, so 1000 should be plenty."
  1000)

(defn fetch-api
  []
  (let [; by default Lagotto shows only yesterday's stuff. 
        result @(http-client/get (str (:lagotto-api-base-url env) "/api/deposits") {:query-params {"until_date" (tomorrow-ymd) "per_page" per-page}
                                                                      :headers {"User-Agent" "Crossref Event Data eventdata.crossref.org (labs@crossref.org)"}})
        deposits (get (json/read-str (:body result)) "deposits")]

    deposits))

(defn fetch-new
  []
  (let [deposits (fetch-api)
        new-deposits (rememberizer/filter-new-events deposits)]
    (l/info "Fetched deposits. Total:" (count deposits) " New:" (count new-deposits))
    new-deposits))

(defn tick
  "Update everything. Should be run on a one-minute or so schedule."
  []
  (l/info "Tick...")
  (with-open [connection (redis/get-connection)]
    (let [new-items (fetch-new)]
      (l/info "Got" (count new-items) "new")
      (doseq [item new-items]
        (let [timestamp-iso8601 (get item "timestamp")
              timestamp-yyyy-mm-dd (.substring timestamp-iso8601 0 10)
              timestamp-key (str "live-stream__events-" timestamp-yyyy-mm-dd)
              two-days-hence (quot (clj-time-coerce/to-long
                                     (clj-time/plus (clj-time-coerce/from-string timestamp-iso8601)
                                                    (clj-time/days 2)))
                                   1000)
              ; Item should be re-assembled exactly as we got it.
              ; NB it was parsed to have string keys not symbols so there's no mucking aobut with dashes.
              item-str (json/write-str item)]

          ; Push to the end of today's log then broadcast to listeners.
          ; Do in this order so there's no race condition if a user somehow connects during that period.
          (.rpush connection timestamp-key (into-array [item-str]))
          (server/broadcast item)

          ; Expire the bucket two days from the time of the last event that was put into it.
          (.expireAt connection timestamp-key two-days-hence)))))
  (l/info "Tock."))

(defn run
  "Run forever."
  []
  ; Sleep between queries rather than do it on a schedule.
  ; This doesn't need to be precise, but we don't want race conditions if a particular query takes a very long time to run.
  (loop []
    (tick)
    (l/info "Snoozing...")
    (Thread/sleep (* 1000 30))
    (recur)))