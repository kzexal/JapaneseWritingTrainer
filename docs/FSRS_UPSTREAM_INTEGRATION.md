# Upstream FSRS integration

## Goal

Use the upstream Rust FSRS crate pinned by Anki while keeping app-specific learning policy in Kotlin. The Room schema, Session Engine, Coordinator and Compose UI must not depend on JNI types.

## Pinned upstream implementation

- Rust crate: fsrs 6.6.2
- Source: https://github.com/open-spaced-repetition/fsrs-rs/tree/v6.6.2
- License: BSD-3-Clause. Preserve the upstream license and attribution when distributing the native library.
- The whole Anki application/workspace is AGPL-3.0-or-later; this project uses the separately published fsrs crate, not Anki's whole application code.

## Current bridge contract

The JNI boundary accepts:
- stability and difficulty, both null for a new card or both supplied for an existing card;
- elapsed whole days;
- desired retention.

It returns the four upstream outcomes (Again, Hard, Good, Easy), each containing stability, difficulty and the raw interval in days.

The Rust crate's own `FSRS::next_states` API is the source of truth. The bridge must not reimplement FSRS math. The Kotlin layer converts the selected raw interval to the app's day/step policy and owns due timestamps, status transitions, counters, learning/relearning steps and queue selection.

## Build on Windows

Prerequisites:
1. Rust stable via rustup (Rust 1.85 or newer because the pinned crate uses edition 2024).
2. Android Studio with Android SDK and NDK installed.
3. cargo-ndk: `cargo install cargo-ndk`.

From the repository's Android project root:

```powershell
cd native
.\build-android.ps1
cd ..
.\gradlew.bat :app:assembleDebug
```

The script currently builds arm64-v8a and x86_64 and places the generated libraries in `app/src/main/jniLibs`. Generated binaries are build outputs; do not commit them unless the repository intentionally adopts checked-in native artifacts.

## Tests and acceptance criteria

- Rust parity tests compare every returned rating and state field against a direct call to the pinned upstream crate for both a new card and an existing card.
- Android instrumentation tests verify that the packaged .so loads and that JNI returns all four rating states.
- Before replacing the Kotlin scheduler, add parity vectors for new, same-day, interday and lapse reviews, and verify the app's step/status behavior separately.
- Keep the existing Session Engine, Room, and UI tests passing.
- Remove the Kotlin FSRS math only after parity and regression tests pass.
- CI checks Rust formatting, unit tests, clippy, and Android ABI compilation.

## Important distinction

The upstream crate calculates FSRS memory states and intervals. Anki's full scheduler also applies learning/relearning steps, queue selection, daily limits, fuzzing and deck configuration. Those policies are not automatically supplied by `fsrs::FSRS::next_states` and remain app-owned until deliberately implemented.
