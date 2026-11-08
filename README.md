# Japanese Writing Trainer

Ứng dụng Android hỗ trợ luyện viết tiếng Nhật bằng màn hình cảm ứng. Phiên bản hiện tại hỗ trợ **71 Hiragana và 71 Katakana** (46 cơ bản + 25 Dakuten/Handakuten mỗi bộ), ghi nhớ thứ tự nét và ôn tập lặp lại ngắt quãng. Ứng dụng được thiết kế theo hướng offline-first: dữ liệu học tập được lưu trên thiết bị, không cần tài khoản.

> **Trạng thái:** Hiragana và 46 Katakana cơ bản đã được triển khai, có thể chọn học riêng hoặc kết hợp. Yōon (âm ghép), Kanji và nhập từ vựng từ workbook Excel vẫn thuộc roadmap.

## Chức năng hiện có

- Thư viện 71 Hiragana và 71 Katakana, gồm kana cơ bản, Dakuten và Handakuten, có nhóm ký tự và tìm kiếm.
- Chọn học Hiragana, Katakana hoặc kết hợp cả hai; tiến độ được lưu riêng theo ID của từng ký tự.
- Xem thứ tự nét và hình dạng nét của từng ký tự.
- Bốn chế độ luyện tập:
  - **Trace:** tập tô theo nét mẫu.
  - **Guided Writing:** luyện từng nét theo thứ tự, có đánh giá nét viết.
  - **Recall Writing:** tự viết ký tự từ trí nhớ.
  - **Free Practice:** luyện viết tự do.
- Ôn tập theo FSRS-6 với các mức đánh giá Again, Hard, Good và Easy.
- Quản lý thẻ mới, đang học và cần ôn; giới hạn số thẻ mới mỗi ngày.
- Lịch sử luyện tập và thống kê tiến độ.
- Tùy chỉnh giao diện, lưới ký tự và gợi ý.
- Sao lưu và khôi phục dữ liệu học tập bằng tệp JSON.
- Hoạt động offline; tiến độ và cài đặt được lưu cục bộ trên thiết bị.

## Công nghệ

- Kotlin
- Android SDK, Jetpack Compose và Material 3
- Room Database
- Rust qua JNI cho phần tính toán FSRS, sử dụng crate `fsrs = 6.6.2`
- Gradle Kotlin DSL

## Cấu trúc repository

```text
JapaneseWritingTrainer/
├── README.md
├── DEVELOPMENT_STANDARDS.md
├── Japanese Writing Trainer – Project Specification.md
├── vocabulary.xlsx
└── JapaneseWritingTrainer/
    ├── app/
    │   └── src/main/
    │       ├── assets/characters/
    │       │   ├── hiragana.json
    │       │   └── katakana.json
    │       ├── java/com/example/japanesewritingtrainer/
    │       └── jniLibs/
    ├── native/
    │   ├── build-android.ps1
    │   ├── build-android.sh
    │   └── fsrs-bridge/
    └── tools/
        ├── generate_hiragana.py
        └── generate_katakana.py
```

## Yêu cầu môi trường

- Android Studio tương thích với Android Gradle Plugin được khai báo trong dự án.
- Android SDK Platform 37 và Android SDK Build-Tools tương ứng.
- Thiết bị Android hoặc emulator chạy Android 8.0 (API 26) trở lên.
- Nếu cần biên dịch lại thư viện Rust JNI: cài Rust toolchain, Android NDK, các Rust Android targets và `cargo-ndk`.

Các phiên bản plugin và thư viện được quản lý trong `JapaneseWritingTrainer/gradle/libs.versions.toml`. Application ID: `com.example.japanesewritingtrainer`.

## Mở và chạy ứng dụng

1. Clone repository.
2. Mở thư mục `JapaneseWritingTrainer/` (thư mục chứa `settings.gradle.kts`) bằng Android Studio.
3. Chờ Gradle Sync hoàn tất.
4. Chọn thiết bị Android hoặc emulator rồi chạy cấu hình `app`.

## Build và kiểm thử

Mở PowerShell tại thư mục `JapaneseWritingTrainer/`:

```powershell
# Unit tests
.\gradlew.bat testDebugUnitTest

# Instrumentation tests (cần thiết bị hoặc emulator đang kết nối)
.\gradlew.bat connectedDebugAndroidTest

# Build debug APK
.\gradlew.bat :app:assembleDebug

# Build release APK và Android App Bundle
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

Đầu ra thường nằm tại:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

Release APK được tạo theo cấu hình hiện tại là **unsigned**. Muốn phân phối bản phát hành cần cấu hình ký ứng dụng bằng keystore riêng. Không đưa keystore hoặc mật khẩu ký vào repository.

## Biên dịch lại thư viện Rust JNI (khi cần)

Các thư viện native đã được tích hợp để Gradle đóng gói cùng ứng dụng. Nếu thay đổi mã Rust hoặc cần tạo lại thư viện, trên Windows mở PowerShell tại thư mục `JapaneseWritingTrainer/`:

```powershell
# Chỉ cần cài một lần nếu máy chưa có cargo-ndk
cargo install cargo-ndk

# Build thư viện cho arm64-v8a và x86_64
.\native\build-android.ps1

# Sau đó build ứng dụng
.\gradlew.bat :app:assembleDebug
```

Script yêu cầu Rust/Cargo, Android NDK và Android toolchain đã được cài đặt, cấu hình. Mã Rust bridge nằm trong `native/fsrs-bridge/`; Kotlin gọi qua JNI. Rust đảm nhiệm phép tính FSRS, còn Kotlin quản lý luồng học, hàng đợi, trạng thái thẻ và lưu trữ Room.

## Sao lưu và khôi phục

Sử dụng chức năng sao lưu/khôi phục JSON trong ứng dụng để chủ động lưu dữ liệu học tập. Nên giữ bản sao lưu ở vị trí an toàn, tách khỏi thiết bị đang sử dụng.

Android Auto Backup có thể hỗ trợ khôi phục dữ liệu trong một số tình huống, nhưng phụ thuộc vào thiết bị, tài khoản và cấu hình hệ thống; không nên xem đây là bản sao lưu duy nhất. Trước khi gỡ cài đặt hoặc chuyển thiết bị, hãy tạo và kiểm tra tệp JSON backup.

## Dữ liệu và ghi công

Dataset Hiragana và Katakana (71 ký tự mỗi bộ, gồm 46 ký tự cơ bản + 25 Dakuten/Handakuten) được lưu lần lượt tại `app/src/main/assets/characters/hiragana.json` và `app/src/main/assets/characters/katakana.json`. Script tạo dữ liệu tương ứng nằm trong `tools/generate_hiragana.py` và `tools/generate_katakana.py`.

Dữ liệu thứ tự nét có nguồn gốc từ dự án [KanjiVG](https://github.com/KanjiVG/kanjivg). Thông tin ghi công và giấy phép của dữ liệu bên thứ ba cần được rà soát, hoàn thiện cùng đợt bổ sung Katakana và Kanji trước khi phát hành chính thức. Giấy phép của dữ liệu bên thứ ba không mặc nhiên áp dụng cho toàn bộ mã nguồn ứng dụng.

## Lộ trình

1. Hoàn thiện README và bổ sung tab **Hướng dẫn học** trong ứng dụng.
2. Hoàn thiện Hiragana/Katakana mở rộng và thiết kế âm ghép (yōon).
3. Bổ sung Kanji dựa trên dữ liệu trong workbook `vocabulary.xlsx`; cấu trúc workbook sẽ được kiểm tra trước khi ánh xạ dữ liệu.
4. Xây dựng chức năng nhập và học từ vựng từ cùng workbook.
5. Rà soát ghi công/giấy phép, ký ứng dụng và chuẩn bị bản phát hành.

