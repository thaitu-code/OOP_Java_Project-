# Kiểm thử module bằng Console trình duyệt

Script: `scripts/workout-module-console.js`. Sao chép toàn bộ nội dung file vào DevTools → Console của trang ứng dụng. Dán script chỉ nạp các hàm, chưa tự gọi API; sau đó gọi lệnh run bên dưới.

## Chuẩn bị

1. Dùng database phát triển đã đồng bộ `scripts/sql/workout_tracking_sync.sql`; script JS không tự sửa schema hoặc chạy SQL.
2. Chạy đúng branch/bản code mới và restart ứng dụng. Script kiểm tra contract API trước khi tạo fixture; lỗi 404/500 lúc đó cần kiểm tra server/log/schema.
3. Đăng nhập bằng **tài khoản thử nghiệm** đã xác minh email, không bị khóa. Lưu đầy đủ hồ sơ tại `/profile`, gồm cân nặng dương. Script chỉ đọc hồ sơ, không tự sửa cân nặng.
4. Không có buổi đang mở sẵn. Script sẽ dừng khi gặp buổi đó, không nhận buổi thật làm fixture hoặc tự hủy. Nếu là fixture của lần chạy trước, xem context rồi dùng cleanup.
5. Giữ tab kiểm thử ở phía trước, không reload/đăng xuất giữa lượt. Các ca nghỉ/timeout chờ thời gian thật; toàn lượt thường vài phút, lâu hơn nếu chạy User với các bài có ngưỡng lớn.

**Dữ liệu thật sẽ thay đổi:** run tạo các buổi và hoàn thành một số bài, vì vậy có lịch sử tập/check-in/streak và có thể có huy hiệu. Không có thao tác hoàn tác những bản ghi này. Dùng tài khoản riêng để không ảnh hưởng dữ liệu sử dụng hằng ngày.

## Admin: đủ quyền kiểm tra toàn bộ nhóm chức năng

Sau khi dán file vào Console:

```javascript
await FitnessModuleTest.run({ mode: "admin" });
```

- Tạo hai nhóm và năm bài riêng có tiền tố `TEST_WT_...`; không sửa bài/nhóm có sẵn.
- Kiểm tra tạo/xem/sửa/ngừng/kích hoạt, validation, ID không tồn tại, nhóm ngừng không nhận gán mới nhưng bài có sẵn vẫn tập được, hard-delete và snapshot lịch sử.
- Kiểm tra tạo buổi đồng thời, thêm trùng đồng thời, chọn/start, ngưỡng tối thiểu, CSRF, token, pause/resume/recover, nghỉ offline/deadline, đổi cấu hình nghỉ và scheduler timeout.
- Kiểm tra hoàn thành bài, công thức duration/calo dùng active, complete gọi lại và hai complete đồng thời, check-in cùng ngày, tổng buổi, bài còn thiếu.
- Kiểm tra hoàn thành buổi có bài dở, hủy buổi có/không có kết quả, gọi lại, trạng thái, lịch sử phân trang/lọc và giữ kết quả sau xóa master.
- Đọc danh mục/huy hiệu cá nhân và kiểm tra các huy hiệu đã hiển thị từ đầu lượt vẫn được giữ.
- Khi thành công, xóa các bài fixture riêng và ngừng các nhóm fixture. Các nhóm ngừng và lịch sử buổi vẫn còn theo quy tắc hệ thống.

## User: quyền sử dụng cá nhân và chặn quản trị

Đăng nhập tài khoản User, dán lại script:

```javascript
await FitnessModuleTest.run({ mode: "user" });
```

Tự chọn ba bài active khác nhau có ngưỡng không quá hai phút. Nếu chưa có đủ, Admin cần tạo trước bằng lệnh dưới. Có thể chỉ định danh sách ID thực tế bằng option `exerciseIds` gồm ít nhất ba ID khác nhau. Khi có bộ mẫu đã lưu cùng tab/origin, script ưu tiên đúng ba ID đó (bài đầu ngưỡng một phút để thử complete quá sớm).

User chạy luồng tập giống Admin, kiểm tra bị chặn API quản trị; bỏ qua các ca cần sửa/xóa master. Nếu bài đầu có ngưỡng dưới 30 giây, ca complete quá sớm được đánh dấu SKIP để tránh coi bài thực sự đủ thời gian là lỗi.

### Khi User chưa có đủ bài phù hợp

1. Đăng xuất rồi đăng nhập **Admin** trong cùng tab trình duyệt.
2. Dán lại **bản mới nhất** của scripts/workout-module-console.js, sau đó chạy:

```javascript
await FitnessModuleTest.seedUserFixtures();
FitnessModuleTest.userFixtures();
```

3. Hàm tạo một nhóm riêng và ba bài active: một bài ngưỡng 1 phút, hai bài ngưỡng 0.1 phút. Không tạo buổi tập, không cần tự nhập ID, không đổi quyền tài khoản. Bộ mẫu được giữ lại cho User; gọi lại sẽ xác minh/dùng lại các ID đã lưu, không tạo trùng.
4. Đăng xuất Admin và đăng nhập lại User **cùng tab**, dán lại script mới, chạy:

```javascript
await FitnessModuleTest.run({ mode: "user" });
```

Nếu mở tab khác/mất sessionStorage, lấy ba ID thực tế từ bảng kết quả lúc Admin seed và truyền option `exerciseIds`. Bộ seed không bị cleanup của lượt kiểm thử cá nhân xóa; Admin có thể quản lý lại bằng các ID của userFixtures(). Nếu thiếu dữ liệu mà chưa tạo fixture buổi/nhóm/bài nào, script mới tự bỏ manifest rỗng, không bắt cleanup chỉ để chạy lại.

## Kiểm tra khác chủ bằng tài khoản thứ hai

Sau lượt A, xem các ID thật:

```javascript
FitnessModuleTest.context();
```

Lấy một `workouts[i].id` và một `workouts[i].detailIds[j]`. Lịch sử buổi còn tồn tại sau dọn fixture. Đăng nhập tài khoản B, dán lại script rồi gọi `testForeignAccess` với hai ID đó. Ví dụ cấu trúc lệnh (thay các biến bằng ID thật đã ghi):

```javascript
await FitnessModuleTest.testForeignAccess({ workoutId, detailId });
```

Nếu dùng cùng tab/origin và sessionStorage vẫn còn, có thể lấy từ manifest A:

```javascript
var target = FitnessModuleTest.context().workouts[0];
await FitnessModuleTest.testForeignAccess({
  workoutId: target.id,
  detailId: target.detailIds[0]
});
```

Chạy lần lượt bằng B có ROLE_USER và B có ROLE_ADMIN để xác minh Admin cũng không vượt quyền sở hữu. Hàm **GET trước**; nếu không nhận 403 thì dừng trước các request ghi, tránh nhầm tài khoản A/B. Không đoán ID chưa được tạo.

## Kết quả, dừng và dọn sau lỗi

```javascript
FitnessModuleTest.report();  // Bảng PASS / FAIL / SKIP và ID fixture, không có token
FitnessModuleTest.context(); // ID đã tạo; có thể giữ qua reload bằng sessionStorage
FitnessModuleTest.stop();    // Dừng chờ/request; không tự hủy hoặc xóa dữ liệu
await FitnessModuleTest.cleanup(); // Chỉ dọn dữ liệu đúng manifest của lượt trước
await FitnessModuleTest.testAnonymous(); // Kiểm tra GET không có cookie bị chuyển /login
```

- Gặp lỗi: lượt dừng, giữ fixture để kiểm tra Network/log. Gửi dòng FAIL và kết quả report/context, không gửi timerToken.
- Cleanup chỉ hủy buổi do lượt đó tạo và còn mở; chỉ xóa bài có ID trong manifest và tên vẫn khớp tiền tố. Khác chủ hoặc tên không khớp thì không tự dọn. Không xóa lịch sử/check-in/huy hiệu; nhóm chỉ ngừng hoạt động.
- Bản mới reset cleaned=false trước dọn và đọc lại sau hủy/xóa/ngừng. 404 có JSON lỗi (như No static resource) không được coi là bản ghi đã xóa. Cleanup chỉ PASS khi buổi đọc lại đã đóng và có endTime; khi FAIL giữ ID để thử lại sau khi sửa API. Bản cũ có thể báo PASS nhầm nếu route GET chưa được nạp, vì vậy cần dán lại file JS mới trước cleanup.
- Khi nhiều tab/devices cùng điều khiển, token có thể bị thu hồi đúng quy tắc. Không chạy hai lượt trên cùng tài khoản. Hai request đồng thời trong script là các ca được điều phối riêng.
- Một lượt không FAIL vẫn có thể có SKIP; các SKIP phải được xử lý riêng trước khi tuyên bố nghiệm thu toàn bộ.

### Tài khoản hiện tại có buổi cũ không thuộc manifest

Ví dụ sau khi đổi từ User sang Admin, context vẫn là fixture của User, nhưng Admin có buổi 1 đang mở. Không dùng cleanup() của manifest User để xử lý buổi Admin. Khi đã xong buổi cũ và muốn chủ động đóng nó, dán bản JS mới rồi gọi rõ ID:

```javascript
var workoutCloseResult = await FitnessModuleTest.closeExistingWorkout(1);
if (workoutCloseResult.FAIL === 0) {
  await FitnessModuleTest.run({ mode: "admin" });
}
```

Lệnh chỉ chạy khi được gọi rõ ràng, không được run tự động gọi. Nó GET buổi chính chủ/current trước khi ghi, chọn complete nếu đã có bài COMPLETED, ngược lại cancel; giữ kết quả đã hoàn thành, hủy các bài dở, GET xác minh sau đóng. Buổi đã đóng có endTime thì không ghi lại. Không thêm buổi cũ vào manifest hoặc xóa manifest của tài khoản trước. ID 1 ở đây là ví dụ ca đã quan sát; dùng đúng ID mà preflight thực tế báo.

Dùng tên workoutCloseResult ở Console thay vì var closed: window.closed là thuộc tính boolean chỉ đọc của trình duyệt. Nếu buổi đã đóng PASS rồi thì chỉ cần gọi trực tiếp await FitnessModuleTest.run({ mode: "admin" });, không cần đóng lại buổi.

### HTTP 409 “Dữ liệu tập luyện xung đột…” khi hủy buổi

Đây là thông báo DataIntegrityViolationException đã được Controller rút gọn. Cần xem ERROR trong Console IntelliJ để lấy SQLState/tên constraint; HTTP 409 một mình chưa xác định được constraint nào lỗi. CHECK enum status cũ chưa có CANCELLED là một khả năng cần kiểm tra, không tự kết luận từ ảnh.

Chạy scripts/sql/workout_tracking_diagnose.sql (chỉ SELECT) trên datasource đúng để xem CHECK/kiểu cột và buổi 4 của ca hiện tại. Nếu chưa đồng bộ schema hoặc CHECK thiếu CANCELLED, áp dụng file workout_tracking_sync.sql bản mới sau khi kiểm tra các truy vấn trùng dữ liệu. Bản mới đồng bộ CHECK status của cả workout_details và workout_sessions; không bỏ CHECK tổng quát hoặc tắt ràng buộc database. Nếu constraint lỗi khác, cần xử lý theo đúng định nghĩa/log, không giả định chạy script là đủ.

Sau khi xác nhận sửa đúng và commit SQL thành công, dán lại JS trong cùng tab/tài khoản, gọi cleanup() để đóng fixture còn mở rồi mới run lại. Không xóa manifest trước cleanup vì sẽ mất dấu ID fixture; không đổi từ cancel sang complete để né lỗi.

Chẩn đoán JDBC READ ONLY ngày 01/11/2026 đã xác nhận trên datasource dự án fitness_ai/public: `workout_details_status_check` thiếu CANCELLED, CHECK của workout_sessions đã đúng. Có bản sửa tối thiểu `scripts/sql/workout_details_allow_cancelled.sql` chỉ thay CHECK này; chưa được trợ lý thực thi. Fixture 4/detail 5 vẫn ở IN_PROGRESS/NOT_STARTED sau lỗi. Controller đã bổ sung log WARN tên constraint/SQLState, có hiệu lực khi ứng dụng nạp bản code mới; 2 ca test log/response an toàn đã đạt.

Cập nhật sau đồng bộ đầy đủ: metadata đã có ck_workout_details_status, ck_workout_sessions_status, ck_workout_details_approved_rest và đủ 7 index (2 PK + 2 unique + 3 tra cứu). Không chạy lại bản sửa tối thiểu theo tên constraint cũ ở trạng thái này. SELECT cuối script sync vẫn có thể trả dữ liệu legacy: ca đã xác minh detail 2/session 2/user_id 3 FROZEN chỉ thiếu min_duration_minutes_snapshot, MET có sẵn. Dòng đó không phải lỗi tạo index; không tự backfill từ master hiện tại hoặc công nhận hoàn thành. Đóng buổi cũ chỉ khi chủ buổi chủ động chọn hủy/đóng.

### GET /workouts trả 404 “No static resource workouts”

Lỗi này nghĩa request không vào handler lịch sử, không phải trang lịch sử rỗng. Kiểm tra location.origin khớp cổng ứng dụng. Source hiện có @GetMapping("/workouts"), compiled class cũng có annotation, và hai test MockMvc cho GET lịch sử/GET current đã đạt. Các bằng chứng đó chưa xác nhận mapping trong tiến trình đang chạy đã được nạp lại.

Trong IntelliJ, Stop đúng BackendJavaProjectApplication rồi Run lại từ D:\OOP_Java_Project- (branch workout_tracking hiện tại), chờ startup xong; đăng nhập lại và thử GET /workouts?page=0&size=1 trước khi run cả script. Không sửa thành POST hoặc bỏ qua kiểm tra contract để né 404. Nếu vẫn lỗi, cần lấy URL/response thật và mapping/log startup của tiến trình mới.

Script mới in URL đầy đủ khi lỗi và không chấp nhận /workouts/current trả 404 có body lỗi thành “không có buổi mở”; 404 hợp lệ của Controller current là body rỗng. Reload trang/đổi đăng nhập thì dán lại file JS mới; manifest fixture vẫn lưu trong sessionStorage cùng tab nếu chưa bị xóa.

## Cấu hình thời gian nếu backend đã thay đổi

Mặc định khớp cấu hình hiện tại: heartbeat 10 giây, timeout 30 giây, scheduler 10 giây, nghỉ test 45 giây (để thử offline quá 30 giây). Nếu backend thực sự khác, truyền đúng cấu hình đã lưu:

```javascript
await FitnessModuleTest.run({
  mode: "admin",
  heartbeatMs: 10000,
  timeoutMs: 30000,
  scanMs: 10000,
  restSeconds: 45
});
```

Các option chỉ đổi cách script chờ, **không đổi cấu hình backend**. Browser không gửi heartbeat trong đoạn giả lập offline, không ngắt mạng toàn máy. Ca scheduler chỉ GET trạng thái sau chờ để không tự kích hoạt freeze bằng heartbeat trước khi quan sát.

## Giới hạn kiểm chứng

- Cần tài khoản/phân quyền riêng cho khóa/chưa xác minh; không tự khóa hoặc đổi quyền Admin bằng script.
- Streak qua 00:00, ngày nghỉ theo lịch, chuỗi dài ngày, mốc huy hiệu và cảnh báo cần kịch bản thời gian/dữ liệu riêng. Qua ngày Việt Nam trong lượt thì ca so sánh streak tương ứng được SKIP.
- HTTP concurrency kiểm tra kết quả quan sát được, không chứng minh đầy đủ isolation/rollback/unique index/FK. Cần đối chiếu SQL.
- Không có kiểm chứng động tác thật/GPS/camera. Các ca này đo logic timer và dữ liệu server trả về.
- Script đã kiểm tra cú pháp bằng Node và kiểm tra offline rằng nạp file không gọi API, cấu hình sai/buổi có sẵn/nhầm chủ đều dừng trước lệnh ghi; thiếu bài không giữ manifest rỗng, User không seed được và Admin seed/dùng lại ba bài bằng API giả lập. Các ảnh người dùng đã xác nhận một số tiền điều kiện thật, chưa có kết quả lifecycle/seed thật để công nhận nghiệm thu toàn module.

Kiểm tra offline có thể chạy từ Terminal dự án:

```powershell
node --check .\scripts\workout-module-console.js
node .\scripts\check-workout-console.cjs
```
