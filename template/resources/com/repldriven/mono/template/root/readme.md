# {{main}}

A [Polylith](https://polylith.gitbook.io/polylith) workspace built on the
[mono](https://github.com/repldriven/mono) component library.

Top namespace: `{{top-ns}}`.

## How this workspace relates to mono

Shared bricks (`error`, `system`, `log`, `env`, `server`, `pulsar`, and the
rest) are **not** copied into this repository. They arrive as a pinned git
dependency, so you see them under `libs` rather than as editable bricks:

```clojure
com.repldriven/mono {:git/url "{{mono/url}}"
                     :git/tag "{{mono/tag}}"
                     :git/sha "{{mono/sha}}"
                     :deps/root "projects/mono-lib"}
```

Test support (`test-system`, `testcontainers`) ships from the same lib symbol
with `:deps/root "projects/mono-test-lib"`, added under the `:test` alias only,
so Docker and the testcontainers tree stay off your runtime classpath. That root
is a superset of the runtime one, because sharing a lib symbol means the `:test`
alias replaces the runtime coordinate rather than adding to it.

Requires on those bricks keep their original namespaces, for example
`com.repldriven.mono.error.interface`. That is deliberate: the namespace is a
property of the brick's source, not of how it was delivered.

To take an upstream fix, bump `:git/tag` and `:git/sha` together in `deps.edn`
and each project's `deps.edn`. Never repoint a tag; mono publishes a new one.

The bricks under `components/` and `bases/` here **are** yours. They were copied
from mono's examples at generation time and rewritten into your namespace. Edit
or delete them freely.

## Prerequisites

The starter bricks are a working implementation of the
[RealWorld](https://realworld-docs.netlify.app/) ("Conduit") API — users,
articles, comments, tags, favorites and follows — backed by postgres. Reads
query the database; writes go through a command bus. It is a real
application rather than a toy, so the shapes it uses are ones worth copying.

You need:

- Java 21 and the Clojure CLI
- Docker, to run the tests (they use testcontainers)
- Network access on first generation, to resolve the pinned mono release

That is the whole list. With [nix](https://nixos.org/download) and
[direnv](https://direnv.net) you need none of it directly:

```bash
direnv allow     # or: nix develop
just doctor      # confirms what is actually on PATH
```

If you do not want the example, delete `components/realworld-domain`,
`components/realworld-store` and `bases/realworld-api`, and remove them from
the three registration sites below. Nothing else depends on them.

## First run

```bash
just check          # clojure -M:poly check
just test           # needs Docker
```

There is no mandatory setup step. `just setup` exists for when you add a
brick that generates code, but nothing in the starter does. The workspace is
initialised as a git repository at generation, because Polylith asks git
which bricks changed and finds nothing to run without one.

The starter is held to the official RealWorld conformance suite, vendored
under `bases/realworld-api/test-resources/realworld-api/hurl`:

```bash
just realworld-hurl                    # postgres, the service, all 13 files
just realworld-hurl 8099               # on another port
just realworld-hurl 8099 other-service # from another project
```

Where the suite and your own tests disagree, the suite is the contract.

The API allows browser requests from `http://localhost:3000`, where the
RealWorld frontends run. Set `REALWORLD_CORS_ORIGIN` to point it at a
deployed frontend instead; `server.jetty-adapter.cors.origins` in
`bases/realworld-api/resources/realworld-api/application.yml` takes a list,
if you need more than one.

## Adding a brick

```bash
clojure -M:poly create component name:my-thing
```

Then register it at **three sites, in lockstep**:

1. `deps.edn`, the `:+realworld` profile `:extra-deps`
2. `deps.edn`, the `:+realworld` profile `:extra-paths` (its `test` and
   `test-resources` directories)
3. `projects/{{main}}/deps.edn`, `:deps`

`poly check` will tell you if you miss one.

## Conventions

Mono's conventions apply here: component interfaces return anomalies rather than
throwing, systems are defined as data in YAML, and all data uses kebab-case
keyword keys. See the [mono readme](https://github.com/repldriven/mono) for the
full set.
