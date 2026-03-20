#!/usr/bin/env bb
;; Usage: bb scripts/fork-domain.bb <domain-name>
;;
;; Strips the bank exemplar and rewires configs for a new domain.

(require '[babashka.fs :as fs]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.pprint :as pprint])

(import '[java.util.regex Matcher])

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(def root (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(defn die [& msgs]
  (binding [*out* *err*]
    (apply println msgs))
  (System/exit 1))

(defn bank-key? [k]
  (str/includes? (str k) "bank-"))

(defn bank-path? [s]
  (str/includes? (str s) "bank-"))

(defn info [& msgs]
  (apply println msgs))

;; ---------------------------------------------------------------------------
;; Validate args
;; ---------------------------------------------------------------------------

(def domain
  (let [args *command-line-args*]
    (when (or (empty? args) (str/blank? (first args)))
      (die "Usage: bb scripts/fork-domain.bb <domain-name>"))
    (let [d (str/lower-case (str/trim (first args)))]
      (when-not (re-matches #"[a-z][a-z0-9-]*" d)
        (die "Domain name must start with a letter and contain"
             "only lowercase letters, digits, and hyphens."))
      (when (= d "bank")
        (die "Domain name cannot be 'bank' — that's the one being removed."))
      d)))

;; ---------------------------------------------------------------------------
;; 1. Delete bank directories
;; ---------------------------------------------------------------------------

(info "Deleting bank directories...")

(doseq [dir (concat (fs/glob root "components/bank-*")
                     (fs/glob root "bases/bank-*")
                     (fs/glob root "projects/bank-*"))]
  (when (fs/directory? dir)
    (info "  rm -rf" (str dir))
    (fs/delete-tree dir)))

;; ---------------------------------------------------------------------------
;; 2. Delete bank-specific files
;; ---------------------------------------------------------------------------

(info "Deleting bank-specific files...")

(doseq [f ["development/src/dev/bank_monolith.clj"
            "scripts/create-accounts.js"
            ".github/workflows/pages.yml"]]
  (let [path (fs/path root f)]
    (when (fs/exists? path)
      (info "  rm" (str path))
      (fs/delete path))))

;; ---------------------------------------------------------------------------
;; 3. Rewrite deps.edn
;; ---------------------------------------------------------------------------

(info "Rewriting deps.edn...")

(let [deps-path (str (fs/path root "deps.edn"))
      deps      (edn/read-string (slurp deps-path))
      bank-alias (get-in deps [:aliases :+bank])
      cleaned   (-> bank-alias
                    (update :extra-deps
                            (fn [m]
                              (into (sorted-map)
                                    (remove (fn [[k _]] (bank-key? k)) m))))
                    (update :extra-paths
                            (fn [ps] (vec (remove bank-path? ps)))))
      new-key   (keyword (str "+" domain))
      deps'     (-> deps
                    (update :aliases dissoc :+bank)
                    (assoc-in [:aliases new-key] cleaned))]
  (spit deps-path (with-out-str (pprint/pprint deps'))))

;; ---------------------------------------------------------------------------
;; 4. Rewrite workspace.edn
;; ---------------------------------------------------------------------------

(info "Rewriting workspace.edn...")

(let [ws-path (str (fs/path root "workspace.edn"))
      ws      (edn/read-string (slurp ws-path))
      ws'     (-> ws
                  (assoc :default-profile-name domain)
                  (update :projects
                          (fn [m]
                            (into {}
                                  (remove (fn [[k _]]
                                            (str/starts-with? (name k) "bank-"))
                                          m)))))]
  (spit ws-path (with-out-str (pprint/pprint ws'))))

;; ---------------------------------------------------------------------------
;; 5. Rewrite .clj-kondo/config.edn
;; ---------------------------------------------------------------------------

(info "Rewriting .clj-kondo/config.edn...")

(let [kondo-path (str (fs/path root ".clj-kondo/config.edn"))
      cfg        (edn/read-string (slurp kondo-path))
      cfg'       (update-in cfg [:output :exclude-files]
                            (fn [fs]
                              (vec (remove #(str/includes? % "bank-schema")
                                           fs))))]
  (spit kondo-path (with-out-str (pprint/pprint cfg'))))

;; ---------------------------------------------------------------------------
;; 6. Rewrite Justfile
;; ---------------------------------------------------------------------------

(info "Rewriting Justfile...")

(let [jf-path (str (fs/path root "Justfile"))
      content (slurp jf-path)
      ;; Replace DOMAIN_ALIASES
      content (str/replace content
                           #"DOMAIN_ALIASES := \":?\+bank\""
                           (str "DOMAIN_ALIASES := \":+" domain "\""))
      ;; Remove export-openapi recipe (from recipe line to next blank line
      ;; before next recipe or EOF)
      content (str/replace content
                           #"(?m)^export-openapi[^\n]*\n(?:(?!^\S)[^\n]*\n)*"
                           "")
      ;; Remove start-bank-app recipe
      content (str/replace content
                           #"(?m)^start-bank-app[^\n]*\n(?:(?!^\S)[^\n]*\n)*"
                           "")]
  (spit jf-path content))

;; ---------------------------------------------------------------------------
;; 7. Rewrite .github/workflows/test.yml
;; ---------------------------------------------------------------------------

(info "Rewriting .github/workflows/test.yml...")

(let [test-path (str (fs/path root ".github/workflows/test.yml"))
      content   (slurp test-path)
      content   (str/replace content
                             "[:dev :+bank]"
                             (str "[:dev :+" domain "]"))
      ;; Remove skip="bank-app" and simplify discover
      content   (str/replace content
                             #"(?m) *skip=\"bank-app\"\n *matrix=\$\(ls projects/ \| grep -vxF \"\$skip\" \| jq -Rc '\[., inputs\]'\)"
                             (Matcher/quoteReplacement
                               "          matrix=$(ls projects/ | jq -Rc '[., inputs]')"))]
  (spit test-path content))

;; ---------------------------------------------------------------------------
;; 8. Rewrite readme.md
;; ---------------------------------------------------------------------------

(info "Rewriting readme.md...")

(let [readme-path (str (fs/path root "readme.md"))
      content     (slurp readme-path)
      ;; Replace "How to Use It" with domain-specific guidance
      content     (str/replace
                    content
                    #"(?s)## How to Use It\n.*?(?=## Exemplar: Queenswood Bank)"
                    (str "## How to Use It\n\n"
                         "Add your domain code:\n\n"
                         "1. Add domain components under `components/" domain "-*/`\n"
                         "2. Add domain bases under `bases/" domain "-*/`\n"
                         "3. Add domain projects under `projects/" domain "-*/`\n"
                         "4. Register your new bricks in the `:+" domain "` alias in `deps.edn`\n\n"
                         "See [Getting Started](#getting-started) for prerequisites and how to\n"
                         "run tests.\n\n"))
      ;; Remove everything from "## Exemplar: Queenswood Bank" up to
      ;; (but not including) "## Architecture"
      content     (str/replace
                    content
                    #"(?s)## Exemplar: Queenswood Bank.*?(?=## Architecture)"
                    "")
      ;; Remove Domain Components section
      content     (str/replace
                    content
                    #"(?s)## Domain Components\n.*?(?=## Domain Bases)"
                    "")
      ;; Remove Domain Bases section
      content     (str/replace
                    content
                    #"(?s)## Domain Bases\n.*?(?=## Domain Projects)"
                    "")
      ;; Remove Domain Projects section
      content     (str/replace
                    content
                    #"(?s)## Domain Projects\n.*?(?=## Getting Started)"
                    "")]
  (spit readme-path content))

;; ---------------------------------------------------------------------------
;; Done
;; ---------------------------------------------------------------------------

(info)
(info (str "Done! Bank exemplar removed. Domain alias is :+" domain))
(info)
(info "Next steps:")
(info (str "  1. Add your domain components under components/" domain "-*/"))
(info (str "  2. Add your domain bases under bases/" domain "-*/"))
(info (str "  3. Add your domain projects under projects/" domain "-*/"))
(info (str "  4. Register deps in the :+" domain " alias in deps.edn"))
