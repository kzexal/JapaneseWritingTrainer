# Spec thiết kế lại UI và Widget – Japanese Writing Trainer (Android)

Phiên bản 1.0 · Màu chính: #6667AB · Nền tảng: Android 8.0+ (API 26+)

## 1. Tổng quan và mục tiêu

Thiết kế lại toàn bộ giao diện app và widget màn hình chính của Japanese Writing Trainer để hiện đại, nhất quán và dễ dùng hơn. Logic học (lặp lại ngắt quãng), dữ liệu và tiến độ hiện có được giữ nguyên.

**Mục tiêu**

1. Từ lúc mở app đến lúc bắt đầu học tối đa 1 chạm.
2. Chữ kana/kanji là nhân vật chính của giao diện, không chỉ là con số thống kê.
3. Một hệ thống thiết kế thống nhất (màu, chữ, hình khối) dùng chung cho app và widget.
4. Widget cho người dùng biết nhanh hôm nay còn bao nhiêu chữ cần ôn và chuỗi ngày học (streak) của mình; bấm vào widget để mở giao diện chính của app.

**Ngoài phạm vi:** thay đổi thuật toán SRS, thêm bộ chữ/kanji mới, đồng bộ đám mây, tài khoản người dùng.

## 2. Hiện trạng và vấn đề

### App

- Hai thanh tab chồng nhau (Hiragana/Katakana/Kanji và Từ vựng/Ôn tập/Lịch sử/Thống kê) có cùng kiểu dáng, khó phân biệt cấp bậc.
- Biểu tượng cài đặt bị chen cạnh tab Kanji, không có vùng riêng.
- Màn hình không có hình ảnh chữ Nhật nào, dù đây là app học chữ. Số "0 / 71" lớn nhưng không chỉ ra việc cần làm tiếp.
- Thanh tiến độ ở 0% trông như bị lỗi.
- Nút phụ "Thư viện" và "Luyện Guided" chỉ là nút viền mảnh, quá yếu so với tầm quan trọng của chúng.
- Ngôn ngữ giao diện lẫn Anh–Việt ("Learning / Relearning", "Guided").
- Bảng màu xám trung tính cùng tím mặc định, chưa có bản sắc riêng.

### Widget

- Thẻ trắng kem cố định, không theo chế độ sáng/tối hay màu hệ thống, lệch tông với màn hình chính.
- Dòng "1 ngày liên tiếp" quá lớn, xuống dòng và chiếm gần hết diện tích; thông tin hữu ích (số chữ cần ôn) lại không có.
- Dòng "Kỷ lục" bị cắt ở cạnh dưới.
- Không có phân cấp thông tin rõ ràng.

## 3. Hệ thống thiết kế (Design System)

### 3.1 Màu sắc

Màu thương hiệu: **#6667AB** (chàm xám/periwinkle). Đây là màu hạt giống (seed) để sinh bảng màu Material 3; các giá trị dưới đây là bảng màu đề xuất cho bản sáng và tối. Chữ trắng trên #6667AB đạt tương phản khoảng 5.1:1 (đạt WCAG AA).

**Bản sáng (Light)**

| Vai trò | Màu | Ghi chú |
| --- | --- | --- |
| primary | #6667AB | Nút chính, thành phần được chọn |
| onPrimary | #FFFFFF | Chữ/biểu tượng trên primary |
| primaryContainer | #E4E3FF | Nền thẻ nổi bật, chip được chọn |
| onPrimaryContainer | #1E1E5A | Chữ trên primaryContainer |
| secondary | #5F6088 | Thành phần phụ |
| secondaryContainer | #E3E1F4 | Nút tonal, chip |
| accent (streak) | #D9693F | Biểu tượng lửa, cảnh báo sắp mất streak |
| accentContainer | #FFE2D6 | Nền nhấn của streak |
| success | #3E8E6B | Đã thuộc, đúng nét |
| warning | #C98A1B | Sắp đến hạn |
| error | #BA1A1A | Sai nét, lỗi |
| background / surface | #FBF9FF | Nền giấy hơi lạnh |
| surfaceContainer | #F1EFF8 | Thẻ thông thường |
| surfaceContainerHigh | #EAE8F3 | Thẻ nâng cao hơn |
| onSurface | #1B1B21 | Chữ chính |
| onSurfaceVariant | #5C5C6B | Chữ phụ |
| outline | #7A7A8C | Viền, đường kẻ |

**Bản tối (Dark)**

| Vai trò | Màu | Ghi chú |
| --- | --- | --- |
| primary | #BFC0FF | Bản sáng hơn của #6667AB để đủ tương phản trên nền tối |
| onPrimary | #2E2F72 | Chữ trên primary |
| primaryContainer | #4B4C91 | Nền thẻ nổi bật |
| onPrimaryContainer | #E2E0FF | Chữ trên primaryContainer |
| secondary | #C5C4E0 | Thành phần phụ |
| secondaryContainer | #444560 | Nút tonal, chip |
| accent (streak) | #FFB59A | Lửa streak, cảnh báo |
| accentContainer | #5C2E1B | Nền nhấn của streak |
| success | #8FD5B0 | Đã thuộc, đúng nét |
| warning | #E6B85C | Sắp đến hạn |
| error | #FFB4AB | Sai nét, lỗi |
| background / surface | #131318 | Nền tối hơi ấm tím |
| surfaceContainer | #1F1F25 | Thẻ thông thường |
| surfaceContainerHigh | #2A2A31 | Thẻ nâng cao hơn |
| onSurface | #E4E1E9 | Chữ chính |
| onSurfaceVariant | #C7C5D0 | Chữ phụ |
| outline | #918F9A | Viền, đường kẻ |

**Quy tắc dùng màu**

- Màu primary chỉ dành cho hành động chính và trạng thái đang chọn; mỗi màn hình có tối đa một nút primary đặc.
- Màu accent chỉ dùng cho streak và cảnh báo; không dùng cho nút thường.
- Trạng thái của chữ trong lưới kana dùng: Mới = surfaceContainerHigh, Đang học = primaryContainer, Đã thuộc = success (kèm biểu tượng tick để không chỉ dựa vào màu).
- **Màu hệ thống (Material You):** có công tắc trong Cài đặt, mặc định **tắt**. Mặc định dùng bảng màu thương hiệu ở trên; từ Android 12 trở lên người dùng có thể bật để lấy màu theo hình nền.

### 3.2 Kiểu chữ

| Vai trò | Font | Cỡ gợi ý |
| --- | --- | --- |
| Ký tự kana/kanji hiển thị lớn | Zen Maru Gothic hoặc Noto Serif JP | 96–160sp (hero), 28–40sp (ô lưới) |
| Tiêu đề màn hình | Font hệ thống hoặc Noto Sans, đậm | 28sp |
| Tiêu đề thẻ | Noto Sans, bán đậm | 20sp |
| Nội dung | Noto Sans, thường | 14–16sp |
| Nhãn, chú thích | Noto Sans, vừa | 12sp |

Font phải hỗ trợ đầy đủ dấu tiếng Việt (kiểm tra dấu chồng ở cỡ lớn). Tôn trọng cỡ chữ hệ thống đến 1.3× mà không cắt chữ.

### 3.3 Hình khối và khoảng cách

- Bo góc: thẻ 24dp, ô lưới kana 12dp, nút và chip 999dp (hình viên thuốc), ô nhập/khung viết 20dp.
- Khoảng cách theo bội số của 4dp; lề màn hình 20dp; khoảng cách giữa các thẻ 12–16dp.
- Phân tầng bằng màu nền (surfaceContainer) thay vì viền; chỉ dùng viền (outline) cho ô nhập và đường kẻ khung viết.
- Vùng chạm tối thiểu 48dp.

### 3.4 Biểu tượng

Dùng Material Symbols Rounded đồng nhất, không trộn emoji. Biểu tượng lửa cho streak là biểu tượng vector riêng (màu accent).

### 3.5 Chuyển động

- Nét vẽ của chữ chạy theo thứ tự nét khi vào màn hình luyện viết (dưới 400ms mỗi lần phát).
- Chuyển trang kiểu fade-through; số liệu đếm lên nhẹ khi vào màn hình.
- Tôn trọng cài đặt "giảm hiệu ứng/animation" của hệ thống (tắt mọi hoạt ảnh không cần thiết).

## 4. Điều hướng

**Thanh điều hướng dưới (bottom navigation), 4 mục:**

1. **Học** – màn chính.
2. **Ôn tập**
3. **Thư viện** – gồm cả Từ vựng dưới dạng tab con.
4. **Thống kê** – gồm cả Lịch sử dưới dạng tab con.

**Quy tắc**

- Bộ chọn chữ **Hiragana / Katakana / Kanji** là bộ chọn dạng segmented ở đầu màn "Học" (và lọc tương ứng ở Ôn tập, Thư viện); không còn là thanh tab cấp 1.
- Top bar có tiêu đề màn hình ở bên trái, biểu tượng Cài đặt ở bên phải (vùng chạm 48dp, tách khỏi bộ chọn chữ).
- Bộ chọn chữ được ghi nhớ giữa các lần mở app.

## 5. Đặc tả các màn hình

### 5.1 Màn "Học" (thay màn Hiragana hiện tại)

Bố cục từ trên xuống:

1. **Top bar:** tiêu đề "Học", chip streak (biểu tượng lửa + số ngày), biểu tượng Cài đặt.
2. **Bộ chọn chữ:** segmented Hiragana / Katakana / Kanji.
3. **Thẻ hành động chính (hero card)**
   - Nền primaryContainer, hiển thị một ký tự lớn kèm romaji: ký tự đến hạn ôn gần nhất, hoặc ký tự mới tiếp theo nếu không có thẻ đến hạn.
   - Nút chính duy nhất, đổi theo trạng thái:
     - Có thẻ đến hạn → "Ôn N chữ".
     - Hết thẻ đến hạn, còn chỉ tiêu ngày → "Học chữ mới".
     - Hết cả hai → "Luyện tự do" kèm dòng chúc mừng ngắn.
   - Dòng phụ: "Hôm nay đã học X/10 chữ mới".
4. **Hàng chip số liệu:** "Đã thuộc N" · "Cần ôn N" · "Chữ mới N" (thay cho khối 3 cột lớn hiện tại).
5. **Lưới tiến độ kana:** mỗi chữ trong bộ đang chọn là một ô (71 ô với Hiragana), tô màu theo trạng thái ở mục 3.1. Bấm vào ô mở chi tiết chữ (cách viết, thứ tự nét, trạng thái ôn). Ở 0% lưới vẫn hiển thị đầy đủ, nên không còn cảm giác trống.
6. **Lối tắt phụ:** hai nút tonal "Thư viện" và "Luyện theo nét" (đổi tên từ "Luyện Guided").

### 5.2 Màn luyện viết

- Khung viết vuông lớn, nền surfaceContainer, có đường kẻ tham chiếu mờ (dạng lưới chữ 田).
- Phát hoạt ảnh thứ tự nét; có nút "Xem lại" và "Xóa nét".
- Phản hồi ngay khi vẽ: đúng nét (success + tick), sai thứ tự (error + biểu tượng cảnh báo); không truyền đạt chỉ bằng màu.
- Thanh tiến độ phiên học ở đầu màn hình; nút thoát có hộp thoại xác nhận.
- Màn kết thúc phiên: tóm tắt số chữ, tỷ lệ đúng, streak hiện tại.

### 5.3 Ôn tập

- Danh sách chia nhóm "Đến hạn hôm nay" và "Sắp tới", mỗi nhóm có số lượng.
- Nút nổi hoặc nút cố định phía dưới: "Bắt đầu ôn".
- Thuật ngữ trên giao diện: "Đang học" và "Học lại" thay cho "Learning / Relearning".

### 5.4 Thư viện

- Tab con: Chữ cái · Từ vựng.
- Chữ cái: lưới toàn bộ ký tự của bộ đang chọn, có bộ lọc theo trạng thái (Tất cả / Mới / Đang học / Đã thuộc) và tìm kiếm theo romaji.
- Từ vựng: danh sách từ với furigana, nghĩa tiếng Việt, lọc theo cấp độ nếu dữ liệu có.

### 5.5 Thống kê

- Thẻ tổng quan: streak hiện tại, kỷ lục, tổng số chữ đã thuộc.
- Lịch nhiệt (heatmap) 12 tuần theo số chữ học mỗi ngày.
- Biểu đồ chữ đã thuộc theo thời gian và tỷ lệ trả lời đúng.
- Tab con "Lịch sử": danh sách các phiên học theo ngày.

### 5.6 Cài đặt

- **Giao diện:** Sáng / Tối / Theo hệ thống; công tắc "Màu theo hình nền" (Android 12+, mặc định tắt).
- **Học:** chỉ tiêu chữ mới mỗi ngày (mặc định 10).
- **Nhắc học:** bật/tắt và chọn giờ.
- **Ngôn ngữ giao diện:** Tiếng Việt / English.
- **Widget:** hướng dẫn thêm widget vào màn hình chính.

## 6. Đặc tả Widget

### 6.1 Nguyên tắc chung

- **Chạm vào bất kỳ vị trí nào của widget chỉ mở giao diện chính của app (màn "Học")**; không có nút hành động riêng và không điều hướng sâu vào ôn tập hay màn khác.
- Widget chỉ hiển thị thông tin, không chứa nút bấm riêng; vì vậy trạng thái được thể hiện bằng chữ và biểu tượng, không bằng nút.
- Màu nền và chữ theo bảng màu ở mục 3.1 (sáng/tối tự động theo hệ thống); nếu người dùng bật "Màu theo hình nền" trong app thì widget cũng theo màu hệ thống.

### 6.2 Ba kích thước

| Kích thước | Nội dung | Ghi chú |
| --- | --- | --- |
| Nhỏ 2×2 | Số chữ cần ôn (rất lớn), nhãn "chữ cần ôn", chip streak nhỏ ở góc | Xem nhanh |
| Vừa 4×2 | Trái: ký tự kana lớn cần ôn gần nhất kèm romaji. Phải: số chữ cần ôn, streak, 7 chấm tròn thể hiện 7 ngày gần nhất (đã học / chưa học). Dòng chú thích dưới cùng | Dùng chính |
| Rộng 4×1 | Một dòng: streak · N chữ cần ôn · dòng trạng thái ngắn | Gọn |

### 6.3 Các trạng thái

| # | Trạng thái | Hiển thị |
| --- | --- | --- |
| 1 | Có chữ đến hạn | Số chữ cần ôn nổi bật + ký tự gần nhất (4×2) + streak |
| 2 | Hết chữ đến hạn, chưa học hôm nay | Dòng "Học thêm chữ mới hôm nay" thay cho số cần ôn |
| 3 | Đã học hôm nay | Biểu tượng tick (success), câu động viên ngắn, streak làm điểm nhấn |
| 4 | Streak sắp mất (sau 18:00 và chưa học) | Chip streak đổi sang accent kèm chữ "Học trước nửa đêm để giữ streak" |
| 5 | Chưa có dữ liệu / lỗi đọc dữ liệu | "Mở app để bắt đầu"; không để trống, không crash |

### 6.4 Kích thước, bố cục và hành vi

- Bo góc theo hệ thống (`system_app_widget_background_radius` trên Android 12+), đệm theo chuẩn; widget phản hồi theo kích thước (responsive) khi người dùng kéo đổi cỡ.
- Không cắt chữ ở bất kỳ cỡ chữ hệ thống nào đến 1.3×; thay vào đó rút gọn hoặc ẩn thông tin phụ theo thứ tự ưu tiên: số chữ cần ôn > streak > ký tự > 7 chấm > dòng chú thích.
- Màn cấu hình khi thêm widget (tùy chọn): chọn bộ chữ hiển thị – Hiragana / Katakana / Kanji / Tất cả.
- Có ảnh/bố cục xem trước (preview) đúng nội dung trong bộ chọn widget của launcher.
- Truy cập: mô tả nội dung (contentDescription) đầy đủ cho TalkBack, ví dụ "Còn 12 chữ cần ôn, chuỗi 5 ngày. Chạm để mở ứng dụng".

### 6.5 Cập nhật dữ liệu

- Làm mới ngay sau khi kết thúc một phiên học.
- Làm mới khi qua nửa đêm (đổi ngày) và định kỳ khoảng 30 phút bằng WorkManager.
- Không dùng báo thức chính xác (exact alarm).
- Dữ liệu widget đọc từ cùng nguồn với app (không nhân bản logic SRS).

## 7. Yêu cầu phi chức năng

- **Truy cập:** tương phản chữ tối thiểu WCAG AA (4.5:1), hỗ trợ cỡ chữ lớn, TalkBack đọc đúng thứ tự, không truyền đạt thông tin chỉ bằng màu.
- **Hiệu năng:** màn "Học" hiển thị dưới 300ms trên máy tầm trung; lưới kana cuộn mượt.
- **Bản địa hóa:** mọi chuỗi nằm trong tài nguyên chuỗi (vi/en); thống nhất thuật ngữ, không lẫn Anh–Việt.
- **Tương thích:** Android 8.0+ (API 26+); Material You chỉ bật từ Android 12+.
- **Giữ nguyên dữ liệu:** không thay đổi lược đồ dữ liệu hiện có nếu không thật cần; nếu cần thì có migration.
- **Chế độ tối:** phải được kiểm thử trên màn hình AMOLED (không dùng đen thuần #000 cho nền thẻ để tránh bệt chữ).

## 8. Giai đoạn và sản phẩm bàn giao

| Giai đoạn | Nội dung | Sản phẩm |
| --- | --- | --- |
| P0 – Design system | Bảng màu sáng/tối, kiểu chữ, hình khối, biểu tượng, thành phần dùng chung | Tệp theme, token màu, kiểu chữ, thư viện thành phần |
| P1 – Màn "Học" và điều hướng | Bottom nav, bộ chọn chữ, hero card, chip số liệu, lưới kana | Mockup + code màn hình |
| P2 – Widget | 3 kích thước, 5 trạng thái, màn cấu hình, preview | Mockup + code widget |
| P3 – Các màn còn lại | Luyện viết, Ôn tập, Thư viện, Thống kê, Cài đặt | Mockup + code |
| P4 – Hoàn thiện | Hoạt ảnh, kiểm tra A11y và font scale, ảnh cho cửa hàng ứng dụng | Báo cáo kiểm thử |

## 9. Giả định

- App hiện dùng Jetpack Compose với Material 3; widget sẽ viết bằng Jetpack Glance. Nếu widget hiện tại dùng RemoteViews/XML thì viết theo đó.
- Chỉ đổi giao diện, không thêm tính năng mới ngoài: lưới kana, widget nhiều trạng thái và màn cấu hình widget.
- "Từ vựng" là tính năng phụ, nằm trong Thư viện.
- Các giá trị màu ngoài #6667AB là đề xuất và có thể tinh chỉnh khi xem mockup.
