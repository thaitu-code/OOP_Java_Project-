# Giao diện GymAI → FitnessAI

Nguồn giao diện: `D:/PTIT/java/GymAI.zip`, các component được `static/index.html` tải. Chỉ chuyển phong cách và bố cục; giữ Spring MVC/Thymeleaf, session, CSRF và nghiệp vụ của FitnessAI. Không đưa JWT, SPA router, timer trình duyệt hoặc dữ liệu mẫu của demo vào ứng dụng chính.

## Các màn hình

- Header chung: menu avatar cho Tổng quan, Tập luyện, Dinh dưỡng, AI, Hồ sơ và Quản trị theo quyền. Chuông dùng thông báo backend, hiển thị số chưa đọc và thao tác đánh dấu đã đọc. Không có sidebar/thanh điều hướng dưới.
- Dashboard: tổng ngày, biểu đồ bảy ngày, mục tiêu theo thực đơn đã xác nhận, buổi đang tập/lịch hiệu lực và huy hiệu thật. Khi thiếu mục tiêu, không tạo số mẫu. Calo tiêu hao chỉ cộng các bài hoàn thành theo `completedAt` trong ngày Việt Nam.
- AI: một composer; gợi ý chỉ điền tin nhắn; trả lời hiển thị bằng DOM text an toàn, hỗ trợ một phần Markdown (danh sách, chữ đậm, code inline, bảng). Sao chép dùng clipboard; không có micro, upload hay đánh giá giả. Đề xuất/nhận kết quả vẫn dùng các API đã có. Lời chào thu gọn khi có lịch sử; composer có hàng riêng, không che nội dung.
- Tập luyện: wizard chọn bài → cấu hình → xác nhận. Bài có cấp độ dùng thông số cấp độ thật; bài thủ công có thể lưu cặp số hiệp/lần dự kiến. Cả danh sách chỉ được ghi sau xác nhận. Tạo buổi không bắt đầu bài, không tự cấp check-in. Timer, lease, nghỉ, khôi phục và kết quả tiếp tục dùng module hiện tại.
- Dinh dưỡng: nhật ký bốn bữa, tổng ngày/mục tiêu hôm nay và biểu đồ tỷ trọng theo gram. Chọn món → nhập gram → xem trước → lưu. Xem trước dùng thông tin món/snapshot đang chỉnh, số liệu lưu do backend xác nhận. Lịch sử không dùng mục tiêu hôm nay. Thực đơn và món đã ăn giữ hai luồng riêng.
- Hồ sơ: ảnh bìa bằng CSS, avatar chữ viết tắt, mặc định chế độ xem, bật Chỉnh sửa mới sửa trường. Giữ ngày sinh, optimistic version và các mục tiêu hiện có. Đã bổ sung định dạng ISO cho ngày sinh để `input type=date` hiển thị/gửi đúng giá trị.
- Xác thực: giữ khung hai phần và hiệu ứng giới thiệu của bản chính; đổi style cùng hệ màu. Giữ các luồng đăng ký, xác minh và khôi phục mật khẩu.
- Quản trị: cùng bộ style; giữ các biểu mẫu tài khoản, thư viện/cấp độ/media và huy hiệu. Không mở rộng quyền.

CSS, script, biểu đồ được phục vụ cục bộ, không cần Tailwind CDN, Google Fonts, Chart.js hoặc router của demo. Font dùng Inter khi máy có sẵn, sau đó Segoe UI/Arial. Media bài/món vẫn dùng nguồn đã cấu hình trong thư viện; không thay bằng ảnh minh họa sai động tác.

## API bổ sung

### `GET /api/dashboard/summary`

Yêu cầu đăng nhập; tài khoản khóa/chưa xác minh bị từ chối. Phản hồi `Cache-Control: no-store`:

```json
{
  "today": "YYYY-MM-DD",
  "days": [{"date": "YYYY-MM-DD", "nutrition": {"calories": 0, "protein": 0, "carbs": 0, "fat": 0}, "burnedCalories": 0}],
  "targets": null,
  "currentWorkout": null,
  "nextWorkout": null
}
```

`days` có đúng bảy ngày liên tiếp, kết thúc hôm nay theo `Asia/Ho_Chi_Minh`. `targets` có `planId/calories/protein/carbs/fat` khi có thực đơn gắn với đề xuất đã xác nhận, còn hiệu lực và chứa món của hôm nay. `nextWorkout` có `planId/date/title/rest/exercises`; lịch tương lai không trở thành lịch hôm nay. Endpoint chỉ đọc dữ liệu, không tạo nhật ký, không gọi AI.

### `POST /workouts/from-selection`

Yêu cầu đăng nhập và CSRF:

```json
{
  "requestId": "UUID",
  "items": [{"exerciseId": 1, "levelId": 2, "plannedSets": 3, "plannedReps": 10}]
}
```

Danh sách 1–50 bài, không trùng; `levelId` và cặp chỉ tiêu chỉ truyền khi dùng. Trả `WorkoutResponse`. Một transaction gồm khóa người dùng, đọc receipt, tạo buổi, thêm từng bài và lưu receipt. Kiểm tra bài/cấp độ/chỉ tiêu/quyền dùng cùng `WorkoutService` với thao tác thường. Một lỗi làm rollback toàn bộ. Không gọi đường bắt đầu bài.

Receipt `workout_selections` có khóa duy nhất `(user_id, request_id)` và SHA-256 của danh sách có thứ tự. Gửi lặp cùng nội dung trả lại buổi đã tạo, kể cả khi buổi đã đóng; cùng khóa khác nội dung trả 409. Tài khoản khác không nhận receipt của nhau. Frontend giữ nguyên payload/requestId khi kết quả mạng chưa rõ, lưu trong sessionStorage theo tài khoản. Lỗi 400/409 cho phép sửa lựa chọn; 5xx/mất mạng cho phép kiểm tra/thử lại yêu cầu cũ.

Bảng mới được JPA tạo khi cấu hình `ddl-auto=update`. Môi trường `validate/none` dùng migration cộng thêm `scripts/sql/2026-11-13-workout-selections.sql`; không xóa hoặc chuyển dữ liệu cũ.

## Kiểm tra và xem trước

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\mvnw.cmd test
node scripts/check-ai-ui.cjs
node scripts/check-workout-ui.cjs
node scripts/check-workout-wizard.cjs
node scripts/check-nutrition-ui.cjs
```

`UiDataIntegrationTest` chạy Spring/JPA transaction thật trên H2 tạm, kiểm tra tạo lặp đồng thời, rollback, khác chủ sở hữu, tài khoản khóa, cấp độ/chỉ tiêu, snapshot dinh dưỡng, ranh giới ngày Việt Nam và kế hoạch tương lai/chưa xác nhận. `UiApiControllerTest` kiểm tra đăng nhập/CSRF/validation/status lỗi. `UiPageRenderingTest` render template thật, kiểm tra menu, form, ISO ngày sinh, quyền quản trị và các trang xác thực.

Lần chạy smoke test đầu tiên trong đợt chuyển giao diện dùng cấu hình PostgreSQL sẵn có vì test khởi động cũ chưa tách datasource. Test khởi động đã được chuyển sang H2 `boot_smoke`; các lần kiểm tra sau dùng datasource kiểm thử độc lập. Không dùng lần khởi động đó để kết luận các hành trình ghi dữ liệu PostgreSQL đã đạt nghiệm thu.

Xem trước trên trình duyệt:

```powershell
.\mvnw.cmd '-Dtest=UiPageRenderingTest,WorkoutPageControllerTest,NutritionPageControllerTest' test
node scripts/preview-ui.cjs
```

Mở `http://127.0.0.1:8773/home`. Server này chỉ dùng HTML Thymeleaf đã render và dữ liệu giả trong RAM; không proxy tới ứng dụng thật, database, email hoặc Gemini. Các số liệu mô phỏng chỉ tồn tại trong script kiểm thử, không nằm trong template/JS sản phẩm. Dừng bằng Ctrl+C.

Đã kiểm tra giao diện desktop và màn hình hẹp: menu avatar, chat/gợi ý/đề xuất, wizard tiến/lùi/xác nhận, thêm món theo gram và cập nhật tổng, hồ sơ xem/chỉnh sửa, khung đăng nhập/giới thiệu, dashboard và các trang quản trị. Kiểm tra trình duyệt này không chứng minh provider Gemini thật hoặc toàn bộ hành trình PostgreSQL thật đã hoạt động.

Chưa triển khai: avatar upload, VI–EN, micro, nhận diện ảnh, RPE/tempo/superset/dropset và quản trị nâng cao.

### Kết quả xác minh 2026-11-13

- Maven: **281 tests, 0 failures, 0 errors, 0 skipped**.
- Frontend: 11 kiểm tra AI, 15 tình huống dinh dưỡng, 8 tình huống wizard và bộ kiểm tra vòng đời/security/media/timer của tập luyện đều đạt; toàn bộ JavaScript qua `node --check`.
- `git diff --check` đạt.
- Ảnh kiểm tra bằng dữ liệu mô phỏng: `target/ui-preview/chat-desktop.jpg`, `target/ui-preview/chat-mobile.jpg`.
- Sau khi cập nhật source, chạy lại ứng dụng trong IntelliJ và tải lại trình duyệt bằng Ctrl+F5.

### 2026-11-13 — Đối chiếu riêng trang hồ sơ với demo

Trang hồ sơ đã được dựng lại theo cấu trúc bốn thẻ của component demo: hàng trên là thẻ hồ sơ có ảnh bìa/avatar/BMI/BMR/TDEE (1/3) và thông tin cá nhân (2/3); hàng dưới là thang BMI và mục tiêu tập luyện. Ảnh bìa dùng đúng ảnh Unsplash được demo tham chiếu, tải về `static/images/profile-cover.jpg` để phục vụ cục bộ. Style riêng ở `static/css/profile.css`.

Biểu mẫu thông tin cá nhân và mục tiêu thuộc cùng một form, giữ ngày sinh ISO, CSRF và expectedVersion. Sửa/Hủy bật/tắt và khôi phục cả hai fieldset; lỗi validation giữ cả hai phần ở chế độ chỉnh sửa. Mục tiêu đích và điều kiện tập luyện vẫn đọc dữ liệu thực tế; chỉnh các thông tin này tiếp tục qua đề xuất AI. Không đưa tần suất/địa điểm mẫu hoặc nút tải avatar chưa hoạt động từ demo vào bản chính.

Các mức BMI tham chiếu được đối chiếu với [CDC](https://www.cdc.gov/bmi/adult-calculator/bmi-categories.html), chỉ hiển thị phân loại người trưởng thành khi tuổi từ 20 trở lên. Các chỉ số hiển thị lấy từ hồ sơ đã lưu; không tự đổi BMR/TDEE khi đang nhập dữ liệu.

Xác minh đợt chỉnh riêng: `UiPageRenderingTest` 8 tests đạt, `node --check` cho profile script/preview đạt, `git diff --check` đạt. Đã kiểm tra desktop, mobile, Sửa/Hủy và xác nhận CSS runtime tại localhost:8081 khớp source. Preview trình duyệt sử dụng dữ liệu mô phỏng, không ghi database. Ảnh: `target/ui-preview/profile-demo-desktop.jpg`, `profile-demo-details.jpg`, `profile-demo-mobile.jpg`.
