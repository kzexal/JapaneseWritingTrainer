# Japanese Writing Trainer — Project Guide

> **Tài liệu chuẩn duy nhất cho yêu cầu sản phẩm, quyết định kỹ thuật, trạng thái triển khai và kế hoạch phát hành.**
> Baseline: `main` · Cập nhật: 26/11/2026 · Application ID: `com.example.japanesewritingtrainer`

## 1. Mục tiêu và phạm vi

Japanese Writing Trainer là ứng dụng Android offline-first để luyện viết tiếng Nhật bằng cảm ứng. Ứng dụng lưu tiến độ và lịch sử trên thiết bị; không yêu cầu tài khoản, máy chủ hoặc đồng bộ cloud.

**Phạm vi hiện tại:** Hiragana, Katakana và Kanji; Trace, Guided Writing, Recall Writing, Free Practice; SRS dùng FSRS-rs; lịch sử, thống kê, streak, widget, cài đặt và JSON backup/restore.

**Trạng thái hiện tại:** Release thử `1.1` (versionCode `2`) đã được ký, xác minh chữ ký, cài đặt và mở ứng dụng thành công trên thiết bị thử sau khi giới hạn phạm vi R8 optimization vào mã ứng dụng. Người dùng đã xác nhận chốt giai đoạn Release thử. Đây chưa phải phát hành công khai.

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
- Không thêm đăng nhập, cloud sync, Firebase hoặc analytics.
- Không xây dựng Anki live sync. Việc nhập một lần dữ liệu từ APKG (nội dung, tiến độ và lịch sử) là luồng migration riêng, không phải đồng bộ hai chiều.

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

## 7. Release thử — đã hoàn tất

### 7.1 Kết quả đã xác nhận

- Application ID: `com.example.japanesewritingtrainer`.
- Gradle version: versionCode `2`, versionName `1.1`; không tự ý bump.
- Release APK và AAB đã build thành công trên máy người dùng.
- APK Release đã được xác minh bằng `apksigner`: chữ ký v2 hợp lệ, RSA 2048; SHA-256 certificate khớp fingerprint của keystore đã tạo.
- APK Release đã cài đặt và mở ứng dụng thành công sau khi sửa R8 package scope.
- R8 optimization chỉ áp dụng cho `com.example.japanesewritingtrainer.**`; giữ `android.r8.gradual.support=true`.
- Người dùng đã xác nhận chốt giai đoạn Release thử.
- Đây là bản Release thử trên thiết bị, không phải tuyên bố phát hành công khai hoặc phát hành Play Store.

### 7.2 Signing và bảo mật

- Keystore release được lưu ngoài repository; không commit keystore, mật khẩu hoặc thông tin bí mật.
- Không yêu cầu người dùng gửi mật khẩu/secret vào chat hoặc GitHub.
- Giữ bản sao lưu keystore ở các vị trí an toàn độc lập.
- Không thay signing identity hoặc tạo keystore mới cho bản cập nhật tương thích nếu chưa có kế hoạch migration rõ ràng.

### 7.3 Release regression

Khi có thay đổi ảnh hưởng đến build, signing, R8, Room, scheduler hoặc luồng học, người dùng chạy các kiểm tra local phù hợp. Không tuyên bố build/test PASS nếu chưa quan sát kết quả từ người dùng hoặc môi trường chạy thực tế.

## 8. Backlog

1. Kiểm tra clean install trên thiết bị phụ/emulator; không xóa dữ liệu học duy nhất.
2. Rà soát attribution/provenance và ShareAlike trước phân phối công khai.
3. Quyết định chính sách Android Auto Backup so với JSON backup thủ công.
4. Các tính năng ngoài phạm vi như yōon writing units, nhận dạng ML/CNN, cloud sync/login chỉ xem xét khi có yêu cầu riêng.

## 9. Tài liệu còn được giữ

- `README.md`: trang giới thiệu và hướng dẫn mở/build ngắn gọn, trỏ về tài liệu này.
- `JapaneseWritingTrainer/THIRD_PARTY_NOTICES.md`: thông báo ghi công và điều khoản dữ liệu/native dependencies bắt buộc giữ khi phân phối.
- Các source code, tests, assets, generator và native libraries cần thiết cho ứng dụng.
