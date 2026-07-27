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
