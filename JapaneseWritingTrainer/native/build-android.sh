#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BRIDGE="$ROOT/native/fsrs-bridge"
OUTPUT="$ROOT/app/src/main/jniLibs"
command -v cargo >/dev/null || { echo "Install Rust/Cargo first." >&2; exit 1; }
command -v cargo-ndk >/dev/null || { echo "Install cargo-ndk: cargo install cargo-ndk" >&2; exit 1; }
rustup target add aarch64-linux-android x86_64-linux-android
cd "$BRIDGE"
cargo ndk -t arm64-v8a -t x86_64 -o "$OUTPUT" build --release
echo "Native libraries generated in $OUTPUT"
