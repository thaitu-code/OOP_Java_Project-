# API Theo dõi tập luyện

Phạm vi tài liệu: backend thư viện bài tập và vòng đời buổi tập. Luồng chính đã có bằng chứng Console User 49 PASS/0 FAIL/4 SKIP, Admin 80/0/3 và khác chủ 16/0/0; schema được xác minh ở PROCESS mốc 248. Các ca tài khoản/thời gian/rollback riêng chưa chạy đầy đủ. Giao diện mới và giới hạn kiểm thử được ghi trong [WORKOUT_FRONTEND.md](WORKOUT_FRONTEND.md); lịch tuần do AI chưa thuộc phần này.

## Đăng nhập và lỗi

- User và Admin đều thao tác buổi của chính mình. Admin không có quyền đổi ID để điều khiển buổi người khác.
- Dùng session đăng nhập hiện tại. POST/PATCH/DELETE phải gửi CSRF hợp lệ trong header `X-CSRF-TOKEN`.
- Chưa đăng nhập có thể nhận `302 /login`. Với fetch phải kiểm tra `response.redirected`, không coi HTML đăng nhập HTTP 200 là JSON thành công.
- `400`: dữ liệu/ID sai; `403`: tài khoản chưa xác minh/bị khóa hoặc khác chủ; `404`: không có buổi/detail tương ứng; `409`: thao tác không hợp trạng thái, chưa đủ thời gian hoặc thiếu cân nặng.
- Trạng thái trả về là nguồn quyết định. Một số thao tác timer trả `200` kèm `FROZEN` khi phát hiện quá hạn; đây không phải hoàn thành bài.

## Buổi tập

### Xóa bài tập khỏi thư viện

`DELETE /admin/exercises/{exerciseId}` dành cho Admin, có CSRF: thành công `204`, không tìm thấy `404`, còn thuộc bất kỳ buổi `IN_PROGRESS` nào thì `409`.

Từ bản sửa ngày 03/11/2026, Service khóa Exercise, kiểm tra buổi mở, rồi đặt liên kết `WorkoutDetail.exercise` thành NULL trước khi xóa Exercise trong cùng transaction. Không xóa dòng lịch sử hoặc sửa snapshot, trạng thái, thời lượng, calo và check-in. Các cấp độ được ORM xóa theo Exercise; media cũ vẫn giữ để lịch sử sử dụng. Luồng không phụ thuộc khóa ngoại hiện có đã được chuyển sang `ON DELETE SET NULL` hay chưa; cột `workout_details.exercise_id` vẫn phải cho phép NULL. Lỗi ràng buộc dữ liệu trả `409` và ghi metadata an toàn vào log máy chủ.

| Method | URL | Body / kết quả |
|---|---|---|
| POST | `/workouts` | Không body; `201` tạo buổi, `409` nếu đã có buổi mở |
| GET | `/workouts/current` | Buổi `IN_PROGRESS` của mình, hoặc `404` |
| GET | `/workouts/current/state` | Snapshot `{workout, details, timer}` của buổi mở trong một request; timer có `timerToken=null`, response `no-store`, `404` rỗng nếu không có buổi |
| GET | `/workouts?page=0&size=20&status=COMPLETED` | Trang lịch sử; status tùy chọn, size 1–100 |
| GET | `/workouts/{workoutId}` | Trạng thái, mốc bắt đầu/kết thúc, tổng phút và calo |
| PATCH | `/workouts/{workoutId}/complete` | Không body; cần ít nhất một detail `COMPLETED` |
| PATCH | `/workouts/{workoutId}/cancel` | Không body; hủy buổi đang mở |

Kết thúc hợp lệ chuyển buổi `COMPLETED`, các bài chưa xong sang `CANCELLED`. Đây là quy tắc người dùng chốt ngày 01/11/2026. Hủy buổi chuyển buổi `CANCELLED` và hủy các bài dở. Cả hai trường hợp đều giữ nguyên kết quả, snapshot và check-in đã có của bài `COMPLETED`; không công nhận bài dở, không tạo thêm check-in. Gọi lại cùng thao tác đóng trả kết quả đã lưu; không đổi giờ kết thúc. Hoàn thành buổi đã hủy hoặc hủy buổi đã hoàn thành trả `409`.

Tổng phút và calo cộng từ các dòng detail `COMPLETED`, kể cả khi buổi đang mở hoặc sau đó bị hủy. Không cộng thời gian chờ mở trang, tạm dừng, đóng băng hoặc bài bị hủy. Tổng được tính từ kết quả detail đã lưu, không tính lại bằng MET/cân nặng mới. Response phân trang gồm `content`, `page`, `size`, `totalElements`, `totalPages`; sắp theo buổi mới nhất.

Tối ưu ngày 01–02/11/2026: đường đọc/quyền workout chỉ lấy ID và cờ enabled/locked, tránh hydrate roles/profile không dùng. Đường ghi tạo/thêm/start/heartbeat/recover/rest vẫn khóa User trước Workout bằng `SELECT ... FOR UPDATE`; không bỏ validation hoặc kiểm tra chủ sở hữu. Thư viện fetch category cùng exercise để tránh N+1. Các route cũ và JSON của chúng giữ nguyên. `/current/state` giúp frontend thay chuỗi current → details → timer bằng một request đọc, không cấp quyền điều khiển.

## Chọn bài và xem trạng thái

| Method | URL | Body / kết quả |
|---|---|---|
| POST | `/workouts/{workoutId}/details` | `{"exerciseId":2}`; `201`, không tự bắt đầu |
| GET | `/workouts/{workoutId}/details` | Tất cả detail theo ID tăng dần, kể cả bài đã xóa khỏi thư viện |
| GET | `/workouts/{workoutId}/details/remaining` | Bài chưa `COMPLETED`/`CANCELLED`, phục vụ hiển thị/nhắc bài còn thiếu |
| GET | `/workouts/{workoutId}/details/{detailId}/timer` | Trạng thái/counter/mốc nghỉ; `timerToken=null`, không tự khôi phục |
| PATCH | `/workouts/{workoutId}/details/{detailId}/rest-duration` | `{"restSeconds":120}` duyệt cấu hình nghỉ 1–300 giây |

Detail response có thêm `approvedRestSecondsSnapshot`, `minDurationMinutesSnapshot`, `activeDurationMillis`, `restDurationMillis`, `cancelledAt`. `sets/reps/weightKg` là dữ liệu tập tự khai, `weightKg` là tải tập chứ không phải cân nặng cơ thể. Cân nặng tính calo lấy từ hồ sơ người dùng ở lúc hoàn thành.

Mỗi bài chỉ có một detail trong buổi. Không thêm/bắt đầu bài ngừng hoạt động. Các bài sẵn có vẫn được tập khi nhóm của chúng ngừng hoạt động; nhóm ngừng chỉ chặn gán mới. Không bắt đầu bài B khi A đang tập/nghỉ/tạm dừng/đóng băng. Cần kết thúc A hoặc đóng/hủy cả buổi. Không có API hủy riêng bài đang tập để vượt quy tắc này.

Duyệt nghỉ chỉ là hành động người dùng xác nhận số giây, chưa tích hợp đề xuất AI. Chưa cấu hình thì không được bắt đầu nghỉ; không tự gán 300 giây. Đổi số giây khi đang nghỉ chỉ áp dụng lần nghỉ kế tiếp.

## Timer

Các route dưới cùng có tiền tố `/workouts/{workoutId}/details/{detailId}`:

| Method | Hậu tố | Body / kết quả |
|---|---|---|
| PATCH | `/start` | Không body; bắt đầu bài `NOT_STARTED`, cấp token |
| PATCH | `/heartbeat` | `{"timerToken":"UUID"}`; gửi mỗi 10 giây |
| PATCH | `/rest` | `{"timerToken":"UUID"}`; lấy thời lượng nghỉ đã duyệt từ snapshot |
| PATCH | `/pause` | `{"timerToken":"UUID"}`; tạm dừng bài `IN_PROGRESS` |
| PATCH | `/resume` | `{"timerToken":"UUID"}`; tiếp tục từ `PAUSED` hoặc `RESTING` đã hết nghỉ; cấp token mới |
| PATCH | `/recover` | Không body; cấp token mới ngay, vô hiệu token cũ ở tab/thiết bị khác |
| PATCH | `/complete` | `{"timerToken":"UUID","sets":3,"reps":10,"weightKg":20}`; các thống kê tập tùy chọn |

- Giữ token ở tab điều khiển, không in token vào log. Khi response trả token mới phải thay token cũ trước request tiếp theo.
- Đang tập: heartbeat quá 30 giây chuyển `FROZEN`, không tự cộng thời gian mất kết nối. Recover bắt đầu tính tiếp từ mốc khôi phục, không cộng bù. Theo yêu cầu ngày 02/11/2026, frontend tự gọi PATCH recover một lần khi tải/quay lại trang có bài đang dở; GET timer vẫn chỉ đọc, không cấp token.
- Scheduler quét mặc định mỗi 10 giây, tối đa 100 buổi/lượt, khóa User → Workout trước đổi trạng thái. Khi đông buổi, trạng thái lưu có thể cập nhật chậm hơn 30 giây; các API heartbeat/complete/rest/pause vẫn kiểm tra timeout ngay khi xử lý.
- Đang nghỉ: thời gian tiếp tục trôi dù offline, chỉ được cộng tới `restEndsAt`. Hết nghỉ vẫn giữ `RESTING` cho đến khi bấm `/resume`, không tự tính tập. Bấm sớm trả `409`.
- Tạm dừng khác nghỉ giữa hiệp: `/pause` chỉ dành cho `IN_PROGRESS`, không cộng thời gian pause. Khi `PAUSED`, dừng gửi heartbeat; dùng `/resume` khi quay lại. Nếu mất token, `/recover` giữ `PAUSED`, sau đó `/resume` mới tính tập.
- `/recover` khi `RESTING` giữ đúng khoảng nghỉ còn lại/đã hết; không cần token cũ. GET timer chỉ xem, không giành quyền điều khiển của tab khác.
- Hoàn thành cần `activeDurationMillis + restDurationMillis` đạt snapshot ngưỡng tối thiểu. Sets/reps không thay thế điều kiện thời gian. Calo chỉ dùng active: `MET × 3.5 × cân_nặng_kg × activeMillis / 12_000_000`, làm tròn 1 số lẻ.
- Duration detail = tổng active + rest theo phút, làm tròn 1 số lẻ. Bài hợp lệ ghi check-in theo ngày hoàn thành ở `Asia/Ho_Chi_Minh`, không theo ngày bắt đầu buổi.
- Complete detail đã `COMPLETED` trả lại kết quả, không tính/check-in lần nữa, kể cả buổi sau đó đã đóng. Timestamps API thống nhất GMT+7 và độ chính xác microsecond; DB `timestamptz` vẫn có thể hiển thị UTC trong công cụ SQL.

## Đồng bộ database trước khi nghiệm thu

File: `scripts/sql/workout_tracking_sync.sql`. Người dùng đã chạy trên database phát triển; trợ lý xác minh READ ONLY đủ CHECK/index/FK ở mốc 248. Không cần chạy lại chỉ vì SELECT cuối còn dòng legacy thiếu snapshot. Khi thiết lập database khác, dùng quy trình dưới đây. Không dùng Flyway.

1. Chạy riêng hai SELECT đầu để kiểm tra trùng buổi đang mở/trùng bài. Nếu có dòng, cần xử lý dữ liệu thực tế trước; script không tự xóa hoặc gộp lịch sử.
2. Chạy transaction DDL còn lại trên database phát triển: thêm các cột thiếu, mở rộng CHECK status cho `CANCELLED`, tạo unique index và index tra cứu. Nếu transaction báo lỗi thì rollback, không tiếp tục thao tác ghi trên transaction lỗi.
3. Các snapshot cũ thiếu vẫn giữ null. Dòng cuối script chỉ liệt kê bài dở thiếu snapshot. Không tự ghi dữ liệu giả để công nhận bài cũ; có thể hủy buổi cũ và tạo buổi mới.
4. Giữ FK `workout_details.exercise_id` nullable và `ON DELETE SET NULL` đã xác minh ở mốc trước. Kết quả cũ dùng snapshot; xóa bài khỏi thư viện vẫn bị chặn nếu còn trong buổi `IN_PROGRESS`.

`fitness.workout.heartbeat-timeout-seconds` giữ 30; `fitness.workout.heartbeat-interval-seconds` giữ 10. Có thể cấu hình `fitness.workout.timeout-scan-interval-millis` (mặc định 10000). Cần kiểm thử scheduler/khóa/rollback trên PostgreSQL thật; biên dịch không chứng minh các truy vấn đã chạy đúng.

## Trạng thái kiểm thử

Bổ sung 02/11/2026: cấp độ tùy ý theo từng bài, snapshot, timer tự chuyển set/nghỉ, mốc kết thúc sớm 2/3 và media upload. Giữ các route cũ, thêm `levelId` tùy chọn cho chọn bài và `automation` trong detail response; bài đã có cấp độ bắt buộc chọn cấp độ hợp lệ. Chi tiết route/DTO và cách áp dụng schema tại [WORKOUT_AUTOMATION.md](WORKOUT_AUTOMATION.md). SQL mới `scripts/sql/workout_automation_sync.sql` chưa được trợ lý chạy trên Neon; không coi kiểm thử mock/metadata là nghiệm thu database thật. Bộ Workout/Exercise hiện đạt 137 test Java, Node regression đạt.

- Script Console kiểm thử module: `scripts/workout-module-console.js`, hướng dẫn tại [WORKOUT_CONSOLE_TEST.md](WORKOUT_CONSOLE_TEST.md). Chạy bằng tài khoản thử nghiệm; Admin tạo fixture riêng, User dùng bài có sẵn, các ca cần tài khoản khác/SQL/thời điểm riêng được đánh dấu SKIP.
- Chạy lại `scripts/Test-WorkoutRest.ps1` cho 79 ca ở các mốc trước; chưa chạy lại sau phần lifecycle mới. Bộ này chưa bao phủ toàn bộ endpoint mới.
- Các lượt User/Admin đã đạt timer/rest/pause/resume/recover, nghỉ offline, token cũ, timeout, hoàn thành/gọi lại, đóng/hủy, tổng hợp/lịch sử; lượt khác chủ đạt 16 ca. SQL mẫu 6 bài COMPLETED khớp công thức/ngưỡng, CHECK/index/FK đã xác minh. Không cộng các lượt thành số ca duy nhất vì có trùng nhau.
- Còn tài khoản khóa/chưa xác minh, mốc 00:00/ngày nghỉ/chuỗi dài và fault injection rollback/isolation có kiểm soát. Người dùng chấp nhận mức kiểm tra hiện tại để chuyển sang frontend; không gắn PASS cho các ca chưa chạy.
- Giao diện mới có test render/security và JS/Chrome với dữ liệu giả; xem tài liệu frontend. Chưa tự coi các kiểm tra này là nghiệm thu frontend trên database thật.

Media qua URL: biểu mẫu Thêm/Sửa bài tập bổ sung mediaUrl cho ảnh/GIF/video hiển thị trực tiếp. Link GitHub /blob/ được chuẩn hóa sang raw khi lưu và hiển thị; aiVideoUrl vẫn phục vụ link trang video mở riêng. Giữ dữ liệu upload/URL cũ khi request bỏ qua mediaUrl, gỡ liên kết bằng chuỗi rỗng. Chi tiết và giới hạn tại WORKOUT_AUTOMATION.md. Không cần cột hoặc route mới.
