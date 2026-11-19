# Japanese Writing Trainer

Ứng dụng Android hỗ trợ người học tiếng Nhật luyện viết Hiragana, Katakana và Kanji bằng cảm ứng, kết hợp hướng dẫn thứ tự nét và ôn tập lặp lại ngắt quãng (SRS). Ứng dụng theo định hướng **offline-first**: nội dung được đóng gói trong ứng dụng, tiến độ và lịch sử học tập được lưu cục bộ trên thiết bị; không yêu cầu tài khoản hoặc đồng bộ cloud.

## Nội dung và chức năng

- **Hiragana:** 71 ký tự (46 cơ bản + 25 dakuten/handakuten).
- **Katakana:** 71 ký tự (46 cơ bản + 25 dakuten/handakuten).
- **Kanji:** 1.031 ký tự thuộc các danh sách học N5–N2. Phân loại JLPT là dữ liệu tham khảo từ cộng đồng, không phải danh sách Kanji chính thức của JLPT.
- **Vocabulary:** 198 mục từ để tra cứu, tìm kiếm và lọc theo nhóm; không tạo tiến độ SRS hay phiên luyện viết riêng.
- Mỗi deck Hiragana, Katakana và Kanji có tiến độ học độc lập. Chọn ký tự trong thư viện để mở luồng luyện viết.
- Các chế độ luyện viết gồm Trace, Guided Writing, Recall Writing và Free Practice.
- Guided Writing hướng dẫn theo thứ tự nét dựa trên stroke geometry; Recall Writing cho phép tự viết và xem gợi ý/đáp án theo luồng học.
- Ôn tập dùng FSRS-rs 6.6.2, với các mức Again, Hard, Good và Easy.
- Hàng đợi học quản lý thẻ mới, đang học, học lại và đến hạn; giới hạn thẻ mới mỗi ngày có thể cấu hình.
- Có lịch sử luyện tập, thống kê theo deck và streak toàn ứng dụng.
- Android Home-screen Widget kích thước 2×1 hiển thị streak và số thẻ đến hạn.
- Có tùy chỉnh giao diện, lưới viết, gợi ý và nhắc học hằng ngày.
- Hỗ trợ sao lưu/khôi phục tiến độ và lịch sử bằng tệp JSON.

## Công nghệ

- Kotlin, Android SDK, Jetpack Compose và Material 3
- Room Database và SharedPreferences
- Rust qua JNI cho phần tính toán FSRS (fsrs = 6.6.2)
- Gradle Kotlin DSL
- Python và openpyxl cho công cụ kiểm tra/sinh dữ liệu từ workbook

## Cấu trúc repository

~~~text
JapaneseWritingTrainer/
├── README.md
├── DEVELOPMENT_STANDARDS.md
├── Japanese Writing Trainer – Project Specification.md
├── RELEASE_CHECKLIST.md
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
    │       └── java/com/example/japanesewritingtrainer/
    ├── native/fsrs-bridge/
    └── tools/
~~~

## Yêu cầu môi trường

- Android Studio tương thích với Android Gradle Plugin trong dự án.
- Android SDK Platform/Build-Tools theo cấu hình Gradle.
- Thiết bị Android hoặc emulator chạy Android 8.0 (API 26) trở lên.
- Python 3.10 trở lên và các dependency trong tools/requirements.txt nếu cần cập nhật dữ liệu từ workbook.
- Rust toolchain, Android NDK, Android Rust targets và cargo-ndk chỉ cần khi biên dịch lại thư viện JNI.

Application ID: com.example.japanesewritingtrainer.

## Mở và chạy ứng dụng

1. Clone repository.
2. Mở thư mục JapaneseWritingTrainer/ (thư mục chứa settings.gradle.kts) bằng Android Studio.
3. Chờ Gradle Sync hoàn tất.
4. Chọn thiết bị Android hoặc emulator rồi chạy cấu hình app.

## Build và kiểm thử

Mở PowerShell tại thư mục JapaneseWritingTrainer/:

~~~powershell
# Unit tests
.\gradlew.bat testDebugUnitTest

# Instrumentation tests (cần thiết bị hoặc emulator đang kết nối)
.\gradlew.bat connectedDebugAndroidTest

# Build debug APK
.\gradlew.bat :app:assembleDebug
~~~

APK Debug thường nằm tại app/build/outputs/apk/debug/app-debug.apk.

Không cần chạy lại toàn bộ build/test chỉ để chỉnh sửa tài liệu. Khi thay đổi mã nguồn, chạy các lệnh kiểm thử phù hợp trước khi sử dụng bản build mới.

## Cập nhật dữ liệu từ Excel

Workbook vocabulary.xlsx ở root repository là nguồn nội dung cho các công cụ cập nhật dữ liệu. Các generator chỉ đọc workbook; không tự sửa file Excel và không cập nhật tiến độ Room.

Mở PowerShell tại thư mục JapaneseWritingTrainer/. Ví dụ với Python 3.13:

~~~powershell
# Cài dependency nếu môi trường Python chưa có
py -3.13 -m pip install -r tools/requirements.txt

# Chạy unit tests cho các công cụ dữ liệu
py -3.13 -m unittest discover -s tools/tests -v

# Sinh dữ liệu Hiragana/Katakana
py -3.13 tools/generate_hiragana.py
py -3.13 tools/generate_katakana.py

# Sinh metadata Kanji từ sheet Kanji
py -3.13 tools/generate_kanji.py

# Cập nhật workbook Kanji theo dữ liệu stroke hiện có (nếu cần)
py -3.13 tools/update_kanji_workbook.py --workbook ..\vocabulary.xlsx --output ..\vocabulary_updated.xlsx

# Sinh dữ liệu Vocabulary
py -3.13 tools/generate_vocabulary.py
~~~

Tên và tham số của từng công cụ có thể thay đổi theo phiên bản; dùng --help để xem tùy chọn hiện tại. Với update_kanji_workbook.py, cài openpyxl trong đúng interpreter được gọi (ví dụ py -3.13 -m pip install openpyxl).

### Kanji stroke geometry

Stroke geometry được lấy từ KanjiVG, không được suy ra từ trường StrokeCount trong Excel. Dữ liệu geometry được nhập và kiểm tra bằng công cụ trong tools/. Nếu thay đổi dữ liệu nguồn, cần chạy validator và đối chiếu số nét/thứ tự nét trước khi đóng gói lại assets.

## Biên dịch thư viện Rust JNI

Các thư viện native đã được tích hợp để Gradle đóng gói cùng ứng dụng. Chỉ cần biên dịch lại khi thay đổi mã Rust hoặc cập nhật FSRS bridge. Trên Windows, mở PowerShell tại thư mục JapaneseWritingTrainer/:

~~~powershell
cargo install cargo-ndk
.\native\build-android.ps1
.\gradlew.bat :app:assembleDebug
~~~

Rust đảm nhiệm phép tính FSRS; Kotlin quản lý chính sách học, hàng đợi, trạng thái thẻ, thời điểm đến hạn và lưu trữ Room.

## Sao lưu và khôi phục

Sử dụng chức năng JSON trong **Cài đặt** để sao lưu dữ liệu học tập. Backup thủ công chứa CardProgress (bao gồm FSRS memory/scheduling fields) và PracticeLog; không chứa content assets hoặc các cài đặt SharedPreferences.

Khi khôi phục, ứng dụng yêu cầu xác nhận trước khi thay thế tiến độ và lịch sử hiện có. JSON được validate trước và dữ liệu được thay thế trong một Room transaction. Restore là thao tác thay thế, không phải merge. Hãy lưu bản backup ở vị trí an toàn tách khỏi thiết bị và tạo backup mới trước khi gỡ ứng dụng hoặc chuyển thiết bị.

Android Auto Backup là cơ chế riêng của hệ điều hành; xem RELEASE_CHECKLIST.md để biết quyết định cấu hình sao lưu hệ điều hành trước khi phát hành.

## Nguồn dữ liệu và giấy phép

- Metadata Kanji dựa trên KANJIDIC2/OpenJLPT; phân loại cấp JLPT N5–N2 là tham khảo từ cộng đồng.
- Stroke geometry của kana và Kanji có nguồn từ KanjiVG.
- Thông tin ghi công và giấy phép được ghi trong JapaneseWritingTrainer/THIRD_PARTY_NOTICES.md và mục **Nguồn dữ liệu và giấy phép** trong ứng dụng. Cần hoàn tất rà soát nghĩa vụ ShareAlike trước khi phân phối công khai.

## Trạng thái phát hành

Các chức năng học cốt lõi, lưu tiến độ, FSRS, backup/restore, thư viện dữ liệu, thống kê và widget đã được triển khai trên main. Debug build và các kiểm tra local/device gần nhất đã được người dùng xác nhận PASS.

Gradle hỗ trợ cấu hình signing bằng bốn biến môi trường: JAPANESE_WRITING_TRAINER_KEYSTORE, JAPANESE_WRITING_TRAINER_STORE_PASSWORD, JAPANESE_WRITING_TRAINER_KEY_ALIAS và JAPANESE_WRITING_TRAINER_KEY_PASSWORD. Khi chưa cấu hình biến nào, Release build không được ký; nếu chỉ cấu hình thiếu một phần, Gradle sẽ dừng với thông báo lỗi.

Ví dụ tạo keystore trên Windows PowerShell (lệnh sẽ yêu cầu nhập mật khẩu; không thêm mật khẩu trực tiếp vào command):

~~~powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\android-signing"
keytool -genkeypair -v -keystore "$env:USERPROFILE\android-signing\japanese-writing-trainer-release.jks" -alias japanese-writing-trainer -keyalg RSA -keysize 4096 -validity 10000
~~~

Sau khi tạo, đặt bốn biến môi trường nêu trên trong môi trường build local; biến KEYSTORE phải trỏ tới đúng file .jks. Tạo ít nhất hai bản sao lưu keystore ở các vị trí an toàn độc lập. Không gửi keystore/mật khẩu qua chat, không đặt file trong repository và không commit vào Git.

Ứng dụng chưa được xem là sẵn sàng phát hành công khai cho đến khi có keystore thực tế, xác minh icon trên thiết bị, rà soát license/backup policy và kiểm tra bản Release đã ký. Theo dõi các bước tại RELEASE_CHECKLIST.md. Không commit keystore, mật khẩu hoặc signing properties vào repository.
