# Module 4 — Theo dõi dinh dưỡng

Ngày lập: 02/11/2026. Cập nhật 04/11/2026: đã triển khai nền Entity/enum, kiểm tra domain/mapping cô lập; chưa có API/giao diện hoặc tạo bảng thật. Đặc tả chốt tại [NUTRITION_DOMAIN.md](NUTRITION_DOMAIN.md), rà soát mở rộng AI tại [AI_INTEGRATION_REVIEW.md](AI_INTEGRATION_REVIEW.md).

## 1. Căn cứ và phạm vi

- Yêu cầu: tạo Food, Nutrition_Log, Log_Detail; quản lý thực phẩm, ghi món theo khối lượng, tự tính tổng calo/protein/carbs/fat trong ngày.
- Đối chiếu PLAN.md mục 9, 16, 18, 19.5 và 21; PROCESS.md về cách triển khai module tập luyện.
- Nền Java hiện có FoodCategory, Food, NutritionLog, LogDetail, MealType và NutritionEntrySource. User dùng khóa Long, bảng users, cột khóa chính id; không lấy users.user_id trong mô tả backup làm mapping thực tế.
- SecurityConfig hiện bảo vệ /admin/** bằng quyền ADMIN. BadgeService hiện trả 0 cho TOTAL_MEAL_LOGS, chưa tích hợp nhật ký ăn.
- Metadata READ ONLY ngày 04/11 xác nhận users.id bigint và chưa có bốn bảng Nutrition trong public. Chưa chạy DDL/DML; script tạo mới đã soạn, chưa thực thi.

Các quy tắc đã chốt trong PLAN:

1. Admin quản lý kho thực phẩm; User tìm/chọn thực phẩm để ghi ăn. Admin cũng dùng nhật ký cá nhân của mình.
2. Chỉ số thực phẩm tính trên 100 g; cần phân biệt thực phẩm sống/chín và phần ăn được.
3. Một người có tối đa một nhật ký cho một ngày. Bốn bữa: BREAKFAST, LUNCH, AFTERNOON, DINNER.
4. Thêm/sửa/xóa chỉ trong ngày hiện tại theo Asia/Ho_Chi_Minh; ngày quá khứ chỉ đọc, không nhập bù.
5. Backend tính dinh dưỡng; lưu snapshot thực phẩm/calo/macro để thay đổi kho không làm đổi lịch sử.
6. Ngừng hoạt động thực phẩm để chặn chọn mới, giữ dữ liệu đã ghi. Không cascade xóa kho thực phẩm qua nhật ký.

## 2. Quy trình phối hợp và bảng tiến độ

Đi theo thứ tự Entity → Repository → DTO/Service/Controller cho từng chức năng → kiểm thử → giao diện. Mỗi phần có đầu ra và bằng chứng riêng; cập nhật PROCESS.md trước khi chuyển phần tiếp theo. Người dùng đã yêu cầu bắt đầu triển khai ngày 04/11; không tự thêm bước xin duyệt từ tài liệu này.

Trạng thái dùng: Chưa làm / Đang làm / Đã có mã / Đã kiểm tra / Chờ xử lý. Ghi rõ kiểm tra bằng đọc nguồn, build, test cô lập hay HTTP/SQL thật; test chưa chạy không được ghi đạt.

| Mã | Phần việc | Đầu ra và điều kiện hoàn tất | Hiện tại |
|---|---|---|---|
| M4-01 | Phân tích, chia luồng | Quy tắc đã có, phương án thiết kế, danh sách điểm cần xác minh | Đã lập tài liệu |
| M4-02 | Entity và enum | FoodCategory, Food, NutritionLog, LogDetail, MealType/NutritionEntrySource; validation/snapshot/version/nguồn; script schema | Đã có mã, 41 test domain/mapping cô lập đạt; chưa DDL/API thật |
| M4-03 | Repository | Tìm thực phẩm/phân trang, log theo người-ngày, chi tiết theo log, tổng bữa/ngày và khóa khi ghi | Chưa làm |
| M4-04 | Kho thực phẩm | DTO → Service → Controller: tạo, tìm/xem, sửa, ngừng/kích hoạt; quyền Admin/User | Chưa làm |
| M4-05 | Nhật ký và thêm món | Tìm/tạo log hôm nay, thêm món, snapshot và tổng; không trùng log khi đồng thời | Chưa làm |
| M4-06 | Sửa/xóa món | Đúng chủ, đúng ngày, giữ snapshot khi đổi gram, tính lại tổng và xử lý món cuối | Chưa làm |
| M4-07 | Tra cứu và lịch sử | Xem theo ngày/bữa, tổng bốn chỉ số; phân trang lịch sử; GET không tạo dữ liệu | Chưa làm |
| M4-08 | Kiểm thử backend | Công thức, validation, quyền, snapshot, giao ngày, cạnh tranh/rollback; ghi phạm vi bằng chứng | Chưa làm |
| M4-09 | Giao diện và nghiệm thu | Admin quản lý kho; User chọn món/gram/bữa, xem tổng/lịch sử; kiểm tra với API | Chưa làm |

Điểm tích hợp sau lõi: TOTAL_MEAL_LOGS đếm số ngày có ít nhất một món; xóa món cuối không còn đóng góp metric nhưng không thu hồi huy hiệu đã nhận. Cảnh báo AI/TDEE, mục tiêu dinh dưỡng và thực đơn AI có lộ trình riêng; không chặn phần ghi ăn bằng các ngưỡng chưa chốt.

## 3. Mô hình dữ liệu đề xuất

Tên Java dùng Food, NutritionLog, LogDetail theo PascalCase; tương ứng Food, Nutrition_Log, Log_Detail trong đề bài. Tên bảng tiếp nối PLAN thay vì tạo thêm bảng trùng chức năng.

| Lớp | Bảng dự kiến | Dữ liệu chính |
|---|---|---|
| FoodCategory | food_categories | categoryId, categoryName, khóa tên chuẩn hóa, description, active, createdAt/updatedAt |
| Food | foods | foodId, category, foodName, khóa tên chuẩn hóa, description/source/imageUrl, caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, active, createdAt/updatedAt |
| NutritionLog | daily_meal_logs | logId (cột meal_log_id), user, logDate, createdAt |
| LogDetail | meal_details | detailId (cột meal_detail_id), nutritionLog, food, mealType, weightGrams, note, loggedAt/updatedAt; snapshot tên/nhóm/ảnh và bốn chỉ số/100 g; bốn giá trị tính theo gram; createdSource/lastModifiedSource, version |

Quan hệ:

```text
User 1 ── N NutritionLog 1 ── N LogDetail N ── 1 Food N ── 1 FoodCategory
```

- Log thuộc một User; detail thuộc một log và tham chiếu một food. Không cần lặp userId trên detail.
- UNIQUE(user_id, log_date) tại DB. Kiểm tra ở Service giúp báo lỗi nhưng không thay thế constraint khi có cạnh tranh.
- Food.active=false vẫn được đọc trong các detail cũ; không xóa vật lý trong phạm vi ban đầu.
- Tên nhóm/thực phẩm chuẩn hóa khoảng trắng và lowercase Locale.ROOT trong khóa tên unique; không phân biệt hoa/thường, vẫn phân biệt dấu.
- Dùng BigDecimal cho gram/dinh dưỡng; LocalDate cho logDate, OffsetDateTime cho timestamp. Precision/scale và schema tạo mới được chốt trong NUTRITION_DOMAIN.md; metadata thật hiện chưa có bảng, script chưa chạy.
- Không lưu bốn tổng ngày ở NutritionLog trong bản đầu; tổng lấy từ các detail để tránh hai nguồn dữ liệu bị lệch.

Snapshot là dữ liệu được chép tại lần ghi ăn. Khi chỉ sửa gram/bữa/note hoặc gửi lại cùng foodId, giữ snapshot để tính lại; thay sang food khác thì chụp thông số food/nhóm đang hoạt động. Đã có logic ở Entity, chưa có Service/API thực hiện thao tác.

## 4. Hướng xử lý từng chức năng

### 4.1. Kho thực phẩm

Admin tạo/sửa tên và bốn chỉ số/100 g, ngừng hoặc kích hoạt. Kiểm tra tên bắt buộc, chỉ số không âm và giới hạn phù hợp cột số. User tìm thực phẩm đang hoạt động theo tên, có phân trang; Admin xem cả thực phẩm đã ngừng.

Thông tin sống/chín thể hiện rõ trong tên/mô tả, source lưu nguồn. Ảnh chỉ URL trực tiếp, không upload/server fetch. Một Food thuộc một nhóm chính; Food và nhóm phải active để chọn mới. Chỉ ngừng/kích hoạt, không hard-delete. Không tự seed số liệu dinh dưỡng không có nguồn.

### 4.2. Nhật ký hôm nay và thêm món

1. Lấy tài khoản từ phiên đăng nhập, kiểm tra trạng thái tài khoản; không nhận userId từ client để chọn chủ log.
2. Backend xác định ngày Việt Nam bằng một Clock có thể thay thế trong test.
3. Kiểm tra foodId có thật/đang hoạt động, mealType hợp lệ, weightGrams > 0 và nằm trong giới hạn được chốt.
4. Trong transaction, tìm hoặc tạo duy nhất log của người/ngày, phối hợp khóa và UNIQUE để xử lý hai món đầu ngày gửi đồng thời.
5. Chụp tên và bốn chỉ số/100 g, tính và lưu bốn kết quả của món.
6. Lấy lại tổng bữa/ngày trong cùng transaction, trả detail và tổng trong một response để giao diện không cần gọi lại nhiều GET.

GET hôm nay khi chưa có log trả danh sách rỗng và tổng 0; chỉ thao tác ghi mới tạo log. Bản đầu không cần job tạo log cho mọi tài khoản lúc 00:00.

### 4.3. Tính tổng

```text
Chỉ số của món = chỉ số snapshot trên 100 g × weightGrams / 100
Tổng bữa = SUM(chỉ số của các detail thuộc bữa đó)
Tổng ngày = SUM(chỉ số của mọi detail thuộc log đó)
```

Ví dụ giả để kiểm tra công thức: Food A có 165 kcal, 31 g đạm, 0 g tinh bột, 3.6 g béo/100 g. Ghi 200 g được 330 kcal, 62 g đạm, 0 g tinh bột, 7.2 g béo. Nếu cùng ngày ghi thêm 100 g Food A thì tổng là 495 kcal, 93 g đạm, 0 g tinh bột, 10.8 g béo.

Đã chốt: làm tròn từng kết quả detail hai chữ số thập phân bằng HALF_UP rồi cộng đúng các kết quả đã lưu. Phần tính/làm tròn từng món đã có ở Entity, phần truy vấn tổng sẽ viết ở Repository/Service. Schema mới dùng scale 2 cho gram/kết quả thay mô tả backup cũ scale 1; chưa có bảng thật cần chuyển đổi. Không tự suy calo từ công thức 4–4–9 khi đã có caloriesPer100g.

Truy vấn SUM/nhóm bữa từ snapshot, không lấy macro từ Food hiện tại. Không tải toàn bộ kho hoặc mọi log để cộng bằng Java. Ngày rỗng trả bốn tổng 0.

### 4.4. Sửa/xóa món

Tìm detail trong phạm vi log của tài khoản; kiểm tra ngày sau khi đã lấy khóa ghi. Ngày nghiệp vụ được xác định ở thời điểm chấp nhận thao tác dưới khóa, không tin thời gian từ trình duyệt.

- Đổi gram: tính lại từ snapshot cũ; đổi bữa: chuyển nhóm tổng; đổi food: kiểm tra food mới và lấy snapshot mới.
- Xóa detail: tính lại tổng từ các detail còn lại; xóa món cuối trả tổng 0. Đề xuất giữ log rỗng, không tính là ngày ăn hợp lệ cho huy hiệu.
- Ngày quá khứ từ chối thay đổi ở backend kể cả gọi API trực tiếp. Admin không tự có quyền sửa nhật ký của người khác.

### 4.5. Lịch sử và giao diện

Đọc theo ngày của chính tài khoản, nhóm theo bốn bữa, hiển thị món/gram/bốn kết quả và tổng. Lịch sử phân trang theo ngày giảm dần; ngày tương lai chưa có nhật ký thực tế, không mở ghi trước.

Giao diện tiếp nối Thymeleaf/HTML/CSS/JS hiện tại: thanh chọn ngày, bốn thẻ tổng, bốn bữa, hộp tìm thực phẩm và nhập gram. Chỉ hôm nay có thao tác ghi. Sau ghi dùng response cập nhật dòng và tổng ngay; chống bấm lặp khi request đang chạy. Admin có màn hình kho riêng.

## 5. Nhóm API dự kiến

Đây là đề xuất để chia việc, chưa phải contract đã triển khai.

| Chức năng | API dự kiến |
|---|---|
| Admin quản lý nhóm | POST/GET /admin/food-categories; GET/PUT /admin/food-categories/{id}; PATCH /admin/food-categories/{id}/status |
| Admin quản lý kho | POST/GET /admin/foods; GET/PUT /admin/foods/{id}; PATCH /admin/foods/{id}/status |
| User tìm thực phẩm | GET /foods?keyword=...&page=... |
| Nhật ký theo ngày | GET /nutrition-logs?date=YYYY-MM-DD |
| Thêm món hôm nay | POST /nutrition-logs/today/details |
| Sửa/xóa món hôm nay | PUT/DELETE /nutrition-logs/today/details/{detailId} |
| Lịch sử phân trang | GET /nutrition-logs/history?page=... |

Add request: foodId, mealType, weightGrams, note. Không nhận userId, logDate, source hay kết quả dinh dưỡng do client/model quyết định. Update request cho phép đổi food/bữa/gram/note kèm expectedVersion; Service áp dụng snapshot và kiểm tra quyền.

## 6. Kiểm tra và điểm cần quyết định khi triển khai

Các ca cần có bằng chứng:

- Công thức gram nguyên/lẻ, biên làm tròn, nhiều món/nhiều bữa, ngày rỗng, giá trị vượt precision.
- Tên trống/trùng, số âm, gram 0, food không tồn tại/ngừng hoạt động, bữa sai.
- Admin/User/anonymous, tài khoản khóa/chưa xác minh, CSRF và truy cập khác chủ.
- Admin sửa hoặc ngừng food không đổi món cũ; sửa gram dùng snapshot, thay food dùng thông số mới.
- Thêm/sửa/xóa tính lại đủ bốn tổng; xóa món cuối về 0, metric ngày ăn xử lý đúng khi tích hợp.
- Hai món đầu ngày đồng thời chỉ có một log và đủ hai detail; sửa/xóa đồng thời không trả tổng lỗi thời; lỗi giữa chừng rollback toàn thao tác.
- Trước/sau 00:00 Việt Nam, request chờ khóa qua ngày; GET lịch sử không ghi dữ liệu.

Các lựa chọn nền đã chốt tại NUTRITION_DOMAIN.md. Repository/Service kế tiếp dùng UNIQUE người/ngày, INSERT ON CONFLICT DO NOTHING + khóa log; kiểm tra owner/ngày/version và trả tổng trong cùng transaction. Cùng food có thể xuất hiện nhiều lần do ăn nhiều lần; không tự gộp/xóa một lần ghi chỉ vì cùng foodId. UI không tự retry POST sau lỗi kết nối; idempotency AI action thuộc lớp thực thi AI sau này.

Không bắt lỗi unique rồi tiếp tục dùng transaction đã hỏng; cơ chế tạo log cạnh tranh cần được thiết kế và kiểm tra với PostgreSQL. Tránh khóa User kéo dài trong mọi lần đọc/ghi vì có thể làm chậm module tập luyện của cùng tài khoản.

Bước tiếp theo: M4-03 Repository; phần Service/Controller/UI/AI chưa được coi là có chỉ từ nền Entity.
