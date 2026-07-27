(ns com.repldriven.mono.env.interface-test
  (:require
    [com.repldriven.mono.env.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :as test :refer [deftest is testing]]))

(deftest edn-test
  (testing
    "A non-zero port number in config is preserved, ie `:port #port 80` -> `:port 80`"
    (let [environment (SUT/config "classpath:env/test-env.edn" :default)
          port (get-in environment [:system :port])]
      (is (= 80 port))))
  (testing
    "A zero port number in config returns an available local port,
            eg `:port #port 0` -> `:port 62457`"
    (let [environment (SUT/config "classpath:env/test-env.edn" :test)
          port (get-in environment [:system :port])]
      (is (and (>= port 1024) (<= port 65535))))))

(deftest yaml-test
  (testing
    "A non-zero port number in config is preserved, ie `:port #port 80` -> `:port 80`"
    (let [environment (SUT/config "classpath:env/application-test.yml" :default)
          port (get-in environment [:system :port])]
      (is (= 80 port))))
  (testing
    "A zero port number in config returns an available local port,
            eg `:port #port 0` -> `:port 62457`"
    (let [environment (SUT/config "classpath:env/application-test.yml" :test)
          port (get-in environment [:system :port])]
      (is (and (>= port 1024) (<= port 65535))))))

(deftest long-tag-test
  (testing "!long coerces to a number, so a setting that needs one gets one"
    (let [config (SUT/config "classpath:env/long-test.yml" :default)]
      (is (= 8091 (get-in config [:system :from-literal])))
      (is (instance? Long (get-in config [:system :from-literal])))
      (is (= 8091 (get-in config [:system :from-quoted]))
          "a quoted scalar is a string to YAML; this is the case worth having")
      (is (instance? Long (get-in config [:system :from-quoted])))))
  (testing "!long-env fails the load when the variable is absent"
    ;; Rather than yielding nil and letting a service start on port 0 or
    ;; with a null timeout. The happy path needs a set environment
    ;; variable, so it is covered by `just realworld-hurl`, which passes
    ;; the port in.
    (is (error/anomaly? (SUT/config "classpath:env/long-env-test.yml"
                                    :default)))))
