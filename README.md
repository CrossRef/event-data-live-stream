# Event Data Live Stream

A live stream of events from Event Data. Currently experimental.

Connect to a websocket to receive events, as they happen, if they happen. Events are sent as they are collected, but there is no specific guarantee that they will be receieved in order or time resolution. 

Currently polls the Lagotto API for new events.

## To Run

    lein with-profile dev run server

Requires config keys:

    :lagotto-api-base-url e.g. "http://api.eventdata.crossref.org"
    :redis-db-number e.g. "5"
    :redis-host e.g. "127.0.0.1"
    :redis-port e.g. "6379"

## To consume

Connect to a websocket at:

    http://«host»/socket

The following GET parameters are supported:

    source_id=«source-id»

Filter for only the given source id. You can find the list of source-ids in the Event Data Technical User Guide.

The websocket interface is very simple.

  1. Set up a connection to "http://live.eventdata.crossref.org/socket"
  2. When you are ready to receive events send the string `start`
  3. To fetch up to the last 24 hours of data, send `catchup «iso8601-date»`, e.g. `catchup 2016-06-23T15:06Z`. If you want everything you can send `catchup 0`, and you will get at least 24 hours worth of data.

## License

Copyright © 2016 Crossref

Distributed under the MIT License.
