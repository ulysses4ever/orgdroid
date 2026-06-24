{ pkgs ? import <nixpkgs> { config.allowUnfree = true; config.android_sdk.accept_license = true; } }:

let
  androidComposition = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "35" ];
    buildToolsVersions = [ "35.0.0" ];
    includeSystemImages = false;
    includeEmulator = false;
  };
  androidsdk = androidComposition.androidsdk;
in
pkgs.mkShell {
  buildInputs = [
    androidsdk
    pkgs.gradle
    pkgs.kotlin
    pkgs.jdk17
  ];

  JAVA_HOME = "${pkgs.jdk17}";
  # On NixOS, aapt2 downloaded from Maven by AGP won't run (wrong ELF interpreter).
  # Override to use the nix-provided binary instead.
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidsdk}/libexec/android-sdk/build-tools/35.0.0/aapt2";

  shellHook = ''
    # The nix store is read-only; AGP's SDK manager tries to write install metadata.
    # Create a writable overlay at ~/.android/sdk that symlinks all nix SDK components.
    NIX_SDK="${androidsdk}/libexec/android-sdk"
    SDK_OVERLAY="$HOME/.android/orgdroid-sdk"
    mkdir -p "$SDK_OVERLAY"
    for item in "$NIX_SDK"/*; do
      name="$(basename "$item")"
      ln -sfT "$item" "$SDK_OVERLAY/$name"
    done
    export ANDROID_HOME="$SDK_OVERLAY"
    echo "sdk.dir=$SDK_OVERLAY" > local.properties
  '';
}
