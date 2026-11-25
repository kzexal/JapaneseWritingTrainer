# Japanese Writing Trainer — Project Guide

> **Tài liệu chuẩn duy nhất cho yêu cầu sản phẩm, quyết định kỹ thuật, trạng thái triển khai và kế hoạch phát hành.**
> Baseline: `main` · Cập nhật: 26/11/2026 · Application ID: `com.example.japanesewritingtrainer`

## 1. Mục tiêu và phạm vi

Japanese Writing Trainer là ứng dụng Android offline-first để luyện viết tiếng Nhật bằng cảm ứng. Ứng dụng lưu tiến độ và lịch sử trên thiết bị; không yêu cầu tài khoản, máy chủ hoặc đồng bộ cloud.

**Phạm vi hiện tại:** Hiragana, Katakana và Kanji; Trace, Guided Writing, Recall Writing, Free Practice; SRS dùng FSRS-rs; lịch sử, thống kê, streak, widget, cài đặt và JSON backup/restore.

**Trạng thái hiện tại:** Release thử `1.1` (versionCode `2`) đã được ký, xác minh chữ ký, cài đặt và mở ứng dụng thành công trên thiết bị thử sau khi giới hạn phạm vi R8 optimization vào mã ứng dụng. Người dùng đã xác nhận chốt giai đoạn Release thử. Đây chưa phải phát hành công khai.

**Ưu tiên tiếp theo:** khảo sát và thiết kế khả năng nhập bốn bộ Anki hiện có (Japanese Grammar, Japanese Kanji, Japanese Vocabulary, English Vocabulary), bao gồm nội dung, tiến độ FSRS và lịch sử ôn tập. Giai đoạn hiện tại chỉ là audit dữ liệu và thiết kế; chưa triển khai importer, chưa thay đổi Room schema và chưa nhập dữ liệu vào ứng dụng.

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
| Vocabulary | Có workbook Excel và bộ Anki Japanese Vocabulary; cần xác định nguồn nội dung chuẩn và quy tắc hợp nhất trước khi nhập |
| Anki import | Đang ở giai đoạn khảo sát bốn APKG; chưa có importer hoặc hợp đồng dữ liệu được chốt |

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

## 8. Khảo sát và thiết kế nhập dữ liệu Anki

### 8.1 Mục tiêu và phạm vi

Mục tiêu là nhập dữ liệu từ bốn bộ APKG hiện có sang JapaneseWritingTrainer, ưu tiên giữ nội dung, trạng thái học, FSRS memory state, due scheduling và lịch sử ôn tập. Đây là migration một chiều từ bản export Anki; không phải Anki sync, không yêu cầu tài khoản hoặc cloud.

Bốn nguồn cần khảo sát:

1. Japanese Grammar
2. Japanese Kanji
3. Japanese Vocabulary
4. English Vocabulary

Mỗi loại nội dung có schema riêng. Không ép Grammar, Kanji, Japanese Vocabulary và English Vocabulary vào một Note model chung; chúng có thể dùng chung card identity/progress abstraction và FSRS scheduler ở tầng domain nếu mapping được xác minh.

### 8.2 Snapshot APKG đã phân tích sơ bộ

Báo cáo được tạo ngày 25/11/2026 từ bốn file APKG, đều sử dụng `collection.anki21b` và Collection schema version 18:

| Bộ Anki | Notes | Cards | New | Review | Revlog |
|---|---:|---:|---:|---:|---:|
| Japanese Grammar | 49 | 49 | 0 | 49 | 592 |
| Japanese Kanji | 1.031 | 1.031 | 939 | 92 | 788 |
| Japanese Vocabulary | 198 | 198 | 83 | 115 | 2.350 |
| English Vocabulary | 200 | 200 | 69 | 131 | 1.930 |
| **Tổng** | **1.478** | **1.478** | **1.091** | **387** | **5.660** |

Trong 5.660 review log, các rating tổng hợp được báo cáo là Again 2.231, Hard 484, Good 1.480 và Easy 1.465.

Có 387 cards đã ôn có các khóa trong `cards.data`: `pos`, `s`, `d`, `dr`, `decay`, `lrt`. Đây là bằng chứng APKG có dữ liệu memory state cần bảo toàn và khảo sát tiếp; chưa được coi là xác nhận tương thích trực tiếp với FSRS-rs 6.6.2.

**Giới hạn của báo cáo hiện tại:** script audit ban đầu đọc các trường `col.models` và `col.decks` theo cấu trúc cũ, trong khi APKG này có các bảng riêng `notetypes`, `fields`, `templates`, `decks`, `deck_config` và `config`. Vì vậy các kết quả `Decks: 0`, `Note Types: 0` và cấu hình rỗng là lỗi của cách đọc báo cáo, không phải kết luận APKG không có deck/Note Type/configuration. Thống kê media cũng chưa được xác minh đầy đủ. Không dùng các trường đó làm căn cứ thiết kế cho đến khi audit schema được sửa.

### 8.3 Những điều phải xác minh trước khi viết importer

- Đọc schema thực tế của `decks`, `notetypes`, `fields`, `templates`, `config`, `deck_config` và các liên kết của chúng.
- Lấy chính xác thứ tự field, Note Type, card template, deck/subdeck, tags, media references và nội dung note; xác minh cách xử lý HTML, audio và hình ảnh.
- Xác định chính xác ý nghĩa, đơn vị và thời điểm của các trường card scheduling: `type`, `queue`, `due`, `ivl`, `reps`, `lapses`, `left`, `odue`, `odid`, `factor`, `mod` và `data`.
- Xác minh cấu trúc FSRS trong `cards.data`, phiên bản/thuật toán, desired retention và tham số liên quan. Không suy luận ý nghĩa trường chưa được xác nhận và không tự tính lại memory state trong bước import.
- Xác minh ý nghĩa `revlog.ease`, `revlog.type`, `ivl`, `lastIvl`, timestamp và đơn vị thời gian; giữ thứ tự lịch sử và liên kết đúng Card.
- Kiểm tra các trạng thái suspended/buried, learning/relearning và filtered/deck override nếu có; snapshot sơ bộ hiện chỉ báo cáo New và Review ở queue hiện tại.
- Xác minh media thực tế trong từng APKG. Không kết luận không có media từ báo cáo sơ bộ.
- Đối chiếu Japanese Kanji APKG với 1.031 Kanji hiện có trong ứng dụng để map vào card identity hiện hữu, tránh tạo duplicate.
- Đối chiếu Japanese Vocabulary APKG với workbook Excel hiện có; xác định nguồn nội dung chuẩn và quy tắc xử lý duplicate/conflict trước khi hợp nhất.
- Kiểm tra sự khác biệt giữa Anki FSRS và FSRS-rs 6.6.2 trước khi quyết định có thể khôi phục memory state trực tiếp hay phải dùng phương án tương thích được kiểm chứng.

### 8.4 Nguyên tắc thiết kế migration

1. APKG gốc là nguồn chỉ đọc; không chỉnh sửa collection gốc.
2. Mỗi nguồn và mỗi Note/Card cần identity mapping ổn định, có namespace nguồn. Không dùng vị trí dòng Excel hoặc thứ tự trong danh sách làm ID.
3. Lưu external Anki IDs/GUID làm provenance; không mặc định Anki ID có thể thay thế app primary key.
4. Import phải idempotent: chạy lại cùng nguồn không tạo duplicate hoặc ghi đè tiến độ mới hơn một cách âm thầm.
5. Nội dung từng loại deck có schema riêng; scheduler/progress dùng abstraction chung chỉ khi mapping không làm mất thông tin.
6. Giữ raw FSRS memory state và raw review history khi có thể; mọi chuyển đổi đơn vị/thời gian phải có test và đối chiếu.
7. Không biến lịch sử Anki thành các lần review mới trong scheduler; lịch sử import phải được phân biệt với review phát sinh trong ứng dụng.
8. Tạo dry-run report trước khi ghi database: số note/card sẽ thêm, map, bỏ qua, xung đột, thiếu media và trạng thái không thể chuyển.
9. Thực hiện import trong transaction hoặc theo batch có checkpoint/rollback được thiết kế rõ ràng; backup dữ liệu app trước khi import.
10. Không xóa hoặc reset CardProgress/PracticeLog hiện có. Bổ sung Room migration rõ ràng nếu cần; tuyệt đối không dùng destructive migration.
11. Sau import phải đối chiếu counts, card identity, due state, FSRS fields và số lượng review history; cung cấp báo cáo kết quả cho người dùng.

### 8.5 Các giai đoạn thực hiện

- **A — Schema audit:** sửa script phân tích để đọc schema Anki v18 và xác minh nội dung, deck, Note Type, media, scheduler config.
- **B — Mapping specification:** lập bảng ánh xạ riêng cho bốn deck, card identity và progress/history.
- **C — Compatibility validation:** kiểm chứng FSRS state, timestamp, due semantics và khả năng map với Room/FSRS-rs hiện tại.
- **D — Dry run:** tạo báo cáo import không ghi dữ liệu, xử lý duplicate và xung đột.
- **E — Implementation:** chỉ bắt đầu sau khi người dùng duyệt mapping/spec; có migration, backup, rollback và idempotency.
- **F — Local validation:** người dùng chạy tests và thử import trên bản backup/test dataset trước khi dùng dữ liệu học chính.

Hiện tại mới có snapshot audit sơ bộ; chưa hoàn tất A, chưa chốt mapping, chưa triển khai importer và chưa nhập bất kỳ dữ liệu Anki nào vào ứng dụng.

## 9. Backlog sau giai đoạn audit

1. Hoàn tất schema audit và mapping specification cho cả bốn bộ Anki.
2. Chốt chính sách hợp nhất Japanese Kanji APKG với Kanji library hiện có và Japanese Vocabulary APKG với workbook Excel.
3. Chỉ triển khai importer sau khi người dùng duyệt thiết kế migration.
4. Kiểm tra clean install trên thiết bị phụ/emulator; không xóa dữ liệu học duy nhất.
5. Rà soát attribution/provenance và ShareAlike trước phân phối công khai.
6. Quyết định chính sách Android Auto Backup so với JSON backup thủ công.
7. Các tính năng ngoài phạm vi như yōon writing units, nhận dạng ML/CNN, cloud sync/login chỉ xem xét khi có yêu cầu riêng.

## 10. Tài liệu còn được giữ

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
