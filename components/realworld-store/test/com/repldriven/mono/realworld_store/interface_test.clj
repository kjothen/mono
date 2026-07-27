(ns ^:eftest/synchronized com.repldriven.mono.realworld-store.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface ;; extends
                                                 ;; `system/components`
    com.repldriven.mono.migrator.interface

    [com.repldriven.mono.realworld-store.interface :as SUT]

    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.jdbc.interface :as jdbc]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.test :refer [deftest is testing]]))

(def ^:private tables
  #{"users" "follows" "articles" "tags" "article_tags" "favorites" "comments"})

(deftest schema-test
  (testing "the store starts with a migrated schema behind it"
    ;; The store refs migrator.migrations rather than jdbc.datasource, and
    ;; that reference is the only thing ordering schema creation before the
    ;; first query. If it were pointed at jdbc directly this would pass or
    ;; fail depending on start order.
    (with-test-system
     [sys "classpath:realworld-store/application-test.yml"]
     (let [store (system/instance sys [:realworld :store])
           {:keys [datasource]} store]
       (is (some? datasource))
       (is (some? (:signer store)))
       (testing "every table in the changelog exists"
         (let [present (->> (jdbc/execute! datasource
                                           [(str
                                             "select table_name from"
                                             " information_schema.tables"
                                             " where table_schema = 'public'")])
                            (map :table-name)
                            (set))]
           (is (empty? (remove present tables))
               (str "missing: " (remove present tables)))))
       (testing "the composite keys that make follow and favorite idempotent"
         ;; Inserting the same pair twice must be a no-op rather than a
         ;; duplicate row, which is what lets the write path use
         ;; `on conflict do nothing` instead of read-then-write.
         (let [pk (fn [table]
                    (->> (jdbc/execute! datasource
                                        [(str "select a.attname as col"
                                              " from pg_index i"
                                              " join pg_attribute a"
                                              "   on a.attrelid = i.indrelid"
                                              "  and a.attnum = any(i.indkey)"
                                              " where i.indrelid = ?::regclass"
                                              "   and i.indisprimary") table])
                         (map :col)
                         (set)))]
           (is (= #{"follower_id" "followed_id"} (pk "follows")))
           (is (= #{"user_id" "article_id"} (pk "favorites")))
           (is (= #{"article_id" "tag_id"} (pk "article_tags")))))))))

(defn- unique
  "A suffix so a test's fixtures cannot collide with another's, since the
  container is shared across the namespace."
  []
  (subs (str (random-uuid)) 24))

(defn- with-store
  [f]
  (with-test-system
   [sys "classpath:realworld-store/application-test.yml"]
   (f (system/instance sys [:realworld :store]))))

(deftest register-test
  (with-store
   (fn [store]
     (let [u (unique)]
       (testing "a registered user comes back with a hashed password"
         (let [row (SUT/register store
                                 {:username (str "jake" u)
                                  :email (str "jake" u "@test.com")
                                  :password "password123"})]
           (is (int? (:id row)))
           (is (= (str "jake" u) (:username row)))
           (is (not= "password123" (:password-hash row))
               "the plaintext must never be stored")
           (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z"
                           (:created-at row))
               "timestamps are pre-formatted for the wire")))
       (testing "a duplicate username names username, not email"
         ;; Which field clashed has to be named, and a 23505
         ;; anomaly cannot say — hence the explicit check before
         ;; insert.
         (let [dup (SUT/register store
                                 {:username (str "jake" u)
                                  :email (str "other" u "@test.com")
                                  :password "password123"})]
           (is (error/rejection? dup))
           (is (= :realworld/username-taken (error/kind dup)))))
       (testing "a duplicate email names email"
         (let [dup (SUT/register store
                                 {:username (str "other" u)
                                  :email (str "jake" u "@test.com")
                                  :password "password123"})]
           (is (= :realworld/email-taken (error/kind dup)))))))))

(deftest authenticate-test
  (with-store
   (fn [store]
     (let [u (unique)
           email (str "auth" u "@test.com")
           row (SUT/register store
                             {:username (str "auth" u)
                              :email email
                              :password "password123"})]
       (testing "the right password authenticates"
         (is (= (:id row) (:id (SUT/authenticate store email "password123")))))
       (testing "an unknown email and a wrong password fail identically"
         ;; Distinguishing them would tell an attacker which emails exist.
         (let [wrong (SUT/authenticate store email "wrongpassword")
               unknown (SUT/authenticate store
                                         (str "nobody" u "@test.com")
                                         "password123")]
           (is (= :realworld/credentials-invalid (error/kind wrong)))
           (is (= :realworld/credentials-invalid (error/kind unknown)))))
       (testing "a token identifies the user by id, so a rename cannot break it"
         (let [t (SUT/token store row)]
           (is (string? t))
           (is (= (str (:id row))
                  (:sub (auth/verify-token (:signer store) t))))))))))

(deftest update-user-test
  (with-store
   (fn [store]
     (let [u (unique)
           row (SUT/register store
                             {:username (str "upd" u)
                              :email (str "upd" u "@test.com")
                              :password "password123"})]
       (testing "only the keys present are changed"
         (let [updated
               (SUT/update-user store (:id row) {:bio "I work at statefarm"})]
           (is (= "I work at statefarm" (:bio updated)))
           (is (= (:username row) (:username updated))
               "an absent key must be left alone, not nulled")))
       (testing "bio can be set back to null explicitly"
         (let [updated (SUT/update-user store (:id row) {:bio nil})]
           (is (nil? (:bio updated)))))
       (testing "a password change re-hashes and the old one stops working"
         (let [_ (SUT/update-user store (:id row) {:password "newpassword1"})
               email (:email row)]
           (is (error/rejection? (SUT/authenticate store email "password123")))
           (is (some? (:id (SUT/authenticate store email "newpassword1"))))))
       (testing
         "taking another user's username is rejected, but keeping your own is not"
         (let [other (SUT/register store
                                   {:username (str "other" u)
                                    :email (str "other" u "@test.com")
                                    :password "password123"})
               clash
               (SUT/update-user store (:id row) {:username (:username other)})
               same
               (SUT/update-user store (:id row) {:username (:username row)})]
           (is (= :realworld/username-taken (error/kind clash)))
           (is (= (:username row) (:username same))
               "excluding self is what makes a no-op rename succeed")))))))

(deftest profile-and-follow-test
  (with-store
   (fn [store]
     (let [u (unique)
           me (SUT/register store
                            {:username (str "me" u)
                             :email (str "me" u "@test.com")
                             :password "password123"})
           them (SUT/register store
                              {:username (str "them" u)
                               :email (str "them" u "@test.com")
                               :password "password123"})]
       (testing "an anonymous viewer sees following false, not an error"
         (let [p (SUT/profile store (:username them) nil)]
           (is (= (:username them) (:username p)))
           (is (false? (:following p)))))
       (testing "following is reflected and is idempotent"
         (is (true? (:following (SUT/follow store (:id me) (:username them)))))
         (is (true? (:following (SUT/follow store (:id me) (:username them))))
             "following twice is a no-op, not a duplicate-key error")
         (is (true? (:following
                     (SUT/profile store (:username them) (:id me))))))
       (testing "unfollowing is also idempotent"
         (is (false? (:following
                      (SUT/unfollow store (:id me) (:username them)))))
         (is (false? (:following
                      (SUT/unfollow store (:id me) (:username them))))))
       (testing "an unknown username is a not-found rejection"
         (is (= :realworld/profile-not-found
                (error/kind (SUT/follow store (:id me) (str "ghost" u)))))
         (is (nil? (SUT/profile store (str "ghost" u) nil))))))))