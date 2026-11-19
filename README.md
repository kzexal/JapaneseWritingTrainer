# Japanese Writing Trainer

Ứng dụng Android hỗ trợ luyện viết tiếng Nhật bằng màn hình cảm ứng và ôn tập lặp lại ngắt quãng. Ứng dụng hoạt động theo hướng offline-first: dữ liệu học tập được lưu cục bộ trên thiết bị, không cần tài khoản.

## Nội dung hiện có

- **Hiragana:** 71 ký tự (46 cơ bản + 25 Dakuten/Handakuten).
- **Katakana:** 71 ký tự (46 cơ bản + 25 Dakuten/Handakuten).
- **Kanji:** 86 ký tự lấy từ sheet `Kanji` trong workbook `vocabulary.xlsx`; metadata được quản lý riêng với stroke geometry.
- **Vocabulary:** 198 từ từ sheet `Vocabulary`, hỗ trợ tìm kiếm đa trường, lọc 15 nhóm và xem chi tiết. Đây là thư viện tra cứu; không có phiên luyện viết Japanese/Reading.
- **Daily streak:** streak được tính trên toàn bộ hoạt động học đã lưu của mọi deck; Statistics và Android Home-screen Widget dùng chung dữ liệu PracticeLog và cùng phép tính.
- Hiragana, Katakana và Kanji có deck riêng. Chọn một ký tự trong thư viện để mở màn hình luyện viết.
- Thư viện ký tự hỗ trợ tìm kiếm; Kanji hỗ trợ lọc theo JLPT và bộ thủ.
- Guided Writing luyện từng nét theo đúng thứ tự, sử dụng stroke geometry.
- Ôn tập theo FSRS-6 với các mức Again, Hard, Good và Easy.
- Quản lý thẻ mới, thẻ đang học và thẻ đến hạn; giới hạn số thẻ mới mỗi ngày được cấu hình riêng cho từng deck.
- Lịch sử luyện tập và thống kê tiến độ.
- Tùy chỉnh giao diện, lưới ký tự và gợi ý.
- Sao lưu và khôi phục dữ liệu học tập bằng tệp JSON.
- Hoạt động offline; không yêu cầu đăng nhập hoặc đồng bộ nhiều thiết bị.

## Chuẩn bị phát hành

Checklist phát hành Android (icon, signing keystore, version, Release build, quyền và giấy phép) được theo dõi tại [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). Debug build thành công không thay thế việc xác minh Release APK/AAB đã ký.

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

## Audit sheet Vocabulary

Trước khi thiết kế Vocabulary Deck, có thể kiểm tra cấu trúc sheet bằng công cụ chỉ đọc:

```powershell
py -3.13 tools/audit_vocabulary.py
```

Công cụ in header thực tế, số dòng dữ liệu, số ô có dữ liệu, số giá trị trùng theo từng cột và một số dòng mẫu. Có thể tăng số dòng mẫu bằng `--sample-rows 10`. Công cụ không sửa workbook và không sinh hoặc thay đổi dữ liệu ứng dụng.

## Cập nhật dữ liệu Vocabulary từ Excel

Sheet `Vocabulary` trong workbook `vocabulary.xlsx` là nguồn dữ liệu nội dung từ vựng. Công cụ chỉ đọc workbook và sinh metadata JSON; không sửa Excel, không tạo hoặc cập nhật tiến độ học.

Mở PowerShell tại thư mục `JapaneseWritingTrainer/`:

```powershell
py -3.13 tools/generate_vocabulary.py
```

Kết quả được ghi vào `app/src/main/assets/characters/vocabulary.json`. ID được tạo từ toàn bộ Unicode code point của trường `Japanese`, không phụ thuộc vị trí dòng. Vì vậy, hai từ đồng âm như `春` và `貼る` vẫn là hai card riêng; các câu ví dụ trùng nhau cũng không làm các card bị gộp. Khi thêm từ hoặc sắp xếp lại workbook, ID hiện có không đổi.

Các trường JLPT, Notes và Tags đang trống trong workbook sẽ được biểu diễn bằng JSON `null`. Generator yêu cầu Japanese, Reading và Vietnamese; các trường ví dụ, nhóm, radicals và mnemonics có thể để trống. Ứng dụng đọc JSON qua `VocabularyRepository`. Dữ liệu Vocabulary chỉ phục vụ tra cứu và xem chi tiết; không tạo phiên luyện viết Japanese/Reading.

## Cập nhật dữ liệu Kanji từ Excel

Workbook `vocabulary.xlsx` ở thư mục gốc repository là nguồn dữ liệu metadata Kanji. Generator chỉ đọc workbook, không chỉnh sửa file Excel. Mở PowerShell tại thư mục `JapaneseWritingTrainer/`:

```powershell
# Cài dependency Python (chỉ cần thực hiện một lần)
py -3.13 -m pip install -r tools/requirements.txt

# Chạy kiểm thử công cụ tạo dữ liệu
py -3.13 -m unittest discover -s tools/tests -v

# Sinh metadata JSON sau khi cập nhật sheet Kanji
py -3.13 tools/generate_kanji.py
```

Generator kiểm tra header, ký tự trùng, StrokeCount và sinh ID theo Unicode code point (không phụ thuộc số thứ tự dòng). Các giá trị chuỗi `None`/`null` được chuẩn hóa thành JSON `null`. Kết quả được ghi vào `app/src/main/assets/characters/kanji.json`.

### Cập nhật stroke geometry Kanji

Stroke geometry được lấy từ [KanjiVG](https://github.com/KanjiVG/kanjivg), không được suy ra từ trường `StrokeCount`. Sau khi tải và giải nén dữ liệu KanjiVG vào `tools/vendor/kanjivg/`, chạy:

```powershell
py -3.13 tools/import_kanjivg_strokes.py --source-dir tools/vendor/kanjivg/kanji
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

Sử dụng chức năng sao lưu/khôi phục JSON trong **Cài đặt** để lưu dữ liệu học tập. Tệp backup hiện chứa `CardProgress` (bao gồm trạng thái và FSRS memory/scheduling fields) cùng toàn bộ `PracticeLog`; dữ liệu ký tự tĩnh nằm trong app assets nên không cần xuất. Cài đặt giao diện và giới hạn thẻ mới lưu trong SharedPreferences không nằm trong tệp backup.

Khi khôi phục, ứng dụng yêu cầu xác nhận trước khi thay thế toàn bộ tiến độ và lịch sử hiện có. JSON được kiểm tra trước; dữ liệu chỉ được thay thế trong một Room transaction, nên lỗi parse/validation/ghi dữ liệu không để lại trạng thái khôi phục dở dang. Giữ ít nhất một bản sao ở vị trí an toàn, tách khỏi thiết bị. Không xem Android Auto Backup là bản sao lưu duy nhất.

Trước khi gỡ cài đặt hoặc chuyển thiết bị, hãy tạo backup mới và kiểm tra có thể đọc/khôi phục trên thiết bị mục tiêu. Restore là thao tác thay thế dữ liệu, không phải merge hai bộ tiến độ.

## Trạng thái và công việc còn lại

- Vocabulary hiện chỉ dùng để tra cứu; tính năng luyện viết Japanese/Reading đã được gỡ khỏi giao diện.
- Streak toàn ứng dụng và Android Home-screen Widget đã triển khai; unit tests, build, instrumentation và kiểm tra widget trên thiết bị đã PASS.
- Backup/restore lưu CardProgress và PracticeLog, có validation và transaction; các kiểm thử edge case và kiểm tra khôi phục trên thiết bị đã PASS.
- Tiếp tục rà soát quy trình cập nhật nội dung từ workbook, đặc biệt tính ổn định ID và nguyên tắc không tự xóa progress khi bỏ dòng khỏi workbook.
- Công việc trước bản phát hành: thiết kế app icon/adaptive icon, rà soát attribution/license KanjiVG, tạo và bảo vệ signing keystore, cập nhật version/changelog, tạo release APK/AAB và kiểm tra cài đặt/nâng cấp trên thiết bị.
- Import workbook trực tiếp trong ứng dụng vẫn để giai đoạn sau; hiện tại workbook trên PC là nguồn dữ liệu và Python generator tạo JSON assets.

