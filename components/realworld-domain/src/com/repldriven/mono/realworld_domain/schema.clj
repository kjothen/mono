(ns com.repldriven.mono.realworld-domain.schema
  "Request schemas, in wire shape.

  Every RealWorld body is wrapped in a single key — `{\"user\": {...}}`,
  `{\"article\": {...}}` — and muuntaja decodes with keyword keys, so the
  schemas are written in the API's own camelCase (`:tagList`) rather than in
  Clojure's kebab-case. Translation to column names happens in the store.

  Required-versus-optional carries meaning beyond validation here. On an
  update, an absent key means leave alone while a present one means change,
  so every update field is optional and `contains?` is what the store
  branches on. `tagList` is the sharp case: absent preserves the tags, `[]`
  clears them, and `null` is rejected.")

(def NonBlank
  "A string that must be present and non-empty.

  `nil` and `\"\"` both fail, and both report as `can't be blank` — see
  `errors/coercion->response`, which does not distinguish them."
  [:string {:min 1 :error/message "can't be blank"}])

(def Password
  "A password, at NIST 800-63B's 8-character minimum.

  The suite asserts this on `PUT /api/user` only, but it is applied to
  registration too: no fixture registers with fewer than 8 characters, and a
  policy that admits a password it will not later accept is not a policy."
  [:string {:min 8 :error/message "is too short (minimum is 8 characters)"}])

(def Nullable
  "An optional string that may be explicitly null. `bio` and `image` are
  both nullable in the wire format, and `\"\"` normalises to null on write."
  [:maybe :string])

(def Register
  [:map
   [:user
    [:map
     [:username NonBlank]
     [:email NonBlank]
     [:password Password]]]])

(def Login
  ;; Password is NonBlank rather than Password: a login carrying a password
  ;; that is merely too short is a failed credential, not a malformed
  ;; request, and the suite wants 401 `credentials invalid` for it.
  [:map
   [:user
    [:map
     [:email NonBlank]
     [:password NonBlank]]]])

(def UpdateUser
  [:map
   [:user
    [:map
     [:username {:optional true} NonBlank]
     [:email {:optional true} NonBlank]
     [:password {:optional true} Password]
     [:bio {:optional true} Nullable]
     [:image {:optional true} Nullable]]]])

(def CreateArticle
  [:map
   [:article
    [:map
     [:title NonBlank]
     [:description NonBlank]
     [:body NonBlank]
     [:tagList {:optional true} [:vector :string]]]]])

(def UpdateArticle
  [:map
   [:article
    [:map
     [:title {:optional true} NonBlank]
     [:description {:optional true} NonBlank]
     [:body {:optional true} NonBlank]
     [:tagList {:optional true} [:vector :string]]]]])

(def AddComment [:map [:comment [:map [:body NonBlank]]]])
