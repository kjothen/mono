(ns com.repldriven.mono.example-bookmark.interface
  (:require
    com.repldriven.mono.example-bookmark.system

    [com.repldriven.mono.example-bookmark.domain :as domain]
    [com.repldriven.mono.example-bookmark.store :as store]
    [com.repldriven.mono.example_schemas.bookmarks :as bookmarks]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.example_schemas.bookmarks
     BookmarkProto$Bookmark)))

(def pb->Bookmark bookmarks/pb->Bookmark)

(defn Bookmark->pb
  [m]
  (proto/->pb (bookmarks/new-Bookmark m)))

(defn Bookmark->java
  [m]
  (BookmarkProto$Bookmark/parseFrom (Bookmark->pb m)))

(defn create
  [config data]
  (store/create config data))

(defn find-by-id
  [config bookmark-id]
  (store/find-by-id config bookmark-id))

(defn find-by-tag
  [config tag]
  (store/find-by-tag config tag))

(def CreateBookmark domain/CreateBookmark)

(def Bookmark domain/Bookmark)
