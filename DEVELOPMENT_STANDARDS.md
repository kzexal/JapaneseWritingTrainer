# Development Standards

These rules apply to all development work in this repository. They are intended to keep the project buildable, maintainable, testable, and consistent with the actual product specification.

## 1. Source of truth and branch policy

- **Develop directly on `main` unless the repository owner explicitly changes this rule.**
- Read the current `main` state before making changes. Do not base implementation, tests, or decisions on another branch.
- Do not create feature branches, cherry-pick, merge, or copy commits from other branches unless explicitly requested.
- Keep commits focused. Use a concise message that describes the change.
- Before editing a file, inspect its current contents and preserve unrelated work.
- Never report a change as complete until the relevant files have been read back or otherwise verified.

## 2. Understand before changing

- Follow the existing architecture and naming conventions before introducing new abstractions.
- Prefer small, targeted changes over broad refactors.
- Do not rewrite the scheduler, persistence layer, canvas, or JNI bridge merely to make the code look different.
- Keep responsibilities separated:
  - Compose screens render UI and forward user actions.
  - Kotlin learning/domain code owns session policy, queues, learning/relearning steps, and application-level scheduling decisions.
  - Room persists user progress and practice history.
  - Rust FSRS-rs provides the FSRS memory-state calculations through JNI.
  - Character/deck assets remain static content, separate from per-user progress.
- Do not add network, account, analytics, or cloud dependencies to the offline-first MVP without an explicit product decision.

## 3. FSRS and learning-state invariants

- Use the pinned upstream FSRS-rs version declared in `native/fsrs-bridge/Cargo.toml`; do not silently upgrade or replace it.
- Rust is responsible for FSRS memory-state calculations. Kotlin remains responsible for application policy such as Learning/Relearning steps, session queues, daily new-card limits, and retry behavior.
- A single learner rating must produce one coherent progress transition and one corresponding history event. Ignore duplicate/concurrent rating submissions while the first persistence operation is in flight.
- `lastReviewedAtEpochMillis` is the timestamp anchor for the last FSRS memory calculation, not the timestamp of every short Learning/Relearning attempt. Practice-attempt timestamps belong in `PracticeLog`.
- Short Again/Hard steps in Learning or Relearning must not repeatedly recalculate FSRS memory. Graduation must calculate elapsed days from the last FSRS memory update.
- Clamp scheduler time against clock rollback so a device clock moving backwards cannot move the FSRS review anchor or due date backwards.
- Preserve the distinction between `NEW`, `LEARNING`, `RELEARNING`, and `REVIEW`.
- Validate input and persisted state at boundaries. Invalid stability, difficulty, retention, elapsed time, or card identifiers must not silently corrupt progress.
- When changing scheduling behavior, cover new cards, same-day reviews, interday reviews, lapses/Again, Learning, Relearning, due queues, and session interruption/resumption.
- Never infer a learner's rating automatically from stroke matching. Any automatic writing analysis must remain feedback unless the product specification explicitly changes this rule.

## 4. Database and backup compatibility

- Treat Room schema changes as data migrations, not disposable development changes.
- Add and test an explicit migration for every database version change.
- Do not use destructive migration or silently reset a learner's progress.
- Keep backup/restore validation strict and restore data transactionally.
- When changing entities, enums, scheduler state, or backup JSON, consider older backups and existing installed databases.
- Update the Specification when the persisted model or compatibility contract changes.

## 5. Writing interaction and UX

- Keep the writing canvas as the primary workspace. Controls such as Undo, Clear, and Hint must remain reachable while writing.
- Hints should not unexpectedly resize or push the canvas and its controls out of reach.
- Prefer clear, minimal feedback over verbose instructions during a writing attempt.
- Stroke matching must tolerate natural finger input. Test with varied stroke position, scale, speed, and shape.
- A matcher rejection must not trap the learner indefinitely; provide a clear recovery path when strict matching prevents progress.
- Do not make a UI change that removes an existing interaction without checking its callers and tests.

## 6. Testing and verification

Run the narrowest relevant checks after each change, then run the full applicable local test/build commands before considering the work complete. Use Android Studio's Gradle tool window or its integrated terminal for Android verification; GitHub Actions is not a required verification gate.

### Rust bridge

From `JapaneseWritingTrainer/native/fsrs-bridge`:

```bash
cargo fmt --check
cargo test
cargo clippy --all-targets -- -D warnings
cargo build --release
```

### Android app

From `JapaneseWritingTrainer`, using Android Studio's Gradle tool window or integrated terminal:

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

- JNI-dependent JVM tests require the host Rust shared library to be available to the test JVM. Build the host library first and set `java.library.path` to `native/fsrs-bridge/target/release`.
- Android ABI libraries must be built separately for the ABIs packaged by the app. A host library is not a substitute for Android `.so` files.
- Run instrumentation tests on an emulator or physical device when changing Room, migrations, Android integration, backup/restore, or device-specific behavior.
- Do not claim tests passed unless the command or CI result was actually observed.
- If a check cannot run in the current environment, state exactly which check remains unverified and why.
- Do not repeatedly rerun checks that have already passed when no relevant code has changed.

## 7. Repository hygiene

- Do not commit generated build output, local machine configuration, IDE caches, or temporary files.
- Keep Gradle build directories, Rust `target/`, local.properties, and IDE metadata ignored.
- Keep source files, lockfiles, build scripts, tests, and required packaged runtime libraries under version control.
- Do not delete tracked files or native runtime libraries as a cleanup shortcut. Confirm whether the app or build depends on them first.
- Prefer reproducible build steps over committing locally generated intermediate artifacts.

## 8. Documentation

- The Project Specification is the product and technical source of truth. Keep it aligned with the implementation on `main`.
- Mark requirements as **Implemented**, **In progress**, **Planned**, or **Out of scope**; do not present a planned feature as an existing capability.
- Record architectural decisions that affect FSRS, persistence, backup compatibility, or writing interaction.
- Update the roadmap when scope or priority changes.
- Keep this file as development policy; keep product behavior and implementation status in the Project Specification.

## 9. Definition of done

A change is complete only when all applicable conditions are met:

1. It is based on the current `main` branch.
2. The implementation follows the existing architecture and these standards.
3. Relevant tests are added or updated.
4. Relevant local checks pass, or remaining verification is explicitly documented.
5. No unrelated files or generated artifacts are included.
6. The Specification and other affected documentation are updated.
7. The commit message accurately describes the delivered change.
