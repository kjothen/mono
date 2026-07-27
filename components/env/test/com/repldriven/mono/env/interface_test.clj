(ns com.repldriven.mono.env.interface-test
  (:require
    [com.repldriven.mono.env.interface :as SUT]
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
  (let [config (SUT/config "classpath:env/long-test.yml" :default)]
    (testing "!long coerces, including a quoted scalar YAML reads as a string"
      (is (= 8091 (get-in config [:system :from-literal])))
      (is (= 8091 (get-in config [:system :from-quoted])))
      (is (instance? Long (get-in config [:system :from-quoted]))))
    (testing "!long wraps !or, so an env var can have a default and a type"
      ;; The coercion has to be outside the or: aero throws parsing "" if
      ;; it is inside, before the fallback is reached.
      (is (= 8080 (get-in config [:system :from-env])))
      (is (instance? Long (get-in config [:system :from-env]))))
    (testing "!or alone falls back without coercing"
      (is (= "fallback" (get-in config [:system :or-alone]))))))
