(ns com.repldriven.mono.env.reader.edn
  (:require
    [com.repldriven.mono.utility.interface :as util]

    [aero.core :as aero])
  (:import
    (java.net ServerSocket)))

;; edn-reader multimethod (extends aero/reader)
(def edn-reader aero/reader)

(defmethod aero/reader 'port
  [_ _ value]
  (if (zero? value)
    (with-open [socket (ServerSocket. 0)] (.getLocalPort socket))
    value))

;; Generates a v7, matching `utility/uuidv7` — time-ordered, so generated
;; values sort by creation. The value is ignored; EDN has no zero-argument
;; tagged literal, so this is written `#random-uuid nil`. A generated value is
;; new on every read of the config, so it names a run, not a thing to be
;; referred back to. `#uuid "0192..."` is untouched and still parses.
(defmethod aero/reader 'random-uuid [_ _ _] (util/uuidv7))

(defn read-config
  [source profile]
  (aero/read-config (util/resolve-source source) {:profile profile}))

(defn config [source profile] (read-config source profile))
