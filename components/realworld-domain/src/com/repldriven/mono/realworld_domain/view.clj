(ns com.repldriven.mono.realworld-domain.view
  "Database rows as RealWorld wire bodies.

  The store returns rows in the workspace's kebab-case keyword convention;
  the API speaks camelCase and wraps everything in a single key. Doing that
  translation here, on plain maps, is what lets the whole error and payload
  contract be tested without a database.

  `article` and `article-summary` differ in one respect that is easy to miss
  and is asserted directly by the suite: a list or feed entry must not carry
  `body` at all. Not null — absent.")

(defn- blank->nil
  [s]
  (when-not (= "" s) s))

(defn profile
  "An author or profile sub-object. `following` is false for an anonymous
  caller, which is why it is a plain boolean rather than nullable."
  [row]
  {:username (:username row)
   :bio (blank->nil (:bio row))
   :image (blank->nil (:image row))
   :following (boolean (:following row))})

(defn profile-body
  [row]
  {:profile (profile row)})

(defn user-body
  "The authentication response. Carries the token, and never the password
  hash — the only place in the API where a user's own email is returned."
  [row token]
  {:user {:email (:email row)
          :token token
          :username (:username row)
          :bio (blank->nil (:bio row))
          :image (blank->nil (:image row))}})

(defn- article-common
  [row]
  {:slug (:slug row)
   :title (:title row)
   :description (:description row)
   :tagList (vec (or (:tag-list row) []))
   :createdAt (:created-at row)
   :updatedAt (:updated-at row)
   :favorited (boolean (:favorited row))
   :favoritesCount (or (:favorites-count row) 0)
   :author (profile {:username (:author-username row)
                     :bio (:author-bio row)
                     :image (:author-image row)
                     :following (:author-following row)})})

(defn article
  "A single article, including `body`."
  [row]
  (assoc (article-common row) :body (:body row)))

(defn article-body
  [row]
  {:article (article row)})

(defn article-summary
  "A list entry. `body` is absent rather than null — the suite asserts the
  key does not exist."
  [row]
  (article-common row))

(defn articles-body
  "A page of articles. `articlesCount` is the total matching the query, not
  the size of this page, so a `limit=1` request over two matches reports
  two."
  [rows total]
  {:articles (mapv article-summary rows)
   :articlesCount (or total (count rows))})

(defn comment-view
  [row]
  {:id (:id row)
   :createdAt (:created-at row)
   :updatedAt (:updated-at row)
   :body (:body row)
   :author (profile {:username (:author-username row)
                     :bio (:author-bio row)
                     :image (:author-image row)
                     :following (:author-following row)})})

(defn comment-body
  [row]
  {:comment (comment-view row)})

(defn comments-body
  [rows]
  {:comments (mapv comment-view rows)})

(defn tags-body
  [names]
  {:tags (vec names)})
