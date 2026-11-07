# Khả năng mở rộng trợ lý AI và ranh giới quyền truy cập

Ngày rà soát: 04/11/2026. Phạm vi: đọc mã hiện tại, đối chiếu PLAN và yêu cầu mới; chưa triển khai chatbot, provider API hoặc quyền thực thi của AI.

## 1. Kết luận từ mã nguồn

Kiến trúc có thể mở rộng bằng một lớp điều phối AI gọi các Service hiện có. Chưa thể nối trực tiếp model vào Entity/Repository hoặc đưa các response hiện tại nguyên trạng cho provider.

| Phần hiện tại | Bằng chứng và khả năng tái sử dụng | Việc còn thiếu trước khi nối AI |
|---|---|---|
| Hồ sơ thể trạng | UserProfile tách khỏi User; UserProfileService cập nhật chiều cao/cân nặng/mục tiêu và tính lại BMI/BMR/TDEE | Validation chủ yếu ở Controller; cần kiểm tra lại ở Service, trạng thái tài khoản, cập nhật từng trường và chống ghi đè khi hai nguồn cùng sửa |
| Tài khoản/bảo mật | User/UserService/VerificationToken chứa tài khoản, email, hash mật khẩu, role và token | Không đưa các Entity này hoặc UserRepository/UserService vào danh sách công cụ của model |
| DTO hồ sơ | UserProfileResponse không có mật khẩu nhưng có username, displayName, birthDate | Tạo AssistantContext DTO riêng; chỉ chọn dữ liệu sức khỏe cần thiết, không gửi username/email/role/token; dùng tuổi thay ngày sinh nếu đủ |
| Tập luyện | WorkoutService/WorkoutTrackingService đã có nghiệp vụ, quyền chủ sở hữu, timer và snapshot; có getRemainingWorkoutDetails | Tạo adapter đọc lịch/bài chưa hoàn thành; không gửi WorkoutTimerResponse chứa timerToken, không cho AI giả heartbeat/complete hoặc sửa kết quả đã chốt |
| Streak/huy hiệu | CheckInStreakService và BadgeService đã hoạt động | getSummary có đối soát/khóa/ghi, không phải truy vấn chỉ đọc; AI đọc snapshot sau đối soát, không trực tiếp đặt streak hoặc cấp huy hiệu |
| Nhắc nhở | Có DailyReminderScheduler, gửi email và đối soát nửa đêm | Scheduler dùng LocalDate.now() theo timezone máy dù cron có zone; cần thống nhất giờ Việt Nam, tránh gửi trùng giữa các luồng và bổ sung thông báo trong ứng dụng |
| Chat/provider/lịch AI | Chưa có Entity/Service/Controller cho chat, recommendation hay client gọi provider trong nguồn hiện tại | Đây là các phần phát triển sau, không coi mô tả PLAN là tính năng đã có |

Đây là kết luận đọc nguồn, không phải nghiệm thu quyền AI hoặc các endpoint hiện hữu bằng HTTP mới.

## 2. Luồng tích hợp được chọn

```text
Giao diện chat / tác vụ nhắc nhở
    → Backend xác định người dùng và quyền
    → AssistantContext (DTO có danh sách trường cho phép)
    → AiProviderClient (API bên ngoài, có thể thay provider)
    → Câu trả lời hoặc đề nghị gọi một công cụ có tên cố định
    → AssistantActionService xác thực công cụ và dữ liệu
    → Profile / Nutrition / Workout Service
    → Transaction, snapshot, audit, DTO kết quả
```

- Backend gắn người dùng từ phiên đăng nhập; model không được chọn userId, username, role, SQL, URL endpoint hay tên phương thức tùy ý.
- Công cụ là các hành động hẹp như đọc hồ sơ sức khỏe, tìm food/exercise đang hoạt động, đọc nhật ký, cập nhật cân nặng/chiều cao/mục tiêu, ghi/sửa/xóa món hôm nay và đề xuất lịch. Không có công cụ sửa trường Entity bất kỳ.
- Lệnh rõ ràng của user qua chat được chuyển thành thao tác tương ứng và chịu cùng validation như giao diện. AI tự đề xuất chưa được hiểu là user đã ăn/tập hoặc đã chấp nhận lịch; lịch/thực đơn đề xuất cần được chấp nhận theo PLAN.
- AI có thể yêu cầu sửa dữ liệu cá nhân được phép; backend vẫn cấm nhật ký quá khứ và kết quả tập đã chốt. Chỉ thao tác tập qua luồng chính mới sinh check-in/streak; model không tự xác nhận vận động thực tế.
- Hành động với dữ liệu bảo mật bị chặn ngay ở danh sách công cụ và DTO, kể cả user đang đăng nhập bằng Admin hoặc prompt yêu cầu mở quyền.
- Tiến trình backend có thể đọc User nội bộ để xác thực; giới hạn này nói về thông tin/công cụ được cấp cho model, không phải cấm backend thực hiện xác thực.
- Không giữ khóa DB trong lúc chờ provider. Khi nhận phản hồi, kiểm tra lại quyền, ngày và version trước khi ghi. Provider lỗi/timeout không làm mất dữ liệu nghiệp vụ.
- Dữ liệu món ăn, ghi chú, nội dung chat và kết quả tìm kiếm là dữ liệu, không phải chỉ dẫn tăng quyền. Không dùng prompt làm lớp phân quyền duy nhất.

Nguồn nguyên tắc: [OWASP LLM06 — Excessive Agency](https://genai.owasp.org/llmrisk/llm062025-excessive-agency/?lang=us), nhấn mạnh công cụ tối thiểu và kiểm tra quyền tại hệ thống thực thi.

## 3. Các phần phát triển sau lõi Nutrition

1. AssistantContext/query adapter: dữ liệu thể trạng, tổng ăn/tập, bài chưa tập, streak và kho hợp lệ; lọc thông tin nhận diện và token trước khi gửi provider.
2. AssistantActionService: allowlist, validation ở tầng Service, cùng chủ sở hữu, version/idempotency, audit trước/sau và liên kết yêu cầu chat. Không gọi UserService quản lý tài khoản hoặc Repository tùy ý.
3. AiProviderClient: giao diện độc lập provider, timeout, giới hạn số lần gọi, credential chỉ ở backend; đầu ra có cấu trúc không được thực thi trực tiếp.
4. ChatSession/ChatMessage: hội thoại thuộc một người, phân trang và bộ nhớ ngữ cảnh riêng; không dựa vào model để ngăn đọc chéo người dùng.
5. MealPlan/WorkoutPlan/NutritionTarget: kế hoạch tách nhật ký thực tế; lưu phê duyệt/phiên bản/ngày hiệu lực. Thay cân nặng không tính lại snapshot các bữa/bài cũ.
6. Notification/delivery: backend xác định điều kiện nhắc, ngày/mốc và chống trùng bền vững; AI chỉ diễn đạt/đề xuất. Gửi email/provider sau commit hoặc qua hàng đợi/outbox, không kéo dài transaction ghi nhật ký/timer.

Chiều cao/cân nặng hiện chỉ giữ giá trị hiện tại. Nếu cần biểu đồ thay đổi thể trạng, thêm lịch sử thể trạng riêng khi triển khai tính năng đó; nhật ký dinh dưỡng không thay thế lịch sử này.

## 4. Tác động ngay tới Nutrition

- Giữ FoodCategory, Food, NutritionLog và LogDetail độc lập với provider/chat; mỗi người/ngày có một log, chỉ sửa hôm nay, tính bằng BigDecimal và snapshot.
- Entity có phương thức nghiệp vụ thay vì setter cho chủ sở hữu/ngày/snapshot/kết quả. Service sau này xác thực actor và gọi cùng phương thức cho cả UI lẫn AI.
- LogDetail có version chống cập nhật cũ; createdSource/lastModifiedSource dùng NutritionEntrySource USER/AI để phân biệt nguồn tạo và sửa. Các trường này do backend gắn, không phải quyền truy cập hay audit đầy đủ.
- Chat/message/action audit sau này tham chiếu ID nghiệp vụ; không thêm FK bắt buộc từ LogDetail tới một hội thoại chưa tồn tại.
- M4-02 chỉ xây nền Entity/enum và kiểm thử cô lập. M4-03 trở đi bổ sung Repository/Service/DTO/Controller; chưa có API AI hoặc Nutrition khi mới xong Entity.
- Metadata READ ONLY trên DB đã cấu hình ngày 04/11 xác nhận users.id là bigint; chưa có bốn bảng food_categories/foods/daily_meal_logs/meal_details trong public. Không chạy DDL hoặc ghi bản ghi thật trong lượt rà soát.

## 5. Ca nghiệm thu khi tích hợp AI

- Context và tool response không có username/email/password/hash/token/role/cookie hoặc dữ liệu người khác; tool đọc tập không trả timerToken.
- Prompt yêu cầu đổi tài khoản/mật khẩu/quyền hoặc gọi SQL/endpoint tùy ý bị từ chối ở backend.
- Đọc/ghi cùng người, tài khoản khóa/chưa xác minh, version cũ, ngày đổi trong lúc gọi provider và snapshot lịch sử được kiểm tra lại.
- User nói “tôi vừa ăn…” có thể ghi theo lệnh; AI gợi ý món không tự sinh LogDetail. Kế hoạch không trở thành kết quả tập đã hoàn thành.
- Retry không nhân đôi hành động; audit đúng nguồn user/AI; provider lỗi không ghi một phần.
- Nhắc bài chưa tập/chuỗi theo dữ liệu backend; không gửi trùng cùng sự kiện hoặc làm sai ngày do timezone máy.
