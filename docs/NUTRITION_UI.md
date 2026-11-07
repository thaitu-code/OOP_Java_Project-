# Giao diện Nutrition

## Phạm vi

- Trang `/nutrition` dành cho người đã đăng nhập, gồm nhật ký, thư viện thực phẩm và lịch sử.
- Trang `/admin/nutrition-library` dành cho Admin, mở mặc định tab quản lý trong cùng template.
- Giữ tông màu, font, thanh điều hướng và nền chuyển sắc hiện tại. CSS riêng dùng tiền tố `n-` và vùng `.nutrition-body`.
- Có liên kết từ Trang chủ, Workout và trang quản trị.
- Không tích hợp trợ lý AI trong thay đổi này.

## File chính

- `src/main/resources/templates/nutrition.html`: cấu trúc trang, CSRF, các tab, hộp thoại và vùng chèn dữ liệu.
- `src/main/resources/static/css/nutrition.css`: giao diện, trạng thái, responsive và focus bàn phím.
- `src/main/resources/static/js/nutrition.js`: gọi API, hiển thị dữ liệu, form nhập món và quản trị.
- `NutritionPageController` đã được người dùng viết trước đó; phần triển khai giao diện dùng nguyên hợp đồng Model và URL này.

## Luồng sử dụng

1. Mở nhật ký: GET `/nutrition-logs?date=yyyy-MM-dd`. Không tạo nhật ký khi xem.
2. Thêm món: chọn thực phẩm từ danh sách phân trang, nhập bữa/gram/ghi chú rồi POST `/nutrition-logs/today/details`.
3. Sửa món: PUT `/nutrition-logs/today/details/{detailId}`, kèm `expectedVersion` lấy từ món đang hiển thị.
4. Xóa món: xác nhận trong hộp thoại rồi DELETE cùng đường dẫn, kèm query `expectedVersion`.
5. Sau khi ghi thành công, hiển thị nhật ký/tổng từ response; đánh dấu lịch sử cần tải lại.
6. Nhật ký ngày khác chỉ xem. Ngày tính theo `Asia/Ho_Chi_Minh`; có kiểm tra chuyển ngày khi trang đang mở/quay lại tab. Backend vẫn quyết định quyền sửa cuối cùng.
7. Cùng Food ID giữ snapshot cũ. Đổi thực phẩm chọn lại từ thư viện đang hoạt động và gửi Food ID mới. Các trường snapshot không gửi từ frontend.
8. Lịch sử GET `/nutrition-logs/history?page=...&size=10`; phân trang đọc `response.page`, mở chi tiết theo ngày.
9. Thư viện GET `/foods` có keyword/categoryId/page/size. Form chọn món có tìm kiếm, lọc nhóm và phân trang riêng.
10. Admin quản lý Food/Category bằng các API POST, PUT và PATCH activate/deactivate hiện có. Không xóa cứng master data.

## Bảo vệ thao tác

- Dùng session cùng origin và CSRF từ meta trên chính trang Nutrition.
- Phát hiện response chuyển sang trang đăng nhập, khóa thao tác khi phiên hết hạn.
- Khóa thao tác ghi và submit trùng trong lúc đang lưu. Không tự gửi lại POST/PUT/DELETE khi timeout, mất kết nối hoặc kết quả ghi chưa rõ.
- Với kết quả chưa rõ, tải lại nhật ký để đối chiếu và yêu cầu người dùng đóng hộp thoại, làm mới trước khi gửi tiếp.
- 404/409 tải lại nhật ký; form cũ khóa submit. Người dùng mở lại form từ dữ liệu mới, không tự nâng version rồi gửi lại thay đổi cũ.
- Bỏ qua response cũ khi người dùng đổi ngày/từ khóa nhanh.
- Escape tên, mô tả và ghi chú trước khi đưa vào markup. Ảnh chỉ nhận URL HTTP/HTTPS trực tiếp đúng định dạng; có fallback khi tải thất bại và không gửi referrer.
- Hiển thị tổng đã được backend lưu; không thay bằng phép tính dinh dưỡng bằng số thực phía trình duyệt.
- Link ảnh GitHub dạng `github.com/{owner}/{repo}/blob/{ref}/{file}` hoặc `/raw/` được chuyển thành `raw.githubusercontent.com/{owner}/{repo}/{ref}/{file}`. Áp dụng khi hiển thị link cũ trong thư viện/chi tiết/ảnh snapshot và chuẩn hóa URL khi lưu form Food mới; không sửa lại dữ liệu nhật ký trong database.

## Kiểm tra đã thực hiện ngày 07/11/2026

- JDK 17, Maven offline, output tách riêng tại `target/nutrition-ui-verification`: biên dịch thành công; 5/5 test `NutritionPageControllerTest` đạt. Kiểm tra render template/CSRF, User không thấy quản lý, Admin thấy quản lý, anonymous redirect, User bị 403 ở trang Admin, và giữ context path.
- `node scripts/check-nutrition-ui.cjs`: 13/13 nhóm tình huống đạt trên chính `nutrition.js` với DOM/HTTP mô phỏng. Bao gồm lifecycle thêm/sửa/đổi Food/xóa, snapshot, version cũ, chỉ xem ngày cũ, chuyển ngày, Admin, CSRF, bấm lưu trùng, lỗi mạng/kết quả ghi sai định dạng không tự lặp POST, response cũ, escape và hết phiên.
- Chrome với HTTP fixture trong RAM: đã thao tác thêm/sửa/xóa món, đối chiếu tổng hiển thị, thư viện hai trang và tìm kiếm rỗng, lịch sử, tạo nhóm và Food quản trị.
- Màn hình rộng 390 px: nội dung rộng 390 px, các bữa xếp một cột, thống kê hai cột. Đã khôi phục viewport bình thường sau kiểm tra.
- Ảnh xem trước nằm tại `target/nutrition-ui-preview/desktop.png` và `mobile.png`. Đây là dữ liệu mô phỏng, không phải ảnh database thật.
- Node syntax và `git diff --check` đạt. Không ghi database, không restart ứng dụng thật, không kiểm thử HTTP thật của frontend mới nối PostgreSQL trong lượt này.

## Chạy lại

Bổ sung sửa ảnh GitHub ngày 07/11/2026: `node scripts/check-nutrition-ui.cjs` đạt 15/15 nhóm (gồm link blob trong thư viện/chi tiết/snapshot, chuẩn hóa URL khi lưu và chặn scheme/credentials/định dạng ảnh không hợp lệ). Link raw Cà chua người dùng cung cấp đã kiểm tra HTTP HEAD, trả 200 và `Content-Type: image/jpeg`; chưa xác nhận lại giao diện trên ứng dụng thật sau khi tải lại resource.

Từ thư mục project, dùng JDK 17:

```powershell
./mvnw.cmd -Dtest=NutritionPageControllerTest test
node scripts/check-nutrition-ui.cjs
node scripts/preview-nutrition-ui.cjs
```

Bản xem trước mặc định tại `http://127.0.0.1:8772/nutrition` và `/admin/nutrition-library`. Chỉ lưu dữ liệu trong RAM; dừng Node sẽ mất dữ liệu mẫu. Có thể đặt `NUTRITION_UI_PREVIEW_PORT` hoặc `NUTRITION_UI_PREVIEW_DIR` để đổi cổng/thư mục template đã render. Không proxy request tới ứng dụng/database thật.

Các script và tài liệu này đang nằm trong thư mục `scripts`/`docs` bị `.gitignore` hiện tại bỏ qua; giữ theo quy ước sẵn có của project. HTML/CSS/JS và test Java không bị bỏ qua.

## Nghiệm thu trên ứng dụng thật

1. Build/chạy lại ứng dụng để nạp `NutritionPageController` và resource mới. Bản `target/classes` cũ chưa có controller/template Nutrition tại thời điểm kiểm tra.
2. Đăng nhập và mở `/nutrition`; Admin mở `/admin/nutrition-library` hoặc dùng liên kết từ trang quản trị.
3. Làm mới trình duyệt bằng Ctrl+F5 nếu còn cache giao diện.
4. Kiểm tra trên dữ liệu của tài khoản: chọn ngày, thêm/sửa/xóa món, chuyển lịch sử, tìm kiếm/lọc/phân trang và quản trị theo quyền.
5. Kiểm tra đồng thời/chuyển ngày và nghiệp vụ backend còn lại nếu cần nghiệm thu toàn module. Các ca frontend mô phỏng không thay thế kiểm thử transaction/database thật.
