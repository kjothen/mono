(ns com.repldriven.mono.kafka.kafka.config
  "Turning a config map into the java.util.Properties Kafka wants.

  Kafka takes configuration as string-keyed properties — `bootstrap.servers`,
  `group.id` — rather than through a builder, so a YAML map reaches the client
  almost unchanged. Keys are stringified without munging: write them exactly as
  Kafka documents them, dots and all.")

(defn- ->string-keys
  [config]
  (into {} (map (fn [[k v]] [(name k) v])) config))

(defn ->properties
  "Merge `configs` left to right into Properties, later ones winning.

  Keys are stringified before merging, not after: YAML gives keywords and
  the defaults here are strings, so merging first would leave both in the
  map and let map order decide which one reached Kafka."
  ^java.util.Properties [& configs]
  (let [props (java.util.Properties.)]
    (doseq [[k v] (apply merge (map ->string-keys configs))]
      (.put props k (if (keyword? v) (name v) (str v))))
    props))
