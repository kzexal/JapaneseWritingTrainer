# Japanese Writing Trainer – Project Specification

*Phiên bản 2.0 · Cập nhật: 08/11/2026 · Baseline: nhánh `main`*

> Tài liệu này mô tả trạng thái thực tế của mã nguồn trên `main` và kế hoạch phát triển tiếp theo. Nhãn **Implemented** nghĩa là đã có triển khai trong mã nguồn; chưa đồng nghĩa mọi hành vi đã được kiểm chứng trên thiết bị thật. Không dùng trạng thái hoặc kết quả kiểm thử từ nhánh khác làm bằng chứng cho `main`.

## 1. Tổng quan sản phẩm

### 1.1 Tầm nhìn

Japanese Writing Trainer là ứng dụng Android hỗ trợ người học tiếng Nhật luyện viết Hiragana và Katakana bằng cảm ứng, kết hợp hướng dẫn thứ tự nét, tự viết từ trí nhớ và ôn tập lặp lại ngắt quãng (SRS).

Ứng dụng theo định hướng **offline-first**: dữ liệu ký tự được đóng gói trong ứng dụng, tiến độ và lịch sử luyện tập được lưu cục bộ trên thiết bị. Dataset hiện tại có 46 Hiragana và 46 Katakana cơ bản; hai script đang được mở rộng đồng bộ lên 71 ký tự mỗi bộ (bổ sung dakuten/handakuten). Kiến trúc deck/card và dữ liệu ký tự được tách riêng để tiếp tục mở rộng.

### 1.2 Nền tảng và công nghệ hiện tại

| Thành phần | Công nghệ / cấu hình |
| --- | --- |
| Nền tảng | Android |
| Ngôn ngữ | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Vẽ và nhận thao tác | Compose Canvas |
| Cơ sở dữ liệu | Room |
| Cài đặt | Android SharedPreferences |
| Dữ liệu ký tự | JSON trong app assets |
| SRS | FSRS-rs 6.6.2 qua Rust JNI |
| Android tối thiểu | API 26 |
| Native ABI được build trong CI | arm64-v8a, x86_64 |

Các phiên bản dependency và Gradle wrapper là nguồn chính xác cho cấu hình build; không tự ý nâng cấp phiên bản chỉ dựa trên tài liệu này.

### 1.3 Mục tiêu phiên bản hiện tại

- Mục tiêu dataset: 71 Hiragana và 71 Katakana (46 ký tự cơ bản + 25 dakuten/handakuten mỗi bộ), có thể chọn từng bộ hoặc học kết hợp.
- Hỗ trợ Trace, Guided Writing, Recall Writing và Free Practice.
- Lưu tiến độ theo từng card và lịch sử luyện tập.
- Lập lịch học mới, học lại và ôn tập bằng FSRS.
- Cho phép điều chỉnh giới hạn card mới mỗi ngày và các tùy chọn luyện viết.
- Hiển thị lịch sử, thống kê, và hỗ trợ backup/restore dữ liệu cục bộ.

### 1.4 Ngoài phạm vi hiện tại

Katakana mở rộng, Kanji, import dữ liệu từ workbook Excel, nhận diện chữ viết bằng AI/CNN, tài khoản người dùng, đồng bộ cloud, Firebase và đồng bộ Anki chưa thuộc phạm vi triển khai hiện tại. Kanji lấy dữ liệu từ workbook đã có trên GitHub và import Vocabulary được đưa vào roadmap ở Phase 6–7. Hoàn thiện nội dung và chức năng trước; attribution/license, signing key và đóng gói phát hành chính thức được để sau khi Katakana và Kanji hoàn thành.

## 2. Trạng thái chức năng

### 2.1 Luyện viết

| Chức năng | Trạng thái | Ghi chú |
| --- | --- | --- |
| Writing Canvas | **Implemented** | Nhận thao tác cảm ứng và lưu các nét trong lượt viết |
| Undo / Clear | **Implemented** | Hoàn tác nét cuối hoặc xóa nét đã viết |
| Grid | **Implemented** | Có tùy chọn hiển thị lưới |
| Trace | **Implemented** | Luyện viết dựa trên mẫu |
| Guided Writing | **Implemented** | Hướng dẫn theo nét và sử dụng Stroke Matcher |
| Recall Writing | **Implemented** | Tự viết, xem gợi ý/đáp án theo luồng học |
| Free Practice | **Implemented** | Luyện tự do, không dùng rating để cập nhật lịch SRS |
| Hint button | **Implemented** | Hiển thị nút gợi ý sau các lần viết không đạt ngưỡng; cần giữ nút điều khiển canvas luôn dễ tiếp cận |

### 2.2 Dataset và thư viện

- **Implemented:** dataset Hiragana JSON trong assets, gồm 71 ký tự (46 cơ bản + 25 dakuten/handakuten), stroke paths từ KanjiVG và metadata nguồn.
- **Implemented:** dataset Katakana JSON trong assets, gồm 71 ký tự (46 cơ bản + 25 dakuten/handakuten); dữ liệu stroke paths được tạo từ KanjiVG và có metadata nguồn.
- **Implemented:** dữ liệu tĩnh của ký tự được tách khỏi tiến độ học; ID có prefix riêng theo script.
- **Implemented:** deck/card model tạo deck độc lập theo character type.
- **Implemented:** thư viện ký tự và lựa chọn Hiragana, Katakana hoặc kết hợp cả hai.
- **In progress:** mở rộng cả Hiragana và Katakana lên 71 ký tự với dakuten/handakuten; yōon được thiết kế riêng vì là writing unit nhiều kana. Rà soát attribution/license cuối cùng.

### 2.3 Learning session và SRS

| Thành phần | Trạng thái | Ghi chú |
| --- | --- | --- |
| Card status | **Implemented** | `NEW`, `LEARNING`, `RELEARNING`, `REVIEW` |
| FSRS memory state | **Implemented** | Lưu stability, difficulty, scheduled/elapsed days và due time |
| FSRS-rs native bridge | **Implemented** | Rust gọi FSRS-rs 6.6.2 và cung cấp kết quả qua JNI |
| Learning / Relearning steps | **Implemented** | Kotlin quản lý policy và các bước học lại |
| Due queue | **Implemented** | Lập hàng đợi card đến hạn |
| Daily new-card limit | **Implemented** | Giới hạn card mới mỗi ngày, có thể cấu hình |
| Session planner / engine / coordinator | **Implemented** | Quản lý thứ tự card, rating và retry trong phiên |
| Difficult-card retry | **Implemented** | Có cơ chế xử lý card gặp nhiều lần Again |
| Scheduling audit | **Implemented; locally verified** | Đã kiểm tra anchor, clock rollback, duplicate concurrent rating, các chuyển trạng thái, queue, retry và khôi phục phiên qua Room; tiếp tục bổ sung bằng chứng khi phát hiện edge case mới |

**Ranh giới trách nhiệm:** Rust/FSRS-rs tính toán trạng thái ghi nhớ và các khoảng lịch đề xuất. Kotlin quản lý chính sách sản phẩm như Learning/Relearning, hàng đợi, giới hạn card mới, retry và vòng đời phiên học. Không thay thế FSRS bằng SM-2.

### 2.4 Tiến độ, cài đặt và dữ liệu

| Chức năng | Trạng thái | Ghi chú |
| --- | --- | --- |
| Room persistence | **Implemented** | Lưu card progress và practice log |
| Database migrations | **Implemented** | Schema hiện tại version 3, có migration 1→2 và 2→3 |
| Practice history | **Implemented** | Lưu mode, rating, thời lượng và timestamp |
| Statistics | **Implemented** | Có thống kê tiến độ, accuracy, streak và trạng thái card |
| Settings | **Implemented** | Theme, số card mới/ngày, grid, hints; lưu bằng SharedPreferences |
| JSON backup/restore | **Implemented** | Có kiểm tra dữ liệu và restore theo transaction |
| Backup compatibility tests | **In progress** | Mở rộng kiểm thử dữ liệu sai, dữ liệu cũ và trường hợp biên |

## 3. Kiến trúc hiện tại

### 3.1 Luồng kiến trúc

```text
Jetpack Compose UI
        |
        v
Learning / Domain layer
(Session Planner, Engine, Coordinator, Stroke Matcher)
        |
        +--------------------+
        |                    |
        v                    v
Room persistence        FSRS-rs JNI bridge
(Card progress, logs)   (Rust / FSRS-rs 6.6.2)
        |
        v
Local character assets (JSON)
```

Sơ đồ thể hiện các trách nhiệm chính, không phải thứ tự gọi bắt buộc của mọi thao tác. Repository và DAO được dùng để đóng gói truy cập dữ liệu.

### 3.2 Mô hình dữ liệu học

- **Character data:** nội dung tĩnh như ký tự, reading, nhóm và dữ liệu nét.
- **Deck:** bộ thẻ học độc lập, có ID, tên, mô tả, loại ký tự và danh sách card ID.
- **Card:** đơn vị học trong một deck; tham chiếu đến character data.
- **Card progress:** trạng thái học riêng của card, gồm status, due time, stability, difficulty, scheduled/elapsed days, reps, lapses, review count, correct count và lần review cuối.
- **Practice log:** lịch sử từng lượt luyện, gồm card, mode, rating (nếu có), duration và timestamp.

**FSRS timestamp invariant:** `lastReviewedAtEpochMillis` là mốc của lần cập nhật FSRS memory gần nhất. Các bước Again/Hard ngắn trong Learning/Relearning vẫn tạo practice log và cập nhật bộ đếm lượt luyện, nhưng không dịch chuyển mốc FSRS này. Khi tốt nghiệp sang Review, elapsed days được tính từ mốc FSRS gần nhất. Thời điểm của từng lần thao tác vẫn được lưu trong `PracticeLog.timestampEpochMillis`.

Không dùng mô hình SM-2 `easeFactor/intervalDays` trong implementation hiện tại. FSRS memory state và lịch học được biểu diễn bằng các trường tương ứng trong `CardProgress`.

### 3.3 Database

Room database hiện có hai entity chính:

1. `CardProgressEntity` — bảng `card_progress`.
2. `PracticeLogEntity` — bảng `practice_log`.

Schema version hiện tại: **3**. Các thay đổi schema tương lai phải có migration rõ ràng và kiểm thử bảo toàn dữ liệu. Không sử dụng destructive migration để xóa tiến độ người học.

### 3.4 Native FSRS bridge

- Rust crate: `JapaneseWritingTrainer/native/fsrs-bridge`.
- Upstream dependency được pin: `fsrs = 6.6.2`.
- JNI library: `japanese_writing_fsrs`.
- Bridge nhận memory state trước đó, elapsed days và desired retention; trả về state/interval cho Again, Hard, Good và Easy.
- Kotlin kiểm tra input và chuyển kết quả native thành domain model.
- Android native library phải được build theo ABI Android; thư viện host dùng cho JVM unit test không thể thay thế thư viện Android.

## 4. Nguyên tắc đánh giá chữ viết

### 4.1 Self-rating

Người học tự chọn Again / Hard / Good / Easy sau lượt Recall hoặc Review. Rating là đầu vào cho scheduler; không tự động suy ra rating từ Stroke Matcher.

### 4.2 Stroke Matcher

Stroke Matcher hiện đã được tích hợp trong luồng Guided/Recall để phân tích nét và quyết định việc chấp nhận nét tiếp theo trong một số bước.

**Điểm cần tiếp tục cải thiện:** dung sai phải phù hợp với thao tác ngón tay thực tế; tránh false rejection khiến người học không thể tiến tiếp. Khi matcher chặn tiến trình, cần có hướng khôi phục rõ ràng. Không tự ý biến matcher thành hệ thống tự chấm FSRS.

### 4.3 Các cấp độ tương lai

| Mức | Cơ chế | Trạng thái |
| --- | --- | --- |
| Level 1 | Người học tự đánh giá | **Implemented** |
| Level 2 | Phân tích nét và gợi ý/feedback | **Partially implemented** |
| Level 3 | Nhận diện chữ viết bằng mô hình ML/CNN | **Planned / Out of current scope** |

## 5. Yêu cầu phi chức năng

- **Offline-first:** các chức năng học cốt lõi không yêu cầu tài khoản hoặc server.
- **Data safety:** tiến độ phải tồn tại sau khi đóng/mở ứng dụng; migration và restore không được âm thầm làm mất dữ liệu.
- **Responsiveness:** canvas và các nút Undo/Clear/Hint phải dùng được trong suốt lượt viết.
- **Compatibility:** hỗ trợ Android API 26 trở lên.
- **Maintainability:** Kotlin giữ policy học; Rust giữ phần tính toán FSRS; dữ liệu tĩnh tách khỏi tiến độ cá nhân.
- **Reproducibility:** các bước build Rust và Android phải có thể chạy trong CI.

Các mục tiêu định lượng về thời gian khởi động, độ trễ vẽ và frame rate chưa có benchmark được xác nhận; sẽ bổ sung khi có phép đo trên thiết bị đại diện.

## 6. Roadmap phát triển

Roadmap dưới đây là kế hoạch từ trạng thái hiện tại trên `main`, không phải kế hoạch khởi tạo dự án từ đầu.

### Phase 0 — Repository, tài liệu và CI

**Mục tiêu:** tạo nền tảng làm việc ổn định, tài liệu phản ánh đúng code và CI xác minh Rust/Android.

- [x] Chọn `main` làm baseline phát triển.
- [x] Tạo tài liệu Development Standards riêng.
- [x] Cập nhật Specification theo implementation hiện tại và roadmap.
- [x] Bổ sung ignore rules cho IDE/build output/Rust target.
- [x] Mở workflow Rust bridge trên push `main` và pull request vào `main`.
- [x] Bổ sung Android JVM unit tests và debug APK build vào CI.
- [ ] Kiểm tra kết quả CI thực tế sau commit.
- [ ] Rà soát và loại bỏ các build artifacts/IDE files đã bị track từ trước, sau khi xác nhận không có runtime dependency.

### Phase 1 — Ổn định learning system

- [x] Audit ban đầu phát hiện và sửa việc short Learning/Relearning steps làm dịch chuyển `lastReviewedAtEpochMillis`, khiến elapsed days lúc tốt nghiệp bị tính thiếu.
- [x] Giữ FSRS review anchor ổn định qua các bước Again/Hard ngắn; chỉ cập nhật anchor khi thực sự tính FSRS memory.
- [x] Chặn rating trùng đồng thời trong lúc thao tác ghi dữ liệu đang chờ hoàn tất.
- [x] Không cho đồng hồ thiết bị lùi làm lùi FSRS anchor hoặc due date.
- [x] Bổ sung validation cho thời gian âm và các giá trị FSRS không hữu hạn trong CardProgress.
- [x] Thêm regression tests cho FSRS anchor, graduation elapsed days, clock rollback và dữ liệu không hữu hạn.
- [x] Giữ `completedCount` không giảm khi người học đưa nhóm thẻ khó trở lại queue.
- [x] Thêm regression test cho rating trùng trong lúc transaction lưu tiến độ/lịch sử đang chờ.
- [x] Thêm test thứ tự due queue theo thời điểm đến hạn, dùng thứ tự deck để phân định khi trùng thời điểm.
- [x] Thêm Room integration test xác nhận daily new-card limit vẫn được giữ khi bắt đầu phiên mới trong cùng ngày.
- [x] Kiểm chứng các chuyển trạng thái NEW → LEARNING → REVIEW và REVIEW → RELEARNING trên toàn bộ rating bằng scheduler matrix; test unit đã pass trên máy phát triển.
- [x] Bổ sung và chạy pass scheduler test matrix cho cả bốn rating ở trạng thái LEARNING, RELEARNING và REVIEW.
- [x] Chạy pass unit/instrumentation tests cho daily limit, due queue, retry thẻ khó và trường hợp hết hàng đợi.
- [x] Kiểm tra thực tế force-stop Android và mở lại ứng dụng giữa quá trình sử dụng; người dùng xác nhận phiên học vẫn khôi phục đúng, không phát hiện mất tiến độ hoặc lặp lại sai card. Room instrumentation test cũng mô phỏng bỏ coordinator cũ, tạo phiên mới và khôi phục queue từ tiến độ/lịch sử đã lưu. Queue đang chạy không được persist nguyên trạng; phiên mới được lập lại từ dữ liệu card trong Room.
- [x] Chạy `testDebugUnitTest` và `assembleDebug` trực tiếp bằng Gradle; người dùng xác nhận unit tests và build đều pass. Các Room instrumentation tests liên quan đến scheduler, queue, migration, backup/restore, progress và settings cũng đã được chạy pass. Không phụ thuộc GitHub Actions để xác minh.

### Phase 2 — Writing experience

- [x] Đánh giá false rejection của Stroke Matcher bằng unit tests cho biến thiên scale/curvature và kiểm tra thực tế các nét đúng, hơi cong/lệch, ngược chiều, quá ngắn/quá dài.
- [x] Xác minh cách khôi phục khi matcher từ chối nét: hint xuất hiện sau hai lần sai; Undo và Clear vẫn hoạt động.
- [x] Kiểm tra Guided Writing và Recall Writing trên thiết bị Android thật; các trường hợp trong test matrix đều pass.
- [x] Kiểm tra Trace và Free Practice trên thiết bị thật; người dùng xác nhận cả hai chế độ hoạt động đúng.
- [x] Kiểm tra canvas và các nút Undo/Clear/Hint trong cả bốn chế độ; người dùng xác nhận các nút vẫn truy cập được.
- [ ] Kiểm tra độ mượt, kích thước màn hình và thao tác chạm trên nhiều kích thước thiết bị.

### Phase 3 — Data integrity và progress

- [x] Kiểm thử migration Room 1→2→3 và 2→3: bảo toàn CardProgress, chuyển card LEARNING đã lapse sang RELEARNING, giữ nguyên PracticeLog; người dùng xác nhận instrumentation tests PASS.
- [x] Kiểm thử backup/restore: instrumentation tests cho JSON sai cấu trúc, cardId trùng và practice-log ID trùng đã được chạy pass trên thiết bị.
- [x] Kiểm thử backup sai `format`: `unsupportedBackupFormatDoesNotReplaceExistingProgressAndHistory` đã xác nhận backup sai định dạng bị từ chối và cả CardProgress lẫn PracticeLog hiện có được giữ nguyên; người dùng chạy instrumentation test và xác nhận PASS.
- [x] Kiểm tra rollback transaction: `failedDatabaseWriteRollsBackRestoreAndPreservesExistingData` đã được người dùng chạy instrumentation test và xác nhận PASS.
- [x] Mở rộng test JSON malformed: `malformedJsonDoesNotReplaceExistingData` xác nhận cả CardProgress và PracticeLog hiện có vẫn được giữ nguyên; người dùng chạy instrumentation test và xác nhận PASS.
- [x] Test `unsupportedBackupDoesNotDeleteExistingProgressOrPracticeHistory`: backup có `schemaVersion = 999` bị từ chối và cả CardProgress lẫn PracticeLog hiện có vẫn được giữ nguyên; người dùng chạy instrumentation test và xác nhận BUILD SUCCESSFUL.
- [x] Test `invalidPracticeLogMetadataDoesNotReplaceExistingData`: cả `durationMs = -1` và `timestampEpochMillis = -1` đều bị từ chối; CardProgress và PracticeLog cũ được giữ nguyên; người dùng xác nhận instrumentation test PASS.
- [x] Test `invalidPracticeLogEnumsDoNotReplaceExistingData`: `mode` và `rating` không thuộc enum hỗ trợ đều bị từ chối; CardProgress và PracticeLog cũ được giữ nguyên; người dùng xác nhận instrumentation test PASS.
- [x] Test `invalidBackupStatusAndLogIdDoNotReplaceExistingData`: `CardProgress.status` không hợp lệ và `PracticeLog.id = 0` đều bị từ chối; dữ liệu cũ được giữ nguyên; người dùng xác nhận instrumentation test PASS.
- [x] Mở rộng kiểm thử CardProgress với stability/difficulty, các bộ đếm, thời lượng lịch FSRS và timestamp âm: `invalidProgressMetadataDoesNotReplaceExistingData` xác nhận các giá trị không hợp lệ đều bị từ chối và giữ nguyên CardProgress lẫn PracticeLog; người dùng xác nhận instrumentation test PASS.
- [x] Kiểm thử timestamp tạo backup âm và `cardId` rỗng trong CardProgress/PracticeLog: `invalidBackupTimestampAndBlankCardIdsDoNotReplaceExistingData` xác nhận cả ba trường hợp đều bị từ chối và dữ liệu hiện có được giữ nguyên; người dùng xác nhận instrumentation test PASS.
- [x] Xác minh tương thích backup schema v1: instrumentation test `versionOneBackupWithoutOptionalNullableFieldsRemainsRestorable` xác nhận backup v1 đời đầu không có các trường nullable FSRS và rating vẫn khôi phục được; người dùng đã chạy test và xác nhận PASS. Hiện chưa tồn tại schema backup version cũ hơn v1.
- [x] Đối chiếu thống kê với practice logs và card progress: Room integration test kiểm tra `reviewCount`/`correctCount` khớp với số lượng và rating trong lịch sử đã chạy pass.
- [x] Android instrumentation tests cho settings đã chạy pass: giá trị mặc định, đọc lại qua instance mới, theme không hợp lệ và giới hạn số card/ngày. Kiểm tra thủ công trên thiết bị bằng Android force-stop rồi mở lại cũng xác nhận Theme, số card mới/ngày, Grid và Hints đều được giữ nguyên.
- [x] Xác minh nâng cấp app bằng cách cài APK versionCode 2/versionName 1.1 đè lên bản cũ trên thiết bị thật: tiến độ học, Practice History và cả bốn cài đặt được giữ nguyên; ứng dụng khởi động bình thường. Đã sửa SettingsScreen để hiển thị versionName thực tế từ Package Manager thay vì chuỗi 1.0 ghi cứng.

### Phase 4 — MVP release readiness

- [x] Chạy `testDebugUnitTest`, `connectedDebugAndroidTest` và `:app:assembleDebug`; người dùng xác nhận cả ba đều PASS trên môi trường Android local.
- [x] Kiểm tra thủ công trên thiết bị thật các luồng học cốt lõi, Practice History, Statistics và cả bốn cài đặt; force-stop/mở lại vẫn giữ dữ liệu.
- [x] Kiểm tra offline trên thiết bị thật: mở ứng dụng, sử dụng cả bốn chế độ luyện viết, rating, tiến độ, lịch sử, thống kê và cài đặt; force-stop/mở lại khi vẫn offline. Người dùng xác nhận PASS.
- [x] Kiểm tra nâng cấp bằng APK cài đè lên bản hiện có; tiến độ, lịch sử và cài đặt được giữ nguyên (xem Phase 3).
- [x] Kiểm tra JSON backup/restore thủ công trên thiết bị: tạo tệp sao lưu, khôi phục từ chính tệp đó và xác minh dữ liệu học/lịch sử/thống kê; người dùng xác nhận PASS.
- [ ] Kiểm tra cài mới trên emulator hoặc thiết bị phụ; không gỡ app/xóa dữ liệu trên thiết bị đang chứa dữ liệu học duy nhất.
- [x] Build `:app:assembleRelease` và `:app:bundleRelease`; người dùng xác nhận cả hai build thành công.
- [x] Kiểm tra native libraries trong APK và AAB: `libjapanese_writing_fsrs.so` có trong `arm64-v8a` và `x86_64`; xác nhận thiết bị Android của người dùng hỗ trợ `arm64-v8a`.
- [x] Hoàn thiện README hiện có: giới thiệu ứng dụng, chức năng thực tế, hướng dẫn build/chạy/test, cấu trúc dự án, Rust JNI, backup/restore và trạng thái roadmap. Đã ghi rõ APK Release hiện tại chưa ký và việc rà soát attribution/license cuối cùng được để sau Katakana/Kanji.
- [x] Kiểm thử tab **Hướng dẫn học**: người dùng xác nhận tab hiển thị, cuộn, điều hướng, theme sáng/tối và portrait hoạt động đúng; nội dung đã được cập nhật khi bổ sung Katakana.
- [ ] Kiểm tra cài mới trên emulator hoặc thiết bị phụ; không gỡ app/xóa dữ liệu trên thiết bị đang chứa dữ liệu học duy nhất.
- [ ] Chỉ công bố bản phát hành cuối sau khi Katakana và Kanji hoàn tất, attribution/license và signing key được xử lý ở giai đoạn phát hành.

### Phase 5 — Mở rộng Hiragana và Katakana

**Mục tiêu:** mở rộng đồng bộ cả hai thư viện lên 71 ký tự mỗi bộ, tái sử dụng toàn bộ writing engine, session engine, Room và FSRS hiện tại.

- [x] Audit dataset Hiragana hiện tại và xác định contract JSON dùng chung cho nhiều bộ ký tự: `CharacterData` đã có `type`, `group`, `reading`, `strokeCount`, `viewBoxSize`, `strokes`; `CharacterRepository.load(context, assetPath)` có thể đọc từng dataset độc lập. ID và glyph phải duy nhất trong mỗi dataset; khi gộp nhiều script cần ID có prefix riêng.
- [x] Audit luồng Deck và xác định các điểm cần mở rộng: nạp nhiều dataset, chọn deck và nhãn Home/Library.
- [x] Thiết kế định danh card ổn định, không phụ thuộc vị trí trong danh sách; ID Hiragana và Katakana có prefix riêng dù có cùng reading/âm.
- [x] Dùng chung schema dữ liệu ký tự: character ID, ký tự, script/type, group, reading, stroke count, viewBox và stroke paths.
- [x] Tạo dataset 46 Katakana cơ bản với thứ tự nét và stroke paths từ KanjiVG; metadata nguồn đã được ghi trong JSON. Rà soát attribution/license cuối cùng vẫn thuộc bước phát hành.
- [x] Mở rộng generator cho cả Hiragana và Katakana: mỗi bộ gồm 46 ký tự cơ bản + 25 dakuten/handakuten (ガ行/が行、ザ行/ざ行、ダ行/だ行、バ行/ば行、パ行/ぱ行); giữ nguyên ID của 46 ký tự cơ bản và dùng ID theo Unicode code point cho ký tự bổ sung để tránh trùng romaji (ví dụ ジ/ヂ, ズ/ヅ).
- [x] Chạy cả `tools/generate_hiragana.py` và `tools/generate_katakana.py`; người dùng xác nhận mỗi dataset đã tạo đủ 71 ký tự. Generator tự validate ID/glyph, stroke count/order và SVG paths. README và instrumentation test đã được cập nhật; automated tests đang chờ người dùng chạy.
- [ ] Thiết kế yōon (キャ, シュ, チョ...) như writing unit gồm nhiều kana; không đưa vào dataset CharacterData vuông hiện tại cho đến khi có cách biểu diễn layout/stroke geometry phù hợp.
- [ ] Thiết kế lại deck selector thành hai tab Hiragana và Katakana; bỏ hoàn toàn lựa chọn học gộp, lựa chọn deck được lưu cục bộ (đã triển khai, chờ kiểm tra UI).
- [ ] Tách giới hạn số thẻ mới mỗi ngày cho Hiragana và Katakana; dữ liệu cài đặt cũ được dùng làm giá trị kế thừa để không làm thay đổi giới hạn đã thiết lập trước đây (đã triển khai, chờ instrumentation tests).
- [ ] Khi chuyển giữa hai tab deck, luôn quay về trang chủ của deck vừa chọn; không giữ màn hình Ôn tập/Thư viện/Luyện tự do của deck trước (đã triển khai, chờ kiểm tra thủ công).
- [ ] Thu gọn điều hướng chức năng về một hàng tab cuộn ngang để dành thêm chiều cao cho Writing Canvas; tăng chiều cao nút học chính trên trang chủ (đã triển khai, chờ kiểm tra trên thiết bị).
- [x] Tái sử dụng Trace, Guided Writing, Recall Writing, Free Practice, SRS, history và statistics; không tạo scheduler hoặc writing engine riêng cho Katakana.
- [x] Bổ sung instrumentation test cho asset Hiragana và Katakana: 71 ký tự mỗi bộ, ID/glyph duy nhất, type/prefix đúng, kiểm tra ID Unicode của ký tự mới, stroke count/order liên tục và SVG paths không rỗng; bổ sung unit test mapping deck-card cho phiên kết hợp.
- [ ] Bổ sung kiểm tra UI, session, progress isolation và Room persistence cho Katakana.
- [ ] Kiểm tra backup/restore và migration khi dữ liệu có thêm script mới; không làm mất progress Hiragana hiện có.
- [ ] Kiểm tra thủ công toàn bộ nhóm Katakana trên thiết bị Android thật.

**Definition of done:** người học có thể học Katakana độc lập qua tab riêng; không có deck học gộp; tiến độ hai bộ không bị trộn; mọi chế độ luyện và SRS hoạt động như với Hiragana.

### Phase 6 — Kanji từ workbook hiện có

**Mục tiêu:** bổ sung Kanji vào ứng dụng bằng cách tái sử dụng dữ liệu đã có trong file Excel `vocabulary.xlsx` ở root repository. Workbook là nguồn dữ liệu nội dung gốc; không tạo danh sách Kanji song song hoặc nhập lại thủ công dữ liệu đã có.

**Quyết định kiến trúc cập nhật dữ liệu:**

- **Giai đoạn hiện tại (A):** chỉnh sửa workbook trên máy tính, chạy Python generator/validator để sinh JSON, đóng gói JSON vào app assets và phát hành/cài bản ứng dụng mới để nhận dữ liệu.
- **Khả năng mở rộng về sau (B):** thiết kế parser, validator, schema và cơ chế upsert độc lập với Android assets để có thể tái sử dụng khi bổ sung chức năng import workbook trực tiếp trong ứng dụng. Không triển khai UI/file picker hoặc import trực tiếp ở Phase 6.
- Không chỉnh sửa workbook gốc bằng generator. Generator chỉ đọc workbook và ghi các artifact được sinh ra.
- Không suy đoán schema: mapping phải dựa trên sheet, header và kiểu dữ liệu thực tế; chỉ xuất các trường thực sự có trong nguồn.

**Inventory đã xác minh trên workbook hiện tại (08/11/2026):**

- Sheet: `Kanji`; 87 dòng gồm header và 86 bản ghi.
- Header: `Kanji`, `Onyomi`, `Kunyomi`, `Meaning`, `Radical`, `StrokeCount`, `ExampleWords`, `Notes`, `JLPT`, `Tags`.
- 86 ký tự hiện tại không trùng; các trường trong inventory đều có dữ liệu; tất cả đang được gắn JLPT N5.
- `StrokeCount` là số nguyên từ 1 đến 18 trong dữ liệu hiện tại. Workbook không cung cấp stroke order hoặc SVG/path geometry; số nét không được dùng thay cho dữ liệu hình học.
- Khi workbook được mở rộng, generator phải đọc toàn bộ sheet mỗi lần chạy, không hard-code số lượng 86 hoặc giả định chỉ có N5.

**Quy tắc cập nhật và định danh:**

- Dùng ID ổn định dựa trên Unicode code point của ký tự Kanji (có namespace/prefix riêng cho loại card); không dùng số dòng Excel, thứ tự sắp xếp, JLPT hoặc nội dung Meaning làm ID.
- Generator hoạt động theo kiểu deterministic: cùng dữ liệu đầu vào phải tạo cùng ID và JSON tương đương.
- Khi thêm dòng mới, tạo content record/card mới.
- Khi sửa metadata của Kanji hiện có, cập nhật nội dung theo ID hiện hữu; không tạo card mới và không reset CardProgress, PracticeLog hoặc FSRS state.
- Khi một dòng bị xóa khỏi workbook, mặc định không xóa card/progress trong ứng dụng. Việc ngừng phân phối nội dung và chính sách archive phải được xử lý tường minh ở bước sau, không suy ra lệnh xóa dữ liệu học.
- Dữ liệu Kanji metadata và stroke geometry phải tách biệt. Stroke data được liên kết theo ID/ký tự và có thể thiếu; thiếu stroke paths thì không bật Trace/Guided/Recall cho ký tự đó.
- Schema JSON cần có version hoặc cơ chế tương đương để hỗ trợ validator, tương thích dữ liệu và khả năng tái sử dụng parser khi triển khai import trực tiếp trong app ở tương lai.

**Các công việc triển khai:**

- [x] Hoàn thiện inventory workbook hiện tại và Python generator validation (duplicate, ô thiếu/kiểu dữ liệu, StrokeCount); không hard-code số lượng bản ghi.
- [x] Thiết kế schema JSON metadata riêng, có schemaVersion và source; không ép dữ liệu metadata vào `CharacterData` vốn yêu cầu stroke paths hợp lệ.
- [x] Tạo `tools/generate_kanji.py` đọc sheet `Kanji`, chuẩn hóa chuỗi `None`/`null`, sinh ID theo Unicode code point và JSON metadata; workbook gốc không bị sửa. Chờ người dùng chạy generator và xác nhận đầu ra thực tế.
- [x] Thêm Python unit tests cho normalize null, Unicode ID, duplicate Kanji, StrokeCount không hợp lệ và schema JSON không chứa geometry; người dùng xác nhận 6 tests PASS. Thêm Android instrumentation tests cho KanjiRepository (chờ chạy trên máy người dùng).
- [ ] Kiểm tra workbook không có stroke geometry; đánh giá nguồn stroke data có thể dùng hợp pháp và attribution/license trước khi tích hợp. Không tự tạo path dựa trên StrokeCount.
- [x] Xây dựng `KanjiData` và `KanjiRepository` đọc/validate metadata JSON độc lập với `CharacterData` và Room card progress; chưa tích hợp upsert vào card/session nên chưa tác động tiến độ học.
- [ ] Xây dựng deck/library và tra cứu/lọc dựa trên các trường thực tế: JLPT, radical, tags, readings và meaning.
- [ ] Tích hợp card Kanji vào session/FSRS theo ID ổn định; chỉ bật writing modes khi có stroke geometry hợp lệ.
- [ ] Bảo đảm Kanji card IDs không va chạm với Hiragana/Katakana/Vocabulary; tiến độ các bộ hiện có được giữ nguyên.
- [ ] Kiểm thử import/update lặp lại, thêm Kanji, sửa metadata, bỏ dòng khỏi workbook, backup/restore và progress isolation.
- [ ] Kiểm tra thủ công thư viện, tìm kiếm, lọc, học và ôn Kanji trên thiết bị Android thật.

**Definition of done:** nội dung Kanji được sinh từ workbook bằng quy trình có thể lặp lại; thêm Kanji mới không cần sửa code ứng dụng hoặc hard-code số lượng; cập nhật metadata không reset tiến độ; dữ liệu bị bỏ khỏi workbook không tự động xóa lịch sử học. Kiến trúc parser/validator/upsert đủ độc lập để hỗ trợ import trực tiếp trong app ở giai đoạn sau, nhưng Phase 6 chưa triển khai chức năng đó.

### Phase 7 — Import Vocabulary từ workbook hiện có

**Mục tiêu:** đưa sheet Vocabulary trong cùng workbook vào ứng dụng offline, tạo Vocabulary Deck/Card từ dữ liệu thực tế của file.

- [ ] Audit sheet Vocabulary, header và các trường Japanese, Reading, Vietnamese, Example, Notes/Tags (chỉ dùng các trường thực sự có trong file).
- [ ] Thiết kế Vocabulary model riêng với CharacterData vì một từ vựng có thể chứa nhiều ký tự và nhiều loại script.
- [ ] Xác định chế độ luyện: viết Japanese chính tả hoặc viết Reading/kana; cho phép người học chọn rõ ràng, không tự thay thế Kanji bằng kana.
- [ ] Tạo Vocabulary Deck/Card có ID ổn định và metadata truy xuất về dòng/nguồn workbook.
- [ ] Hỗ trợ nạp dữ liệu từ workbook trong repository và quy trình cập nhật/import lại có preview; không bắt buộc người dùng chọn file nếu dữ liệu đã được đóng gói sẵn, nhưng vẫn thiết kế để thay workbook có kiểm soát.
- [ ] Validate header, ô rỗng, kiểu dữ liệu, dòng trống, trùng lặp và nội dung không hợp lệ; báo lỗi theo sheet/dòng.
- [ ] Upsert theo khóa ổn định; thay đổi nội dung không reset FSRS progress; bỏ dòng khỏi Excel không tự động xóa card/progress.
- [ ] Ghi import trong transaction; lỗi giữa chừng phải rollback.
- [ ] Với từ có Hiragana/Katakana/Kanji, ánh xạ từng ký tự sang dataset tương ứng; nếu thiếu stroke paths thì chỉ cho phép chế độ học phù hợp, không hiển thị chức năng luyện viết không khả dụng.
- [ ] Bổ sung tests cho parse workbook, duplicate, import lại, update, rollback, backup/restore và bảo toàn progress/history.
- [ ] Kiểm tra thực tế một nhóm từ vựng có kana và Kanji trên thiết bị Android.

**Definition of done:** dữ liệu Vocabulary từ workbook xuất hiện thành deck có thể học/ôn tập; import lại không nhân đôi card hoặc reset tiến độ; người học phân biệt được luyện Japanese và Reading.

### Nguyên tắc thứ tự và phạm vi

1. Hoàn tất Phase 4: README và tab Hướng dẫn học trong ứng dụng; clean install trên emulator/thiết bị phụ.
2. Hoàn tất Phase 5 Katakana.
3. Hoàn tất Phase 6 Kanji bằng pipeline Excel → Python validator/generator → JSON assets; thiết kế parser/validator/upsert theo hướng có thể tái sử dụng cho import trực tiếp trong app về sau, nhưng chưa triển khai UI import.
4. Hoàn tất Phase 7 import Vocabulary từ cùng workbook, tái sử dụng nguyên tắc nguồn dữ liệu và cập nhật an toàn đã xây dựng ở Phase 6.
5. Sau khi Katakana và Kanji hoàn thành, xử lý attribution/license, signing keystore và các bước phát hành cuối; Grammar có thể được xem xét sau.
6. Không triển khai cloud sync, login, Firebase hoặc Anki sync trong các phase này; ứng dụng tiếp tục offline-first và single-device theo định hướng hiện tại.

## 7. Acceptance criteria

| ID | Tiêu chí | Trạng thái hiện tại |
| --- | --- | --- |
| AC-01 | Có dữ liệu Hiragana và Katakana mở rộng (71 ký tự mỗi bộ) | **Implemented; dataset generated, automated tests pending** |
| AC-02 | Viết nhiều nét bằng cảm ứng, Undo/Clear | **Implemented** |
| AC-03 | Trace và hiển thị dữ liệu nét | **Implemented** |
| AC-04 | Guided Writing và Recall Writing | **Implemented** |
| AC-05 | Self-rating bốn mức và cập nhật FSRS | **Implemented** |
| AC-06 | Learning, Relearning, Review queue | **Implemented** |
| AC-07 | Lưu tiến độ qua lần mở app | **Implemented; migration 1→2→3 và 2→3 đã được kiểm thử trên thiết bị/emulator** |
| AC-08 | Lịch sử luyện và thống kê | **Implemented** |
| AC-09 | Settings và JSON backup/restore | **Implemented; automated edge-case tests và manual backup/restore đã PASS** |
| AC-10 | Build và chạy trên thiết bị Android thật | **Debug build và manual device checks PASS; release build còn chờ** |
| AC-11 | Offline đầy đủ | **Manual offline acceptance trên thiết bị thật PASS** |

## 8. Definition of done

Một phase hoặc tính năng chỉ được đánh dấu hoàn thành khi:

1. Được triển khai trên `main`.
2. Có test phù hợp với logic mới hoặc regression test cho lỗi đã sửa.
3. Các lệnh kiểm tra liên quan đã chạy và có kết quả quan sát được.
4. Nếu cần kiểm tra trên thiết bị, đã chạy instrumentation/manual test và ghi rõ thiết bị/API.
5. Không làm hỏng dữ liệu Room, backup cũ hoặc contract JNI.
6. Tài liệu được cập nhật đồng bộ.
7. Các mục chưa kiểm chứng được ghi rõ, không đánh dấu hoàn thành chỉ vì code đã tồn tại.

## 9. Tài liệu liên quan

- [Development Standards](DEVELOPMENT_STANDARDS.md)
- [README](README.md)
- [FSRS Rust bridge source](JapaneseWritingTrainer/native/fsrs-bridge/)
- [Android application source](JapaneseWritingTrainer/app/src/main/)
