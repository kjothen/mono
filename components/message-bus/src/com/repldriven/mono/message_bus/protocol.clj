(ns com.repldriven.mono.message-bus.protocol (:refer-clojure :exclude [send]))

;; `opts` is per-send, backend-specific, and optional: a backend that has
;; nothing to do with a given key ignores it. `:key` is the one every
;; partitioned backend understands — records sharing a key share a
;; partition, which is the only ordering guarantee Kafka or Pulsar offer.
(defprotocol Producer
  (send [this message]
        [this message opts]))

(defprotocol Consumer
  (subscribe [this handler-fn])
  (unsubscribe [this]))
