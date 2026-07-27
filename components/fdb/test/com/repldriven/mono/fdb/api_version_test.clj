(ns com.repldriven.mono.fdb.api-version-test
  "The FDB API version is constrained from two directions at once, and neither
  is visible at the call site.

  `FDB/selectAPIVersion` is JVM-global and one-shot, so `db` and `record-db`
  cannot disagree within a process. And the Record Layer expresses far fewer
  versions than the client accepts, so the usable ceiling is set by the
  library rather than by the client that is installed.

  Both failures surface as exceptions from deep inside FDB that name neither
  the config key nor the component. These assertions state the constraints
  where they can be read, and fail if either shifts."
  (:require
    [com.repldriven.mono.fdb.system.components :as components]

    [clojure.test :refer [deftest is testing]])
  (:import
    (com.apple.foundationdb.record RecordCoreArgumentException)
    (com.apple.foundationdb.record.provider.foundationdb APIVersion)))

(deftest api-version-test
  (testing "db and record-db default to the same API version"
    ;; A disagreement fails at start with "FoundationDB API already started at
    ;; different version", and which of the two reports it depends on start
    ;; order, so it is worth pinning here rather than discovering there.
    (is (= components/default-api-version
           (:api-version (:system/config components/db))
           (:api-version (:system/config components/record-db)))))

  (testing "both accept api-version as config"
    (doseq [[component-name component] [["db" components/db]
                                        ["record-db" components/record-db]]]
      (is (contains? (:system/config component) :api-version)
          (str component-name " should take api-version from config"))))

  (testing "the default is a version the Record Layer can express"
    (is (= components/default-api-version
           (.getVersionNumber
            (APIVersion/fromVersionNumber components/default-api-version)))))

  (testing "the Record Layer ceiling is below what a 7.4 client accepts"
    ;; A 7.4 client selects 730 and 740 happily; the Record Layer's APIVersion
    ;; enum stops at API_VERSION_7_1. Raising default-api-version therefore
    ;; waits on the library, not on the installed client.
    (doseq [n [730 740]]
      (is (thrown? RecordCoreArgumentException
                   (APIVersion/fromVersionNumber n))
          (str n " is expected to be beyond the Record Layer's range")))))
