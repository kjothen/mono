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

The starter bricks demonstrate a protobuf and FoundationDB pipeline, which is
the most demanding thing mono supports.

The quickest route is [nix](https://nixos.org/download) and
[direnv](https://direnv.net). `flake.nix` pins the whole native toolchain at
the versions mono `{{mono/tag}}` was built against, so:

```bash
direnv allow     # or: nix develop
just doctor      # confirms what is actually on PATH
```

Without nix you need these yourself, at these versions:

- Java 21 and the Clojure CLI
- [`protoc`](https://github.com/protocolbuffers/protobuf)
  `{{toolchain/protoc-version}}` and
  [`protoc-gen-clojure`](https://github.com/protojure/protoc-plugin)
  `{{toolchain/protoc-gen-clojure-version}}` on `PATH`, for code generation
- The [FoundationDB](https://github.com/apple/foundationdb/releases) client
  library `{{toolchain/fdb-version}}`, to run anything that touches `fdb`
- Docker, to run the tests (they use testcontainers)
- Network access on first generation, to resolve the pinned mono release

The versions are exact, not minimums, and both failures are silent until
runtime. A FoundationDB client only talks to a cluster sharing its protocol
version, and a newer `protoc` emits code for the protobuf 4 runtime, which the
FDB Record Layer mono pins does not support. `just doctor` reports both.

If you do not want FoundationDB, delete `components/example-bookmark` and
`bases/example-api`, remove them from the three registration sites below, and
the protobuf and FDB prerequisites go away with them — along with the need for
`flake.nix`.

## First run

```bash
just setup          # clojure -X:deps prep, generates protobuf code into gen/
just check          # clojure -M:poly check
just test           # needs Docker
```

`just setup` is not optional. `example-bookmark` declares `:deps/prep-lib`, and
its `classes` path does not exist until prep has run, so tools.deps cannot build
a classpath before then.

## Adding a brick

```bash
clojure -M:poly create component name:my-thing
```

Then register it at **three sites, in lockstep**:

1. `deps.edn`, the `:+example` profile `:extra-deps`
2. `deps.edn`, the `:+example` profile `:extra-paths` (its `test` and
   `test-resources` directories)
3. `projects/{{main}}/deps.edn`, `:deps`

`poly check` will tell you if you miss one.

## Conventions

Mono's conventions apply here: component interfaces return anomalies rather than
throwing, systems are defined as data in YAML, and all data uses kebab-case
keyword keys. See the [mono readme](https://github.com/repldriven/mono) for the
full set.
