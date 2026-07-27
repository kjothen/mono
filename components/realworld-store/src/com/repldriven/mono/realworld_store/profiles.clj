(ns com.repldriven.mono.realworld-store.profiles
  (:require
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.jdbc.interface :as jdbc]))

(defn by-username
  "Profile row, with `following` relative to `viewer-id`.

  `viewer-id` may be nil for an anonymous caller: the join then matches
  nothing and `following` is false, which is what the API wants — no
  separate anonymous query."
  [ds username viewer-id]
  (jdbc/execute-one!
   ds
   [(str "select u.username, u.bio, u.image,"
         "       (f.follower_id is not null) as following"
         "  from users u"
         "  left join follows f"
         "    on f.followed_id = u.id"
         "   and f.follower_id = ?::bigint"
         " where u.username = ?")
    viewer-id username]))

(defn- with-target
  [ds username f]
  (let-nom>
    [target (jdbc/execute-one! ds
                               ["select id from users where username = ?"
                                username])]
    (if-not target
      (error/reject :realworld/profile-not-found "not found")
      (f (:id target)))))

(defn follow
  "Follow `username`. Idempotent: the composite primary key makes a repeat
  a no-op rather than something to check for first."
  [ds follower-id username]
  (jdbc/with-transaction
   [tx ds nil]
   (with-target tx
                username
                (fn [target-id]
                  (let-nom>
                    [_ (jdbc/execute-one!
                        tx
                        [(str "insert into follows (follower_id, followed_id)"
                              " values (?, ?) on conflict do nothing")
                         follower-id target-id])]
                    (by-username tx username follower-id))))))

(defn unfollow
  "Unfollow `username`. Also idempotent — deleting nothing is success."
  [ds follower-id username]
  (jdbc/with-transaction
   [tx ds nil]
   (with-target tx
                username
                (fn [target-id]
                  (let-nom>
                    [_ (jdbc/execute-one!
                        tx
                        [(str "delete from follows"
                              " where follower_id = ? and followed_id = ?")
                         follower-id target-id])]
                    (by-username tx username follower-id))))))
