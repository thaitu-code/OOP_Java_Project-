# Giao diện Theo dõi tập luyện

## Mở giao diện thật

Khởi động lại ứng dụng từ đúng `D:\OOP_Java_Project-` sau khi thêm Controller, rồi đăng nhập và mở:

- User/Admin: `http://localhost:8081/workout`.
- Thư viện: `http://localhost:8081/workout#library`.
- Lịch sử: `http://localhost:8081/workout#history`.
- Admin quản lý dữ liệu: `http://localhost:8081/admin/workout-library`.

Trang chủ có ba thẻ dẫn tới thư viện, buổi tập và lịch sử. Trang quản trị tài khoản có liên kết “Quản lý bài tập”. Không mở HTML bằng file:// để tập thật: các API cần session đăng nhập và CSRF do Thymeleaf render.

## Cách sử dụng

1. Cập nhật cân nặng trong Hồ sơ trước khi hoàn thành bài. Đây là cân nặng cơ thể dùng tính calo, khác với tải tập nhập ở bước hoàn thành.
2. Bấm “Bắt đầu buổi tập”, hoặc “Tạo buổi & thêm” từ thư viện. Nếu đã có buổi mở, giao diện hiển thị buổi đó và không tự tạo buổi khác.
3. Tìm theo tên/nhóm, xem mô tả, MET, thời gian tối thiểu và link video. Bài đã có trong buổi không được thêm lần nữa. Bài đang hoạt động vẫn được tập nếu nhóm đã ngừng.
4. Trong “Buổi tập của tôi”, chọn bài rồi bấm “Bắt đầu bài”. Không tự bắt đầu khi chọn hoặc thêm bài. Khi đang điều khiển bài dở, hoàn thành bài đó trước khi chuyển sang bài khác.
5. Tùy chọn nhập 1–300 giây và bấm “Duyệt nghỉ”. “Nghỉ giữa hiệp” lấy giá trị đã duyệt; đổi cấu hình không đổi deadline của lần nghỉ đang diễn ra. Hết giờ nghỉ phải tự bấm “Tiếp tục tập”. “Tạm dừng” không tính thời gian nghỉ giữa hiệp.
6. Bấm “Hoàn thành bài tập”; có thể nhập số hiệp, reps mỗi hiệp và tải tập. Không gửi thời gian, calo hoặc cân nặng cơ thể từ client. Backend kiểm tra ngưỡng tập + nghỉ, tính calo chỉ từ thời gian tập và ghi check-in. Giao diện không tự công nhận hoàn thành.
7. Khi có ít nhất một bài hoàn thành, có thể hoàn thành buổi. Hộp xác nhận giải thích các bài dở sẽ bị hủy; kết quả bài đã xong giữ nguyên. Hủy buổi cũng giữ những kết quả/check-in đã có.
8. Mở Lịch sử, lọc trạng thái, chuyển trang và bấm Chi tiết để xem snapshot từng bài, kể cả bài đã xóa khỏi thư viện. Mốc giờ hiển thị theo `Asia/Ho_Chi_Minh`.

## Bộ đếm, kết nối và nhiều tab

- Mốc 251 (01–02/11/2026): trạng thái buổi đọc từ `GET /workouts/current/state`, gồm tổng kết, detail và timer không token. Core thư viện/buổi tải độc lập với summary. Chuyển tab và chọn bài chưa bắt đầu phản hồi tại chỗ; đọc lịch sử chạy riêng khỏi hàng đợi thao tác ghi, có cache RAM 15 giây và gộp request cùng trang đang tải. Ghi kết quả/tạo/đóng buổi làm hết hiệu lực cache; “Làm mới” vẫn đọc server.
- Khi thêm bài, một dòng chờ lưu xuất hiện ngay, chưa có detail ID và chưa được tính hoàn thành/check-in. Khi server xác nhận mới thay bằng kết quả thật. Thông báo/loading nằm cố định để tránh đẩy nội dung và đổi vị trí nút; thông báo thường tự ẩn sau 8 giây và có nút đóng. Các lỗi cần xử lý không tự ẩn.
- Heartbeat chỉ cập nhật nội dung cần thay đổi, không dựng lại cả danh sách và thẻ thư viện; giữ focus và dữ liệu đang nhập. Formatter được tái sử dụng, phần hiển thị không ghi lại cùng nội dung mỗi 250ms. Chuyển tab trình duyệt trong thời gian ngắn không kích hoạt tải lại toàn bộ dữ liệu.
- Sau tối ưu mốc 250, thêm bài vào buổi đang có chỉ dùng một POST. Giao diện cập nhật từ DTO backend trả về, không chờ chuỗi GET current → details → timer. Tạo buổi rồi thêm vẫn cần hai POST theo hai hành động nghiệp vụ. Lưu/trạng thái nhóm/bài và đóng/hủy buổi cũng dùng phản hồi đã xác nhận để cập nhật phần liên quan; không tải lại toàn bộ sau mỗi thao tác. “Làm mới” vẫn đọc trạng thái hiện tại từ server.
- Nút có trạng thái đang xử lý ngay khi bấm; thao tác mạng được xếp hàng nếu heartbeat đang chạy, không bị bỏ qua. Điều hướng tab/thư viện/hướng dẫn tại chỗ phản hồi ngay. Chống bấm trùng giữ theo thao tác/ID, backend vẫn kiểm tra trùng và quyền như cũ. Không poll timer cho bài chưa bắt đầu hoặc đã đóng; bài dở vẫn được theo dõi.
- Bộ đếm lớn khi đang tập chỉ là hiển thị tạm tính. Hai chỉ số “Tập đã ghi” / “Nghỉ đã ghi” và thanh ngưỡng lấy từ phản hồi server. Tổng phút/calo buổi chỉ cộng bài COMPLETED theo API.
- Heartbeat đọc cấu hình từ server, mặc định 10 giây. Các thao tác chỉ đọc không tự cấp hoặc giành token.
- Tải lại/mở trang có bài đang dở: frontend tự gọi recover một lần, nhận token mới và thu hồi token ở tab/thiết bị khác, không cần bấm nút/xác nhận. Token vẫn chỉ trong bộ nhớ. Nếu recover lỗi thì có nút thủ công để thử lại; lỗi token hoặc polling không tự gọi recover để giành quyền lại.
- Chuyển tab để xem video không tự kết thúc bài và không tự recover. Heartbeat tiếp tục nếu trình duyệt cho phép; nếu trình duyệt treo/throttle hoặc mất mạng quá 30 giây thì backend quyết định đóng băng. Tải lại trang đọc trạng thái rồi tự recover bài đang dở theo yêu cầu mới ngày 02/11/2026.
- Khi rời trang qua `pagehide`, ngừng điều khiển; quay lại qua back/forward cache tự khôi phục một lần cho lần vào mới. Sự kiện pageshow ban đầu/lặp không tạo nhiều recover. Thời gian offline không cộng bù tập, nghỉ chỉ chạy tới deadline; PAUSED vẫn PAUSED sau khi nhận lại quyền.
- Request có giới hạn chờ 15 giây. Khi phản hồi ghi bị mất, giao diện chỉ đọc lại trạng thái; không tự lặp thao tác ghi. Lỗi nghiệp vụ hiển thị bằng văn bản, không đưa trace/HTML lỗi vào DOM.
- Token không ghi ra Console, localStorage hoặc sessionStorage. CSRF lấy từ meta Thymeleaf, dùng session cùng origin. Video chỉ mở http/https với `noopener noreferrer`; tên/mô tả từ API được escape.

## Quản trị

Tab quản lý chỉ được render cho Admin, route `/admin/workout-library` và API `/admin/**` vẫn được Spring Security bảo vệ. Việc ẩn tab không thay thế phân quyền backend.

- Nhóm: tạo, đổi tên, ngừng và kích hoạt lại. Không có xóa vật lý nhóm.
- Bài: tạo/cập nhật tên, nhóm, mô tả, MET, ngưỡng, hiệp/reps đề xuất và video; ngừng/kích hoạt lại; xóa hẳn sau xác nhận. Backend chặn xóa bài còn trong buổi mở.
- Admin vẫn tập trong buổi của chính mình; trang không có tính năng vượt quyền sở hữu để điều khiển buổi người khác.

## Tệp và giới hạn

- `WorkoutPageController`: route HTML, thông tin hồ sơ, vai trò và cấu hình timer; không đổi các route JSON `/workouts`.
- `templates/workout.html`, `static/css/workout.css`, `static/js/workout.js`: dùng tông xanh nhạt, header và CSS chung hiện có; bố cục hai cột desktop, một cột màn hình nhỏ.
- Chưa tích hợp AI đề xuất lịch tuần/thời lượng nghỉ, hoặc đo quãng đường chạy. Đây là giao diện cho API workout hiện có; không trình bày dữ liệu AI giả.

## Kiểm tra đã thực hiện ngày 01–02/11/2026

- 5 test render/security mới: chuyển đăng nhập, render User/CSRF/ẩn Admin, render Admin/nhắc cân nặng, User bị chặn route Admin, context path. Chạy cùng 4 test mapping/error hiện có: **9 test đạt**, không kết nối database thật.
- `node scripts/check-workout-ui.cjs`: chạy đúng JS frontend với DOM/API giả; kiểm tra lifecycle/CSRF/timer/media, tự recover đúng một lần khi vào trang/reload/Back, không giành lại token qua polling/visibility, giữ pause/rest, không start bài chưa bắt đầu, lỗi/mất CSRF có fallback, response cũ sau pagehide không áp dụng, thư viện chậm không chặn heartbeat/điều khiển, payload/đóng buổi/404/context path/escaping. Đây không phải kiểm thử PostgreSQL.
- Chrome với HTTP fixture riêng: tạo/thêm hai bài, start/pause/resume/rest, hoàn thành/check-in, kết thúc có bài dở, lịch sử chi tiết, Admin tạo nhóm/bài và tìm kiếm; xem bố cục desktop và viewport nhỏ. Không ghi dữ liệu vào app/database thật.
- Mốc 250: người dùng báo thao tác mất 2–5 hoặc hơn 5 giây. Quan sát thụ động Network của tab app thật thấy 11 phản hồi heartbeat 200 khoảng 1.714–1.919 giây đến response headers, không đọc/in token hay body. Điều này xác nhận có độ trễ ở mức HTTP; chưa tách thời gian DB/khóa/xử lý để quy nguyên nhân riêng cho database. Không khởi động lại/tải lại/điều khiển buổi người dùng đang tập.
- Bản tối ưu qua 5 test render/security bằng `surefire:test` đọc template trực tiếp từ src (không build/copy classpath làm restart app đang chạy), Node syntax và Console regression cũ. JS regression mới giữ request bằng Promise để mô phỏng mạng chậm: xác nhận thêm bài một request, thông báo hiện trước phản hồi nhưng không tự ghi thành công, bấm trùng không gửi hai POST, thao tác pause trong lúc heartbeat chậm được thực hiện đúng một lần, điều hướng vẫn phản hồi ngay; trạng thái nhóm/bài và xóa thư viện chỉ một API mỗi thao tác. Đây là kiểm tra giả lập, chưa phải số đo trước/sau của bản tối ưu trên app thật.
- Ảnh `docs/screenshots/workout-ui-preview.png` dùng dữ liệu giả. Cần người dùng đăng nhập app thật để nghiệm thu frontend nối backend/database; các lượt Console backend 49/80/16 trước đó vẫn là bằng chứng riêng.

### Mốc 251: kiểm tra hiệu năng và hồi quy

- **95/95 test Java đạt** trong output cô lập `target/workout-performance`: toàn bộ nhóm workout hiện có và 6 ca mới kiểm tra projection/lock, tạo/thêm, snapshot không phát token, quyền tài khoản và buổi rỗng. Không copy classpath đang chạy, không khởi động lại app thật; các test dùng mock repository, không ghi DB.
- Node regression chạy đúng frontend: request bundle, core hoạt động khi summary chậm, dòng pending trước response, chống click trùng, hàng đợi timer, giữ nội dung form, không dựng lại danh sách khi heartbeat không đổi trạng thái, cache/TTL/dedup lịch sử và không khóa ghi trong lúc lịch sử tải chậm; các ca bảo mật/lifecycle cũ vẫn đạt.
- Chrome ở cổng fixture riêng với độ trễ API giả lập 1800ms: loading phản hồi ngay, đổi tab được trong lúc POST đang chờ, dòng bài chờ lưu hiện đúng rồi chuyển sang dòng thật. Focus nút “Chọn” vẫn còn sau nhiều heartbeat (counter server từ 00:35 tới 02:11). Ảnh `docs/screenshots/workout-smooth-preview.png` là dữ liệu giả.
- `DiagnoseWorkoutDatabase --latency`: 8 lượt `SELECT 1` mất 248.5–290.3ms đi–về, trong khi EXPLAIN ANALYZE báo execution 0.019ms. Chi phí đường truyền DB đáng kể; không suy mọi độ trễ đều do tốc độ thực thi SQL.
- `scripts/ProfileWorkoutReads.java` được chạy bằng classpath cô lập với naming strategy giống Spring Boot, DDL=none, tắt logging để không in thông tin kết nối. Đường đọc chạy READ ONLY rồi rollback. Probe native FOR UPDATE dùng transaction thường với username ngẫu nhiên không tồn tại vì PostgreSQL cấm FOR UPDATE trong READ ONLY; trả rỗng và rollback, không khóa tài khoản thật.

| Phép đo ORM trên DB hiện tại | Trước | Sau |
|---|---|---|
| Tra cứu tài khoản phục vụ workout (lượt đã warm) | 2 SQL, 482.8ms | 1 SQL, 247.9ms |
| Thư viện + nhóm (lượt đã warm) | 3 SQL, 725.3ms | 1 SQL, 246.7ms |
| Mô hình ba lượt đọc màn hình / snapshot gộp | 12 SQL, 2904.0–3122.8ms | 3 SQL, 754.5–951.9ms |

Đây là đo đường đọc ORM, không phải benchmark HTTP end-to-end của giao diện production. Query plan, connection pooling, cold start và dữ liệu có thể ảnh hưởng kết quả. Bản mới chưa được nạp vào app đang chạy; cần Stop/Run đúng project và Ctrl+F5 để nghiệm thu luồng thật. Buổi dở vẫn dùng cơ chế khôi phục hiện có. Không đổi timezone, heartbeat 10 giây, timeout 30 giây hoặc quy tắc tính kết quả.

Chạy kiểm tra JS: `node scripts/check-workout-ui.cjs`. Chạy kiểm tra trang: `.\mvnw.cmd -Dtest=WorkoutPageControllerTest test`.

Mốc 252 (02/11/2026), phạm vi chỉ Workout/Exercise: sau khi API complete xác nhận thành công, UI cập nhật kết quả và tải lại thống kê check-in độc lập để giải phóng hàng đợi thao tác. Không thay nghiệp vụ check-in/streak/huy hiệu/email hoặc contract API. Node regression giữ summary chưa trả về và xác nhận vẫn kết thúc được buổi; toàn bộ 99 test Java cô lập đạt, gồm bảo toàn kết quả/quyền/idempotency khi hủy buổi dùng projection. Đọc chi tiết Exercise fetch nhóm cùng SQL: phép đo ORM thật 2 SQL → 1 SQL, lượt warm 516.3 → 252.6ms. Lượt fetch đầu 2071.0ms có overhead khởi tạo repository; không coi là cam kết độ trễ HTTP. Cần Stop/Run + Ctrl+F5 để áp dụng vào app thật.

Nếu cần xem trước độc lập, chạy test trang để tạo HTML trong `target/workout-preview`, sau đó `node scripts/preview-workout-ui.cjs` và mở `http://127.0.0.1:8771/workout`. Dữ liệu fixture nằm trong RAM, mất khi dừng process; không proxy sang ứng dụng cổng 8081. Dùng Ctrl+C để dừng. Đây là tiện ích kiểm thử, không phải chế độ vận hành thật của website.

## Cấp độ và timer tự động (02/11/2026)

Xem [WORKOUT_AUTOMATION.md](WORKOUT_AUTOMATION.md) cho quy tắc, API và áp dụng SQL. User chọn cấp độ riêng cho từng bài; Admin tạo tên tùy ý và bộ set/reps/giây tập/nghỉ/thời gian dự kiến. Ngưỡng kết thúc sớm là 2/3 thời gian dự kiến (tập + nghỉ), tới mốc đầy đủ đổi nhãn nút. Backend xác nhận mốc; chưa bấm thì không tự complete, sau complete mới chọn bài kế tiếp ở NOT_STARTED.

Frontend xác nhận thêm bằng heartbeat ở ranh giới set/nghỉ và mốc 2/3/đầy đủ để không phải chờ tối đa 10 giây mới đổi nút. Kế hoạch không hiện điều khiển nghỉ thủ công. Bài/phiên cũ chưa có cấp độ giữ chế độ cũ. GIF/video đã tải được hiển thị trong hướng dẫn/timer bằng URL media nội bộ, không nhúng HTML tùy ý.

Bộ kiểm thử Java Workout/Exercise đạt 137/137; Node regression đạt cả chọn cấp độ, ranh giới, mốc kết thúc và chuyển bài không tự start. Chrome fixture tạo cấp độ QA/chọn/tập/complete và chờ bài kế tiếp đạt, xem ảnh trong tài liệu automation. SQL mới chưa chạy trên Neon và chưa nghiệm thu HTTP/transaction thật.

## Link ảnh/GIF/video trực tiếp

Admin → Thêm/Sửa bài tập → Link ảnh/GIF/video hiển thị trực tiếp. Dùng link tệp HTTP/HTTPS có đuôi GIF/PNG/JPG/JPEG/WebP/MP4/WebM (được có query/fragment); ảnh/GIF hiển thị trong hướng dẫn và timer, video có controls. Giữ URL upload nội bộ và tùy chọn mở link trang video ở trường aiVideoUrl. Không cần schema hoặc route mới.

Link GitHub /blob/ là HTML, được tự đổi sang raw.githubusercontent.com ở backend khi lưu và frontend khi hiển thị, kể cả URL đã snapshot trước đó mà không ghi lại lịch sử. Đã xác nhận HEAD URL Push-up.gif thật: trang GitHub 200 text/html, raw 200 image/gif. Không cần thay đổi CORS để xử lý nhầm URL này.

Nếu tệp không truy cập được, khung media hiện thông báo và nút Mở liên kết thay vì để ảnh vỡ. URL vẫn cần công khai/không chặn nhúng; URL có đuôi tệp chưa chứng minh nguồn thật sự trả nội dung ảnh. Server ứng dụng không tự tải hoặc kiểm tra HTTP từ các URL Admin nhập.

## Tự khôi phục khi vào trang — mốc 255 (02/11/2026)

Đây là thay đổi theo yêu cầu mới của người dùng, thay quy tắc trước đó cần bấm recover. Sau khi đọc snapshot có bài IN_PROGRESS/RESTING/PAUSED/FROZEN, frontend tự PATCH recover một lần cho lần vào trang. Không tự start NOT_STARTED hoặc recover COMPLETED/CANCELLED. Back/forward cache nhận một lần vào mới; pageshow lặp không gọi thừa.

Nút thủ công chỉ là đường thử lại khi lỗi hoặc tab đã bị thu hồi quyền. Polling/visibility không tự giành lại token. Request đang dở của lần vào cũ không áp dụng token sang lần vào mới; mọi ghi vẫn qua hàng đợi và CSRF. Pause vẫn giữ pause, rest giữ quy tắc backend, không cộng bù active offline.

Buổi được điều khiển/heartbeat ngay sau đọc và khôi phục xong, không đợi thư viện tải xong. Reload /admin/workout-library#session giữ tab session thay vì ép sang manage; URL Admin không có hash vẫn vào manage.

Kiểm tra mới: Node exact-source VM với backend/clock dùng chung giữa các tab, gồm reload/Back, một lần mỗi entry, token cũ không giành lại, rest/pause, lỗi CSRF/no retry loop, không start bài chưa tập, response chậm khi rời/quay lại, thư viện chậm vẫn heartbeat/pause. 5 test MockMvc render/security đạt trong output cô lập. Chrome fixture FROZEN được tự khôi phục lúc mở và reload, ảnh docs/screenshots/workout-auto-recovery-preview.png là dữ liệu giả. Không tác động phiên tập thật hoặc DB. Backend không đổi; bộ 159 Java đầy đủ là bằng chứng của mốc 254, không phải chạy lại toàn bộ trong mốc này.
