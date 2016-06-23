(ns event-data-live-stream.redis
  (:import [redis.clients.jedis Jedis JedisPool JedisPoolConfig])
  (:require [config.core :refer [env]]))

(defn make-jedis-pool
  []
  ; Nice big pool in case we have multiple threads trying to interact with multiple queues.
  ; Real chance of deadlock otherwise!
  (let [pool-config (new org.apache.commons.pool2.impl.GenericObjectPoolConfig)]
    (.setMaxTotal pool-config 100)
  (new JedisPool pool-config (:redis-host env) (Integer/parseInt (:redis-port env)))))


(def jedis-pool
  "Jedis pool based on the currently booted context. This must be called after context has booted."
  (delay (make-jedis-pool)))

(defn get-connection
  "Get a Redis connection from the pool. Must be closed."
  []
  (let [resource (.getResource @jedis-pool)]
    (.select resource (Integer/parseInt (:redis-db-number env)))
    resource))