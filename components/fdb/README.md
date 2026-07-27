# FDB Component

FoundationDB integration component providing database lifecycle management,
system component registration, and key-value operations.

## Requirements

The FoundationDB Java client requires the native `libfdb_c` library on the
host. Without it, the client fails at runtime rather than at build time.

The version matters: FoundationDB requires a compatible protocol version
between client and cluster, so a client that does not match the server cannot
connect, and says nothing useful about why. The required version is
`foundationdb.version` in [`versions.json`](../../versions.json) at the
workspace root, which is also what the Nix flake and CI build from. The
commands below read it rather than naming a version, so they cannot go stale;
run them from the workspace root.

Run `just doctor` at any point to check what is actually on `PATH`.

### Installation

**With Nix (recommended).** The flake provides the native library and
`fdbcli` automatically; `direnv allow` or `nix develop` is all that is needed.

**macOS without Nix.** Homebrew has no FoundationDB formula. Install the
official package:

```bash
FDB=$(jq -r .foundationdb.version versions.json)
ARCH=$([ "$(uname -m)" = "arm64" ] && echo arm64 || echo x86_64)
curl -fLO "https://github.com/apple/foundationdb/releases/download/${FDB}/FoundationDB-${FDB}_${ARCH}.pkg"
sudo installer -pkg "FoundationDB-${FDB}_${ARCH}.pkg" -target /
```

Note that only the 7.4 line and 7.3.75 and earlier ship macOS packages; 7.3.76
through 7.3.79 are Linux-only.

**Linux.**

```bash
FDB=$(jq -r .foundationdb.version versions.json)
ARCH=$([ "$(uname -m)" = "aarch64" ] && echo aarch64 || echo amd64)
curl -fLO "https://github.com/apple/foundationdb/releases/download/${FDB}/foundationdb-clients_${FDB}-1_${ARCH}.deb"
sudo dpkg -i "foundationdb-clients_${FDB}-1_${ARCH}.deb"
```

## Usage

The component registers `:fdb/cluster-file-path` and `:fdb/db` system
components. Include via `testcontainers/fdb-test.yml` in tests, or configure
directly with a cluster file path in production:

```yaml
system:
  fdb:
    cluster-file-path: /path/to/fdb.cluster
    database:
      api-version: 710  # optional, defaults to 710
```

The API version is the client API contract, not the server version, and is
independent of the installed client's version. The Record Layer path uses the
matching `APIVersion/API_VERSION_7_1`.

Access the database and use the interface:

```clojure
(system/with-system [sys (system-config)]
  (let [db (system/instance sys [:fdb :db])]
    (fdb/set db "key" "value")
    (fdb/get db "key")))
```

## Testing

Tests run against a FoundationDB server in a container, built from
`components/testcontainers/resources/fdb/Dockerfile` at the version in
`fdb-version` in the matching `fdb.clj`. That version is asserted to match
`versions.json`, so the containerised server and the client on the host cannot
disagree on protocol version.

The host still needs `libfdb_c` installed, as above — the container provides
the server, not the client.
