# Upstream FSRS integration plan

## Goal

Replace the hand-maintained FSRS-6 math in `Fsrs6Scheduler` with the upstream FSRS implementation used by Anki, while keeping the app's domain models, Room persistence, session engine, and UI independent from the scheduler.

## Upstream source

- Anki: https://github.com/ankitects/anki
- Anki pins the Rust crate `fsrs = 6.6.2` in its Cargo workspace.
- The crate source: https://github.com/open-spaced-repetition/fsrs-rs
- Crate license: BSD-3-Clause (retain copyright/license notices when redistributing).
- Anki application/workspace license: AGPL-3.0-or-later. Reusing the complete Anki scheduler/application code is a separate licensing decision; non-commercial personal use does not itself waive license terms.

## Integration boundary

Keep `CardReviewScheduler` as the Kotlin-facing interface. Implement an adapter backed by the upstream Rust `fsrs` crate through Android JNI (or a small maintained binding), and map only these values across the boundary:

- prior memory state (stability, difficulty; absent for a new card)
- elapsed time in days
- rating (Again/Hard/Good/Easy)
- desired retention and maximum interval
- returned memory state and interval

The Kotlin layer continues to own the app-specific learning policy, card status transitions, due timestamps, and counters. This separation prevents upstream algorithm code from becoming coupled to Room entities or Compose.

## Acceptance criteria

1. Pin the upstream FSRS crate version; do not track an unpinned `main`.
2. Preserve BSD-3-Clause license text and attribution in the bundled source/native artifact.
3. Add upstream-reference test vectors for new, same-day, interday, and lapse reviews.
4. Verify the adapter output against the upstream crate for all vectors (tolerance for floating-point values; exact rating/status mapping).
5. Keep existing session, Room, and UI tests passing.
6. Remove the local mathematical implementation only after parity tests pass.
7. Document Android NDK/Cargo build prerequisites and ensure CI can build all supported ABIs.

## Important distinction

The upstream `fsrs` crate implements the FSRS memory model and scheduling calculations. Anki's complete scheduler also includes queue selection, learning/relearning steps, daily limits, fuzzing, and deck configuration. Those policies should be ported deliberately rather than assumed to be provided by the crate.
