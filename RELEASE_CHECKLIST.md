# Release readiness checklist

Cập nhật: 20/11/2026  
Baseline: `main`

Tài liệu này ghi nhận các bước cần hoàn tất trước khi phát hành. Không xem build Debug thành công là bằng chứng Release APK đã được ký và kiểm tra.

## Trạng thái hiện tại

| Hạng mục | Trạng thái | Ghi chú |
| --- | --- | --- |
| Chức năng học cốt lõi | PASS theo xác nhận kiểm thử gần nhất | Hiragana, Katakana, Kanji, Vocabulary library, Session/FSRS, History, Statistics |
| Backup/restore JSON | PASS theo xác nhận kiểm thử gần nhất | Validate dữ liệu và restore bằng Room transaction |
| Daily streak và widget | Đã triển khai; cần kiểm tra lại trong Release | Chỉ hỗ trợ layout 2×1, hiển thị streak và số thẻ đến hạn |
| Debug build và tests | PASS theo xác nhận người dùng ở lần kiểm tra trước | Cần chạy lại sau đợt cleanup cuối |
| Application ID | Đã cố định | `com.example.japanesewritingtrainer` |
| Version | Chưa chốt phát hành | Hiện `versionCode = 2`, `versionName = 1.1`; chỉ tăng khi tạo bản phát hành đầu tiên |
| Launcher icon | Đã tích hợp | Cần xác minh hiển thị thực tế trên thiết bị |
| Release signing | Chưa hoàn tất | Gradle đã hỗ trợ bốn biến môi trường; keystore chưa được tạo |
| Release build | Chưa xác minh | Chưa có APK Release được ký |
| Third-party attribution | Đã có notice trong repo và màn Nguồn dữ liệu | Cần rà soát nghĩa vụ license/ShareAlike trước khi phân phối |
| Android Auto Backup | Chưa chốt chính sách | Manifest hiện bật backup cho database và SharedPreferences |

## 1. Kiểm tra ứng dụng trước phát hành

- [x] Hoàn thiện các luồng học và ôn tập theo phạm vi sản phẩm đã thống nhất.
- [x] Giữ giao diện sáng/tối theo bảng màu cố định; không dùng dynamic color.
- [x] Giữ widget duy nhất ở kích thước 2×1.
- [ ] Chạy lại Debug build và các kiểm thử local/device sau cleanup.
- [ ] Kiểm tra launcher icon, widget, nhắc học, backup/restore và các luồng học trên thiết bị thật.
- [ ] Kiểm tra các luồng JNI/FSRS trong bản Release.

Các mục TalkBack, font scale lớn, landscape và widget nhiều kích thước không thuộc phạm vi phát hành hiện tại.

## 2. Signing và bảo vệ khóa

- [ ] Tạo release keystore trên máy phát hành.
- [x] Cấu hình Gradle đọc signing credentials từ bốn biến môi trường; thiếu một phần credentials sẽ làm build dừng rõ ràng.
- [ ] Cấu hình bốn biến môi trường trong phiên build local.
- [ ] Tạo ít nhất hai bản sao lưu keystore ở các vị trí an toàn độc lập.
- [ ] Xác minh chữ ký và certificate của APK phát hành.
- [ ] Không đưa keystore, mật khẩu hoặc signing properties vào repository.

Tên biến môi trường:

- `JAPANESE_WRITING_TRAINER_KEYSTORE`
- `JAPANESE_WRITING_TRAINER_STORE_PASSWORD`
- `JAPANESE_WRITING_TRAINER_KEY_ALIAS`
- `JAPANESE_WRITING_TRAINER_KEY_PASSWORD`

Mất keystore có thể khiến không thể phát hành bản cập nhật tương thích cho các bản APK đã cài đặt bằng khóa đó.

## 3. Version và Release build

- [ ] Chốt versionName/versionCode khi sẵn sàng phát hành; versionCode phải lớn hơn bản đã phát hành trước đó.
- [ ] Chạy `:app:assembleRelease` sau khi cấu hình signing.
- [ ] Kiểm tra APK đã ký, package ID, version, ABI và manifest đã merge.
- [ ] Cài bản Release lên thiết bị thật; kiểm tra mở app, Room, FSRS JNI, backup/restore, widget và nhắc học.
- [ ] Kiểm tra nâng cấp từ bản cũ mà không mất tiến độ.
- [ ] Lưu artifact phát hành và checksum ở vị trí an toàn.

## 4. Dữ liệu, quyền và giấy phép

- [ ] Quyết định có giữ Android Auto Backup hay chỉ dùng JSON backup thủ công. Hiện manifest bật backup cho database và SharedPreferences.
- [ ] Xác nhận phạm vi dữ liệu được sao lưu và khôi phục.
- [ ] Rà soát merged manifest; chỉ giữ quyền cần thiết cho tính năng hiện tại.
- [x] Ghi nhận nguồn dữ liệu và license trong `THIRD_PARTY_NOTICES.md` và màn Nguồn dữ liệu trong Cài đặt.
- [ ] Rà soát nghĩa vụ license/ShareAlike đối với dữ liệu phái sinh trước khi phân phối công khai.
- [ ] Chuẩn bị ghi chú phát hành và hướng dẫn người dùng backup trước khi cập nhật.

## Quy tắc bảo vệ dữ liệu

- Không dùng destructive Room migration.
- Không thay thế dữ liệu học bằng backup chưa được validate.
- Restore phải tiếp tục thực hiện theo transaction và có xác nhận rõ ràng.
- Không đưa keystore, mật khẩu, token hoặc dữ liệu cá nhân vào Git.
- Không tăng versionCode/versionName chỉ để thử build; chỉ bump khi chốt bản phát hành.
