(ns com.repldriven.mono.test-telemetry.core
  "Reading spans back off an in-memory telemetry instance."
  (:import
    (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)))

(defn- in-memory-exporter
  ^InMemorySpanExporter [instance]
  (let [exporter (:exporter instance)]
    (when (instance? InMemorySpanExporter exporter) exporter)))

(defn finished-spans
  "Spans an in-memory telemetry instance has collected so far.

  Returns a vector of `SpanData`, or nil when the instance is not
  collecting in memory."
  [instance]
  (when-let [exporter (in-memory-exporter instance)]
    (vec (.getFinishedSpanItems exporter))))

(defn clear-spans!
  "Discard the spans an in-memory telemetry instance has collected.

  No-op when the instance is not collecting in memory."
  [instance]
  (when-let [exporter (in-memory-exporter instance)] (.reset exporter)))
