# Upstream FSRS Rust bridge

This module pins the official FSRS Rust crate at **6.6.2** (the version used by Anki's Cargo workspace at the time this integration was planned).

## Scope

- Rust calculates the four FSRS outcomes (Again/Hard/Good/Easy), each with memory stability, difficulty and interval.
- Kotlin continues to own app-specific NEW/LEARNING/REVIEW transitions, review counters, due timestamps and Room persistence.
- This is an integration scaffold: Gradle packaging of Android native libraries is deliberately a separate step.

## Build and test on a machine with Rust

From this directory:

```powershell
cargo test
cargo fmt --check
```

## Android native build (next integration step)

Install Android NDK, Rust Android targets and cargo-ndk. Build the shared library for each ABI supported by the app, then package the resulting `libjapanese_writing_fsrs.so` under `app/src/main/jniLibs/<abi>/`.

Example for arm64:

```powershell
cargo ndk -t arm64-v8a -o ..\..\app\src\main\jniLibs build --release
```

The Kotlin JNI entry point is `UpstreamFsrsNative.nativeReview(String): String`. The JSON input and output shape is intentionally small and can be parity-tested against FSRS reference vectors before replacing the existing scheduler.

## License

FSRS Rust crate is BSD-3-Clause. This bridge is MIT. Keep upstream notices and license texts when distributing.
