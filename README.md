# Japanese Writing Trainer

Ứng dụng Android offline-first hỗ trợ luyện viết Hiragana, Katakana và Kanji bằng cảm ứng, kết hợp Guided Writing, Recall, Free Practice và ôn tập lặp lại ngắt quãng FSRS.

- **Nền tảng:** Kotlin, Jetpack Compose, Room, Rust JNI / FSRS-rs 6.6.2.
- **Android:** API 26 trở lên.
- **Application ID:** `com.example.japanesewritingtrainer`.
- **Dữ liệu học:** lưu cục bộ; hỗ trợ JSON backup/restore; không yêu cầu tài khoản hoặc cloud sync.

## Bắt đầu

1. Clone repository.
2. Mở thư mục `JapaneseWritingTrainer/` trong Android Studio (thư mục chứa `settings.gradle.kts`).
3. Chờ Gradle Sync, chọn thiết bị Android rồi chạy app.

## Build và kiểm tra (Windows PowerShell)

Chạy tại thư mục `JapaneseWritingTrainer/`:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Instrumentation tests cần emulator hoặc thiết bị Android đang kết nối. Rust toolchain/NDK/cargo-ndk chỉ cần khi thay đổi hoặc build lại JNI bridge.

## Tài liệu dự án

**[PROJECT.md](PROJECT.md)** là tài liệu chuẩn duy nhất cho yêu cầu sản phẩm, kiến trúc, quyết định UI, trạng thái triển khai, quy tắc phát triển và checklist Release thử.

Ghi công và giấy phép dữ liệu: [THIRD_PARTY_NOTICES.md](JapaneseWritingTrainer/THIRD_PARTY_NOTICES.md).

## Phát hành

Release APK cần được ký bằng keystore riêng lưu ngoài repository. Không commit keystore hoặc mật khẩu. Chưa chuyển sang phát triển Vocabulary cho đến khi người dùng xác nhận Release thử đạt các điều kiện trong `PROJECT.md`.
