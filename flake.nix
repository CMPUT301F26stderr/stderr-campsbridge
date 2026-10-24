{
  description = "stderr-campsbridge development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs = {nixpkgs, ...}: let
    system = "x86_64-linux";

    pkgs = import nixpkgs {
      inherit system;
      config = {
        allowUnfree = true;
        android_sdk.accept_license = true;
      };
    };

    # Keep the shared hook runner version when using stable nixpkgs.
    preCommit = pkgs.pre-commit.overridePythonAttrs (_: {
      version = "4.6.2";
      src = pkgs.fetchFromGitHub {
        owner = "pre-commit";
        repo = "pre-commit";
        tag = "v4.6.2";
        hash = "sha256-aCEN9dVz/3lB2gy7U+6dVj3jSM7cmVsstOp+LHvYRsU=";
      };
      # The upstream suite provisions unrelated language toolchains.
      # Validate the configured hooks with the project checks instead.
      doCheck = false;
      doInstallCheck = false;
      dontUsePytestCheck = true;
      nativeCheckInputs = [];
      nativeInstallCheckInputs = [];
      preCheck = "";
      postCheck = "";
    });

    # Run CI's read-only hooks from Nix rather than cached pip environments.
    # The locked package is pre-commit-hooks 6.0.0, matching the local hooks.
    checkPython = pkgs.python3.withPackages (ps: [
      ps.pre-commit-hooks
      ps.identify
      ps.pyyaml
    ]);

    android = pkgs.androidenv.composeAndroidPackages {
      platformVersions = ["37.0"];
      buildToolsVersions = ["36.0.0" "37.0.0"];

      includeEmulator = false;
      includeSystemImages = false;
      includeNDK = false;
      includeCmake = false;
    };

    # Separate runtime SDK keeps emulator images out of ordinary builds.
    androidUi = pkgs.androidenv.composeAndroidPackages {
      platformVersions = ["35"];
      buildToolsVersions = ["36.0.0"];
      includeEmulator = true;
      includeSystemImages = true;
      systemImageTypes = ["google_apis"];
      abiVersions = ["x86_64"];
      includeNDK = false;
      includeCmake = false;
    };

    sdk = "${android.androidsdk}/libexec/android-sdk";

    # Match the formatter version and checksum in .pre-commit-config.yaml.
    ktlintJar = pkgs.fetchurl {
      url = "https://github.com/ktlint/ktlint/releases/download/1.8.0/ktlint";
      hash = "sha256-o/1iAgfVxA2myniblef4I8VOhUt/ref2E+kQlqNwbXU=";
    };

    ktlint = pkgs.writeShellApplication {
      name = "ktlint";
      runtimeInputs = [pkgs.jdk25];
      text = ''
        exec java --enable-native-access=ALL-UNNAMED -jar ${ktlintJar} "$@"
      '';
    };
  in {
    devShells.${system} = rec {
      default = pkgs.mkShell {
        packages = [
          pkgs.jdk25
          pkgs.firebase-tools
          pkgs.nodejs_24
          android.androidsdk
          pkgs.bash
          pkgs.git
          pkgs.gnutar
          pkgs.zstd
          pkgs.gh
          pkgs.openssh
          pkgs.direnv
          pkgs.actionlint
          pkgs.shellcheck
          preCommit
          checkPython
          pkgs.go
          pkgs.gitleaks
          pkgs.drawio-headless
          ktlint
        ];

        JAVA_HOME = pkgs.jdk25.home;
        ANDROID_HOME = sdk;

        # Use the Nix-patched executable rather than the Maven binary on NixOS.
        GRADLE_OPTS =
          "-Dorg.gradle.project.android.aapt2FromMavenOverride="
          + "${sdk}/build-tools/37.0.0/aapt2";

        shellHook = ''
          export PATH="${sdk}/platform-tools:$PATH"
        '';
      };

      # Build with the normal SDK; boot a pinned API 35 device separately.
      ui = pkgs.mkShell {
        inputsFrom = [default];
        packages = [androidUi.androidsdk];
        ANDROID_HOME = sdk;
        ANDROID_UI_HOME = "${androidUi.androidsdk}/libexec/android-sdk";
        JAVA_HOME = pkgs.jdk25.home;
        GRADLE_OPTS =
          "-Dorg.gradle.project.android.aapt2FromMavenOverride="
          + "${sdk}/build-tools/37.0.0/aapt2";
      };

      # Hook checks need Python, Go, and Java, but not the Android SDK.
      hooks = pkgs.mkShell {
        packages = [
          pkgs.bash
          pkgs.git
          pkgs.gnutar
          pkgs.zstd
          preCommit
          checkPython
          pkgs.go
          pkgs.jdk25
          pkgs.gitleaks
          ktlint
        ];

        JAVA_HOME = pkgs.jdk25.home;
      };

      # Repository validation does not need the Android SDK or app toolchain.
      repository = pkgs.mkShell {
        packages = [
          pkgs.bash
          pkgs.git
          pkgs.gh
          pkgs.openssh
          pkgs.actionlint
          pkgs.shellcheck
          preCommit
        ];
      };
    };
  };
}
