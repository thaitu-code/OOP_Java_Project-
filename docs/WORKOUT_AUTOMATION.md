# Tự động hóa buổi tập và cấp độ bài tập

Cập nhật 02/11/2026. Đã triển khai trong source Workout/Exercise; chưa áp dụng SQL hoặc nghiệm thu với Neon mới. Không sửa nghiệp vụ tài khoản, check-in, streak, huy hiệu hoặc email.

## Quy tắc đã chốt

- Người dùng tự chọn bài từ thư viện, chọn cấp độ riêng cho từng bài. Không thêm buổi mẫu.
- Admin tạo tên cấp độ tùy ý. Mỗi cấp độ có số set, reps/set (được trống), giây/set, giây nghỉ và thời gian hoàn thành dự kiến.
- Thời gian dự kiến là thông số Admin nhập riêng, không bắt buộc bằng tổng chu kỳ set/nghỉ. Đây là mốc đổi nút sang **Kết thúc & bài tiếp theo**.
- Mốc **Kết thúc bài tập sớm** bằng 2/3 thời gian dự kiến. Cả hai mốc tính tập + nghỉ, loại pause và phần tập bị đóng băng. Sets/reps không phải điều kiện xác minh động tác.
- Nút kết thúc xuất hiện theo thời gian backend đã ghi nhận, không dùng đồng hồ ước tính của giao diện để cấp check-in.
- Cả hai nút lưu bài hiện tại rồi chọn bài chưa bắt đầu tiếp theo; người dùng phải bấm **Bắt đầu bài**. API complete lỗi thì không chuyển bài.
- Hết mốc đầy đủ không tự complete. Sau set cuối không tạo thêm set/nghỉ giả, active tiếp tục đếm trên set cuối tới khi người dùng kết thúc.
- Không còn bài kế tiếp: giữ kết quả bài cuối, người dùng bấm **Hoàn thành buổi** theo nghiệp vụ hiện tại.
- Calo chỉ tính active time; số reps thực tế không được hệ thống xác minh bằng cảm biến.

## Cách dùng Admin

1. Mở `/admin/workout-library`, tạo nhóm và bài như hiện tại. Ngưỡng thủ công cũ chỉ phục vụ bài chưa cấu hình cấp độ.
2. Ở dòng bài chọn **Cấp độ → + Cấp độ**, nhập tên, set, reps, giây/set, giây nghỉ và giây dự kiến.
3. Có thể sửa, ngừng hoặc kích hoạt lại cấp độ. Các lần tập đã chọn giữ snapshot.
4. Chọn **GIF / Video** để tải GIF, MP4 hoặc WebM. Link video ngoài `aiVideoUrl` cũ vẫn được giữ.

Giới hạn dữ liệu hiện tại: 1–100 set, reps dương hoặc trống, 1–86400 giây/set, 1–300 giây nghỉ và 1–86400 giây dự kiến. Đây là giới hạn triển khai, không phải khuyến nghị sức khỏe. Không tự sinh bộ thông số cấp độ mặc định.

## Cách dùng User

1. Chọn bài: nếu có cấp độ thì chọn cấp độ và xem thông số trước khi thêm.
2. Bấm **Bắt đầu bài**: tự tập set → nghỉ → set mới khi kết nối hợp lệ.
3. Theo dõi set, tập/nghỉ đã ghi, mốc 2/3 và mốc đầy đủ; có thể tạm dừng khi đang tập.
4. Chọn kết thúc sớm hoặc kết thúc đầy đủ khi nút hiện; nếu chưa bấm thì bài vẫn tiếp tục.
5. Đợi lưu thành công, bài kế tiếp được chọn và chờ bắt đầu.

Bài chưa có cấp độ vẫn dùng timer thủ công cũ để tương thích. Bài đã có cấp độ yêu cầu chọn một cấp độ đang hoạt động, không bỏ qua bằng request cũ chỉ có exerciseId.

## Timer và offline

- Heartbeat 10 giây, timeout 30 giây, giữ token theo tab. Frontend gửi thêm heartbeat tại ranh giới pha.
- Backend tách một khoảng heartbeat thành active/rest chính xác qua các ranh giới. Chuyển pha tự động không xoay token; recover vẫn xoay token.
- Ngưỡng chính xác: `ceil(estimatedSeconds * 1000 * 2 / 3)` millisecond. Ví dụ dự kiến 1 giây thì ngưỡng là 667ms, không phải 666ms.
- Quá timeout đang tập: FROZEN, không cộng bù active mất kết nối.
- Quá timeout đang nghỉ: nghỉ tối đa tới deadline, FROZEN; không tính active sau deadline. Recover trước deadline giữ nghỉ còn lại; recover sau deadline bắt đầu set mới từ lúc quay lại.
- Bộ quét timeout nhận thêm bài tự động RESTING; bài thủ công RESTING giữ quy tắc cũ.
- Tải lại/mở lại trang có bài đang dở tự gọi recover một lần, không cần nút xác nhận. Quay lại bằng Back/forward cache cũng tự khôi phục; chỉ đổi tab hoặc polling không giành quyền. PAUSED vẫn giữ pause, NOT_STARTED/COMPLETED/CANCELLED không được tự start/recover. Khi API lỗi thì giữ nút thủ công để thử lại, không lặp PATCH liên tục. Token tab cũ bị thu hồi và tab cũ không tự gọi recover để lấy lại.
- Pause sát ranh giới có thể nhận trạng thái RESTING nếu đã hết set; không biến khoảng nghỉ thành active khi resume.

## Dữ liệu và API

- Thêm `exercise_levels` liên kết bài tập. Snapshot cấp độ lưu tên/ID, set/reps, giây/set, giây dự kiến, nghỉ và media. Tiến độ lưu set hiện tại, elapsed trong set, pha nghỉ.
- `WorkoutDetailRequest` thêm `levelId` tùy chọn để tương thích; bài đã có cấp độ yêu cầu level thuộc đúng bài và đang hoạt động.
- DetailResponse thêm `automation`: ngưỡng millisecond, tiến độ pha, `canFinishEarly`, `fullTargetReached`. Với cấp độ, đây là dữ liệu mốc hoàn thành. `minDurationMinutesSnapshot` cũ vẫn là ngưỡng thủ công và không dùng để tính mốc cấp độ.
- ExerciseResponse thêm `levels` và `mediaUrl`; fetch cùng nhóm/cấp độ để tránh N+1 theo từng bài.
- Giữ API cũ và các lời gọi check-in/huy hiệu. Rest/rest-duration thủ công không được ghi đè cấu hình tự động đã chọn.

| Method | URL | Mục đích |
|---|---|---|
| POST | `/admin/exercises/{exerciseId}/levels` | Tạo cấp độ |
| PUT | `/admin/exercises/{exerciseId}/levels/{levelId}` | Sửa cấp độ |
| PATCH | `/admin/exercises/{exerciseId}/levels/{levelId}/activate` | Kích hoạt |
| PATCH | `/admin/exercises/{exerciseId}/levels/{levelId}/deactivate` | Ngừng |
| POST multipart | `/admin/exercises/{exerciseId}/media` | Upload trường `file` |
| GET | `/exercise-media/{uuid}.{gif,mp4,webm}` | Người đăng nhập đọc media, hỗ trợ Range |

Mọi route ghi mới chỉ dành cho Admin và yêu cầu CSRF.

## Lưu GIF/video

- Tệp lưu tại `uploads/exercises` trên máy chủ ứng dụng, Neon chỉ lưu đường dẫn. Thư mục tệp đã loại khỏi Git.
- Cấu hình `fitness.exercise.media-directory` tới thư mục/volume bền vững khi deploy. Sao lưu cả DB và thư mục tệp.
- `fitness.exercise.media-max-bytes` mặc định 52428800 (50 MiB). Multipart dùng cùng giới hạn, có phần dư cho form.
- Kiểm tra chữ ký định dạng và giới hạn đọc stream, tên UUID; không dùng tên tệp người dùng làm đường dẫn. Không tuyên bố đây là bộ giải mã/kiểm chứng toàn bộ video.
- Ghi tệp xong mới khóa DB để gắn URL. Nếu gắn thất bại dọn tệp mới; giữ tệp cũ vì snapshot có thể còn tham chiếu.
- MIME đúng loại, nosniff, cache private; không cho đọc đường dẫn tùy ý. Admin ghi, người đăng nhập xem.

## Áp dụng và kiểm thử

1. Sao lưu/chuẩn bị DB phát triển, chạy `scripts/sql/workout_automation_sync.sql` trên đúng Neon. Trợ lý chưa chạy SQL này.
2. Stop/Run đúng checkout và Ctrl+F5. Không dùng Flyway. Buổi cũ không có snapshot cấp độ tiếp tục ở chế độ cũ.
3. Tạo cấp độ cho hai bài, kiểm tra set/nghỉ tự động, hai mốc nút kết thúc, bài kế tiếp chờ bắt đầu, upload và xem media.

Test Java Workout/Exercise dùng mock/temp file; có test dựng metadata Hibernate/parse JPQL không kết nối DB. Node dùng fake clock và HTTP RAM. Các kiểm tra này không thay thế nghiệm thu trên Neon.

Chrome mô phỏng đã kiểm tra Admin tạo cấp độ, chọn cấp độ lúc thêm bài, timer sang set cuối và tiếp tục vượt mục tiêu, complete chọn bài tiếp ở NOT_STARTED. Hai ảnh `docs/screenshots/workout-levels-preview.png` và `docs/screenshots/workout-automation-preview.png` là dữ liệu giả.

## Media qua link trực tiếp — cập nhật 02/11/2026

Admin → Thêm/Sửa bài tập có trường **Link ảnh/GIF/video hiển thị trực tiếp**, dùng lại media_url nên không cần SQL mới nếu đã áp dụng schema automation. Hỗ trợ URL HTTP/HTTPS có đường dẫn tệp GIF/PNG/JPG/JPEG/WebP/MP4/WebM; query/fragment được giữ. Ảnh/GIF dùng img, video dùng video trong hướng dẫn và timer. Link YouTube/trang xem video vẫn dùng aiVideoUrl để mở riêng.

ExerciseRequest thêm mediaUrl tùy chọn: bỏ qua hoặc null giữ media cũ khi sửa; chuỗi rỗng gỡ liên kết; giá trị hợp lệ đổi sang URL mới. File upload cũ không bị xóa và các kế hoạch đã chọn giữ URL snapshot. Timer kế hoạch tự động không lấy media hiện tại thay cho snapshot null.

Backend chỉ kiểm tra/chuyển đổi URL, không tải hoặc HEAD URL từ server ứng dụng. Không chấp nhận javascript/data/file, userinfo hoặc đường dẫn nội bộ tùy ý. Link upload nội bộ UUID vẫn hợp lệ. Dung lượng trường tối đa 500 ký tự kể cả sau chuẩn hóa.

URL github.com/{owner}/{repo}/blob/{ref}/{path} (và /raw/) được đổi sang raw.githubusercontent.com/{owner}/{repo}/{ref}/{path} khi lưu và khi hiển thị, để hỗ trợ cả snapshot/URL cũ mà không viết lại lịch sử. Lý do: /blob/ trả trang HTML, không phải nội dung ảnh dù tên URL kết thúc bằng .gif. Kho private/link cần đăng nhập hoặc URL bị chặn nhúng vẫn có thể không hiển thị; link phải truy cập được từ trình duyệt người dùng.

Chẩn đoán thực tế trên tab của người dùng ngày 02/11/2026: link GitHub Push-up.gif ở commit a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c trả 200 text/html; cùng tệp trên raw.githubusercontent.com trả 200 image/gif qua HEAD. Chỉ đọc trang và HTTP headers, không sửa DB hoặc điều khiển/reload buổi đang chạy.
