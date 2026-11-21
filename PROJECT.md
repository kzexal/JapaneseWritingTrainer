# Japanese Writing Trainer — Project Guide

> **Tài liệu chuẩn duy nhất cho yêu cầu sản phẩm, quyết định kỹ thuật, trạng thái triển khai và kế hoạch phát hành.**
> Baseline: `main` · Cập nhật: 21/11/2026 · Application ID: `com.example.japanesewritingtrainer`

## 1. Mục tiêu và phạm vi

Japanese Writing Trainer là ứng dụng Android offline-first để luyện viết tiếng Nhật bằng cảm ứng. Ứng dụng lưu tiến độ và lịch sử trên thiết bị; không yêu cầu tài khoản, máy chủ hoặc đồng bộ cloud.

**Phạm vi hiện tại:** Hiragana, Katakana và Kanji; Trace, Guided Writing, Recall Writing, Free Practice; SRS dùng FSRS-rs; lịch sử, thống kê, streak, widget, cài đặt và JSON backup/restore.

**Ưu tiên ngay lúc này:** hoàn tất cleanup repository, kiểm tra và phát hành thử bản hiện tại. Chỉ chuyển sang công việc Vocabulary sau khi người dùng xác nhận bản Release thử đã cài đặt và vận hành ổn. Không mở rộng Vocabulary/Excel pipeline trong giai đoạn Release thử.

## 2. Nền tảng và kiến trúc

| Hạng mục | Quyết định |
|---|---|
| Android | Android 8.0+ (API 26+) |
| Ngôn ngữ / UI | Kotlin, Jetpack Compose, Material 3 |
| Vẽ | Compose Canvas |
| Lưu trữ | Room + SharedPreferences |
| Nội dung | JSON assets đóng gói trong APK |
| Scheduler | FSRS-rs 6.6.2 qua Rust JNI |
| Native ABI | arm64-v8a và x86_64 |
| Package | `com.example.japanesewritingtrainer` |
| Phiên bản đang có trong Gradle | versionCode 2, versionName 1.1 |

Trách nhiệm phải được giữ tách biệt:
- **Compose UI:** hiển thị và chuyển tiếp thao tác.
- **Kotlin domain/session layer:** Learning/Relearning steps, hàng đợi, daily limit, retry, trạng thái card và due timestamps.
- **Room:** CardProgress và PracticeLog.
- **Rust FSRS-rs:** tính toán FSRS memory state và các khoảng lịch cho Again/Hard/Good/Easy.
- **Static assets:** nội dung ký tự và stroke geometry; không trộn với tiến độ cá nhân.

Không thay FSRS bằng SM-2. Không để UI/Room phụ thuộc kiểu dữ liệu JNI.

## 3. Yêu cầu sản phẩm và trạng thái hiện tại

### 3.1 Deck và thư viện

| Nội dung | Trạng thái / yêu cầu |
|---|---|
| Hiragana | 71 ký tự (46 cơ bản + 25 dakuten/handakuten); deck độc lập |
| Katakana | 71 ký tự (46 cơ bản + 25 dakuten/handakuten); deck độc lập |
| Kanji | 1.031 ký tự thuộc danh sách học N5–N2; deck độc lập |
| JLPT | N5–N2 là phân loại tham khảo từ cộng đồng, không phải danh sách Kanji chính thức |
| Kanji library | Tìm theo Kanji, âm đọc, Romaji, nghĩa; lọc theo bộ thủ |
| Vocabulary | Có workbook và các artifact hiện hữu trong repo. Không tiếp tục triển khai hoặc chỉnh pipeline trong giai đoạn Release thử |

Quy tắc định danh:
- ID card ổn định, không phụ thuộc thứ tự dòng Excel hoặc vị trí trong danh sách.
- Kana dùng namespace riêng theo script; Kanji dùng prefix `kanji_` và Unicode code point.
- Không được làm thay đổi ID hiện có khi cập nhật metadata.
- Không tự xóa CardProgress/PracticeLog nếu một dòng dữ liệu bị bỏ khỏi nguồn.
- Kanji metadata và stroke geometry là hai nguồn dữ liệu tách biệt. Không tạo stroke path từ StrokeCount.

### 3.2 Luyện viết

- Trace hiển thị mẫu và thứ tự nét.
- Guided Writing kiểm tra nét theo thứ tự, cung cấp feedback; nét sai không được tự động chuyển sang nét kế tiếp.
- Recall Writing cho phép tự viết và xem gợi ý/đáp án theo luồng học.
- Free Practice không tự cập nhật FSRS rating.
- Undo, Clear và Hint phải luôn truy cập được, không đẩy Canvas khỏi vùng nhìn.
- Canvas là vùng làm việc chính; giao diện chỉ hỗ trợ portrait.
- Không tự động suy ra Again/Hard/Good/Easy từ Stroke Matcher. Người học tự rating.

### 3.3 Learning, FSRS và session

Các trạng thái card: `NEW`, `LEARNING`, `RELEARNING`, `REVIEW`.

Các bất biến:
1. Mỗi rating hợp lệ tạo một chuyển trạng thái nhất quán và một PracticeLog tương ứng; chặn rating trùng khi thao tác lưu trước đó còn đang chạy.
2. `lastReviewedAtEpochMillis` là mốc cập nhật FSRS memory gần nhất, không phải timestamp của mọi bước Learning/Relearning ngắn.
3. Again/Hard ngắn trong Learning/Relearning không gọi lại FSRS memory calculation.
4. Graduation tính elapsed days từ FSRS anchor gần nhất.
5. Đồng hồ thiết bị lùi không được làm lùi FSRS anchor hoặc due date.
6. Queue, daily new-card limit, retry và Learning/Relearning policy do Kotlin quản lý.
7. Card gặp nhiều lần Again trong một phiên có thể được xử lý theo difficult-card policy; không được làm một card chiếm toàn bộ phiên.

Các kiểm tra scheduler/Room và khôi phục phiên đã được người dùng xác nhận PASS trong những lượt làm việc trước. Không tuyên bố test mới đã chạy nếu chưa có kết quả local mới.

### 3.4 Tiến độ, lịch sử, thống kê và widget

- Room lưu CardProgress và PracticeLog; schema hiện tại version 3.
- Mọi thay đổi schema phải có migration rõ ràng; tuyệt đối không dùng destructive migration.
- Home, Review Overview, History và Statistics phải làm mới dữ liệu khi quay lại màn hình; Room Flow tiếp tục là nguồn cập nhật tiến độ phù hợp.
- Thống kê tiến độ theo deck; streak là thống kê toàn ứng dụng dựa trên PracticeLog.
- Widget Android 2×1 chỉ đọc dữ liệu, hiển thị streak và số thẻ đến hạn; không tự sửa tiến độ.
- JSON backup/restore gồm CardProgress và PracticeLog; validate trước khi restore và thay thế trong một Room transaction. Restore là replace, không phải merge.
- Không thêm đăng nhập, cloud sync, Firebase, analytics hoặc đồng bộ Anki.

### 3.5 Giao diện và trải nghiệm

- Màu thương hiệu indigo `#6667AB`; dùng bảng màu Light/Dark cố định trong thiết kế, không dùng dynamic color theo wallpaper.
- Theme: Light / Dark / System.
- Trạng thái học: New màu xanh dương, Learning màu đỏ, Learned/Review màu xanh lá; thứ tự hiển thị New → Learning → Learned.
- Due count dùng màu cảnh báo.
- Không thêm thanh tiến độ tổng thể nếu không có yêu cầu mới.
- Widget chỉ hỗ trợ 2×1.
- Bỏ qua TalkBack, font scale lớn và landscape trong phạm vi hiện tại.
- Ưu tiên giao diện gọn, rõ, Canvas lớn; không thêm màn hoặc chế độ luyện ngoài phạm vi đã thống nhất.

## 4. Dữ liệu Kanji và hiệu năng

- Metadata: `app/src/main/assets/characters/kanji.json`.
- Stroke geometry nguồn: KanjiVG/kanji-db-data, được đóng gói trong `kanji_strokes_shards/`.
- Các shard giúp chỉ nạp geometry của ký tự cần học; không nạp toàn bộ geometry khi mở thư viện.
- Kanji metadata được preload ở background; không chặn UI thread.
- Search index và radical group được tạo một lần cho tập metadata đang dùng.
- Romaji search bao gồm onyomi, kunyomi và example words.
- Không xóa monolithic/source JSON hoặc generator chỉ vì shard đã tồn tại: phải kiểm tra toolchain và build references trước khi loại bỏ.
- Dataset validator phải kiểm tra tổng số record, ID/glyph duy nhất, metadata hợp lệ, shard mapping, stroke count/order, SVG paths và viewBox.

Nguồn và điều khoản dữ liệu được ghi tại `JapaneseWritingTrainer/THIRD_PARTY_NOTICES.md`. Giữ attribution cùng các notice/licence assets khi phân phối. Việc xác nhận provenance/version workbook và rà soát nghĩa vụ ShareAlike là điều kiện trước khi phát hành công khai.

## 5. Quy tắc phát triển và repository

- Làm việc trực tiếp trên `main`; không tạo feature branch, merge hoặc cherry-pick trừ khi người dùng yêu cầu.
- Đọc phiên bản file mới nhất trước khi sửa; không ghi đè thay đổi không liên quan.
- Commit theo nhóm thay đổi có ý nghĩa, message mô tả chính xác.
- Không commit keystore, mật khẩu, token, local.properties, IDE cache, build output hoặc Rust target.
- Giữ Gradle wrapper, lockfiles, mã nguồn, tests, assets runtime và native libraries cần thiết.
- Không chạy GitHub Actions; kiểm tra được thực hiện local bằng Android Studio/terminal.
- Không tự tăng versionCode/versionName. Chỉ thay đổi khi người dùng chốt version phát hành.
- Không xóa dữ liệu người học hoặc thay đổi Room/backup contract nếu chưa có migration và kiểm thử tương ứng.
- Không khẳng định build/test PASS nếu chưa quan sát kết quả từ người dùng hoặc môi trường chạy thực tế.

## 6. Kiểm tra local

Mở thư mục `JapaneseWritingTrainer/` (nơi có `settings.gradle.kts`) trong Android Studio.

PowerShell:

```powershell
# Rust bridge (chỉ khi thay đổi Rust/JNI)
cd native/fsrs-bridge
cargo fmt --check
cargo test
cargo clippy --all-targets -- -D warnings
cargo build --release
cd ../..

# Android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Instrumentation tests cần emulator/thiết bị kết nối. Không chạy lại các test không liên quan nếu không có thay đổi mã nguồn ảnh hưởng.

## 7. Release thử — điều kiện trước khi chuyển sang Vocabulary

### 7.1 Trạng thái

- Application ID: `com.example.japanesewritingtrainer`.
- Gradle hiện đặt versionCode 2 / versionName 1.1; chưa tự ý bump.
- Gradle đã hỗ trợ ký Release bằng bốn biến môi trường; hiện chưa có bằng chứng một keystore đã được tạo và bản APK Release đã ký/cài đặt thành công.
- Debug build và các kiểm tra local/device trước đây đã được người dùng xác nhận PASS; cần chạy lại các kiểm tra liên quan sau cleanup.
- Không phát hành công khai trước khi hoàn tất rà soát attribution/license và signing.

### 7.2 Tạo keystore an toàn trên máy phát hành

Không đưa keystore hoặc mật khẩu vào Git/chat. Tạo keystore ngoài repository:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\android-signing"
keytool -genkeypair -v -keystore "$env:USERPROFILE\android-signing\japanese-writing-trainer-release.jks" -alias japanese-writing-trainer -keyalg RSA -keysize 4096 -validity 10000
```

Đặt các biến môi trường trong PowerShell hiện tại (nhập giá trị thực tại máy, không commit):

- `JAPANESE_WRITING_TRAINER_KEYSTORE`
- `JAPANESE_WRITING_TRAINER_STORE_PASSWORD`
- `JAPANESE_WRITING_TRAINER_KEY_ALIAS`
- `JAPANESE_WRITING_TRAINER_KEY_PASSWORD`

Sao lưu keystore ở ít nhất hai vị trí an toàn độc lập. Mất keystore có thể khiến không thể phát hành bản cập nhật tương thích.

### 7.3 Build và cài thử

Sau khi cleanup được pull về và đã chạy các kiểm tra Debug:

```powershell
.\gradlew.bat :app:assembleRelease
```

APK dự kiến: `app/build/outputs/apk/release/app-release.apk`.

Trên thiết bị:
- Xác minh APK được ký bằng `apksigner verify --verbose --print-certs app-release.apk`.
- Cài bản Release lên thiết bị thử; không gỡ app hoặc xóa dữ liệu trên thiết bị duy nhất đang chứa tiến độ học.
- Kiểm tra khởi động, từng deck, Guided/Recall, FSRS rating, Room persistence, History, Statistics, widget, nhắc học, offline và JSON backup/restore.
- Force-stop/mở lại và xác minh tiến độ/cài đặt không mất.
- Nếu thử nâng cấp, cài đè lên bản hiện có và kiểm tra dữ liệu được giữ nguyên.
- Ghi lại thiết bị/API, version, kết quả và lỗi phát sinh trước khi tuyên bố Release thử đạt.

### 7.4 Gate hoàn tất Release thử

Chỉ chuyển sang Vocabulary khi người dùng xác nhận:
- [ ] Cleanup commit đã pull và build Debug/tests phù hợp PASS.
- [ ] Release APK đã ký và cài đặt được trên thiết bị thử.
- [ ] Các luồng học, FSRS, lưu dữ liệu, widget/nhắc học và backup/restore đã được kiểm tra.
- [ ] Không phát hiện mất dữ liệu hoặc lỗi nghiêm trọng sau force-stop/restart.
- [ ] Đã ghi nhận các vấn đề còn lại; người dùng đồng ý chuyển sang Vocabulary.

## 8. Backlog sau Release thử

1. Chỉ sau khi Release thử đạt: tiếp tục hoàn thiện Vocabulary dựa trên workbook Excel đã có.
2. Kiểm tra clean install trên thiết bị phụ/emulator; không xóa dữ liệu học duy nhất.
3. Rà soát attribution/provenance và ShareAlike trước phân phối công khai.
4. Quyết định chính sách Android Auto Backup so với JSON backup thủ công.
5. Các tính năng ngoài phạm vi như yōon writing units, nhận dạng ML/CNN, cloud sync/login chỉ xem xét khi có yêu cầu riêng.

## 9. Tài liệu còn được giữ

- `README.md`: trang giới thiệu và hướng dẫn mở/build ngắn gọn, trỏ về tài liệu này.
- `JapaneseWritingTrainer/THIRD_PARTY_NOTICES.md`: thông báo ghi công và điều khoản dữ liệu/native dependencies bắt buộc giữ khi phân phối.
- `vocabulary.xlsx`: workbook nguồn hiện có; không chỉnh sửa trong giai đoạn Release thử.
- Các source code, tests, assets, generator và native libraries cần thiết cho ứng dụng.
