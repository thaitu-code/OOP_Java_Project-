# Hội thoại AI thống nhất

Cập nhật nguồn: 12/11/2026. Đây là hướng dẫn cho triển khai mới; không coi các kết quả kiểm thử cô lập là nghiệm thu Gemini hoặc PostgreSQL thật.

## Sử dụng

Trang `/ai` có một ô nhập và nút **Gửi**. Người dùng không chọn chế độ hỏi đáp hay thực hiện. Backend gửi ngữ cảnh và hội thoại để Gemini trả lời, hỏi bổ sung hoặc đề xuất hành động.

- “TDEE là gì?” và “Nếu tôi nặng 65 kg thì sao?” là hỏi đáp, không cập nhật hồ sơ.
- “Tôi vừa cân được 65 kg” có thể tạo đề xuất đổi cân nặng hiện tại.
- “Tôi muốn xuống 60 kg” có thể tạo đề xuất lưu cân nặng đích, tách khỏi cân nặng hiện tại.
- “Tôi vừa ăn 150 g cơm” cần xác định món và bữa trước khi đề xuất ghi nhật ký hôm nay.
- “Đổi bài hôm nay” cần xác định đúng lịch/bài và phạm vi một ngày; chưa rõ thì hỏi lại.

Đề xuất hiện chi tiết do backend dựng, có **Xác nhận / Chỉnh sửa / Hủy**. Trước xác nhận chỉ lưu tin nhắn và bản nháp, chưa ghi thay đổi nghiệp vụ. Chỉnh sửa đưa người dùng trở lại cùng ô nhập. Một câu “Đồng ý” chỉ xác nhận khi có đúng một đề xuất còn chờ trong phiên; nhiều đề xuất phải chọn đúng thẻ. Câu trả lời ngắn tiếp tục ngữ cảnh hiện tại.

Lịch tập có thể bắt đầu được chuẩn bị từ biểu mẫu tại `/workout`; biểu mẫu chuyển lựa chọn sang hội thoại để bổ sung thông tin và xem trước. Chấp nhận lịch không thêm bài hoặc chạy timer. Người dùng bấm **Thêm các bài hôm nay** riêng.

Thực đơn cho chọn từng món hôm nay, nhập gram thực ăn rồi xác nhận. Món đã ghi hiện trạng thái riêng; bản sửa thực đơn giữ liên kết nguồn để tránh ghi lặp cùng một mục kế hoạch. Sửa/xóa món đã ghi vẫn đi qua nghiệp vụ nhật ký và version hiện có.

## Các thành phần

- `AiConversationService`: điều phối ý định bằng phản hồi JSON của provider và ngữ cảnh hội thoại; không mở transaction trong lúc gọi provider.
- `AiConversationStore`: lưu lượt yêu cầu/tin nhắn/bản nháp trong các transaction ngắn, phân trang và trạng thái lỗi/thử lại.
- `AiContextService`: chỉ chọn dữ liệu được phép; gồm tuổi/thể trạng, điều kiện tập, mục tiêu, tổng và món ăn, kết quả tập gần đây, bài còn dở, streak và kế hoạch. Không serialize User hoặc DTO timer. Có một lượt tra cứu kho có giới hạn khi thiếu ứng viên.
- `AiDraftEngine`: kiểm tra và dựng bản xem trước từ dữ liệu thật; thực thi payload đã lưu, không gọi provider để suy diễn lại lúc xác nhận.
- `AiDraftService`: xác minh chủ sở hữu, hạn, fingerprint và trạng thái; chốt kết quả cùng thay đổi nghiệp vụ trong một transaction. Xóa snapshot cache trước khi kiểm tra lại để không dùng dữ liệu trước lúc chờ provider.
- `AiScheduleService`: nguồn xác định lịch/ngày nghỉ chung cho thêm bài, streak và nhắc nhở.
- `AiNotificationService`: điều kiện do backend tính, thông báo trong ứng dụng và chống trùng theo sự kiện. Nội dung dùng mẫu nên không phụ thuộc provider đang hoạt động.

## Hợp đồng API

Các endpoint bên dưới cần đăng nhập. Các thao tác ghi cần CSRF của phiên hiện tại.

| Endpoint | Ý nghĩa |
|---|---|
| `GET /api/ai/chat/session` | Ngữ cảnh kỹ thuật của lần đăng nhập; tải lại trang không cấp ngữ cảnh mới |
| `POST /api/ai/chat` | Gửi `{sessionId, content, requestId}`; `requestId` là UUID giữ nguyên khi thử lại cùng yêu cầu |
| `GET /api/ai/chat/turns/{requestId}` | Kiểm tra kết quả lượt đang chờ, không gọi provider thêm |
| `GET /api/ai/chat?before=...` | Tải 10 tin chưa xóa, theo cursor |
| `DELETE /api/ai/chat/{id}` | Xóa mềm tin của mình; hủy đề xuất chưa thực hiện gắn với lượt đó; giữ nghiệp vụ đã xác nhận |
| `GET /api/ai/proposals?sessionId=...` | Các đề xuất chưa hết hạn trong ngữ cảnh |
| `POST /api/ai/proposals/{id}/confirm` | Xác nhận đúng payload đã xem; gọi lặp trả kết quả đã lưu |
| `POST /api/ai/proposals/{id}/cancel` | Hủy bản nháp, không đảo ngược thao tác đã thực hiện |
| `GET /api/ai/schedule?date=YYYY-MM-DD` | Lịch hiệu lực của người đăng nhập tại ngày Việt Nam được hỏi |
| `POST /api/ai/recommendations/{id}/apply-workout` | Chấp nhận lịch đã lưu; không tự thêm bài |
| `POST /api/ai/recommendations/{id}/start-today-workout` | Thêm bài hôm nay vào buổi đang mở hoặc tạo buổi mới; không tự bắt đầu/hoàn thành bài |
| `POST /api/ai/recommendations/{id}/log-meals` | Ghi `{items:[{id,grams}]}` của thực đơn hôm nay, theo lựa chọn người dùng |
| `GET /api/ai/notifications` | Cập nhật điều kiện và lấy thông báo riêng của người dùng |
| `PATCH /api/ai/notifications/{id}/read` | Đánh dấu đọc; độc lập với trạng thái còn áp dụng |

Phản hồi gửi tin gồm `requestId`, `status` (`PENDING/COMPLETE/FAILED`), `userMessage`, `aiMessage`, `proposals`, `error`, `retryable`. Frontend cập nhật theo ID, không thêm trùng tin khi tải lịch sử hoặc nhận lại kết quả.

`POST /api/ai/actions` và `POST /api/ai/weekly-workout` cũ trả **410** và hướng về chat. Không còn đường gọi model rồi ghi trực tiếp từ hai endpoint này. Các API đọc kế hoạch và đánh dấu đã đọc vẫn được giữ.

## Quy tắc dữ liệu và mặc định

- Bản nháp có hạn 30 phút; tối đa 3 lần thử một lượt gọi provider; tối đa 8 lượt mới/người/phút. Một người chỉ có một lượt đang gọi provider tại một thời điểm. Một lượt được tra cứu thêm kho tối đa một lần.
- Các bản nháp độc lập được duyệt riêng. Sửa bản nháp dùng `supersedesDraftId`, không hủy các bản nháp không liên quan. Thay hồ sơ làm đề xuất kế hoạch phụ thuộc hồ sơ cũ hết hợp lệ.
- Lịch tuần lặp đến khi thay đổi. Lịch tương lai dùng `SCHEDULED` và chỉ thay lịch trước từ ngày hiệu lực; khoảng hiệu lực giữ được lịch sử. `specificDate` chỉ điều chỉnh đúng một ngày và gắn với lịch tuần gốc.
- Ngày không có bài trong dữ liệu cũ chưa đủ để công nhận nghỉ. Lịch mới phải xác định rõ ngày tập/nghỉ. Không cho chuyển hôm nay thành nghỉ sau khi đã bắt đầu bất kỳ bài nào, kể cả buổi sau đó bị hủy.
- Streak đối soát lấy ngày nghỉ từ resolver; không tăng check-in vì đọc/chấp nhận lịch. Mốc bắt đầu đối soát là sự kiện tập, lịch được chấp nhận hoặc bản ghi ngày đầu tiên đã có; không tự suy ra từ thời điểm tạo tài khoản không có múi giờ rõ ràng.
- Cấp độ và chỉ tiêu phải khớp dữ liệu kho. Bài thời gian giữ reps trống và hiển thị giây; không tự chọn cấp độ đầu tiên. Bài đang được lịch AI tham chiếu chỉ ngừng hoạt động, không xóa gốc làm hỏng liên kết.
- Mục tiêu dinh dưỡng được lưu cùng phiên bản thực đơn; tổng thực đơn tính theo từng ngày. Giới hạn kiểu số là kiểm tra dữ liệu, không phải chứng nhận mục tiêu phù hợp chuyên môn.
- Thao tác ghi món mang ngày đã xem trước; sang ngày mới phải xem lại, không tự chuyển món ngày cũ sang ngày mới. Nhật ký ngày cũ chỉ đọc, snapshot vẫn giữ nguyên.
- Cảnh báo vượt TDEE chỉ dùng điều kiện đã chốt cho `LOSE_WEIGHT`; chưa cảnh báo thiếu calo tự động. Có thêm nhắc buổi còn dở, bỏ lỡ từ 3 ngày phải tập và mốc streak. Đã đọc và hết áp dụng là hai trạng thái riêng.
- Email nhắc tập chỉ còn một scheduler: lấy ngày Việt Nam, cùng điều kiện bỏ lỡ/ngày nghỉ, đặt dấu chống trùng trước khi gửi và bỏ dấu khi lỗi gửi được báo. Không giữ transaction khi gửi email nhắc tập. Chưa có cơ chế bảo đảm giao email đúng một lần khi tiến trình chết giữa bước đặt dấu và gửi.

## Schema và chạy ứng dụng

Nguồn bổ sung `ai_chat_turns`, `ai_action_drafts`, `ai_notifications`, các cột mục tiêu/phiên bản/điều kiện tập của hồ sơ, metadata hiệu lực/nguồn đề xuất, cấp độ và liên kết nguồn món kế hoạch. Không đổi tên bảng chat cũ hoặc xóa dữ liệu cũ.

Dự án hiện dùng Hibernate schema update. Khi chạy bản mới cần xác minh các bảng/cột/index được bổ sung trong đúng cơ sở dữ liệu đang dùng. Không coi annotation hoặc việc H2 tạo schema thành công là bằng chứng PostgreSQL đã được cập nhật. Bản nháp không tồn tại trước bản cập nhật này; dữ liệu kế hoạch cũ thiếu cấp độ/ngày nghỉ cần tạo phiên bản mới khi không đủ điều kiện sử dụng.

Giữ cấu hình `GEMINI_API_KEY` và `GEMINI_MODEL` ở môi trường phía máy chủ. Phản hồi JSON và kiểm tra lý do model dừng được thực hiện theo [tài liệu GenerateContent của Google](https://ai.google.dev/api/generate-content); không tự đổi model đã cấu hình của dự án.

## Kiểm chứng

Kết quả chạy 12/11/2026: **263 kiểm thử Java đạt, 0 lỗi/0 bỏ qua** trong bộ chọn `*Test,!BackendJavaProjectApplicationTests`, gồm 11 ca tích hợp H2. Kiểm tra giao diện AI đạt 11 ca, dinh dưỡng đạt 15 ca, kiểm tra vòng đời giao diện workout đạt; kiểm tra cú pháp toàn bộ JavaScript đạt.

- Java: kiểm thử hỏi đáp/đề xuất, cập nhật hiện tại và đích, quyền/CSRF, hết hạn, stale version, retry, định danh trùng, lịch lặp và override, ngày nghỉ/streak; cùng hồi quy module cũ.
- Tích hợp: Spring/JPA thật trên H2 tạm, provider/email giả. Kiểm tra chưa xác nhận không sửa hồ sơ, hai xác nhận đồng thời chỉ ghi một lần, cập nhật trong lúc provider chạy, dữ liệu khác chủ, tài khoản khóa, sửa món và hết điều kiện cảnh báo. H2 không thay thế kiểm thử câu SQL `ON CONFLICT` riêng của PostgreSQL.
- Giao diện: `scripts/check-ai-ui.cjs`, `scripts/check-nutrition-ui.cjs`, `scripts/check-workout-ui.cjs` chạy mã JavaScript thật với DOM/HTTP giả. Không phải nghiệm thu trình duyệt thật.
- Cần nghiệm thu tiếp trên môi trường chạy thật: chất lượng nhận biết lời nói qua Gemini/model được cấu hình; Hibernate cập nhật PostgreSQL hiện hữu; giao dịch PostgreSQL và luồng ghi món mới; giao email và hiển thị thực tế trên trình duyệt.

Phiên công cụ hiện tại không có biến môi trường `GEMINI_API_KEY` hoặc `GEMINI_MODEL`, nên không chạy kiểm tra provider thật. Cấu hình trong IntelliJ hoặc tiến trình ứng dụng khác có thể khác phiên này; không tự sao chép hoặc thay khóa.
