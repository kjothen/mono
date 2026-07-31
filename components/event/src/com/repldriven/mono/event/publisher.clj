(ns com.repldriven.mono.event.publisher
  (:require
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility]))

(defn envelope
  [event-name causation-id correlation-id]
  {:id (str (utility/uuidv7))
   :correlation-id correlation-id
   :event event-name
   :payload nil
   :causation-id causation-id
   :traceparent (telemetry/inject-traceparent)
   :tracestate nil})

;; `:event-channel` is this namespace's; everything else in opts is the
;; bus's and goes through untouched. A key is never derived from the
;; envelope — causation-id records what caused an event, which is not the
;; same question as which partition it belongs on, and a publisher that
;; conflated the two would key events the caller never meant to order.
(defn publish
  ([bus envelope] (publish bus envelope {}))
  ([bus envelope opts]
   (let [{:keys [event-channel]
          :or {event-channel :event}}
         opts]
     (message-bus/send bus
                       event-channel
                       envelope
                       (dissoc opts :event-channel)))))
