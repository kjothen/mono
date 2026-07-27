(ns com.repldriven.mono.testcontainers.system.components.fdb
  (:require
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.net ServerSocket)
    (java.time Duration)
    (org.testcontainers.containers FixedHostPortGenericContainer)
    (org.testcontainers.containers.wait.strategy Wait)
    (org.testcontainers.images.builder ImageFromDockerfile)))

(def fdb-version "7.4.6")
(def default-image-name (str "mono/foundationdb:" fdb-version))

(defn- fdb-image
  "Build context for the FDB image, loaded from this component's own resources.

  Read from the classpath rather than from a path relative to the workspace
  root, so the component is self-contained when consumed as a library. A
  consuming workspace has no infra directory of ours to find.

  `fdb-version` is passed as a build arg rather than defaulted in the
  Dockerfile, so the server version has one definition in this component
  instead of two that can drift. A test asserts it still matches
  versions.json, which is what the client is built from."
  [image-name]
  (-> (ImageFromDockerfile. image-name false)
      (.withFileFromClasspath "Dockerfile" "fdb/Dockerfile")
      (.withFileFromClasspath "fdb.bash" "fdb/fdb.bash")
      (.withBuildArg "FDB_VERSION" fdb-version)))

(defn- free-port
  "Finds a free port by briefly opening and closing a server socket."
  []
  (with-open [ss (ServerSocket. 0)] (.getLocalPort ss)))

(defn- start-container
  [config]
  (let [{:keys [image-name]} config
        _ (log/info "Building FDB image:" image-name)
        built-name (.get (fdb-image image-name))
        port (free-port)
        _ (log/info "Starting FDB container, image:" built-name "port:" port)]
    (doto (FixedHostPortGenericContainer. ^String built-name)
      (.withFixedExposedPort (int port) (int port))
      (.addExposedPort (int port))
      (.withEnv "FDB_PORT" (str port))
      (.withStartupTimeout (Duration/ofSeconds 120))
      (.waitingFor (Wait/forListeningPort))
      (.start))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping FDB container")
                  (when (some? instance) (.stop instance)))
   :system/config {:image-name default-image-name}
   :system/config-schema [:map [:image-name string?]]
   :system/instance-schema some?})
