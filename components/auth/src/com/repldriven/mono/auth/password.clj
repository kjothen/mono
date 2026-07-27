(ns com.repldriven.mono.auth.password
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]

    [buddy.hashers :as hashers]))

(defn derive-hash
  [plain]
  (if (or (nil? plain) (not (string? plain)) (empty? plain))
    (error/reject :auth/hash-password "Password must be a non-empty string")
    (try-nom :auth/hash-password
             "Failed to hash password"
             (hashers/derive
              plain))))

(defn verify
  [plain hashed]
  (if (or (not (string? plain)) (not (string? hashed)))
    false
    ;; `hashers/verify` throws on a hash string it cannot parse, which is
    ;; an ordinary condition when the stored value is corrupt or was
    ;; written by another system. A malformed hash is not a match.
    (let [result (try-nom :auth/verify-password
                          "Failed to verify password"
                          (hashers/verify plain hashed))]
      (if (error/anomaly? result) false (true? (:valid result))))))
