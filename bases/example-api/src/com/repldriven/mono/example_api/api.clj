(ns com.repldriven.mono.example-api.api
  (:require
    [com.repldriven.mono.example-bookmark.interface
     :as bookmark]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.server.interface :as server]

    [reitit.http :as http]
    [reitit.ring :as ring]))

(defn- create
  [request]
  (let [{:keys [bookmark-store parameters]} request
        {:keys [body]} parameters
        result (bookmark/create bookmark-store body)]
    (if (error/anomaly? result)
      {:status 500
       :body {:message (:message result)}}
      {:status 201
       :body result})))

(defn- find-by-id
  [request]
  (let [{:keys [bookmark-store parameters]} request
        {:keys [path]} parameters
        {:keys [id]} path
        result (bookmark/find-by-id bookmark-store id)]
    (cond
     (error/anomaly? result)
     {:status 500
      :body {:message (:message result)}}

     (nil? result)
     {:status 404
      :body {:message "Bookmark not found"}}

     :else
     {:status 200
      :body result})))

(defn- list-bookmarks
  [request]
  (let [{:keys [bookmark-store parameters]} request
        {:keys [query]} parameters
        {:keys [tag]} query]
    (if tag
      (let [result (bookmark/find-by-tag bookmark-store tag)]
        (if (error/anomaly? result)
          {:status 500
           :body {:message (:message result)}}
          {:status 200
           :body result}))
      {:status 200
       :body []})))

(defn- routes
  [ctx]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi {:info {:title "Bookmarks"
                            :description "Bookmarks API"
                            :version "1.0.0"}}
           :handler (server/standard-openapi-handler)}}]
   ["/api" {:interceptors (:interceptors ctx)}
    ["/bookmarks"
     {:get {:parameters
            {:query [:map
                     [:tag {:optional true}
                      string?]]}
            :responses
            {200 {:body
                  [:vector bookmark/Bookmark]}}
            :handler list-bookmarks}
      :post {:parameters
             {:body bookmark/CreateBookmark}
             :responses
             {201 {:body bookmark/Bookmark}}
             :handler create}}]
    ["/bookmarks/{id}"
     {:get {:parameters
            {:path [:map [:id string?]]}
            :responses
            {200 {:body bookmark/Bookmark}}
            :handler find-by-id}}]]])

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx)
                                  server/standard-router-data)
                     (ring/routes (server/standard-openapi-ui-handler)
                                  (ring/create-default-handler))
                     server/standard-executor))
