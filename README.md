# Japanese Writing Trainer

Ứng dụng Android hỗ trợ luyện viết tiếng Nhật bằng màn hình cảm ứng và ôn tập lặp lại ngắt quãng. Ứng dụng hoạt động theo hướng offline-first: dữ liệu học tập được lưu cục bộ trên thiết bị, không cần tài khoản.

## Nội dung hiện có

- **Hiragana:** 71 ký tự (46 cơ bản + 25 Dakuten/Handakuten).
- **Katakana:** 71 ký tự (46 cơ bản + 25 Dakuten/Handakuten).
- **Kanji:** 86 ký tự lấy từ sheet `Kanji` trong workbook `vocabulary.xlsx`; metadata được quản lý riêng với stroke geometry.
- Mỗi bộ chữ có thể được chọn thành deck học riêng; tiến độ được lưu theo ID ổn định của ký tự.
- Thư viện ký tự hỗ trợ tìm kiếm; Kanji hỗ trợ lọc theo JLPT và bộ thủ.
- Guided Writing luyện từng nét theo đúng thứ tự, sử dụng stroke geometry.
- Ôn tập theo FSRS-6 với các mức Again, Hard, Good và Easy.
- Quản lý thẻ mới, thẻ đang học và thẻ đến hạn; giới hạn số thẻ mới mỗi ngày được cấu hình riêng cho từng deck.
- Lịch sử luyện tập và thống kê tiến độ.
- Tùy chỉnh giao diện, lưới ký tự và gợi ý.
- Sao lưu và khôi phục dữ liệu học tập bằng tệp JSON.
- Hoạt động offline; không yêu cầu đăng nhập hoặc đồng bộ nhiều thiết bị.

## Công nghệ

- Kotlin
- Android SDK, Jetpack Compose và Material 3
- Room Database
- Rust qua JNI cho phần tính toán FSRS, sử dụng crate `fsrs = 6.6.2`
- Gradle Kotlin DSL
- Python và openpyxl cho công cụ tạo dữ liệu từ workbook

## Cấu trúc repository

```text
JapaneseWritingTrainer/
├── README.md
├── DEVELOPMENT_STANDARDS.md
├── Japanese Writing Trainer – Project Specification.md
├── vocabulary.xlsx
└── JapaneseWritingTrainer/
    ├── THIRD_PARTY_NOTICES.md
    ├── app/
    │   └── src/main/
    │       ├── assets/characters/
    │       │   ├── hiragana.json
    │       │   ├── katakana.json
    │       │   ├── kanji.json
    │       │   └── kanji_strokes.json
    │       ├── java/com/example/japanesewritingtrainer/
    │       └── jniLibs/
    ├── native/
    │   └── fsrs-bridge/
    └── tools/
        ├── generate_kanji.py
        ├── import_kanjivg_strokes.py
        ├── requirements.txt
        └── tests/
```

## Yêu cầu môi trường

- Android Studio tương thích với Android Gradle Plugin được khai báo trong dự án.
- Android SDK Platform và Build-Tools theo cấu hình Gradle.
- Thiết bị Android hoặc emulator chạy Android 8.0 (API 26) trở lên.
- Python 3.10 trở lên và dependency trong `tools/requirements.txt` nếu cần sinh lại dữ liệu Kanji.
- Rust toolchain, Android NDK, Android Rust targets và `cargo-ndk` chỉ cần khi biên dịch lại thư viện JNI.

Application ID: `com.example.japanesewritingtrainer`.

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
```

Đầu ra debug APK thường nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

Không cần chạy lại toàn bộ build/test chỉ để chỉnh sửa tài liệu. Khi thay đổi mã nguồn, chạy các lệnh kiểm thử phù hợp trước khi sử dụng bản build mới.

## Cập nhật dữ liệu Kanji từ Excel

Workbook `vocabulary.xlsx` ở thư mục gốc repository là nguồn dữ liệu metadata Kanji. Generator chỉ đọc workbook, không chỉnh sửa file Excel. Mở PowerShell tại thư mục `JapaneseWritingTrainer/`:

```powershell
# Cài dependency Python (chỉ cần thực hiện một lần)
python -m pip install -r tools/requirements.txt

# Chạy kiểm thử công cụ tạo dữ liệu
python -m unittest discover -s tools/tests -v

# Sinh metadata JSON sau khi cập nhật sheet Kanji
python tools/generate_kanji.py
```

Generator kiểm tra header, ký tự trùng, StrokeCount và sinh ID theo Unicode code point (không phụ thuộc số thứ tự dòng). Các giá trị chuỗi `None`/`null` được chuẩn hóa thành JSON `null`. Kết quả được ghi vào `app/src/main/assets/characters/kanji.json`.

### Cập nhật stroke geometry Kanji

Stroke geometry được lấy từ [KanjiVG](https://github.com/KanjiVG/kanjivg), không được suy ra từ trường `StrokeCount`. Sau khi tải và giải nén dữ liệu KanjiVG vào `tools/vendor/kanjivg/`, chạy:

```powershell
python tools/import_kanjivg_strokes.py --source-dir tools/vendor/kanjivg/kanji
```

Công cụ đối chiếu từng ký tự và số nét với workbook, giữ thứ tự nét từ SVG và sinh `app/src/main/assets/characters/kanji_strokes.json`. Nếu thiếu SVG hoặc số nét không khớp, công cụ dừng với lỗi thay vì tạo geometry không hợp lệ.

Dữ liệu KanjiVG được phân phối theo CC BY-SA 3.0. Xem `JapaneseWritingTrainer/THIRD_PARTY_NOTICES.md` để biết thông tin ghi công và giấy phép. Khi phân phối ứng dụng, cần giữ thông tin ghi công và tuân thủ điều kiện của giấy phép đối với phần dữ liệu phái sinh.

## Biên dịch thư viện Rust JNI

Các thư viện native đã được tích hợp để Gradle đóng gói cùng ứng dụng. Chỉ cần biên dịch lại khi thay đổi mã Rust hoặc cập nhật FSRS bridge. Trên Windows, mở PowerShell tại thư mục `JapaneseWritingTrainer/`:

```powershell
# Chỉ cài một lần nếu máy chưa có cargo-ndk
cargo install cargo-ndk

# Build thư viện cho các Android ABI được cấu hình
.\native\build-android.ps1

# Build ứng dụng
.\gradlew.bat :app:assembleDebug
```

Rust đảm nhiệm phép tính FSRS; Kotlin quản lý chính sách học, hàng đợi, trạng thái thẻ, thời điểm đến hạn và lưu trữ Room.

## Sao lưu và khôi phục

Sử dụng chức năng sao lưu/khôi phục JSON trong ứng dụng để chủ động lưu dữ liệu học tập. Nên giữ bản sao lưu ở vị trí an toàn, tách khỏi thiết bị đang sử dụng. Không nên xem Android Auto Backup là bản sao lưu duy nhất vì khả năng khôi phục còn phụ thuộc thiết bị, tài khoản và cấu hình hệ thống.

Trước khi gỡ cài đặt hoặc chuyển thiết bị, hãy tạo và kiểm tra tệp JSON backup.

## Lộ trình tiếp theo

- Hoàn thiện kiểm thử thực tế cho Kanji Learning, Guided Writing, History và Statistics.
- Kiểm tra an toàn luồng cập nhật nội dung: thêm Kanji mới không cần hard-code số lượng; cập nhật metadata không reset tiến độ; bỏ dòng khỏi workbook không tự xóa lịch sử học.
- Rà soát backup/restore với dữ liệu Kanji.
- Hoàn thiện nhận diện ứng dụng (app icon/adaptive icon), thông tin phiên bản và chuẩn bị bản phát hành.
- Đánh giá khả năng nhập workbook trực tiếp trong ứng dụng ở giai đoạn sau; hiện tại workbook trên PC vẫn là nguồn dữ liệu và công cụ Python tạo JSON.

