(ns com.repldriven.mono.service.system
  (:require
    [com.repldriven.mono.service.pet-processor :as pet-processor]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private pet-processor-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (pet-processor/->PetProcessor config (atom {}))))
   :system/config {:schemas system/required-component}
   :system/instance-schema some?})

(system/defcomponents :pets {:processor pet-processor-component})
