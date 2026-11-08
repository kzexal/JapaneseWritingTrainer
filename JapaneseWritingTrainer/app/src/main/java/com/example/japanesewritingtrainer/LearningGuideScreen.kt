package com.example.japanesewritingtrainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Hướng dẫn sử dụng các chức năng hiện có của ứng dụng. */
@Composable
fun LearningGuideScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Hướng dẫn học",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Japanese Writing Trainer giúp bạn ghi nhớ cách viết tiếng Nhật bằng cách luyện viết thường xuyên và ôn lại đúng thời điểm.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GuideSection(
            title = "1. Bắt đầu học",
            items = listOf(
                "Mở Thư viện để xem 46 ký tự Hiragana hiện có, tìm kiếm ký tự hoặc chọn theo nhóm.",
                "Chọn một ký tự để xem cách đọc, số nét và thứ tự viết.",
                "Nên học từng nhóm nhỏ, viết chậm và chú ý hướng đi của từng nét trước khi tăng tốc."
            )
        )

        GuideSection(
            title = "2. Các chế độ luyện viết",
            items = listOf(
                "Trace – quan sát và tô theo nét mẫu để làm quen hình dạng và thứ tự nét.",
                "Guided Writing – viết từng nét theo thứ tự; ứng dụng kiểm tra nét hiện tại trước khi chuyển sang nét tiếp theo.",
                "Recall Writing – tự nhớ và viết lại ký tự mà không dựa vào nét mẫu.",
                "Free Practice – luyện viết tự do trên vùng vẽ; phù hợp để ôn lại sau khi đã quen ký tự."
            )
        )

        GuideSection(
            title = "3. Ôn tập và đánh giá FSRS",
            items = listOf(
                "Từ Trang chủ, chọn bắt đầu học hoặc ôn tập khi có ký tự đến hạn.",
                "Sau lượt ôn, chọn một trong bốn mức: Again (chưa nhớ), Hard (khó nhớ), Good (nhớ được) hoặc Easy (nhớ dễ dàng).",
                "Đánh giá giúp FSRS tính lịch ôn tiếp theo dựa trên trạng thái ghi nhớ của từng thẻ.",
                "Nếu chọn Again, thẻ có thể cần được luyện lại sớm hơn. Hãy chọn đúng mức độ nhớ thực tế, không chọn theo tốc độ viết."
            )
        )

        GuideSection(
            title = "4. Theo dõi tiến độ",
            items = listOf(
                "Trang chủ hiển thị tổng quan số ký tự đã thuộc, đang học, đến hạn và số ký tự mới.",
                "Lịch sử ghi lại các lượt luyện viết đã hoàn thành.",
                "Thống kê tổng hợp hoạt động luyện tập và tiến độ được lưu trong ứng dụng."
            )
        )

        GuideSection(
            title = "5. Cài đặt và dữ liệu",
            items = listOf(
                "Trong Cài đặt, bạn có thể đổi giao diện, giới hạn số thẻ mới mỗi ngày, bật/tắt lưới và gợi ý.",
                "Dùng Sao lưu để tạo tệp JSON chứa dữ liệu học tập; lưu tệp ở nơi an toàn.",
                "Dùng Khôi phục để chọn tệp JSON đã sao lưu. Kiểm tra kỹ tệp trước khi xác nhận khôi phục.",
                "Ứng dụng hoạt động offline và lưu dữ liệu trên thiết bị. Hãy chủ động sao lưu trước khi chuyển máy hoặc gỡ ứng dụng."
            )
        )

        GuideSection(
            title = "6. Nội dung hiện có và lộ trình",
            items = listOf(
                "Phiên bản hiện tại cung cấp bộ Hiragana và bốn chế độ luyện viết nêu trên.",
                "Katakana, Kanji và học từ vựng từ workbook Excel là các phần dự kiến bổ sung; chưa khả dụng trong phiên bản hiện tại."
            )
        )
    }
}

@Composable
private fun GuideSection(title: String, items: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
