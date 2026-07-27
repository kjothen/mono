{
  description = "{{main}} development environment";

  # A JDK, Clojure, just and Docker. That is the whole toolchain.
  #
  # Earlier versions of this template pinned a FoundationDB client, protoc
  # and a protoc plugin, because the starter bricks were protobuf-backed and
  # needed all three at exact versions. The starter is postgres-backed now,
  # Docker supplies postgres, and there is no code generation step — so none
  # of that is here, and `just setup` is no longer something you must run
  # before anything will resolve.
  #
  # This file is optional. Without nix, install a JDK 21, the Clojure CLI and
  # Docker yourself and everything works the same way.

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
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            pkgs.clojure
            pkgs.colima
            pkgs.docker
            pkgs.docker-credential-helpers
            pkgs.jdk21
            pkgs.just
          ];

          shellHook = ''
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
