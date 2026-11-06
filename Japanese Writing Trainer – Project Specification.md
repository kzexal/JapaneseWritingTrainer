# Japanese Writing Trainer – Project Specification

*Phiên bản 1.0 · Phương án C · MVP: Hiragana Writing Trainer*

## 1. Product Requirements Document (PRD)

### 1.1 Tầm nhìn

Xây dựng ứng dụng Android học viết tiếng Nhật bằng cảm ứng, kết hợp **luyện nét**, **viết từ trí nhớ** và **ôn tập thông minh (SRS)**. Ứng dụng hoạt động offline, dữ liệu độc lập, có thể mở rộng lâu dài từ Hiragana → Katakana → Kanji N5–N1.

### 1.2 Vấn đề cần giải quyết

- Ứng dụng hiện có thường chỉ cho xem thứ tự nét, thiếu cơ chế ép người học **tự nhớ và viết lại**.
- Thiếu lịch ôn tập gắn với *từng chữ* theo mức độ nhớ.
- Dữ liệu bị khóa trong ứng dụng, khó tích hợp với quy trình học sẵn có (Excel → Anki).

### 1.3 Đối tượng người dùng

- Người tự học tiếng Nhật ở mức bắt đầu (Hiragana) đến trung cấp (Kanji N5–N1).
- Người đã quen với SRS/Anki, muốn luyện **viết tay** thay vì chỉ nhận diện mặt chữ.

### 1.4 Mục tiêu và chỉ số thành công (MVP)

| Mục tiêu | Chỉ số |
| --- | --- |
| Học trọn bộ 46 Hiragana | 46/46 ký tự có dữ liệu nét và học được |
| Viết bằng ngón tay mượt | Vẽ nét không giật, độ trễ cảm nhận được thấp |
| Ôn tập có hệ thống | Mỗi ký tự có `dueDate` và được xếp vào hàng đợi ôn |
| Giữ tiến độ | Đóng/mở app không mất dữ liệu |
| Cài đặt được | Build APK chạy trên điện thoại thật |

### 1.5 Ngoài phạm vi MVP

Katakana, Kanji, AI nhận diện chữ viết, đồng bộ Anki, import Excel, tài khoản/server/Firebase.

### 1.6 Ràng buộc và giả định

- Nền tảng: Android smartphone; Kotlin + Jetpack Compose.
- Offline-first, không cần mạng.
- Một nhà phát triển, vừa làm vừa học Kotlin → thời gian thực tế có thể lệch so với kế hoạch.
- Nguồn dữ liệu nét chữ SVG cần kiểm tra giấy phép trước khi dùng (xem mục 6).

## 2. Functional Requirements

### 2.1 Chế độ học

| Mã | Chế độ | Mô tả | Ưu tiên |
| --- | --- | --- | --- |
| FR-01 | Trace | Tập viết theo nét mẫu mờ | MVP |
| FR-02 | Guided Writing | Viết từng nét, có gợi ý nét tiếp theo | MVP |
| FR-03 | Recall Writing | Tự viết từ trí nhớ, xem đáp án sau | MVP |
| FR-04 | Review | Ôn tập theo lịch SRS | MVP |
| FR-05 | Free Practice | Viết tự do, không tính điểm | Should |

### 2.2 Canvas viết

- FR-10: Vẽ nét bằng ngón tay, ghi lại điểm theo thời gian.
- FR-11: Hiển thị lưới ô vuông (có đường chéo/giữa, bật/tắt được).
- FR-12: Undo nét cuối, Clear toàn bộ.
- FR-13: Hỗ trợ nhiều nét; phân tách nét theo thao tác nhấc tay.

### 2.3 Stroke Engine

- FR-20: Parse SVG path thành đường vẽ trên Compose Canvas.
- FR-21: Hiển thị chữ mẫu, từng nét, số thứ tự nét.
- FR-22: Animation vẽ từng nét theo thứ tự.
- FR-23: Tô sáng nét tiếp theo trong Guided Writing.

### 2.4 Thư viện ký tự

- FR-30: Danh sách ký tự theo nhóm (cơ bản, các hàng a/ka/sa…).
- FR-31: Màn hình chi tiết: chữ, cách đọc, số nét, animation.
- FR-32: Tìm kiếm theo ký tự hoặc romaji.
- FR-33: Hiển thị trạng thái học của từng ký tự (mới / đang học / đã thuộc).

### 2.5 Đánh giá và SRS

- FR-40: **Level 1** – Tự đánh giá sau khi viết: Again / Hard / Good / Easy.
- FR-41: SRS cập nhật `easeFactor`, `intervalDays`, `dueDate` theo đánh giá.
- FR-42: Hàng đợi ôn hôm nay = các ký tự có `dueDate ≤ hôm nay`.
- FR-43: Giới hạn số ký tự mới mỗi ngày (cấu hình được).
- FR-44: *(Should)* **Level 2** – Gợi ý tự động dựa trên vùng bắt đầu, hướng, thứ tự nét; chỉ mang tính tham khảo, người học vẫn quyết định kết quả cuối.

### 2.6 Tiến độ và thống kê

- FR-50: Lưu lịch sử mỗi lần luyện (thời điểm, chế độ, kết quả).
- FR-51: Màn hình thống kê: số chữ đã học, số lần ôn, tỷ lệ đạt, chuỗi ngày học.

### 2.7 Cài đặt và dữ liệu

- FR-60: Settings: số chữ mới/ngày, bật/tắt lưới, bật/tắt gợi ý.
- FR-61: Backup/Restore dữ liệu học (xuất/nhập file JSON).
- FR-62: Dữ liệu persist qua Room.

### 2.8 Yêu cầu phi chức năng

| Mã | Yêu cầu |
| --- | --- |
| NFR-01 | Chạy hoàn toàn offline |
| NFR-02 | Khởi động app < 3 giây trên máy tầm trung |
| NFR-03 | Vẽ nét mượt, không rơi khung hình rõ rệt |
| NFR-04 | Dữ liệu người dùng chỉ lưu trên thiết bị |
| NFR-05 | Hỗ trợ Android API 26+ *(đề xuất, cần xác nhận)* |
| NFR-06 | Dữ liệu ký tự tách biệt khỏi dữ liệu học để dễ mở rộng |

## 3. User Flow

### 3.1 Lần đầu mở app

1. Mở app → Home (không cần đăng ký).
2. Chọn **Bắt đầu bài đầu tiên** → vào bài học nhóm あ.
3. Với mỗi ký tự: xem animation → **Trace** → **Guided** → **Recall**.
4. Sau Recall, tự đánh giá Again/Hard/Good/Easy.
5. Ký tự được lên lịch ôn; quay lại Home với tiến độ cập nhật.

### 3.2 Ngày học thường ngày

1. Mở Home → xem *số chữ cần ôn* và *bài mới hôm nay*.
2. Bấm **Ôn tập** → Review lần lượt từng ký tự đến hạn (viết từ trí nhớ).
3. Xem đáp án → tự đánh giá → SRS cập nhật.
4. Hết hàng đợi → có thể học chữ mới hoặc Free Practice.

### 3.3 Tra cứu thư viện

Home → Library → chọn nhóm → chọn ký tự → Chi tiết → chọn chế độ luyện.

### 3.4 Luồng trong một lượt Recall

`Hiện cue (cách đọc)` → `Người học viết` → `Bấm Xem đáp án` → `So sánh với chữ mẫu` → `Chọn Again/Hard/Good/Easy` → `Cập nhật SRS` → `Ký tự tiếp theo`

## 4. UI/UX Specification

### 4.1 Các màn hình

| Màn hình | Nội dung chính |
| --- | --- |
| **Home** | Bài hôm nay, tiến độ tổng, số chữ cần ôn, nút Ôn tập / Học mới |
| **Library** | Lưới/ danh sách ký tự theo nhóm, ô tìm kiếm, trạng thái từng chữ |
| **Character Detail** | Chữ lớn, cách đọc, số nét, animation, nút chọn chế độ luyện |
| **Writing** | Canvas + lưới, chữ mẫu mờ, nút Undo/Clear/Gợi ý, nút Xem đáp án |
| **Review** | Giống Writing nhưng ẩn mẫu ban đầu; kết thúc bằng 4 nút đánh giá |
| **Statistics & Settings** | Biểu đồ tiến độ, cấu hình *(làm sau khi phần học ổn định)* |

### 4.2 Điều hướng

Bottom navigation: **Home · Library · Review · Stats/Settings**. Màn Writing mở toàn màn hình từ Library hoặc Home.

### 4.3 Nguyên tắc thiết kế

- Canvas là trung tâm: chiếm tối đa diện tích, nút điều khiển gọn.
- Một thao tác chính mỗi màn hình.
- Phản hồi tức thì khi chạm; không có hộp thoại gây gián đoạn khi đang viết.
- Chống chạm nhầm: vùng canvas bỏ qua cử chỉ cạnh màn hình.
- Hỗ trợ giao diện sáng/tối *(Should)*.
- Chữ mẫu và nét người dùng phải phân biệt rõ về màu sắc.

### 4.4 Trạng thái cần thiết kế

Chưa có dữ liệu · Đang tải · Hết hàng đợi ôn · Hoàn thành bài · Lỗi tải dữ liệu ký tự.

## 5. Database Schema (Room)

### 5.1 Bảng `character_progress`

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| `characterId` | TEXT (PK) | Khớp `id` trong dataset, vd `hiragana_a` |
| `easeFactor` | REAL | Mặc định 2.5 |
| `intervalDays` | INTEGER | Mặc định 0 |
| `dueDate` | INTEGER | Epoch day; null/0 nếu chưa học |
| `reviewCount` | INTEGER | Tổng số lần ôn |
| `correctCount` | INTEGER | Số lần viết đạt |
| `incorrectCount` | INTEGER | Số lần chưa đạt |
| `lastReviewedAt` | INTEGER | Epoch millis |
| `status` | TEXT | `NEW` / `LEARNING` / `REVIEW` |

### 5.2 Bảng `practice_log`

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | INTEGER (PK, auto) |  |
| `characterId` | TEXT (FK, index) |  |
| `mode` | TEXT | TRACE / GUIDED / RECALL / REVIEW / FREE |
| `rating` | TEXT | AGAIN / HARD / GOOD / EASY (nếu có) |
| `durationMs` | INTEGER | Thời gian viết |
| `timestamp` | INTEGER | Epoch millis |

### 5.3 Bảng `app_settings` *(hoặc DataStore)*

`newPerDay`, `showGrid`, `showHints`, `theme`.

### 5.4 Thuật toán SRS đề xuất (MVP)

Dùng biến thể đơn giản kiểu SM-2:

| Đánh giá | Hành động |
| --- | --- |
| **Again** | `intervalDays = 0` (ôn lại trong ngày/ngày mai), `easeFactor −= 0.2` (tối thiểu 1.3), `incorrectCount++` |
| **Hard** | `intervalDays = max(1, intervalDays × 1.2)`, `easeFactor −= 0.15` |
| **Good** | `intervalDays = max(1, intervalDays × easeFactor)` (lần đầu: 1 ngày) |
| **Easy** | `intervalDays = max(2, intervalDays × easeFactor × 1.3)`, `easeFactor += 0.15` |

Sau mỗi lần: `dueDate = today + intervalDays`, `reviewCount++`, `lastReviewedAt = now`. Các hệ số là giá trị khởi điểm, nên cấu hình được và điều chỉnh sau khi dùng thực tế.

## 6. Dataset Specification

### 6.1 Nguyên tắc

Tách **dữ liệu tĩnh** (nét chữ, chung cho mọi người) khỏi **dữ liệu học** (riêng người dùng, lưu trong Room). Liên kết qua `characterId`.

### 6.2 Cấu trúc JSON

```json
{
  "id": "hiragana_a",
  "character": "あ",
  "type": "hiragana",
  "group": "basic",
  "reading": "a",
  "strokeCount": 3,
  "strokes": [
    { "order": 1, "svgPath": "M ..." },
    { "order": 2, "svgPath": "M ..." },
    { "order": 3, "svgPath": "M ..." }
  ]
}
```

### 6.3 Quy ước

- `id`: `<type>_<romaji>`, chữ thường, không dấu cách.
- `type`: `hiragana` | `katakana` | `kanji`.
- `group`: nhóm hiển thị (vd `a`, `ka`, `sa`…) hoặc cấp độ (`n5`…).
- `strokes[].order` bắt đầu từ 1, liên tục, không trùng.
- `strokeCount` phải bằng độ dài `strokes`.
- Toàn bộ path dùng **cùng hệ tọa độ** (viewBox thống nhất, vd 109×109 hoặc 1024×1024) để render đồng nhất.
- Tệp đặt tại `assets/characters/`, ví dụ `hiragana.json`.

### 6.4 Nguồn dữ liệu và giấy phép

Nguồn thường dùng cho nét chữ là dự án mở như **KanjiVG** (giấy phép Creative Commons BY-SA 3.0, có dữ liệu kana và kanji). Cần **xác nhận lại giấy phép và yêu cầu ghi công** trước khi đưa vào ứng dụng phát hành, và ghi nguồn trong README/màn hình About.

### 6.5 Công cụ trong thư mục `tools/`

Script chuyển SVG nguồn → JSON theo schema trên, kèm bước kiểm tra tự động (đủ nét, thứ tự liên tục, path hợp lệ). Cách làm này phù hợp với kinh nghiệm pipeline Excel → Anki của bạn và dùng lại được khi mở rộng sang Kanji.

## 7. Technical Architecture

### 7.1 Tổng quan

MVVM + Repository Pattern, offline-first.

`UI (Compose) → ViewModel → Repository → { Room DAO, Asset JSON loader }`

### 7.2 Cấu trúc dự án

```
JapaneseWritingTrainer/
├── README.md
├── docs/            (PROJECT_PLAN, REQUIREMENTS, ARCHITECTURE,
│                     DATABASE, UI_DESIGN, DATASET, ROADMAP)
├── app/
│   ├── ui/          home · library · writing · review · statistics
│   ├── domain/      model · srs · handwriting
│   ├── data/        local (database, dao) · repository · assets
│   └── navigation/
├── data/
└── tools/
```

### 7.3 Công nghệ

| Công nghệ | Vai trò |
| --- | --- |
| Kotlin | Ngôn ngữ |
| Jetpack Compose | UI |
| Compose Canvas | Vẽ và nhận input chạm |
| Room | Lưu tiến độ học |
| Kotlin Serialization | Đọc JSON ký tự |
| Navigation Compose | Điều hướng |
| ViewModel + StateFlow | Quản lý trạng thái |
| Coroutines | Xử lý bất đồng bộ |
| SVG path parser | Chuyển `svgPath` thành `Path` để vẽ |

### 7.4 Thành phần cốt lõi

- **WritingCanvas**: thu điểm chạm thành danh sách nét (`List<Stroke>`), mỗi nét là chuỗi `Point(x, y, t)`.
- **StrokeRenderer**: parse và vẽ SVG, hỗ trợ tô theo từng nét và animation.
- **SrsScheduler** (domain): hàm thuần `(progress, rating, today) → newProgress`, dễ unit test.
- **CharacterRepository**: nạp dataset từ assets, cache trong bộ nhớ.
- **ProgressRepository**: đọc/ghi Room, tạo hàng đợi ôn.
- **StrokeMatcher** (Level 2): so khớp vùng bắt đầu, hướng, thứ tự, hình dạng với dung sai rộng.

### 7.5 Chiến lược kiểm tra chữ viết

| Mức | Cơ chế | Phiên bản |
| --- | --- | --- |
| Level 1 | Tự đánh giá Again/Hard/Good/Easy | MVP |
| Level 2 | Stroke matching, chỉ gợi ý | Sau MVP nếu còn thời gian |
| Level 3 | Nhận diện tự động (CNN/handwriting recognition) | Tương lai |

Lý do: Level 1 giúp SRS chạy ngay mà không cần AI; chấm nét quá cứng sẽ gây khó chịu vì nét ngón tay không bao giờ trùng khít SVG.

### 7.6 Kiểm thử

- Unit test: SRS scheduler, parser dataset, StrokeMatcher.
- Test DAO với Room in-memory.
- Kiểm thử thủ công trên điện thoại thật (độ mượt canvas, chống chạm nhầm).

## 8. Development Roadmap

| Milestone | Nội dung | Kết quả |
| --- | --- | --- |
| **M1 – Prototype** | Phase 0–1 | Mở app và viết chữ trên Canvas |
| **M2 – Writing Engine** | Phase 2–3 | Hiển thị Hiragana và thứ tự nét |
| **M3 – Learning System** | Phase 4 | Guided, Recall, đánh giá kết quả |
| **M4 – MVP** | Phase 5–6 | SRS, lưu tiến độ, thống kê, APK |
| **M5 – Expansion** | Phase 7 | Katakana, Kanji, Anki, nâng cấp chấm nét |

Ước lượng MVP: khoảng 6–8 tuần, phụ thuộc tốc độ làm quen Kotlin và xử lý dữ liệu.

## 9. Task Breakdown theo tuần

### Tuần 1 – Phase 0 & bắt đầu Phase 1

- [ ] Chốt phạm vi MVP, hoàn thiện tài liệu này.
- [ ] Cài Android Studio, tạo project Kotlin + Compose.
- [ ] Tạo Git repository, thêm cấu trúc `docs/`.
- [ ] Xác định nguồn SVG và kiểm tra giấy phép.
- [ ] Màn hình Writing đầu tiên: vẽ nét bằng ngón tay.

### Tuần 2 – Phase 1: Writing Canvas

- [ ] Lưới ô vuông.
- [ ] Undo / Clear.
- [ ] Tối ưu độ mượt khi vẽ.
- [ ] Tạo dữ liệu mẫu cho chữ あ.

### Tuần 3 – Phase 2: Stroke Engine

- [ ] Parse SVG path, hiển thị あ.
- [ ] Render từng nét, hiển thị số thứ tự.
- [ ] Animation thứ tự nét.
- [ ] Chế độ **Trace** (hoàn thành Milestone: prototype dùng được).

### Tuần 4 – Phase 3: Character Library

- [ ] Viết script `tools/` chuyển dữ liệu → JSON.
- [ ] Import 46 Hiragana, kiểm tra tự động.
- [ ] Màn hình Library, Character Detail, tìm kiếm.

### Tuần 5 – Phase 4: Guided & Recall

- [ ] Guided Writing: tô sáng nét tiếp theo, viết từng nét.
- [ ] Recall Writing: ẩn mẫu, nút xem đáp án.
- [ ] Màn hình tự đánh giá 4 mức.

### Tuần 6 – Phase 5: SRS & Progress

- [ ] Room: entity, DAO, repository.
- [ ] SrsScheduler + unit test.
- [ ] Hàng đợi ôn, màn hình Review.
- [ ] Lưu lịch sử luyện viết.

### Tuần 7 – Phase 5 (tiếp) & Phase 6

- [ ] Màn hình Home và Statistics.
- [ ] Settings, Backup/Restore.
- [ ] Sửa lỗi, tối ưu giao diện.

### Tuần 8 – Hoàn thiện

- [ ] Kiểm thử trên điện thoại thật.
- [ ] Đối chiếu Acceptance Criteria.
- [ ] Build APK và cài đặt.
- [ ] Hoàn thiện README, ghi công nguồn dữ liệu.

## 10. Acceptance Criteria cho MVP

MVP được xem là hoàn thành khi **tất cả** điều kiện sau đạt:

| # | Tiêu chí | Cách kiểm chứng |
| --- | --- | --- |
| AC-01 | Học được toàn bộ 46 Hiragana | Mỗi ký tự có dữ liệu nét hợp lệ và mở được chế độ luyện |
| AC-02 | Viết trực tiếp bằng ngón tay | Vẽ nhiều nét, Undo/Clear hoạt động đúng |
| AC-03 | Xem thứ tự nét | Animation và số thứ tự nét đúng với dữ liệu |
| AC-04 | Có Trace, Guided Writing, Recall | Cả ba chế độ chạy hết một ký tự từ đầu đến cuối |
| AC-05 | Tự đánh giá và SRS | Chọn Again/Hard/Good/Easy làm `dueDate` thay đổi đúng quy tắc |
| AC-06 | Lịch ôn tập | Màn hình Review chỉ hiện ký tự đến hạn |
| AC-07 | Lưu tiến độ | Đóng hẳn app, mở lại, tiến độ và lịch ôn còn nguyên |
| AC-08 | Thống kê cơ bản | Hiển thị số chữ đã học, số lần ôn, tỷ lệ đạt |
| AC-09 | Offline | Hoạt động đầy đủ khi tắt mạng |
| AC-10 | Cài đặt được | Cài APK lên điện thoại Android thật và chạy ổn định |

## 11. Rủi ro và cách giảm thiểu

| Rủi ro | Mức độ | Giảm thiểu |
| --- | --- | --- |
| Dữ liệu SVG không đồng nhất hoặc vướng giấy phép | Cao | Kiểm tra sớm ở Phase 0, viết script kiểm tra dữ liệu |
| Canvas giật khi vẽ | Trung bình | Thử trên máy thật từ Phase 1, tránh tạo lại `Path` mỗi khung hình |
| Chấm nét quá cứng nhắc | Trung bình | Giữ Level 1 làm chính, Level 2 chỉ gợi ý với dung sai rộng |
| Chưa quen Kotlin làm chậm tiến độ | Trung bình | Làm prototype nhỏ trước, không viết toàn bộ app cùng lúc |
| Thêm tính năng tùy hứng | Trung bình | Bám tài liệu này; tính năng mới phải đưa vào Roadmap trước |

## 12. Bước tiếp theo

1. Tạo project Android Studio (Kotlin + Jetpack Compose).
2. Làm màn hình **Writing Canvas** và xử lý cảm ứng.
3. Tạo dữ liệu mẫu cho chữ **あ** và hiển thị SVG.
4. Hoàn thành **Trace** để có prototype dùng được.

Sau khi bạn xem xong, mình có thể tách tài liệu này thành các file riêng (`PROJECT_PLAN.md`, `REQUIREMENTS.md`, `ARCHITECTURE.md`…) để đưa vào thư mục `docs/` của repository, hoặc bắt đầu viết code cho Writing Canvas.
