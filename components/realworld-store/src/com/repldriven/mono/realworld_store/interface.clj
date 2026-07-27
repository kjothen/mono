(ns com.repldriven.mono.realworld-store.interface
  "Postgres persistence for the RealWorld example.

  Holds all the SQL, reads and writes alike, because every interesting read
  spans what would otherwise be separate aggregates: an article listing
  needs the author, the tags, whether the caller favorited it, how many
  others did, and whether the caller follows the author — one statement, or
  an N+1. Splitting by aggregate would have meant either that N+1 or one
  brick issuing SQL against another's tables, which `poly check` cannot see.

  Wire shaping is not done here. Rows come back in the workspace's
  kebab-case convention and `realworld-domain` turns them into RealWorld
  bodies, which is what keeps the response contract testable without a
  database."
  (:require
    com.repldriven.mono.realworld-store.system

    [com.repldriven.mono.realworld-store.articles :as article-store]
    [com.repldriven.mono.realworld-store.comments :as comment-store]
    [com.repldriven.mono.realworld-store.profiles :as profiles]
    [com.repldriven.mono.realworld-store.users :as users]))

;; --- users ---------------------------------------------------------------

(defn register
  "Create a user, or reject with `:realworld/username-taken` or
  `:realworld/email-taken` naming the field that clashed.

  Args:
  - store: a `realworld-store/store` instance.
  - data: `{:username :email :password}`, already validated."
  [store data]
  (users/create (:datasource store) data))

(defn authenticate
  "The user for these credentials, or a `:realworld/credentials-invalid`
  rejection — the same one whether the email is unknown or the password is
  wrong.

  Args:
  - store: a `realworld-store/store` instance.
  - email: the email offered.
  - password: the plaintext password offered."
  [store email password]
  (users/authenticate (:datasource store) email password))

(defn user
  "The user row for an id, or nil.

  Args:
  - store: a `realworld-store/store` instance.
  - id: the user id, as it appears in a token's `:sub`."
  [store id]
  (users/by-id (:datasource store) id))

(defn update-user
  "Patch the fields present in `data`, leaving absent keys alone.

  Args:
  - store: a `realworld-store/store` instance.
  - id: the user to patch.
  - data: only the keys the request actually carried."
  [store id data]
  (users/update-user (:datasource store) id data))

(defn token
  "A signed token identifying `row`.

  The claim is the user id rather than the username, so a token survives a
  username change — which the suite exercises.

  Args:
  - store: a `realworld-store/store` instance.
  - row: a user row."
  [store row]
  (users/token (:signer store) row))

;; --- profiles ------------------------------------------------------------

(defn profile
  "A profile, with `following` relative to `viewer-id` (which may be nil).

  Args:
  - store: a `realworld-store/store` instance.
  - username: whose profile.
  - viewer-id: the caller's user id, or nil when anonymous."
  [store username viewer-id]
  (profiles/by-username (:datasource store) username viewer-id))

(defn follow
  "Follow `username`, idempotently. Returns the resulting profile, or a
  `:realworld/profile-not-found` rejection.

  Args:
  - store: a `realworld-store/store` instance.
  - follower-id: the caller's user id.
  - username: whom to follow."
  [store follower-id username]
  (profiles/follow (:datasource store) follower-id username))

(defn unfollow
  "Unfollow `username`, idempotently. Returns the resulting profile, or a
  `:realworld/profile-not-found` rejection.

  Args:
  - store: a `realworld-store/store` instance.
  - follower-id: the caller's user id.
  - username: whom to unfollow."
  [store follower-id username]
  (profiles/unfollow (:datasource store) follower-id username))

;; --- articles ------------------------------------------------------------

(defn article
  "One article, or nil. `viewer-id` may be nil, giving `favorited` and
  `following` false.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: the article slug.
  - viewer-id: the caller's user id, or nil."
  [store slug viewer-id]
  (article-store/by-slug (:datasource store) slug viewer-id))

(defn articles
  "A filtered page as `{:rows [...] :total n}`, where `total` counts all
  matches rather than this page.

  Args:
  - store: a `realworld-store/store` instance.
  - params: `{:tag :author :favorited :limit :offset}`, all optional.
  - viewer-id: the caller's user id, or nil."
  [store params viewer-id]
  (article-store/find-articles (:datasource store) params viewer-id))

(defn feed
  "A page of articles by the people `viewer-id` follows.

  Args:
  - store: a `realworld-store/store` instance.
  - viewer-id: the caller's user id.
  - limit, offset: paging, both may be nil."
  [store viewer-id limit offset]
  (article-store/feed (:datasource store) viewer-id limit offset))

(defn create-article
  "Create an article and return it. The caller supplies the slug, since
  slugs come from `realworld-domain`.

  Args:
  - store: a `realworld-store/store` instance.
  - data: `{:author-id :slug :title :description :body :tagList}`."
  [store data]
  (article-store/create (:datasource store) data))

(defn update-article
  "Patch an article. `tagList` absent leaves tags alone; present replaces
  them, empty list included. Rejects with not-found or forbidden.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: which article.
  - author-id: the caller, who must own it.
  - data: only the keys the request carried."
  [store slug author-id data]
  (article-store/update-article (:datasource store) slug author-id data))

(defn delete-article
  "Delete an article and, by cascade, its tags, favorites and comments.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: which article.
  - author-id: the caller, who must own it."
  [store slug author-id]
  (article-store/delete-article (:datasource store) slug author-id))

(defn favorite
  "Favorite an article, idempotently, returning it.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: which article.
  - user-id: the caller."
  [store slug user-id]
  (article-store/favorite (:datasource store) slug user-id))

(defn unfavorite
  "Unfavorite an article, idempotently, returning it.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: which article.
  - user-id: the caller."
  [store slug user-id]
  (article-store/unfavorite (:datasource store) slug user-id))

;; --- comments and tags ---------------------------------------------------

(defn comments
  "An article's comments, oldest first, or a not-found rejection.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: which article.
  - viewer-id: the caller's user id, or nil."
  [store slug viewer-id]
  (comment-store/for-article (:datasource store) slug viewer-id))

(defn create-comment
  "Add a comment and return it.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: which article.
  - author-id: the caller.
  - body: the comment text."
  [store slug author-id body]
  (comment-store/create (:datasource store) slug author-id body))

(defn delete-comment
  "Delete one of the caller's comments. Rejects with not-found or forbidden.

  Args:
  - store: a `realworld-store/store` instance.
  - slug: which article.
  - comment-id: which comment.
  - author-id: the caller, who must own it."
  [store slug comment-id author-id]
  (comment-store/delete-comment (:datasource store) slug comment-id author-id))

(defn tags
  "Tag names in use, alphabetically.

  Args:
  - store: a `realworld-store/store` instance."
  [store]
  (article-store/tags (:datasource store)))
