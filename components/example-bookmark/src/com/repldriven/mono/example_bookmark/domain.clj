(ns com.repldriven.mono.example-bookmark.domain)

(def CreateBookmark
  [:map
   [:url string?]
   [:title string?]
   [:tags [:vector string?]]])

(def Bookmark
  [:map
   [:bookmark-id string?]
   [:url string?]
   [:title string?]
   [:tags [:vector string?]]])
