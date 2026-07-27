(ns com.repldriven.mono.realworld-api.command
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.realworld-domain.interface :as domain]))

(defn send
  "Dispatch a write and wait for its reply.

  The envelope is built from the request, so idempotency and correlation
  ids flow through from the caller's headers."
  [request name payload]
  (let [{:keys [commands]} request]
    (command/send commands
                  (assoc (command/req->command-request request name)
                         :payload
                         payload))))

(defn response
  "A command reply as a Ring response.

  A REJECTED reply carries only the string form of its anomaly kind, which
  is enough: every write rejection in this API is a fixed field-and-message
  pair, so the kind selects the whole response. An unrecognised one is a
  fault rather than a client error — it means a kind was raised that the
  response table does not know about.

  Args:
  - envelope: the reply from `send`.
  - ok-status: the status to use on success.
  - render: turns the ACCEPTED payload into a body. Omit for 204."
  ([envelope ok-status] (response envelope ok-status nil))
  ([envelope ok-status render]
   (let [{:keys [status payload reason message]} envelope]
     (cond
      (error/anomaly? envelope)
      {:status 500 :body {:errors {:server [(str (error/kind envelope))]}}}

      (= "ACCEPTED" status)
      (if render {:status ok-status :body (render payload)} {:status ok-status})

      :else
      (or (domain/reason->response reason)
          {:status 500 :body {:errors {:server [(or message "failed")]}}})))))
