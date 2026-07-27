(ns com.repldriven.mono.realworld-store.processor
  (:require
    [com.repldriven.mono.realworld-store.articles :as articles]
    [com.repldriven.mono.realworld-store.comments :as comments]
    [com.repldriven.mono.realworld-store.profiles :as profiles]
    [com.repldriven.mono.realworld-store.users :as users]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]))

;; Every write goes through here, addressed by command name.
;;
;; Rejections must be built with `error/reject` and never with
;; `error/unauthorized`. `command/command-response` branches on
;; `rejection?` then `error?` and treats anything else as success, so an
;; `:unauthorized/anomaly` would be reported to the caller as ACCEPTED with
;; a null payload. Authorisation failures here are ordinary rejections
;; carrying `:realworld/article-forbidden` and friends.
;;
;; Payloads are plain Clojure data because the bus is a local one. Nothing
;; is serialised, so unlike the Pulsar example there is no schema step.
;;
;; Rows are returned as rows. Turning them into RealWorld bodies is
;; `realworld-domain`'s job, which is what keeps the wire contract testable
;; without a database.

(defn- accepted
  [result]
  (if (error/anomaly? result) result {:status "ACCEPTED" :payload result}))

(defn- dispatch
  [ds data command]
  (let [{:keys [actor-id slug username comment-id]} data]
    (case command
      "register" (users/create ds data)
      "update-user" (users/update-user ds actor-id (:patch data))
      "follow" (profiles/follow ds actor-id username)
      "unfollow" (profiles/unfollow ds actor-id username)
      "create-article" (articles/create ds data)
      "update-article" (articles/update-article ds slug actor-id (:patch data))
      "delete-article" (articles/delete-article ds slug actor-id)
      "favorite" (articles/favorite ds slug actor-id)
      "unfavorite" (articles/unfavorite ds slug actor-id)
      "create-comment" (comments/create ds slug actor-id (:body data))
      "delete-comment" (comments/delete-comment ds slug comment-id actor-id)
      (error/reject :realworld/unknown-command
                    (str "Unknown command: " command)))))

(defrecord RealworldProcessor [config]
  processor/Processor
    (process [_ message]
      (let [{:keys [command payload]} message
            {:keys [store]} config]
        (accepted (dispatch (:datasource store) payload command)))))
