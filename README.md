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

The websocket interface is very simple. Set up a connection and when you are ready to receive events send the string "start". From that point on, events will be sent. If you want to catch up the last calendar day (i.e. all events since the start of yesterday), send "catchup" and all those events will be sent.

## License

Copyright © 2016 Crossref

Distributed under the MIT License.
