# Nutrition — Cấu trúc và luồng nghiệp vụ đã chốt

Cập nhật 04/11/2026. Có nền Entity/enum và phương thức nghiệp vụ; chưa có Repository/Service/API/giao diện. Xem AI_INTEGRATION_REVIEW.md về phần AI sẽ xây sau.

## 1. Quan hệ và thuộc tính

```text
FoodCategory 1 ── N Food 1 ── N LogDetail N ── 1 NutritionLog N ── 1 User
```

| Entity | Thuộc tính |
|---|---|
| FoodCategory / food_categories | categoryId, categoryName, categoryNameKey, description, active, createdAt, updatedAt |
| Food / foods | foodId, category, foodName, foodNameKey, description, source, imageUrl, caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, active, createdAt, updatedAt |
| NutritionLog / daily_meal_logs | logId, user, logDate, createdAt |
| LogDetail / meal_details | detailId, nutritionLog, food, mealType, weightGrams, note; foodNameSnapshot, categoryNameSnapshot, imageUrlSnapshot; bốn chỉ số/100 g snapshot; calculatedCalories/Protein/Carbs/Fat; loggedAt, updatedAt, createdSource, lastModifiedSource, version |

- MealType: BREAKFAST, LUNCH, AFTERNOON, DINNER. NutritionEntrySource: USER, AI. Enum lưu chuỗi.
- ID mới là Integer, khóa User hiện có là Long. Quan hệ ManyToOne LAZY; không cascade xóa hoặc setter công khai cho owner/ngày/snapshot/kết quả/version.
- categoryNameKey/foodNameKey do backend chuẩn hóa tên, gộp khoảng trắng và lowercase Locale.ROOT, unique tại DB; vẫn phân biệt dấu tiếng Việt. Đây là trường kỹ thuật để Service và unique không lệch cách kiểm tra trùng.
- FoodCategory tên tối đa 100, mô tả 1000. Food tên 150, mô tả 2000, source/imageUrl 500. Note 500. Chuỗi tùy chọn trống thành null; tên bắt buộc.
- Ảnh chỉ URL trực tiếp HTTP/HTTPS PNG/JPG/JPEG/WebP/GIF, cho query string; không upload, không server fetch, không credential trong URL. Frontend sau này dùng placeholder nếu không có/lỗi ảnh. Snapshot giữ URL, không sao chép binary của ảnh ngoài.
- Dinh dưỡng/gram dùng BigDecimal. Calo/100 g numeric(7,2), macro/100 g numeric(6,2), gram numeric(7,2), kết quả numeric(12,2). Timestamp OffsetDateTime, ngày LocalDate theo Asia/Ho_Chi_Minh.
- Ngày/owner và nguồn tạo không đổi sau khi ghi. Source do backend gắn; không nhận từ payload user/model và không dùng source để cấp quyền. Version do JPA quản lý, request sửa/xóa sau này gửi expectedVersion để Service kiểm tra.

## 2. Phân trách nhiệm

| Tầng | Trách nhiệm |
|---|---|
| Entity hiện có | Giá trị hợp lệ, ngày được sửa, snapshot, công thức, sửa nguyên vẹn sau khi kiểm tra đủ đầu vào, timestamp/nguồn |
| Repository sẽ viết ở M4-03 | Query có scope người/ngày, tổng/đếm từ detail, unique và khóa log khi ghi |
| Service sẽ viết sau | Actor từ phiên/backend, trạng thái tài khoản, quyền Admin/chủ sở hữu, validation dùng chung UI/AI, ngày/version dưới khóa, transaction và response DTO |
| Controller/UI hoặc AI adapter | Nhận lệnh với schema cố định; không cấp quyền ghi Entity/SQL tùy ý; gọi cùng Service |

Entity không tự xác thực phiên đăng nhập. Test Entity không phải bằng chứng quyền HTTP, khóa/rollback hoặc optimistic locking trên PostgreSQL đã hoạt động.

## 3. Luồng đã chốt

### Kho thực phẩm

Admin tạo/sửa/ngừng/kích hoạt nhóm và Food. Mỗi Food có đúng một nhóm. Khi tạo/chuyển nhóm phải chọn nhóm active; vẫn cho Admin sửa Food giữ nguyên nhóm đang ngừng.

Food được chọn mới khi cả Food và nhóm active. Ngừng nhóm không ghi đè trạng thái riêng Food; kích hoạt nhóm lại không tự kích hoạt Food đã ngừng. Không có hard-delete nhóm/Food trong module này.

### Nhật ký và thêm món

GET ngày chưa có nhật ký trả rỗng/tổng 0, không tạo bản ghi. Thao tác ghi món đầu ngày mới tìm/tạo log; UNIQUE(user_id,log_date) bảo vệ một log/người/ngày. Không job tạo log đại trà lúc 00:00.

Add request dự kiến gồm foodId, mealType, weightGrams, note; không có userId/logDate/snapshot/kết quả/source. Backend lấy ngày và actor, kiểm tra Food/nhóm, tạo snapshot rồi tính và lưu detail.

Mỗi lần ăn là một detail riêng kể cả cùng Food/bữa. Giao diện chống bấm lặp; khi POST lỗi kết nối không tự retry, tải lại log để đối chiếu. Cơ chế idempotency bền vững của AI action sẽ thuộc lớp thực thi AI, không suy rằng trùng foodId là request trùng.

### Công thức và tổng

```text
Giá trị món = snapshot trên 100 g × weightGrams / 100
Kết quả món làm tròn hai chữ số bằng HALF_UP
Tổng bữa/ngày = SUM(các kết quả đã lưu của detail tương ứng)
```

Đầu vào không âm, gram > 0; không tự làm tròn đầu vào vượt hai chữ số có nghĩa sau dấu thập phân. Calo dùng caloriesPer100g, không suy lại bằng 4–4–9. Không lưu tổng trùng lặp ở NutritionLog.

Ví dụ giả kiểm thử: 165 kcal/31 g đạm/0 g carbs/3.6 g fat trên 100 g; ăn 200 g có 330/62/0/7.2. Sửa 150 g có 247.50/46.50/0/5.40.

### Sửa và xóa

- Đổi gram: tính từ snapshot cũ. Đổi bữa/note: không lấy lại thông số master. Gửi cùng foodId kể cả Entity được tải lại vẫn giữ snapshot.
- Đổi sang foodId khác: food/nhóm mới phải active; thay toàn bộ snapshot tên/nhóm/ảnh/dinh dưỡng rồi tính lại.
- Food/nhóm đã ngừng vẫn cho sửa gram/bữa/note của detail hôm nay. Không cho chọn mới thực phẩm đó.
- Giữ loggedAt/createdSource; cập nhật updatedAt/lastModifiedSource. Kiểm tra toàn bộ đầu vào trước khi đổi state để lỗi không để lại snapshot/kết quả dở dang trong Entity.
- Service xóa phải kiểm tra cùng owner/ngày/version như sửa. Xóa món cuối giữ log rỗng, tổng 0; ngày không có detail không tính TOTAL_MEAL_LOGS. Không thu hồi huy hiệu đã nhận.
- Khóa ngày căn cứ thời gian backend sau khi chờ khóa; quá khứ/tương lai không được ghi. Entity cũng chặn ngày không còn hiện tại. Admin không được sửa nhật ký người khác chỉ vì có role Admin.

## 4. Mở rộng AI

Cả UI và AI dùng cùng Nutrition Service; model chỉ thấy DTO cần thiết, không Entity User hoặc token. Source USER/AI chỉ là dấu nguồn, chưa thay audit hành động có before/after/requestId sau này.

Kế hoạch ăn, mục tiêu calo/macro và cuộc chat là mô hình riêng; AI đề xuất một món không tự tạo detail “đã ăn”. Lịch sử giữ snapshot dù cân nặng/mục tiêu hoặc kho thay đổi. Không gắn Nutrition trực tiếp với một provider/model.

## 5. Database, kiểm tra và phần tiếp theo

- Metadata READ ONLY ngày 04/11/2026: users.id bigint, chưa có bốn bảng Nutrition trong public. Chưa tạo/chỉnh dữ liệu thật.
- scripts/sql/nutrition_initial_schema.sql là script tạo mới trong transaction, không DROP/seed, chưa chạy. Nếu Hibernate đã tạo bảng trước đó, phải đọc metadata và dùng SQL đồng bộ phù hợp; không chạy lại bản tạo mới. Không tự restart app để Hibernate ghi schema.
- DDL có unique tên chuẩn hóa/người-ngày, FK giữ liên kết và CHECK số không âm/gram dương/enum/thời gian. Ở giai đoạn này mới kiểm tra mapping Hibernate; chưa có kiểm chứng thực thi DDL hoặc ràng buộc trên DB thật.
- Bản cuối JDK17/Maven đạt 41 test: NutritionDomainTest 39, NutritionMappingTest 1, WorkoutAutomationMappingTest 1, không failure/error/skip. Biên dịch 97 nguồn/19 test, POM/output cô lập trong target/nutrition-verification; không chạy contextLoads hoặc thay target/classes của app. Test domain bao phủ tính toán, làm tròn, số/URL/tên Unicode sai, nguồn USER/AI, đổi/ngừng master, snapshot, cập nhật không thành công và biên ngày Việt Nam. Test mapping dựng toàn mô hình, kiểm tra unique/version/scale và parse JPQL tổng/đếm/phạm vi người dùng mà không chạy query.
- Bước tiếp: M4-03 Repository, rồi DTO/Service/Controller theo chức năng. Tạo log cạnh tranh dùng INSERT ON CONFLICT DO NOTHING + khóa log trong transaction; kiểm tra ngày sau khi chờ, tổng trả cùng response. Service vẫn phải xác thực chủ và version, không trông chờ vào Entity.
