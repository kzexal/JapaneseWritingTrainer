# Japanese Writing Trainer – Project Specification

*Phiên bản 2.0 · Cập nhật: 07/11/2026 · Baseline: nhánh `main`*

> Tài liệu này mô tả trạng thái thực tế của mã nguồn trên `main` và kế hoạch phát triển tiếp theo. Nhãn **Implemented** nghĩa là đã có triển khai trong mã nguồn; chưa đồng nghĩa mọi hành vi đã được kiểm chứng trên thiết bị thật. Không dùng trạng thái hoặc kết quả kiểm thử từ nhánh khác làm bằng chứng cho `main`.

## 1. Tổng quan sản phẩm

### 1.1 Tầm nhìn

Japanese Writing Trainer là ứng dụng Android hỗ trợ người học tiếng Nhật luyện viết Hiragana bằng cảm ứng, kết hợp hướng dẫn thứ tự nét, tự viết từ trí nhớ và ôn tập lặp lại ngắt quãng (SRS).

Ứng dụng theo định hướng **offline-first**: dữ liệu ký tự được đóng gói trong ứng dụng, tiến độ và lịch sử luyện tập được lưu cục bộ trên thiết bị. Phiên bản hiện tại tập trung vào Hiragana; kiến trúc deck/card và dữ liệu ký tự được tách riêng để có thể mở rộng sau này.

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

- Luyện viết bộ 46 ký tự Hiragana cơ bản.
- Hỗ trợ Trace, Guided Writing, Recall Writing và Free Practice.
- Lưu tiến độ theo từng card và lịch sử luyện tập.
- Lập lịch học mới, học lại và ôn tập bằng FSRS.
- Cho phép điều chỉnh giới hạn card mới mỗi ngày và các tùy chọn luyện viết.
- Hiển thị lịch sử, thống kê, và hỗ trợ backup/restore dữ liệu cục bộ.

### 1.4 Ngoài phạm vi hiện tại

Katakana, Kanji, nhận diện chữ viết bằng AI/CNN, tài khoản người dùng, đồng bộ cloud, Firebase, đồng bộ Anki và import Excel chưa thuộc phạm vi triển khai hiện tại. Chỉ đưa vào roadmap khi có quyết định sản phẩm riêng.

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

- **Implemented:** dataset Hiragana JSON trong assets, gồm 46 ký tự.
- **Implemented:** dữ liệu tĩnh của ký tự được tách khỏi tiến độ học.
- **Implemented:** deck/card model cho phép một bộ thẻ tham chiếu đến character data.
- **Implemented:** thư viện ký tự và các màn hình tra cứu/chi tiết hiện có.
- **Planned:** mở rộng dataset sang Katakana hoặc Kanji sau khi MVP Hiragana ổn định.

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
- [ ] Mở rộng test backup có `schemaVersion` không hỗ trợ để xác nhận cả CardProgress và PracticeLog hiện có vẫn được giữ nguyên.
- [x] Xác minh tương thích backup schema v1: instrumentation test `versionOneBackupWithoutOptionalNullableFieldsRemainsRestorable` xác nhận backup v1 đời đầu không có các trường nullable FSRS và rating vẫn khôi phục được; người dùng đã chạy test và xác nhận PASS. Hiện chưa tồn tại schema backup version cũ hơn v1.
- [x] Đối chiếu thống kê với practice logs và card progress: Room integration test kiểm tra `reviewCount`/`correctCount` khớp với số lượng và rating trong lịch sử đã chạy pass.
- [x] Android instrumentation tests cho settings đã chạy pass: giá trị mặc định, đọc lại qua instance mới, theme không hợp lệ và giới hạn số card/ngày. Kiểm tra thủ công trên thiết bị bằng Android force-stop rồi mở lại cũng xác nhận Theme, số card mới/ngày, Grid và Hints đều được giữ nguyên.
- [ ] Xác minh không mất dữ liệu khi nâng cấp app bằng quy trình nâng cấp thực tế; migration Room 1→2→3 và 2→3 đã có instrumentation coverage riêng.

### Phase 4 — MVP release readiness

- [ ] Chạy instrumentation tests trên emulator và kiểm tra thủ công trên thiết bị thật.
- [ ] Kiểm tra offline, cài mới, nâng cấp và backup/restore.
- [ ] Build release APK/AAB và kiểm tra native libraries theo ABI.
- [ ] Hoàn thiện ghi công/giấy phép nguồn dataset.
- [ ] Cập nhật README, hướng dẫn cài đặt và release notes.
- [ ] Chỉ công bố MVP sau khi Acceptance Criteria bên dưới được kiểm chứng.

### Phase 5 — Mở rộng sau MVP

Chỉ xem xét sau khi MVP Hiragana ổn định: Katakana, Kanji theo cấp độ, cải thiện stroke analysis, import/export bổ sung hoặc đồng bộ. Các tính năng này chưa được cam kết trong MVP.

## 7. Acceptance criteria

| ID | Tiêu chí | Trạng thái hiện tại |
| --- | --- | --- |
| AC-01 | Có dữ liệu 46 Hiragana | **Implemented** |
| AC-02 | Viết nhiều nét bằng cảm ứng, Undo/Clear | **Implemented** |
| AC-03 | Trace và hiển thị dữ liệu nét | **Implemented** |
| AC-04 | Guided Writing và Recall Writing | **Implemented** |
| AC-05 | Self-rating bốn mức và cập nhật FSRS | **Implemented** |
| AC-06 | Learning, Relearning, Review queue | **Implemented** |
| AC-07 | Lưu tiến độ qua lần mở app | **Implemented; migration 1→2→3 và 2→3 đã được kiểm thử trên thiết bị/emulator** |
| AC-08 | Lịch sử luyện và thống kê | **Implemented** |
| AC-09 | Settings và JSON backup/restore | **Implemented; cần tiếp tục kiểm thử edge cases** |
| AC-10 | Build và chạy trên thiết bị Android thật | **Cần kiểm chứng release** |
| AC-11 | Offline đầy đủ | **Cần kiểm chứng acceptance trên thiết bị** |

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
