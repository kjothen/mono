(ns com.repldriven.mono.realworld-domain.slug
  (:require
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.string :as str]))

(def ^:private separators #"[^a-z0-9]+")

(defn- slugify
  [title]
  (-> (str title)
      (str/lower-case)
      (str/replace separators "-")
      (str/replace #"^-+|-+$" "")))

(defn- suffix
  "Eight hex characters from the random tail of a uuidv7.

  The tail, not the head: uuidv7 leads with a 48-bit millisecond
  timestamp, so its first eight characters are 32 bits of that clock and
  change roughly once a minute. Two articles created back to back would
  have taken the same suffix — which is precisely the case the suite
  tests. The last twelve characters are random."
  []
  (let [s (str (utility/uuidv7))]
    (subs s (- (count s) 8))))

(defn slug
  "A URL slug for `title`, different on every call.

  The suffix is unconditional rather than added on collision: the suite
  requires two articles sharing a title to get different slugs, and doing
  it unconditionally avoids both a read-then-write race and a retry loop.
  A title that slugifies to nothing still yields a usable slug."
  [title]
  (let [base (slugify title)]
    (if (str/blank? base) (suffix) (str base "-" (suffix)))))
