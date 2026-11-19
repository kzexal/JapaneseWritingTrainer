# Release readiness checklist

Cập nhật: 19/11/2026  
Baseline: `main`

Tài liệu này ghi nhận các việc cần hoàn tất trước khi tạo bản phát hành có chữ ký. Không xem việc build Debug thành công là bằng chứng Release APK/AAB đã sẵn sàng.

## Trạng thái hiện tại

| Hạng mục | Trạng thái | Ghi chú |
| --- | --- | --- |
| Chức năng học cốt lõi | PASS theo xác nhận kiểm thử gần nhất | Hiragana, Katakana, Kanji, Vocabulary, Session/FSRS, History, Statistics |
| Backup/restore JSON | PASS theo xác nhận kiểm thử gần nhất | Có validation và Room transaction; backup chứa CardProgress và PracticeLog |
| Daily streak và App Widget | PASS theo xác nhận kiểm thử gần nhất | Widget dùng PracticeLog và cùng phép tính với Statistics |
| Debug build và tests | PASS theo xác nhận người dùng | Unit tests, instrumentation tests và assembleDebug |
| Application ID | Đã cố định | `com.example.japanesewritingtrainer` |
| Version | Chưa bump cho release | Hiện `versionCode = 2`, `versionName = 1.1`; chỉ tăng khi chốt bản phát hành |
| Launcher icon | Chưa sẵn sàng | Icon hiện tại vẫn là hình mẫu Android robot trên nền xanh dạng grid; cần thiết kế icon riêng và adaptive icon |
| Release signing | Chưa cấu hình | Chưa có signingConfig cho keystore phát hành |
| Release build | Chưa xác minh | Chưa có bằng chứng `assembleRelease` hoặc cài đặt bản Release đã ký |
| KanjiVG attribution | Đã có notice | Cần rà soát điều kiện CC BY-SA 3.0 và cách phân phối dữ liệu phái sinh trước khi phát hành công khai |
| Android Auto Backup | Cần quyết định sản phẩm | Manifest đang bật backup và include Room database + SharedPreferences cho cloud backup/device transfer |

## Việc cần làm

### 1. Nhận diện ứng dụng

- [ ] Thiết kế launcher icon riêng, dễ nhận ra ở kích thước nhỏ.
- [ ] Tạo adaptive icon foreground/background; kiểm tra safe zone và mask hình tròn/vuông/bo góc.
- [ ] Kiểm tra monochrome icon trên launcher Android hỗ trợ themed icons.
- [ ] Xem trước icon trên nền sáng/tối và trên nhiều launcher.
- [ ] Giữ tên ứng dụng nhất quán giữa launcher, widget và màn hình thông tin ứng dụng.

### 2. Signing và bảo vệ khóa

- [ ] Tạo release keystore trên máy phát hành.
- [ ] Lưu keystore và mật khẩu ngoài repository; không commit keystore, mật khẩu hoặc signing properties.
- [ ] Cấu hình Gradle đọc signing credentials từ biến môi trường hoặc file properties nằm ngoài Git.
- [ ] Tạo ít nhất hai bản sao lưu keystore ở nơi an toàn; mất keystore có thể khiến việc cập nhật ứng dụng đã phát hành trở nên không thể.
- [ ] Xác minh chữ ký và certificate của APK/AAB phát hành.

### 3. Version và Release build

- [ ] Chốt versionName/versionCode; versionCode phải tăng so với bản cài đặt trước.
- [ ] Chạy `assembleRelease` sau khi cấu hình signing.
- [ ] Kiểm tra APK/AAB đã ký, package ID, version, ABI và manifest đã merge.
- [ ] Cài bản Release lên thiết bị thật; kiểm tra mở app, Room, FSRS JNI, backup/restore, widget và nâng cấp từ bản cũ.
- [ ] Lưu artifact phát hành và checksum; không lưu private signing key trong cùng thư mục artifact công khai.

### 4. Dữ liệu, quyền và giấy phép

- [ ] Quyết định có giữ Android Auto Backup hay chỉ dùng JSON backup thủ công. Hiện manifest bật `allowBackup` và backup rules bao gồm database + SharedPreferences; đây là cơ chế hệ điều hành, khác với đồng bộ trong ứng dụng.
- [ ] Xác nhận phạm vi dữ liệu được sao lưu: JSON backup thủ công chứa CardProgress và PracticeLog, không chứa cài đặt SharedPreferences hoặc content assets.
- [ ] Rà soát quyền trong merged manifest; chỉ giữ quyền cần thiết cho tính năng hiện tại.
- [ ] Rà soát KanjiVG CC BY-SA 3.0, attribution và nghĩa vụ ShareAlike đối với dữ liệu hình học được phân phối.
- [ ] Bổ sung thông tin phiên bản, ghi chú phát hành và hướng dẫn backup trước khi nâng cấp.

## Quy tắc bảo vệ dữ liệu

- Không dùng destructive Room migration.
- Không thay thế dữ liệu học bằng backup chưa được validate.
- Restore phải tiếp tục thực hiện theo transaction và có xác nhận rõ ràng vì thao tác này thay thế tiến độ/lịch sử hiện tại.
- Không đưa keystore, mật khẩu, token hoặc dữ liệu cá nhân vào Git.
- Không tăng versionCode/versionName chỉ để thử build; chỉ bump khi chốt một bản phát hành cụ thể.
