(ns com.repldriven.mono.testcontainers.fdb-version-test
  "Guards the one toolchain version that versions.json cannot reach.

  Everything else built from versions.json — the native client in flake.nix,
  the client library in CI, a generated workspace's flake — moves in one edit.
  This component cannot read it: it ships in mono-test-lib, and a consuming
  workspace has no versions.json of ours to find, so the server version is
  declared here instead.

  FDB requires a compatible protocol version between client and cluster, so a
  mismatch between the two is not a cosmetic inconsistency: tests would fail
  at connect time with an error that says nothing about versions."
  (:require
    [com.repldriven.mono.testcontainers.system.components.fdb :as fdb]

    [com.repldriven.mono.json.interface :as json]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]))

(deftest fdb-version-test
  (testing "the testcontainers FDB server version matches versions.json"
    (let [f (io/file "versions.json")]
      (is (.exists f)
          (str "versions.json not found at " (.getAbsolutePath f)
               " — this test reads it relative to the workspace root"))
      (when (.exists f)
        (let [declared (-> (slurp f)
                           (json/read-str)
                           (get-in ["foundationdb" "version"]))]
          (is (= declared fdb/fdb-version)
              (str "fdb-version in the testcontainers component is "
                   fdb/fdb-version
                   " but versions.json declares "
                   declared
                   ". The FDB client and the testcontainers server share a "
                   "protocol version and must be bumped together.")))))))
