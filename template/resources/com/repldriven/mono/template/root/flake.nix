{
  description = "{{main}} development environment";

  # Native toolchain for a workspace built on mono.
  #
  # The versions below are not editable defaults: they were stamped in at
  # generation time from the versions.json of the mono release this workspace
  # pins ({{mono/tag}}), because they are a property of that release rather
  # than a local preference.
  #
  #   FoundationDB  the client must share a protocol version with the server
  #                 mono's testcontainers image builds, or tests fail at
  #                 connect time with an error that mentions no versions
  #   protoc        must stay on the line that emits code for the protobuf-java
  #                 runtime mono pins; a newer protoc targets protobuf 4, which
  #                 the FoundationDB Record Layer does not support
  #
  # To move them, take a newer mono release and regenerate, rather than editing
  # here. If you do not use FoundationDB, this whole file is optional; see the
  # prerequisites section of the readme.
  #
  # Targets macOS, matching mono's own devshell: the protoc and FoundationDB
  # archives fetched below are the macOS builds.

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnsupportedSystem = true;
        };

        fdbArch = if pkgs.stdenv.isAarch64 then "arm64" else "x86_64";
        fdbBinary = pkgs.stdenv.mkDerivation {
          name = "foundationdb-{{toolchain/fdb-version}}";
          src = pkgs.fetchurl {
            url = "https://github.com/apple/foundationdb/releases/download/{{toolchain/fdb-version}}/FoundationDB-{{toolchain/fdb-version}}_${fdbArch}.pkg";
            sha256 =
              if pkgs.stdenv.isAarch64 then
                "{{toolchain/fdb-sha256-aarch64}}"
              else
                "{{toolchain/fdb-sha256-x86-64}}";
          };
          buildInputs = [
            pkgs.xar
            pkgs.cpio
          ];
          unpackPhase = ''
            xar -xf $src
            cat FoundationDB-clients.pkg/Payload | gunzip -dc | cpio -i
          '';
          installPhase = ''
            mkdir -p $out/lib $out/bin
            cp -r usr/local/lib/* $out/lib/
            cp -r usr/local/bin/* $out/bin/
          '';
        };

        protocGenClojure = pkgs.stdenv.mkDerivation {
          name = "protoc-gen-clojure-{{toolchain/protoc-gen-clojure-version}}";
          src = pkgs.fetchurl {
            url = "https://github.com/protojure/protoc-plugin/releases/download/v{{toolchain/protoc-gen-clojure-version}}/protoc-gen-clojure";
            sha256 = "{{toolchain/protoc-gen-clojure-sha256}}";
          };
          dontUnpack = true;
          installPhase = ''
            mkdir -p $out/bin
            cp $src $out/bin/protoc-gen-clojure
            chmod +x $out/bin/protoc-gen-clojure
          '';
        };

        protocArch = if pkgs.stdenv.isAarch64 then "aarch_64" else "x86_64";
        protocBinary = pkgs.stdenv.mkDerivation {
          name = "protoc-{{toolchain/protoc-version}}";
          src = pkgs.fetchurl {
            url = "https://github.com/protocolbuffers/protobuf/releases/download/v{{toolchain/protoc-version}}/protoc-{{toolchain/protoc-version}}-osx-${protocArch}.zip";
            sha256 =
              if pkgs.stdenv.isAarch64 then
                "{{toolchain/protoc-sha256-aarch64}}"
              else
                "{{toolchain/protoc-sha256-x86-64}}";
          };
          sourceRoot = ".";
          nativeBuildInputs = [ pkgs.unzip ];
          installPhase = ''
            mkdir -p $out/bin $out/include
            cp bin/protoc $out/bin/
            cp -r include/* $out/include/
          '';
        };

        libPath = pkgs.lib.makeLibraryPath [ fdbBinary ];

        # Wrap clojure/clj to always set DYLD_LIBRARY_PATH for the FDB native
        # library. DYLD_* vars are stripped by macOS SIP when launching
        # restricted processes, so env inheritance alone is unreliable — the
        # wrapper bakes the path in at the binary level.
        clojureWithFdb = pkgs.writeShellScriptBin "clojure" ''
          export DYLD_LIBRARY_PATH="${libPath}:$DYLD_LIBRARY_PATH"
          exec ${pkgs.clojure}/bin/clojure "$@"
        '';
        cljWithFdb = pkgs.writeShellScriptBin "clj" ''
          export DYLD_LIBRARY_PATH="${libPath}:$DYLD_LIBRARY_PATH"
          exec ${pkgs.clojure}/bin/clj "$@"
        '';

      in
      {
        # Deliberately smaller than mono's own devshell. Formatting and linting
        # come from deps.edn aliases (:format/zprint, :lint/clj-kondo), so they
        # are not needed here.
        devShells.default = pkgs.mkShell {
          buildInputs = [
            cljWithFdb
            clojureWithFdb
            pkgs.colima
            pkgs.docker
            pkgs.docker-credential-helpers
            fdbBinary
            pkgs.jdk21
            pkgs.just
            protocBinary
            protocGenClojure
          ];

          shellHook = ''
            # Ensure the pinned protoc takes precedence over any protoc
            # inherited from a parent direnv environment
            export PATH="${protocBinary}/bin:$PATH"

            # Make libfdb_c findable by the JVM's JNI loader
            export LD_LIBRARY_PATH="${libPath}:$LD_LIBRARY_PATH"
            export DYLD_LIBRARY_PATH="${libPath}:$DYLD_LIBRARY_PATH"

            # Colima/Docker configuration for testcontainers
            export DOCKER_HOST="unix://$HOME/.config/colima/default/docker.sock"
            export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
            export TESTCONTAINERS_REUSE_ENABLE="TRUE"

            if ! colima status &>/dev/null; then
              echo "Docker not running — use 'just start-docker' to start"
            fi
            echo "{{main}} environment loaded (mono {{mono/tag}})"
          '';
        };
      }
    );
}
