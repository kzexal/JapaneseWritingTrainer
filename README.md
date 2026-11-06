# Japanese Writing Trainer

Ứng dụng Android luyện viết tiếng Nhật bằng cảm ứng, xây dựng với Kotlin và Jetpack Compose. Ứng dụng hoạt động offline, lưu tiến độ trên thiết bị bằng Room và dùng FSRS-rs để lập lịch ôn tập.

## Chức năng hiện có

- Luyện viết Hiragana bằng Trace, Guided Writing, Recall Writing và Luyện tự do.
- Thư viện 46 Hiragana, lọc theo nhóm và tìm kiếm bằng ký tự hoặc romaji.
- Xem chi tiết ký tự, thứ tự nét và animation.
- Ôn tập theo FSRS-6, hàng đợi thẻ khó và giới hạn số chữ mới mỗi ngày.
- Lịch sử luyện viết, thống kê, tùy chỉnh giao diện/lưới/gợi ý.
- Sao lưu và khôi phục tiến độ cùng lịch sử bằng JSON.
- Dữ liệu lưu cục bộ; không yêu cầu tài khoản hoặc đồng bộ đám mây.

## Build

Mở thư mục \`JapaneseWritingTrainer/\` bằng Android Studio hoặc chạy từ PowerShell:

\`\`\`powershell
cd JapaneseWritingTrainer
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
\`\`\`

APK debug được tạo tại:

\`app/build/outputs/apk/debug/app-debug.apk\`

## Dataset và ghi công

Dữ liệu SVG thứ tự nét trong \`app/src/main/assets/characters/hiragana.json\` được chuyển đổi từ **KanjiVG**.

- Copyright © Ulrich Apel.
- License: [Creative Commons Attribution-ShareAlike 3.0 (CC BY-SA 3.0)](https://creativecommons.org/licenses/by-sa/3.0/).
- Source project: [https://github.com/KanjiVG/kanjivg](https://github.com/KanjiVG/kanjivg).
- Tệp dữ liệu chuyển đổi từ KanjiVG được phân phối theo CC BY-SA 3.0. Khi sửa đổi hoặc phân phối lại dữ liệu này, hãy giữ ghi công và tuân thủ điều khoản ShareAlike của giấy phép.

Có thể tạo lại dataset bằng script ở thư mục `JapaneseWritingTrainer/tools/`:

```powershell
cd JapaneseWritingTrainer
python tools/generate_hiragana.py
```

Script tải SVG nguồn, kiểm tra đủ 46 ký tự, ID duy nhất, số nét và thứ tự nét liên tục trước khi ghi đè JSON.

Mã nguồn ứng dụng và dữ liệu bên thứ ba là các thành phần riêng biệt; giấy phép của dataset không tự động áp dụng cho toàn bộ mã nguồn ứng dụng.

## Cấu trúc chính

- \`JapaneseWritingTrainer/app/src/main/java/\`: mã nguồn Android.
- \`JapaneseWritingTrainer/app/src/main/assets/characters/\`: dataset ký tự.
- \`JapaneseWritingTrainer/native/fsrs-bridge/\`: JNI bridge tới FSRS-rs.
- \`docs/\`: tài liệu kỹ thuật và tích hợp FSRS.
