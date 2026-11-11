# FITNESS AI — KẾ HOẠCH SẢN PHẨM, NGHIỆP VỤ VÀ TRIỂN KHAI

**Phiên bản tài liệu:** 1.2 — cập nhật 15 nhóm quyết định và 5 câu trả lời bổ sung của chủ dự án ngày 17/10/2026.

**Dự án được khảo sát:** `D:\OOP_Java_Project-`.

**Múi giờ nghiệp vụ:** `Asia/Ho_Chi_Minh` (UTC+7).

**Mục đích:** để một người chưa tham gia dự án vẫn hiểu website giải quyết việc gì, ai sử dụng, sử dụng như thế nào, dữ liệu được xử lý ra sao và cần xây dựng những gì.

> Đây là đặc tả của sản phẩm cần xây dựng, kèm hiện trạng được kiểm tra. Chức năng mô tả trong tài liệu không đồng nghĩa đã được lập trình hoặc đã chạy thành công. Tại thời điểm khảo sát, repository mới có bộ khung Spring Boot và một entity `User` sơ khai.

## Mục lục

1. [Nguồn thông tin và cách đọc tài liệu](#1-nguồn-thông-tin-và-cách-đọc-tài-liệu)
2. [Tổng quan sản phẩm](#2-tổng-quan-sản-phẩm)
3. [Quyết định đã chốt](#3-quyết-định-đã-chốt)
4. [Hiện trạng dự án và khoảng cách cần triển khai](#4-hiện-trạng-dự-án-và-khoảng-cách-cần-triển-khai)
5. [Vai trò và quyền sử dụng](#5-vai-trò-và-quyền-sử-dụng)
6. [Bản đồ giao diện và cách sử dụng](#6-bản-đồ-giao-diện-và-cách-sử-dụng)
7. [Tài khoản và xác thực](#7-tài-khoản-và-xác-thực)
8. [Hồ sơ, mục tiêu và chỉ số cơ thể](#8-hồ-sơ-mục-tiêu-và-chỉ-số-cơ-thể)
9. [Nhật ký dinh dưỡng](#9-nhật-ký-dinh-dưỡng)
10. [AI xây dựng và người dùng chấp nhận lịch tập](#10-ai-xây-dựng-và-người-dùng-chấp-nhận-lịch-tập)
11. [Thực hiện bài tập và ghi nhận kết quả](#11-thực-hiện-bài-tập-và-ghi-nhận-kết-quả)
12. [Check-in, ngày nghỉ và streak](#12-check-in-ngày-nghỉ-và-streak)
13. [Hệ thống huy hiệu](#13-hệ-thống-huy-hiệu)
14. [Chat với AI và khuyến nghị](#14-chat-với-ai-và-khuyến-nghị)
15. [Thông báo và xử lý theo ngày](#15-thông-báo-và-xử-lý-theo-ngày)
16. [Chức năng Admin](#16-chức-năng-admin)
17. [Dashboard và thống kê](#17-dashboard-và-thống-kê)
18. [Từ điển database hiện có](#18-từ-điển-database-hiện-có)
19. [Dữ liệu cần bổ sung](#19-dữ-liệu-cần-bổ-sung)
20. [Kiến trúc và luồng xử lý](#20-kiến-trúc-và-luồng-xử-lý)
21. [Quản lý schema bằng Hibernate](#21-quản-lý-schema-bằng-hibernate)
22. [Trình tự triển khai](#22-trình-tự-triển-khai)
23. [Kịch bản nghiệm thu](#23-kịch-bản-nghiệm-thu)
24. [Đối chiếu quyết định và phần cần hoàn thiện](#24-đối-chiếu-quyết-định-và-phần-cần-hoàn-thiện)
25. [Thuật ngữ và nguồn tham khảo](#25-thuật-ngữ-và-nguồn-tham-khảo)

## 1. Nguồn thông tin và cách đọc tài liệu

### 1.1. Nguồn đã đọc

- Toàn bộ cuộc trò chuyện [Phân Tích Database Fitness AI](https://share.gemini.google/mNOqJUxaVToU), bao gồm phát biểu của người dùng, phân tích của Gemini, SQL đề xuất và lộ trình code.
- File `fitness_ai.backup` do người dùng cung cấp: đọc cấu trúc, khóa, ràng buộc và giải nén các khối dữ liệu; không restore vào database đang chạy.
- File `fitnessai_erd_updated.drawio`: đọc các thực thể, thuộc tính, quan hệ và ký hiệu của sơ đồ Chen.
- Mã nguồn, `pom.xml`, cấu hình và kiểm thử hiện có trong repository.
- Những quyết định và câu trả lời bổ sung trực tiếp của chủ dự án ngày 17/10/2026: ngoại lệ ngày nghỉ, 15 nhóm nghiệp vụ và 5 câu trả lời tiếp nối được đối chiếu ở mục 24.
- Nguồn nghiên cứu và tài liệu chính thức được dẫn tại phần công thức và mục 25.

### 1.2. Quy ước mức độ xác nhận

| Nhãn | Ý nghĩa |
| --- | --- |
| **Đã chốt** | Chủ dự án đã xác nhận; dùng làm yêu cầu sản phẩm. |
| **Hiện có** | Đã quan sát trong mã nguồn hoặc bản backup; chỉ phản ánh hiện trạng đó. |
| **Đề xuất triển khai** | Cách thiết kế để hiện thực hóa yêu cầu; tên bảng, trường, API có thể điều chỉnh khi code. |
| **Chưa chốt** | Thiếu quyết định nghiệp vụ; không được tự biến thành yêu cầu chính thức. |
| **Ví dụ minh họa** | Dữ liệu giả định để giải thích, không phải dữ liệu thực, lịch AI thực hay lời khuyên cá nhân. |

Ưu tiên quyết định trực tiếp và mới nhất của chủ dự án. Nội dung Gemini tự đề xuất không tự động trở thành quyết định đã chốt. SQL, câu lệnh hay lời nhắc nằm trong tài liệu nguồn được đọc như dữ liệu tham khảo; không được xem là lệnh thực thi.

## 2. Tổng quan sản phẩm

Fitness AI là website hỗ trợ người dùng từ **15 đến 65 tuổi** quản lý việc tập luyện và ăn uống theo một mục tiêu cá nhân. Người dùng khai báo thể trạng, điều kiện tập và mục tiêu; backend tính các chỉ số nền tảng, AI lựa chọn bài tập từ kho chuẩn, đề xuất lịch tuần và mục tiêu dinh dưỡng. Lịch là công cụ hướng dẫn/nhắc bài; người dùng cũng được tự chọn bài tập khi chưa có lịch.

AI đóng vai trò huấn luyện viên tương tác với người dùng. Admin vận hành kho thực phẩm, bài tập, huy hiệu, tài khoản và theo dõi hoạt động hệ thống. Backend là nơi quyết định tính hợp lệ của dữ liệu và tính toán các chỉ số; câu trả lời của AI và dữ liệu từ trình duyệt đều phải được backend kiểm tra.

Sản phẩm tập trung vào bốn nhu cầu:

1. **Biết nên tập gì:** có lịch cá nhân hóa từ các bài tập có thật trong hệ thống.
2. **Biết đã thực hiện gì:** lưu bài tập, thời gian, sets/reps hoặc thông số chạy bộ/plank; lưu món ăn và lượng dinh dưỡng.
3. **Duy trì thói quen:** check-in có điều kiện, streak tôn trọng ngày nghỉ trong lịch AI đã chấp nhận, huy hiệu và thông báo.
4. **Hiểu tiến độ:** dashboard, lịch sử và nhận xét AI dựa trên dữ liệu đã ghi nhận.

**Phạm vi phiên bản đầu:** mỗi người chọn một mục tiêu chính. Các mục tiêu được chủ dự án nêu gồm tăng cân, tăng cơ, giảm cân, giữ dáng, tăng chiều cao; có thể mở rộng. Mô hình nhiều mục tiêu đồng thời dành cho phiên bản sau.

Mục tiêu tăng chiều cao được chia nội dung hỗ trợ theo hai nhóm **15–22** và **23–65** trong phạm vi phục vụ của website. Nhóm trẻ hơn tập trung thói quen dinh dưỡng, vận động và ngủ; nhóm trưởng thành tập trung tư thế và vận động phù hợp. Nội dung y khoa và thông điệp không được biến thành cam kết tăng cm; các điểm cần chỉnh lý từ mô tả ban đầu được giải thích tại mục 8.8.

### 2.1. Một vòng sử dụng điển hình

```text
Đăng ký/đăng nhập
    → Khai báo hồ sơ + một mục tiêu
    → Backend tính chỉ số
    → AI đề xuất lịch/thực đơn, hoặc người dùng yêu cầu qua chat
    → Người dùng xem, trao đổi chỉnh sửa và phê duyệt
    → Xem lịch hôm nay hoặc tự chọn bài khi chưa có lịch
    → Chọn bài, bấm bắt đầu, thực hiện, hoàn thành ít nhất một bài hợp lệ
    → Check-in trong ngày, cập nhật streak/huy hiệu
    → Ghi thực phẩm đã ăn, theo dõi calo và macro
    → Xem lịch sử, thông báo hoặc trao đổi thêm với AI
```

Ngày nghỉ hợp lệ theo lịch đang áp dụng: không tập thì giữ nguyên streak; nếu tự tập và hoàn thành bài hợp lệ thì tăng streak như các ngày khác. Ngày nghỉ không bị tính là bỏ tập.

## 3. Quyết định đã chốt

| Chủ đề | Quy tắc chính thức |
| --- | --- |
| Java | Project hiện khai báo Java 17; chưa có yêu cầu đổi Java. |
| Database | PostgreSQL; định hướng Neon trong cuộc trò chuyện nguồn. Tình trạng database cloud đang chạy chưa được xác minh. |
| Quản lý schema | Dùng Hibernate để quản lý schema từ Entity, không dùng Flyway cho kế hoạch này. |
| Vai trò | `ROLE_USER`, `ROLE_ADMIN`; không có tài khoản Coach. AI là coach. |
| Mục tiêu | Phiên bản đầu chỉ một mục tiêu; phiên bản sau mới hỗ trợ nhiều mục tiêu. |
| Độ tuổi/công thức | 15–65 tuổi; TDEE dùng 5 hệ số tại mục 8.4; OTHER dùng hằng số −78 theo quy ước sản phẩm. |
| Cá nhân hóa | Thu thập kinh nghiệm, địa điểm/thiết bị, thời gian/số ngày có thể tập, chấn thương/hạn chế vận động. |
| Dinh dưỡng mục tiêu | AI xác định calo và gram protein/carbs/fat mục tiêu; MEAL_PLAN có bữa, món trong foods và số gram. |
| Lịch tập | AI tự đề xuất hoặc lắng nghe yêu cầu; user trao đổi để sửa từng bài/ngày. Lịch lặp đến khi thay đổi; hỗ trợ đổi riêng hôm nay. |
| Giờ khuyến nghị | 05:00–08:00, 15:00–18:00, 19:00–21:00 giờ Việt Nam. Ngoài khung chỉ nhắc, không khóa tập luyện. |
| Phạm vi lịch | Lịch và cờ ngày thuộc từng người dùng, không phải lịch dùng chung của bài tập. |
| Tính toán | Backend tính BMR, TDEE, calo ăn và calo tập. |
| Check-in | Hoàn thành ít nhất một bài hợp lệ trong các bài đã chọn; chọn bài hoặc bấm kết thúc ngay không được check-in. |
| Chưa có lịch | Được chọn bài tự do để check-in; ngày chưa có ngày nghỉ hợp lệ được coi là ngày phải tập. |
| Ngưỡng hoàn thành | Gồm tập + nghỉ, loại pause/đóng băng. Bài theo cấp độ dùng 2/3 thời gian dự kiến do Admin nhập để kết thúc sớm; đủ thời gian dự kiến đổi nút kết thúc đầy đủ. Sets/reps không phải điều kiện xác minh. Bài thủ công cũ giữ ngưỡng riêng. |
| Buổi đang dở | Giữ để tiếp tục; tắt trình duyệt thì đóng băng, không cộng thời gian; chỉ một buổi và một bài đang thực hiện cho mỗi user. |
| Reps | Số lần lặp **mỗi hiệp**. |
| Chạy bộ | Theo dõi thời gian chạy, nghỉ giữa chặng và quãng đường. |
| Plank | Chỉ chọn thời gian giữ tư thế làm mốc; số hiệp/nghỉ là dữ liệu theo dõi hoặc hướng dẫn. |
| Ngày nghiệp vụ | `Asia/Ho_Chi_Minh`; chốt ngày lúc 00:00 theo giờ Việt Nam. |
| Ngày phải tập bị bỏ lỡ | Nếu cả ngày không có check-in hợp lệ, reset streak về 0 khi ngày đó kết thúc. |
| Ngày nghỉ hợp lệ | Không tập: giữ nguyên; hoàn thành bài hợp lệ: tăng 1, tối đa một lần/ngày. |
| Nhắc không tập | Bắt đầu sau 3 ngày phải tập bị bỏ lỡ, sau đó nhắc mỗi ngày phải tập tiếp theo; bỏ qua ngày nghỉ. |
| Chúc mừng streak | Mốc 7, 14, 21, 28… và 30, 60, 90, 100, 365; mỗi mốc chỉ chúc mừng một lần/user, không gửi lại khi đạt lại ở chuỗi mới. |
| Huy hiệu | Data-Driven; đã nhận giữ vĩnh viễn; thêm mới cấp bù người đủ điều kiện; thay ngưỡng không thu hồi. |
| Lịch sử | Kết quả đã ghi nhận không sửa hồi tố; riêng nhật ký ăn hôm nay được sửa/xóa, khóa khi sang 00:00; snapshot giữ số liệu cũ. |
| Tài khoản | Xác minh email, quên mật khẩu qua email; v1 cho nhiều thiết bị đăng nhập. |
| Chat | Một luồng duy nhất/user; mỗi lần đăng nhập mở màn hình trống, bấm tải 10 tin cũ; được xóa từng tin USER/AI, không xóa cả luồng. Ngữ cảnh AI vẫn có giới hạn riêng. |
| Admin | Có đầy đủ chức năng User và quyền quản trị; không khóa/gỡ quyền Admin cuối cùng. |
| Dữ liệu chuẩn | Ngừng hoạt động để không chọn mới, giữ lịch sử; bài ngừng hoạt động trong lịch yêu cầu AI đề xuất thay. |
| Báo cáo | Áp dụng công thức mục 17; loại tài khoản Admin, bị khóa và thử nghiệm. |

**Đã chốt “Hibernate thuần”:** bỏ Flyway; vẫn giữ Spring Data JPA và Hibernate từ project hiện tại. Không chuyển sang yêu cầu viết tất cả truy vấn bằng Hibernate Session trực tiếp.

## 4. Hiện trạng dự án và khoảng cách cần triển khai

### 4.1. Những gì đã kiểm tra

| Thành phần | Hiện trạng |
| --- | --- |
| Maven | Có `pom.xml`, Maven Wrapper; Spring Boot parent 4.1.1, Java 17. |
| Dependencies | Có JPA, Security, WebMVC, PostgreSQL, Lombok, DevTools và dependencies test. |
| Flyway | Vẫn có hai dependencies và `spring.flyway.enabled=true`; cần bỏ/tắt khi triển khai quyết định mới. |
| Ứng dụng | Có class chạy `BackendJavaProjectApplication`, package gốc `vn.id.fitnessai`. |
| Entity | Chỉ có `User`, mới khai báo `id`, `email`, `password`. |
| Ánh xạ `User` | Chưa khớp backup: backup dùng `user_id`, `username`, `email`, `password_hash`, `created_at`. |
| Repository/Service/Controller | Chưa có các lớp nghiệp vụ này trong `src`. |
| Frontend | Chưa có các file giao diện trong cây `src` đã khảo sát. |
| Cấu hình | Port hiện là 8081; `ddl-auto=update`; không công bố thông tin kết nối hay mật khẩu trong PLAN. |
| Test | Có một `contextLoads`; chưa có kiểm thử nghiệp vụ. |
| Backup | PostgreSQL 16.15; 17 bảng, không có view/function/trigger. |
| Dữ liệu backup | Hai dòng `roles`: `ROLE_ADMIN`, `ROLE_USER`; các bảng còn lại không có dòng dữ liệu. |

Không coi thư mục `target`, file đã build hoặc test có tên `contextLoads` là bằng chứng toàn bộ sản phẩm đã hoạt động. Trong nhiệm vụ lập PLAN này không chạy ứng dụng, không thử kết nối Neon, không build hoặc restore database.

### 4.2. Những yêu cầu mới database chưa đáp ứng đầy đủ

- Chưa lưu lịch tập theo thứ, ngày nghỉ, thời gian hiệu lực và trạng thái người dùng đồng ý.
- Chưa lưu danh sách bài được chọn trước khi tập và mốc bắt đầu của từng lần thực hiện bài.
- Chưa phân loại cách đo bài tập: reps, chạy bộ, giữ tư thế.
- Chưa có quãng đường chạy, thời gian giữ plank, thời gian nghỉ giữa chặng/hiệp.
- Chưa có lịch sử check-in/ngày nghỉ/ngày bỏ lỡ để giải thích streak và báo cáo theo thời gian.
- Chưa có trạng thái khóa tài khoản.
- Danh sách `goal` còn thiếu tăng cân và tăng chiều cao.
- Chưa có cơ chế lưu dấu chống tạo thông báo trùng và theo dõi lỗi yêu cầu AI.
- Chưa có kinh nghiệm/điều kiện tập, mục tiêu cân nặng từ chat, mục tiêu calo/macro và thực đơn có cấu trúc.
- Chưa có timer pause/đóng băng, lịch thay đổi tạm thời trong ngày, trạng thái cảnh báo hết áp dụng.
- Chưa có xác minh/reset mật khẩu qua email, đánh dấu tài khoản thử nghiệm và trạng thái xóa từng tin chat.

Các bổ sung được đề xuất ở mục 19; chưa phải schema đã tồn tại.

## 5. Vai trò và quyền sử dụng

| Hoạt động | Người chưa đăng nhập | User | Admin |
| --- | --- | --- | --- |
| Đăng ký/đăng nhập | Có | Có thể đăng xuất/đăng nhập lại | Có thể đăng xuất/đăng nhập lại |
| Xem/sửa hồ sơ cá nhân | Không | Của mình | Có quyền xem hồ sơ người dùng theo yêu cầu quản trị |
| Chấp nhận lịch, ghi tập, ghi ăn | Không | Của mình | Có, cho dữ liệu cá nhân của mình |
| Chat AI, xem khuyến nghị | Không | Của mình | Có cho cá nhân; có quyền giám sát theo phạm vi quản trị |
| Quản lý foods/categories/exercises/badges | Không | Không | Có |
| Khóa/mở khóa, đổi quyền | Không | Không | Có |
| Thống kê cá nhân | Không | Của mình | Xem dữ liệu quản trị |
| Báo cáo toàn hệ thống | Không | Không | Có |

AI không có tài khoản, mật khẩu hay bản ghi `ROLE_COACH`. AI phân tích thông tin backend cung cấp và trả đề xuất; backend lưu dữ liệu và áp dụng quyền truy cập. AI không tự cấp Admin, tự check-in hay tự ghi người dùng đã hoàn thành bài tập.

ROLE_ADMIN bao gồm quyền sử dụng chức năng User ở tầng phân quyền. Người mang cả hai role vẫn thuộc nhóm Admin khi lọc báo cáo toàn hệ thống.

Việc Admin được **xem** hồ sơ/hội thoại không mặc nhiên bao gồm quyền sửa kết quả tập hoặc tạo check-in thay người dùng; những quyền sửa thay này chưa được yêu cầu.

## 6. Bản đồ giao diện và cách sử dụng

**Đề xuất triển khai:** SPA dùng HTML/CSS/Vanilla JavaScript trong tài nguyên tĩnh Spring Boot; History API và `router.js` điều hướng đường dẫn sạch. Tailwind CSS, FontAwesome, Chart.js, Marked.js, KaTeX, SockJS/STOMP là định hướng trong trao đổi nguồn, chưa có trong repository.

### 6.1. Các màn hình người dùng

| Màn hình | Hiển thị gì | Người dùng làm gì | Kết quả cần thấy |
| --- | --- | --- | --- |
| Đăng nhập/đăng ký | Form tài khoản, xác minh email, quên mật khẩu | Đăng ký, xác minh, đăng nhập/reset | Vào hệ thống với đúng quyền |
| Thiết lập hồ sơ | Thể trạng, vận động, mục tiêu | Điền và lưu | Hồ sơ hợp lệ, chỉ số backend tính |
| Dashboard | Lịch hôm nay, check-in, streak, calo, huy hiệu, thông báo | Chọn thao tác tiếp theo | Biết hôm nay tập hay nghỉ và đã làm được gì |
| Kế hoạch AI | Đề xuất từng ngày, bài tập, ngày nghỉ, giải thích | Xem, chấp nhận hoặc chưa đồng ý | Chỉ lịch được chấp nhận trở thành lịch áp dụng |
| Lịch tập tuần | Thứ hai đến chủ nhật và ngày nghỉ | Xem bài mỗi ngày, chọn bài | Thấy lịch của chính mình |
| Buổi tập | Video, mục tiêu bài, đồng hồ, hiệp/chặng, bài còn dở | Bắt đầu, tạm dừng/tiếp tục, hoàn thành, kết thúc | Check-in khi đủ thời gian tối thiểu; đóng tab không cộng thời gian |
| Dinh dưỡng | Nhật ký ngày và bốn bữa | Tìm thực phẩm, nhập gram, sửa/xóa món | Calo và macro cập nhật |
| Lịch sử/tiến độ | Kết quả từng ngày, buổi, bài và biểu đồ | Chọn khoảng thời gian | Phân biệt lịch dự kiến với kết quả thực tế |
| Huy hiệu | Đã đạt, chưa đạt, điều kiện | Xem tiến độ | Biết vì sao đạt hoặc còn thiếu bao nhiêu |
| AI Coach | Lượt chat hiện tại, nút xem 10 tin cũ, trạng thái phản hồi | Hỏi, sửa lịch qua chat, tải tin cũ, xóa từng tin | Một luồng duy nhất; thay lịch có phê duyệt |
| Thông báo | Nội dung, loại, thời điểm, đã đọc/chưa đọc | Mở thông báo | Nội dung hiển thị và cập nhật trạng thái đọc |
| Tài khoản | Hồ sơ hiện tại, thông tin phiên | Cập nhật hồ sơ, đăng xuất | Chỉ số/lịch liên quan được xử lý theo quy tắc |

Các tên nút và bố cục cụ thể còn có thể thay đổi; chức năng và kết quả nghiệp vụ phải được giữ.

### 6.2. Hướng dẫn cho người mới

1. Đăng ký, xác minh email rồi đăng nhập. Tài khoản tự đăng ký không được tự chọn Admin.
2. Điền hồ sơ 15–65 tuổi, điều kiện tập và **một** mục tiêu; kiểm tra đơn vị cm/kg và ngày sinh.
3. Xem các chỉ số ước tính. Nếu thiếu dữ liệu cần cho công thức, hệ thống phải nói rõ thay vì hiển thị số tự bịa.
4. Xem lịch/thực đơn AI đề xuất hoặc yêu cầu qua chat. Chưa có lịch vẫn có thể chọn bài tự do.
5. Chấp nhận lịch nếu đồng ý. Chỉ đọc đề xuất/chỉ chat với AI chưa làm lịch có hiệu lực.
6. Ngày phải tập: mở bài, xem hướng dẫn, chọn bài rồi bấm bắt đầu. Hoàn thành điều kiện của ít nhất một bài.
7. Chờ backend xác nhận kết quả. Dashboard hiển thị đã check-in; không dựa riêng vào đồng hồ trình duyệt.
8. Ngày nghỉ theo lịch: không tập vẫn bảo toàn streak; nếu tập hợp lệ thì tăng streak 1.
9. Ghi các món đã ăn vào bữa tương ứng, xem tổng calo và dinh dưỡng của ngày.
10. Theo dõi huy hiệu, lịch sử và thông báo. Đổi lịch/thể trạng qua các thao tác hiện tại, không sửa kết quả lịch sử. Khi quay lại buổi dở, tiếp tục timer đã đóng băng.

### 6.3. Trạng thái cần giải thích rõ trên giao diện

- “Chưa có hồ sơ”, “Chưa có lịch được chấp nhận”, “Đang chờ AI”, “AI chưa phản hồi được”.
- “Hôm nay cần tập”, “Hôm nay nghỉ theo lịch”, “Đã check-in hôm nay”, “Chưa hoàn thành bài hợp lệ”.
- “Đề xuất chưa áp dụng” và “Lịch đang áp dụng” phải phân biệt rõ.
- “Streak được bảo toàn do ngày nghỉ” khác với “Đã check-in”; ngày nghỉ không tạo check-in giả.
- “Đang nghỉ giữa hiệp/chặng” có thể được tính vào ngưỡng tối thiểu; “Tạm dừng/Đóng băng” không được tính.
- Khi đăng nhập, khung chat trống không có nghĩa lịch sử bị xóa; nút “Xem 10 tin nhắn trước” tải từng đợt.
- “0 kcal đã ghi nhận” khác với “chưa có dữ liệu ăn/tập”; không suy ra người dùng chưa ăn hay chưa vận động ngoài ứng dụng.

## 7. Tài khoản và xác thực

### 7.1. Đăng ký

**Luồng mục tiêu:** nhập username/email/mật khẩu → backend kiểm tra hợp lệ và trùng → băm mật khẩu → tạo `users` → gán `ROLE_USER` qua `user_roles` → trả kết quả đăng ký.

`username` và `email` phải duy nhất theo backup. **Đã chốt:** xác minh email và quên mật khẩu qua email. Quy tắc độ dài mật khẩu, chuẩn hóa hoa/thường và thời hạn mã/link là cấu hình cần hoàn thiện khi triển khai.

**Đề xuất triển khai:** dùng bộ mã hóa mật khẩu của Spring Security, ví dụ BCrypt; chỉ lưu `password_hash`, không trả trường này cho frontend hoặc Admin. Tạo bản ghi streak ban đầu với `current_streak=0`, `highest_streak=0`; hồ sơ đầy đủ được tạo ở bước thiết lập thể trạng.

### 7.2. Đăng nhập và phân quyền

Kiểm tra thông tin đăng nhập và trạng thái tài khoản. Định hướng xác thực JWT: backend cấp token, frontend gửi kèm khi gọi API cần bảo vệ; backend xác minh rồi xác định người dùng/quyền. TTL token, refresh token, vị trí lưu token và xử lý đăng xuất cần chốt khi triển khai bảo mật.

API phải lấy chủ thể từ phiên xác thực. Không cho phép đổi `user_id` trong request để đọc/sửa dữ liệu người khác. Phân quyền Admin phải kiểm tra ở backend dù frontend đã ẩn menu quản trị.

**Đã chốt:** v1 cho đăng nhập nhiều thiết bị đồng thời. Mỗi lần đăng nhập mở một lượt hiển thị chat mới; các phiên thiết bị khác không bị xóa lịch sử hoặc đăng xuất chỉ vì có đăng nhập mới. Giới hạn một buổi/bài đang tập vẫn áp dụng theo user trên mọi thiết bị.

**Đề xuất luồng email:** đăng ký → gửi link/mã xác minh có thời hạn và chỉ dùng một lần → xác minh → cho truy cập chức năng cá nhân. Quên mật khẩu → gửi link reset → xác thực token một lần → lưu mật khẩu băm mới. Cần nhà cung cấp email/cấu hình gửi; chưa có bằng chứng email đã gửi thành công. Thời hạn token, gửi lại và tác động reset lên phiên cũ được cấu hình khi triển khai.

### 7.3. Khóa/mở khóa

Admin có quyền khóa/mở khóa theo yêu cầu đã chốt. Schema cần bổ sung trạng thái tài khoản. Khi khóa: từ chối đăng nhập và các thao tác được bảo vệ của tài khoản đó, kể cả khi vẫn giữ token cũ; kết nối chat cũng phải kiểm tra tương ứng. Cách thu hồi token cụ thể là thiết kế triển khai.

**Đã chốt:** không khóa hoặc gỡ quyền Admin cuối cùng còn hoạt động. Phải kiểm tra trong transaction để hai thao tác quản trị đồng thời không làm mất toàn bộ quyền vận hành. Không tự cấp tài khoản Admin công khai bằng mật khẩu mặc định trong tài liệu.

## 8. Hồ sơ, mục tiêu và chỉ số cơ thể

### 8.1. Các trường và ý nghĩa

| Trường | Ý nghĩa và cách dùng |
| --- | --- |
| `display_name` | Tên hiển thị, không thay thế username đăng nhập. |
| `gender` | `MALE`, `FEMALE`, `OTHER`; hằng số sản phẩm tương ứng +5, −161, −78. |
| `birth_date` | Ngày sinh để tính tuổi tại ngày Việt Nam hiện tại. |
| `height_cm` | Chiều cao tính bằng cm. |
| `weight_kg` | Cân nặng **cơ thể**, khác mức tạ tập. |
| `activity_level` | Mức vận động nền để ước tính TDEE. |
| `goal` | Một mục tiêu chính trong phiên bản đầu. |
| `calculated_bmr` | Chỉ số chuyển hóa nền/nghỉ ước tính do backend tính. |
| `calculated_tdee` | Nhu cầu năng lượng cả ngày ước tính do backend tính. |
| Kinh nghiệm | Mới bắt đầu/trung bình/lâu năm hoặc bộ mức tương đương; enum cụ thể là thiết kế triển khai. |
| Địa điểm/thiết bị | Tại nhà/phòng gym và thiết bị sẵn có để lọc bài phù hợp. |
| Khả năng bố trí lịch | Số ngày, ngày/giờ và thời lượng có thể tập. |
| Chấn thương/hạn chế | Thông tin người dùng cung cấp để AI tránh đề xuất không phù hợp; không dùng để tự chẩn đoán. |
| Cân nặng mong muốn | Thông tin bổ sung qua chat; không bắt nhập trên form chọn mục tiêu. |

Ràng buộc hiện có: ngày sinh trước ngày hiện tại; chiều cao 50–260 cm; cân nặng 20–400 kg; BMR và TDEE dương. Yêu cầu sản phẩm bổ sung tuổi tròn **15–65**, bao gồm hai đầu. Backend tính tuổi theo ngày Việt Nam để kiểm tra, không chỉ giới hạn trên form.

### 8.2. Tính tuổi

```text
todayVN = ngày hiện tại trong Asia/Ho_Chi_Minh
ageYears = số năm tròn từ birth_date tới todayVN
```

Không lấy năm hiện tại trừ năm sinh mà bỏ qua việc đã đến sinh nhật hay chưa. Ví dụ sinh 18/10/2006: ngày 17/10/2026 là 19 tuổi, ngày 18/10/2026 mới 20 tuổi. Quy tắc sinh nhật 29/2 cần kiểm thử theo cách xử lý ngày của Java.

### 8.3. BMR/REE theo Mifflin–St Jeor

**Đã chốt cho sản phẩm:** dùng dạng rút gọn Mifflin–St Jeor với hằng số theo lựa chọn giới tính dưới đây. Nghiên cứu gốc dự đoán năng lượng lúc nghỉ (REE); tên trường sản phẩm là `calculated_bmr`. Công thức nam/nữ được đối chiếu trong [nghiên cứu Mifflin và cộng sự](https://pubmed.ncbi.nlm.nih.gov/2305711/).

```text
W = cân nặng cơ thể (kg)
H = chiều cao (cm)
A = tuổi tròn (năm)

MALE:   BMR = 10 × W + 6.25 × H − 5 × A + 5
FEMALE: BMR = 10 × W + 6.25 × H − 5 × A − 161
OTHER:  BMR = 10 × W + 6.25 × H − 5 × A − 78
Đơn vị kết quả: kcal/ngày
```

**Nguồn của hằng số OTHER:** quy ước do chủ dự án xác nhận, `(5 + (−161)) / 2 = −78`; không phải hệ số thứ ba được nghiên cứu Mifflin xác lập. Các số đều là ước tính. Nghiên cứu gốc khảo sát người 19–78 tuổi, nên phạm vi sản phẩm 15–18 tuổi cần chính sách dinh dưỡng phù hợp tuổi, không coi việc áp dụng công thức là bằng chứng đã được xác nhận cho thanh thiếu niên. Thiếu trường bắt buộc thì yêu cầu bổ sung, không điền số giả.

**Ví dụ minh họa:** nam, 20 tuổi, 70 kg, 170 cm:

```text
BMR = 10 × 70 + 6.25 × 170 − 5 × 20 + 5
    = 700 + 1062.5 − 100 + 5
    = 1667.5 kcal/ngày
```

### 8.4. TDEE

```text
TDEE = BMR × hệ số vận động
```

**Đã chốt:** dùng bộ hệ số sau cho năm mức vận động. Đây là cấu hình sản phẩm được chủ dự án đồng ý, không phải bảng hệ số trích từ nghiên cứu Mifflin:

| Mức | Diễn giải trên form | Hệ số đã chốt |
| --- | --- | --- |
| `SEDENTARY` | Ít vận động | 1.20 |
| `LIGHT` | Vận động nhẹ | 1.375 |
| `MODERATE` | Vận động vừa | 1.55 |
| `ACTIVE` | Vận động nhiều | 1.725 |
| `VERY_ACTIVE` | Vận động rất nhiều | 1.90 |

**Ví dụ mức MODERATE:** `1667.5 × 1.55 = 2584.625 kcal/ngày`, hiển thị một chữ số thập phân thành `2584.6`.

BMR/TDEE không phải lượng calo của riêng buổi tập. Không tự cộng toàn bộ calo tập vào TDEE rồi gọi kết quả là nhu cầu chính xác; hệ số vận động có thể đã tính đến việc tập luyện.

### 8.5. Mục tiêu và lượng ăn mục tiêu

`goal` hiện chỉ cho `LOSE_WEIGHT`, `GAIN_MUSCLE`, `MAINTAIN`. Yêu cầu mới cần thêm tăng cân và tăng chiều cao; mã như `GAIN_WEIGHT`, `INCREASE_HEIGHT` chỉ là tên đề xuất cho Entity/enum.

Một mục tiêu dùng để điều hướng cách AI chọn bài và giải thích kế hoạch. TDEE vẫn được tính từ thể trạng và vận động; việc đổi mục tiêu không tự thay công thức TDEE.

**Đã chốt:** AI xác định `target_calories`, `target_protein_grams`, `target_carbs_grams`, `target_fat_grams` mỗi ngày và đưa lời khuyên từ đó. TDEE là mức nền ước tính; target là mục tiêu do kế hoạch cá nhân đề xuất, không dùng hai tên như một chỉ số.

Người dùng chỉ cần chọn mục tiêu tập luyện. Cân nặng đích có thể được nói trong chat; khi mục tiêu tăng cân/tăng cơ và còn thiếu thông tin này, AI hỏi người dùng rồi ghi nhận thông tin được cung cấp. Phải lưu vào dữ liệu có cấu trúc cùng nguồn/thời điểm, không chỉ trông chờ ngữ cảnh chat ngắn hạn nhớ mãi. AI không tự đặt cân nặng mong muốn thay người dùng.

AI đề xuất phân bổ và mục tiêu; backend kiểm tra dữ liệu, tính tổng thực đơn từ foods/gram và lưu kết quả. Không mặc định thâm hụt 500 kcal, tỷ lệ macro hoặc thời hạn cho mọi user. Dung sai giữa năng lượng thực đơn và target, cũng như giới hạn dinh dưỡng theo tuổi/hạn chế, cần quy tắc kiểm tra được xác định trước khi triển khai AI; chưa có bộ số đó được chốt.

### 8.6. Khi lưu/cập nhật hồ sơ

1. Kiểm tra đơn vị, các trường bắt buộc và giới hạn dữ liệu.
2. Tính tuổi theo ngày Việt Nam; tính lại BMR/TDEE bằng backend khi đủ điều kiện.
3. Lưu hồ sơ và chỉ số nhất quán trong cùng thao tác nghiệp vụ.
4. AI phân tích cập nhật; nếu có đề xuất mới thì thông báo, cho người dùng phê duyệt hoặc trao đổi chỉnh sửa.
5. Lịch cũ giữ hiệu lực cho đến khi thay đổi được chấp nhận; phiên bản mới tác động hiện tại/tương lai, không sửa kết quả đã ghi nhận.

**Đề xuất triển khai:** ghi phiên bản/thời điểm tính và làm mới chỉ số khi tuổi thay đổi, thay vì để số lưu trong DB cũ mãi đến lần chỉnh hồ sơ tiếp theo. Lưu lịch sử cân nặng/chiều cao nếu muốn biểu đồ tiến độ cơ thể; schema hiện chỉ có giá trị hiện tại.

### 8.7. Đơn vị và làm tròn

Backend thống nhất kg, cm, gram, phút/giây và kcal. Dùng kiểu số thập phân cho các trường tương ứng `numeric`; không lấy số đã làm tròn trên giao diện làm đầu vào tính tiếp.

**Đề xuất triển khai:** `BigDecimal`, chính sách làm tròn rõ ràng như `HALF_UP`, làm tròn tại điểm lưu/hiển thị. Precision/scale hiện có nằm ở mục 18; nếu cần giây cho plank thì không ép toàn bộ dữ liệu về phút chỉ có một chữ số thập phân.

### 8.8. Nội dung mục tiêu tăng chiều cao và giới hạn khẳng định

**Định hướng chủ dự án:** nhóm 10–22 tuổi hỗ trợ phát triển tự nhiên qua dinh dưỡng, vận động và ngủ; nhóm từ 23 tuổi tập trung tư thế và giải tỏa căng mỏi. Vì v1 chỉ phục vụ 15–65 tuổi, nhóm áp dụng thực tế là **15–22** và **23–65**, không tự mở đăng ký cho 10–14 tuổi.

| Nhóm | Phạm vi nội dung cần hỗ trợ | Cách diễn đạt trong sản phẩm |
| --- | --- | --- |
| 15–22 | Giáo dục dinh dưỡng đủ năng lượng/protein; vai trò canxi, D, K, kẽm, magiê; vận động phù hợp và thói quen ngủ | Hỗ trợ sức khỏe xương và thói quen phát triển; không suy ra còn sụn tăng trưởng chỉ từ tuổi |
| 23–65 | Vận động, tư thế, giãn cơ phù hợp với tình trạng/khả năng cá nhân | Hỗ trợ tư thế và sự thoải mái khi vận động; không cam kết tăng chiều dài xương hay tăng 1–3 cm |

Bóng rổ, bơi lội, nhảy dây, xà đơn, bài kháng lực phù hợp, Cobra/Child’s Pose là các nhóm hoạt động chủ dự án mong muốn AI cân nhắc. AI chỉ chọn mục có trong kho và phù hợp thông tin user; không mặc định mọi người phải treo xà hoặc tự điều trị gù/võng lưng/lệch xương chậu. Lời khai chấn thương không tương đương chẩn đoán của hệ thống.

**Chỉnh lý thông điệp y khoa:** không đưa nguyên văn cam kết “lấy lại 1–3 cm” vào giao diện như kết quả chắc chắn. Tình trạng trưởng thành của xương khác nhau giữa cá nhân; số tuổi phân nhóm không phải kết quả kiểm tra sụn tăng trưởng. [AAOS về xương và phát triển theo tuổi](https://www.orthoinfo.org/staying-healthy/healthy-bones-at-every-age/), [NIDDK về GH và sụn tăng trưởng](https://www.niddk.nih.gov/health-information/endocrine-diseases/acromegaly).

Chủ dự án nêu nhắc ngủ 22:00–02:00. Có thể ghi nhận mong muốn xây thói quen ngủ đều, nhưng không khẳng định GH chỉ/cao nhất ở một khung giờ cố định cho mọi người: nghiên cứu ghi nhận đỉnh GH liên quan lúc bắt đầu ngủ sâu và dịch khi giờ ngủ dịch. Nội dung thay thế đề xuất: “Duy trì giờ ngủ đều đặn và ngủ đủ theo độ tuổi để hỗ trợ sức khỏe và phát triển.” [Nghiên cứu về GH trong giấc ngủ](https://www.jci.org/articles/view/105893), [NHLBI về vai trò của giấc ngủ](https://www.nhlbi.nih.gov/health/sleep/why-sleep-important).

Kho foods hiện chỉ có kcal/protein/carbs/fat, chưa có hàm lượng vi chất. AI có thể giải thích khái niệm, nhưng không được tuyên bố đã cộng đủ canxi/D/K/kẽm/magiê nếu không có dữ liệu và bộ nhu cầu phù hợp. Không tự kê liều bổ sung vitamin, khoáng chất hay hormone. Nội dung tăng chiều cao và giới hạn dinh dưỡng cho 15–17 tuổi cần được rà soát chuyên môn trước khi đưa vào dùng thực tế.

## 9. Nhật ký dinh dưỡng

### 9.1. Kho thực phẩm

Admin tạo thực phẩm với tên, calo và protein/carbs/fat trên 100 gram. User tìm trong kho này khi ghi ăn. Các chỉ số phải cùng quy ước thực phẩm (sống/chín, phần ăn được…) để số liệu có ý nghĩa; quy tắc quản trị nguồn dữ liệu cần thống nhất khi seed.

Không để AI tự tạo một `food_id` không tồn tại rồi lưu như món chuẩn. Tính năng user tự thêm món riêng hoặc nhận diện món từ ảnh chưa được yêu cầu.

### 9.2. Ghi một món

1. Mở nhật ký hôm nay theo giờ Việt Nam và chọn bữa: `BREAKFAST` (sáng), `LUNCH` (trưa), `AFTERNOON` (chiều), `DINNER` (tối). Ngày quá khứ chỉ để xem.
2. Chọn thực phẩm, nhập số gram thực tế.
3. Backend tìm hoặc tạo `daily_meal_logs` duy nhất cho `(user_id, log_date)`.
4. Backend đọc thông số thực phẩm, tính calo/macro, lưu `meal_details` và `logged_at`.
5. Trả món đã ghi cùng tổng bữa/ngày; kiểm tra điều kiện huy hiệu và cảnh báo tương ứng.

`logged_at` là mốc ghi nhận. **Đã chốt:** nhật ký ăn hôm nay được thêm/sửa/xóa; khi sang 00:00 giờ Việt Nam, ngày vừa kết thúc trở thành lịch sử chỉ đọc. Không nhập bù hoặc sửa/xóa ngày đã chốt. “Sửa món cũ” trong quy tắc cảnh báo là chỉnh một món đã ghi **trong ngày hiện tại**, không sửa nhật ký của ngày quá khứ.

### 9.3. Công thức và ví dụ

```text
calo món = calories_per_100g × weight_grams / 100
protein món = protein_per_100g × weight_grams / 100
carbs món = carbs_per_100g × weight_grams / 100
fat món = fat_per_100g × weight_grams / 100
tổng bữa/ngày = tổng chỉ số các món thuộc bữa/ngày đó
```

**Ví dụ minh họa:** thực phẩm A có 165 kcal, 31 g protein, 0 g carbs, 3.6 g fat/100 g. Ghi 200 g sẽ được 330 kcal, 62 g protein, 0 g carbs, 7.2 g fat. Đây là bộ số minh họa công thức, không phải dữ liệu dinh dưỡng đã seed.

Backend tính `calculated_calories`; request từ trình duyệt không được quyết định calo bằng số tự truyền lên. Protein/carbs/fat hiện chưa được snapshot trong `meal_details`; xem vấn đề lịch sử ở mục 19.

### 9.4. Sửa/xóa món và chuyển ngày

**Đã chốt:** chỉ sửa/xóa món thuộc ngày hiện tại ở Việt Nam. Backend xác minh chủ sở hữu và ngày nghiệp vụ tại lúc ghi nhận thay đổi, không chỉ dựa nút sửa trên frontend. Thao tác tính lại tổng và đánh dấu cảnh báo không còn áp dụng nếu điều kiện đã hết. Huy hiệu đã nhận giữ vĩnh viễn; dữ liệu lịch sử và chỉ số snapshot không bị Admin sửa thực phẩm làm thay đổi.

Nếu mở form trước 00:00 nhưng gửi lưu sau khi ngày đã kết thúc, backend từ chối sửa ngày cũ và yêu cầu tải lại nhật ký. Quy tắc khóa ngày không phụ thuộc scheduler có chạy đúng lúc hay không.

**Reset nhật ký calo lúc 00:00 nghĩa là chuyển dashboard sang ngày mới**, không xóa dữ liệu hôm trước và không sửa các tổng lịch sử thành 0. Ngày mới chưa ghi món có tổng ghi nhận bằng 0; các ngày cũ vẫn tra cứu được.

### 9.5. Cảnh báo vượt calo

Quy tắc từ trao đổi nguồn vẫn được ghi nhận: người có mục tiêu `LOSE_WEIGHT` và tổng calo ngày vượt TDEE nhận `CALORIE_ALERT`. Yêu cầu mới bổ sung cảnh báo thừa/thiếu theo mục tiêu dinh dưỡng AI xác định; backend xét điều kiện, nội dung có thể dùng mẫu hoặc do AI viết. Ngưỡng/dung sai thiếu, thời điểm chốt thiếu và việc dùng target hay TDEE cho từng trường hợp cần hoàn thiện; không báo thiếu ngay buổi sáng chỉ vì chưa ghi đủ lượng cả ngày.

**Ví dụ:** TDEE 2000 kcal, tổng đã ghi 2150 kcal → vượt 150 kcal. Không suy ra từ chênh lệch này rằng người dùng phải tập thêm một số phút cụ thể khi chưa có quy tắc tư vấn được chốt.

**Đã chốt:** cảnh báo thừa/thiếu calo và bài tập tối đa một lần/ngày cho hoàn cảnh tương ứng. Khi chỉnh món được phép làm điều kiện không còn đúng, đánh dấu cảnh báo **không còn áp dụng**, không đồng nhất trạng thái này với đã đọc và không xóa dấu đã gửi để tạo lại nhiều lần trong ngày. Dấu chống trùng phải được lưu cùng dữ liệu sự kiện.

## 10. AI xây dựng và người dùng chấp nhận lịch tập

### 10.1. Dữ liệu đầu vào và giới hạn trách nhiệm

**Đã chốt:** AI phân tích thể trạng và mục tiêu để chọn bài tập trong `exercises`, thiết kế lịch các ngày trong tuần. AI không được bịa ra bài tập như thể chúng đã tồn tại trong kho.

Backend cung cấp hồ sơ hợp lệ, mục tiêu, chỉ số đã tính, danh sách bài tập ứng viên cùng category, MET, ngưỡng tối thiểu, sets/reps và loại đo. Khi đã có lịch sử, có thể cung cấp dữ liệu tập/ăn phù hợp với yêu cầu phân tích. Tránh gửi mật khẩu, token hay thông tin kết nối cho AI.

**Đã chốt:** bổ sung tất cả đầu vào thiết bị/địa điểm, kinh nghiệm, thời gian/số ngày có thể tập, chấn thương/hạn chế vận động. Chưa có trong backup nên phải bổ sung mô hình và màn hình/chat thu thập. Nếu thiếu hoặc nội dung mơ hồ, AI hỏi lại thay vì tự suy ra.

### 10.2. Luồng lập lịch

```text
Hồ sơ hợp lệ + một mục tiêu + kho bài tập
    → AI tự đề xuất hoặc user yêu cầu/chỉnh qua chat
    → Backend chuẩn bị yêu cầu
    → Gemini đề xuất lịch có cấu trúc
    → Backend kiểm tra exercise_id, ngày, chỉ tiêu và tính đầy đủ
    → Lưu đề xuất WORKOUT_PLAN chưa áp dụng
    → Người dùng xem lịch và giải thích
    → Đồng ý: lưu lịch cá nhân được chấp nhận
    → Dashboard/lịch tuần sử dụng lịch có hiệu lực
```

**Đề xuất triển khai:** phản hồi có cấu trúc gồm các ngày, loại ngày tập/nghỉ, `exercise_id`, thứ tự bài và chỉ tiêu theo loại bài. Không chỉ lưu một đoạn văn rồi cố đoán lịch từ câu chữ mỗi lần mở dashboard.

Nếu bài không tồn tại/đã ngừng hoạt động, chỉ tiêu sai loại, thiếu ngày hoặc AI không phản hồi: không kích hoạt lịch lỗi. Hiển thị trạng thái và cho thử lại. Khi kho bài tập trống, yêu cầu Admin bổ sung; không sinh ID giả.

Lịch giúp AI biết bài dự kiến, bài đã tập và bài còn thiếu để nhắc. Không có lịch thì danh sách user chọn trong phiên là căn cứ theo dõi. Bỏ một bài trong danh sách không làm mất check-in nếu đã hoàn thành hợp lệ một bài khác trong ngày.

### 10.3. Cờ thứ trong tuần thuộc về ai?

Ví dụ người A tập Squat vào thứ hai, người B tập cùng bài vào thứ tư. Bản ghi Squat trong `exercises` là dữ liệu chuẩn dùng chung; không thể đặt `flagMonday=true` trên đó rồi coi như lịch của mọi người.

Yêu cầu cờ Monday/Tuesday… được hiểu là **bài tập nào được gán cho ngày nào trong lịch của một người dùng đã chấp nhận**. Có thể thể hiện trên giao diện dưới dạng cờ, checkbox hoặc ô lịch.

**Đề xuất mô hình lưu trữ:** lịch cá nhân → các ngày của lịch → các bài của từng ngày. Trường `day_of_week` biểu diễn thứ; nếu giao diện cần bảy cờ, backend chuyển các dòng lịch thành cờ tương ứng. Cách này cũng cho phép cùng bài xuất hiện ở nhiều ngày với chỉ tiêu khác nhau. Tên bảng cụ thể chưa phải quyết định của chủ dự án.

### 10.4. Phân biệt ba loại dữ liệu

| Loại | Ý nghĩa | Có tạo check-in không? |
| --- | --- | --- |
| Kho bài tập | Hướng dẫn và thông số chuẩn | Không |
| Kế hoạch đã chấp nhận | Dự kiến tập gì, ngày nào nghỉ | Không; ngày nghỉ chỉ bảo toàn streak |
| Kết quả thực hiện | Bài nào đã làm hợp lệ, làm khi nào | Có thể tạo check-in theo mục 12 |

Một `WORKOUT_PLAN` là đề xuất/kế hoạch. Một `workout_session` là buổi thực tế. Chấp nhận kế hoạch không tự tạo các buổi đã hoàn thành.

`ai_recommended_exercises` hiện chỉ nối recommendation với exercise. Nó chưa lưu thứ trong tuần, số thứ tự, chỉ tiêu cá nhân, ngày nghỉ hay trạng thái chấp nhận. Cần bổ sung mô hình lịch, không coi bảng nối hiện tại là đủ.

### 10.5. Đồng ý, thay lịch và ngày nghỉ

- Đọc lịch chưa đồng nghĩa đồng ý. User có thể phê duyệt/đổi từng bài và lịch qua chat; backend chuyển yêu cầu rõ ràng thành thay đổi có cấu trúc, không chỉ sửa đoạn văn.
- Ngày nghỉ được bảo toàn streak phải thuộc lịch AI **đã được người dùng chấp nhận và đang có hiệu lực**.
- Không coi ngày thiếu bài do dữ liệu lỗi hoặc lịch chưa có là ngày nghỉ hợp lệ.
- Lịch mới không được tự sửa kết quả tập đã ghi nhận.

**Đã chốt:** lịch lặp hằng tuần đến khi user yêu cầu thay đổi. AI tự đề xuất hoặc đáp ứng yêu cầu; user trao đổi để sửa từng bài/ngày. Khi thể trạng/mục tiêu đổi, AI thông báo đề xuất mới để user phê duyệt/chỉnh, không âm thầm thay lịch.

Thay đổi được xác nhận có hiệu lực ngay cho hiện tại/tương lai. **Đã chốt khung giờ nên tập:** 05:00–08:00, 15:00–18:00, 19:00–21:00 theo `Asia/Ho_Chi_Minh`. Ngoài khung này AI chỉ nhắc về thời điểm tập, **không khóa chức năng, không chặn bắt đầu/tiếp tục tập và không làm kết quả mất điều kiện check-in chỉ vì ngoài giờ**. Các khung giờ là lời khuyên sản phẩm, không phải kiểm định rằng mọi người đều phù hợp cùng thời điểm tập.

Người dùng vẫn chỉnh lịch qua AI ngoài khung giờ; lịch không bị treo chờ giờ tập mới có hiệu lực. AI ưu tiên các khung trên khi gợi ý thời điểm, đồng thời xét lịch rảnh người dùng. Không lấy yêu cầu sửa lịch làm check-in, không sửa dữ liệu ngày đã chốt.

**Thay riêng một ngày:** “Hôm nay tôi muốn chạy bộ” tạo điều chỉnh cho ngày hôm nay; không tự đổi mọi ngày cùng thứ của các tuần sau. “Từ nay đổi thứ hai sang chạy bộ” cập nhật lịch lặp. AI hỏi lại khi không rõ tạm thời hay lâu dài.

**Đề xuất triển khai:** lịch gốc có phiên bản; bảng điều chỉnh theo ngày lưu `specific_date`, nguồn yêu cầu, mốc hiệu lực và nội dung thay thế. User chỉ chấp nhận phần được sửa là đủ để áp dụng phần đó; không bắt làm lại toàn bộ lịch đã chấp nhận. Chính sách đổi ngày hôm nay thành ngày nghỉ khi đã bỏ buổi cần chốt để tránh miễn hồi tố.

**Chưa có lịch:** theo phương án B đã chốt, user được chọn bài tự do, hoàn thành một bài hợp lệ để check-in. Mọi ngày không có ngày nghỉ hợp lệ đều là ngày phải tập; danh sách bài chọn trong phiên được lưu để AI nhắc bài chưa hoàn thành. Chỉ đăng nhập/chọn bài vẫn không được check-in.

**Ví dụ minh họa:** lịch được chấp nhận có thứ hai tập A/B, thứ ba nghỉ, thứ tư tập C. Thứ ba không có check-in vẫn giữ chuỗi. Thứ tư bắt đầu C rồi kết thúc ngay không được check-in, nên bị tính bỏ lỡ khi ngày kết thúc. Đây không phải lịch tập khuyến nghị cho một người cụ thể.

## 11. Thực hiện bài tập và ghi nhận kết quả

### 11.1. Phân biệt buổi và lần thực hiện một bài

Một buổi tập (`workout_sessions`) có thể chứa nhiều bài. Mỗi kết quả bài nằm trong `workout_details`. Để kiểm tra thời gian tối thiểu của **bài đó**, cần biết mốc bắt đầu, các khoảng tập/nghỉ được tính và pause/đóng băng của lần thực hiện bài, không chỉ giờ bắt đầu chung của buổi.

**Ví dụ:** buổi bắt đầu 18:00, bài B bắt đầu 18:20. Không được lấy 20 phút trước 18:20 làm thời gian người dùng đã thực hiện B.

### 11.2. Luồng sử dụng và xác nhận

1. Người dùng xem lịch hoặc danh sách bài có thể chọn; xem video và chỉ tiêu.
2. Chọn một hoặc nhiều bài; backend lưu danh sách lựa chọn cho buổi.
3. Bấm bắt đầu bài; backend ghi mốc bắt đầu và trả thông tin đồng hồ/chỉ tiêu.
4. Với bài theo cấp độ: user chọn cấp độ riêng lúc thêm bài; timer tự tập set → nghỉ → set mới theo cấu hình Admin đã lưu cho lần tập. Với bài thủ công cũ: giữ cơ chế nghỉ được user duyệt và tạm dừng/tiếp tục.
5. Khi bấm hoàn thành, backend kiểm tra người sở hữu, bài đã chọn, trạng thái và thời gian tối thiểu được ghi nhận; sets/reps/quãng đường là dữ liệu tự khai phục vụ theo dõi, không phải bằng chứng bắt buộc.
6. Hợp lệ: lưu kết quả và calo; nếu đây là bài hợp lệ đầu tiên của ngày thì ghi check-in, cập nhật streak.
7. Bài theo cấp độ có nút kết thúc sớm từ 2/3 thời gian dự kiến, đổi thành Kết thúc & bài tiếp theo khi đạt mốc đầy đủ; cả hai chỉ chọn bài kế tiếp sau khi lưu thành công, chờ user bấm bắt đầu. Bài tiếp theo không tăng streak lần nữa trong cùng ngày.
8. Khi kết thúc buổi: có ít nhất một bài hợp lệ thì buổi có kết quả được công nhận; không có bài nào hợp lệ thì không tạo check-in.

**Đề xuất trạng thái:** buổi đang tập/tạm dừng/đóng băng/kết thúc; bài chưa bắt đầu/đang tập/đang nghỉ/tạm dừng/đóng băng/hoàn thành. Kết thúc có ít nhất một bài hợp lệ có thể ánh xạ `COMPLETED`, kết thúc không có bài hợp lệ ánh xạ `CANCELLED`; schema hiện tại cần mở rộng trạng thái khi cần. Không yêu cầu hoàn thành tất cả bài để check-in.

**Đã chốt:** đóng trình duyệt khi chưa kết thúc thì giữ buổi ở trạng thái đóng băng, không tự hủy và không cộng thời gian vắng mặt. Khi quay lại, cho tiếp tục đúng buổi và hiển thị bài còn thiếu. Nhắc việc chưa hoàn thành là chức năng trong website/khi có kết nối; chưa yêu cầu push ngoài trình duyệt.

### 11.3. Điều kiện theo loại bài

| Loại đo — tên đề xuất | Dữ liệu cần ghi | Điều kiện cần hỗ trợ |
| --- | --- | --- |
| Reps/kháng lực | Thời gian, sets, reps mỗi hiệp, mức tạ kg | Đủ thời gian tối thiểu; sets/reps là chỉ tiêu và số tự ghi, không chặn check-in |
| Chạy bộ | Thời gian chạy, nghỉ, quãng đường, mục tiêu đã chọn | User chọn thời gian hoặc quãng đường làm mốc; đại lượng còn lại để đánh giá; vẫn phải qua thời gian tối thiểu |
| Giữ tư thế như plank | Thời gian giữ mục tiêu/thực tế, số hiệp, thời gian nghỉ | Chỉ chọn thời gian giữ tư thế làm mốc; vẫn phải qua thời gian tối thiểu gồm nghỉ, loại pause/đóng băng |

Schema hiện buộc mọi bài có `recommended_reps>0` và mọi kết quả có `reps>0`. Cần đổi sang kiểm tra tùy loại, không nhập giả “1 rep” cho chạy bộ/plank chỉ để vượt constraint.

**Đã chốt:** reps là mỗi hiệp. Ví dụ 3 sets × 10 reps nghĩa là 3 hiệp, mỗi hiệp 10 lần; tổng 30 lần là chỉ số suy ra. Schema có một cột reps nên chưa thể mô tả chính xác trường hợp từng hiệp lần lượt 10/9/8; đề xuất bổ sung chi tiết hiệp nếu cần giữ dữ liệu đó.

### 11.4. Thời gian hoạt động và thời gian nghỉ

Phân biệt thời gian từ bắt đầu đến kết thúc theo đồng hồ, thời gian tập được theo dõi, thời gian nghỉ trong bài và thời gian pause/đóng băng. Hệ thống không có cảm biến chứng minh vận động; trường thời gian tập phản ánh trạng thái timer được ghi nhận. Chạy bộ/plank lưu riêng hoạt động và nghỉ.

```text
counted_duration = thời gian tập theo timer + nghỉ trong bài được ghi nhận
excluded_duration = tạm dừng + đóng băng + khoảng không được server công nhận
check-in bài hợp lệ khi counted_duration >= min_duration_minutes
```

Không lấy trực tiếp `now − start_time` khi có pause/đóng trình duyệt. Thời gian nghỉ AI đề xuất và user phê duyệt được tính trong bài; “nghỉ giữa hiệp” khác “tạm dừng buổi”.

**Ví dụ chạy bộ:** chạy 5 phút + nghỉ 1 phút + chạy 5 phút → active 10 phút, nghỉ 1 phút, elapsed 11 phút. Quãng đường là tổng các chặng chạy; nếu có thông số tốc độ, tính từ quãng đường/thời gian thực chạy.

**Ví dụ plank:** giữ 30 giây × 3 hiệp, nghỉ 30 giây giữa hai lần chuyển hiệp → active 90 giây, nghỉ 60 giây, elapsed 150 giây.

User có thể đặt mục tiêu giữ tư thế 90 giây; số hiệp mô tả cách thực hiện, không phải một chế độ mục tiêu thay cho thời gian. Thời gian giữ phục vụ tiến độ mục tiêu; thời gian được tính để chặn check-in vẫn gồm nghỉ trong bài như quy tắc chung. Đủ ngưỡng check-in không tự chứng minh đã giữ tư thế đủ mục tiêu.

**Ví dụ ngưỡng:** bài cần 10 phút; đã tập 6 phút, nghỉ trong bài 2 phút, pause 5 phút → mới tính 8 phút, chưa đủ; cần thêm 2 phút được ghi nhận. Thời gian đóng băng không được bù khi mở lại.

**Đã chốt:** tạm dừng không cộng thời gian; tải lại có thể khôi phục; mất mạng không tự công nhận hoàn thành; một buổi và một bài đang thực hiện/user trên mọi thiết bị. Server dùng các khoảng timer đã xác nhận. **Đề xuất kỹ thuật:** heartbeat/lease và mốc ghi nhận cuối giúp phát hiện tab đóng/mất kết nối; không thể khẳng định phát hiện chính xác tức thì chỉ bằng sự kiện đóng trang. Chu kỳ heartbeat và dung sai là cấu hình triển khai cần kiểm thử, không phải số đã được user chốt.

### 11.5. Chống check-in ảo theo đúng phạm vi

Mục tiêu là chặn chọn bài rồi bấm kết thúc ngay để lấy check-in. Backend từ chối khi bài chưa bắt đầu hợp lệ hoặc chưa đủ thời gian được tính. **Không dùng số hiệp/reps/mức tạ/quãng đường tự khai làm điều kiện chứng minh đã tập để cấp check-in.** Các mục tiêu đó vẫn được hiển thị, ghi nhận và phân tích.

Frontend đếm ngược chỉ là hiển thị. Thời gian chặn phải được xác minh ở server; sửa JS hoặc tự gửi request “đã tập đủ 10 phút” không được bỏ qua kiểm tra. Request lặp lại không được tạo hai kết quả/check-in cho cùng lần hoàn thành.

Đây không phải tính năng camera/GPS/cảm biến xác minh động tác. Sets/reps và quãng đường tự khai chưa chứng minh người dùng thật sự tập; không mô tả hệ thống như đã có khả năng nhận diện gian lận thể chất.

### 11.6. Calo tiêu hao theo MET

Định hướng công thức trong trao đổi nguồn:

```text
calories_burned = MET × 3.5 × body_weight_kg / 200 × active_minutes
```

MET biểu diễn mức tiêu hao tương đối so với nghỉ; quy ước 1 MET gắn với 3.5 ml oxy/kg/phút. Đây là ước tính dựa trên mức hoạt động, không phải phép đo cá nhân. [Compendium of Physical Activities](https://pacompendium.com/).

**Ví dụ minh họa:** MET 6, cân nặng cơ thể 70 kg, active 10 phút → `6 × 3.5 × 70 / 200 × 10 = 73.5 kcal`. MET 6 chỉ là đầu vào minh họa, không mặc định cho mọi bài chạy hay plank.

- Dùng cân nặng **cơ thể**, không dùng `workout_details.weight_kg` vốn là mức tạ.
- Không tính toàn bộ thời gian nghỉ bằng MET của vận động chính. Cách cộng năng lượng nghỉ và cách tính thời gian cho bài kháng lực còn cần chốt.
- Kết quả công thức trên là năng lượng ước tính trong hoạt động; nếu muốn hiển thị phần vượt mức nghỉ phải định nghĩa chỉ số riêng, không âm thầm đổi sang `(MET − 1)`.
- Không quy đổi “tập đủ bài” thành calo cố định không phụ thuộc thời gian/cân nặng.

**Đã chốt:** snapshot MET/cân nặng và thông số tính tại lúc ghi kết quả; thay thông số hiện tại không làm số lịch sử đổi. Hoàn thành bài nào thì ghi calo bài đó ngay, không chờ hoàn thành cả buổi. Chỉ đủ thời gian chặn check-in không có nghĩa đã đạt mọi mục tiêu thời gian/quãng đường tự chọn; giao diện cần phân biệt “được công nhận” với “đạt mục tiêu kế hoạch”.

### 11.7. Cấp độ động và chu kỳ tự động — chốt ngày 02/11/2026

Admin tạo/đặt tên cấp độ tùy ý cho từng bài, cấu hình số set, reps/set (được trống cho bài thời gian), số giây/set, giây nghỉ và thời gian hoàn thành dự kiến. User chọn cấp độ riêng cho từng bài khi thêm vào buổi, không dùng buổi mẫu.

Thời gian dự kiến do Admin nhập riêng, tính tập + nghỉ. Tối thiểu kết thúc sớm là `ceil(thời_gian_dự_kiến_ms × 2 / 3)` tới millisecond. Đạt mốc đầy đủ chỉ đổi nhãn nút, không tự công nhận bài. Hết set cuối vẫn đếm active trên set cuối, không tạo thêm set/nghỉ. Chỉ khi user bấm kết thúc và backend xác nhận mới gọi nghiệp vụ check-in hiện có. Cả kết thúc sớm và đầy đủ chuyển bài tiếp ở trạng thái chưa bắt đầu; bài cuối hoàn thành rồi user đóng buổi như cũ.

Heartbeat phải tách active/rest tại ranh giới; calo chỉ active. Offline quá timeout không tự tập tiếp: nghỉ tới hạn rồi chờ recover, không tính bù active. Quyết định bổ sung ngày 02/11/2026: khi user tải lại/mở lại trang có bài dở, frontend tự recover một lần; Back/forward cache cũng tự khôi phục. Pause vẫn giữ pause. Chỉ đổi tab/polling hoặc bị thu hồi token không tự recover để tránh các tab giành quyền lẫn nhau. Cấu hình cấp độ và media được snapshot cho lần chọn; chỉnh dữ liệu chuẩn không đổi lịch sử. Bài cũ chưa có cấp độ tiếp tục dùng luồng thủ công; không tự backfill kế hoạch giả.

Admin có thể tải GIF/MP4/WebM cho bài. Binary lưu tại thư mục bền vững của máy chủ ứng dụng, Neon lưu đường dẫn; sao lưu cả hai. Chỉ Admin upload, người đăng nhập được xem, kiểm tra loại/dung lượng/đường dẫn, không ghi đè tệp cũ còn được snapshot tham chiếu. Chi tiết API, cách sử dụng và cấu hình tại [WORKOUT_AUTOMATION.md](docs/WORKOUT_AUTOMATION.md).

## 12. Check-in, ngày nghỉ và streak

### 12.1. Các khái niệm

| Khái niệm | Định nghĩa |
| --- | --- |
| Check-in hợp lệ | Ít nhất một bài được backend công nhận hoàn thành trong ngày Việt Nam. |
| Ngày phải tập | Ngày không có ngoại lệ nghỉ hợp lệ; chưa có lịch vẫn được tập tự chọn và ngày đó được coi là ngày phải tập. |
| Ngày nghỉ hợp lệ | Ngày nghỉ được xác định trong lịch AI đã chấp nhận và có hiệu lực cho ngày đó. |
| `current_streak` | Số ngày check-in trong chuỗi hiện tại; ngày nghỉ hợp lệ không tăng và không cắt chuỗi. |
| `highest_streak` | Giá trị cao nhất từng đạt của `current_streak`. |
| Ngày bỏ lỡ | Ngày phải tập kết thúc mà không có check-in hợp lệ. |

Hệ quả của quy tắc nghỉ: streak 7 nghĩa là 7 ngày check-in hợp lệ trong một chuỗi không bị ngày phải tập bỏ lỡ cắt đứt; chuỗi có thể trải qua hơn 7 ngày lịch vì có ngày nghỉ.

### 12.2. Xử lý trong ngày

- Chưa có bài hợp lệ: chưa check-in; không tăng streak.
- Bài hợp lệ đầu tiên của ngày: tạo một check-in và tăng streak một đơn vị sau khi đối soát các ngày trước.
- Bài thứ hai, buổi thứ hai hoặc request gửi lại trong ngày: không tăng streak thêm.
- Cập nhật `highest_streak = max(highest_streak, current_streak)` sau tăng.
- Không dùng “đăng nhập”, “chọn bài”, “bắt đầu buổi” hoặc “chấp nhận lịch” thay check-in.

**Đã chốt:** ngày nghỉ vẫn tập và hoàn thành bài hợp lệ thì tăng streak 1; không tập thì giữ nguyên. Mỗi ngày tối đa một lần tăng, dù tập theo lịch hay tự chọn.

### 12.3. Chốt ngày lúc 00:00 giờ Việt Nam

Khi sang ngày `D`, xử lý ngày vừa kết thúc `D−1`:

| Trạng thái ngày `D−1` | Streak | Đếm bỏ tập |
| --- | --- | --- |
| Có check-in hợp lệ | Giữ số đã tăng trong ngày | Về 0 |
| Nghỉ hợp lệ, không check-in | Giữ nguyên | Bỏ qua ngày này, giữ số đang đếm |
| Phải tập, không check-in | Reset về 0 | Tăng 1 |

“Reset lúc 00:00” không có nghĩa reset tất cả mọi người vào đầu mỗi ngày. Chỉ reset khi một ngày **phải tập đã kết thúc** mà chưa check-in. Sau lần bỏ lỡ, lần check-in hợp lệ kế tiếp bắt đầu lại từ 1.

**Ví dụ:** thứ hai check-in, streak=6; thứ ba nghỉ → vẫn 6; thứ tư chưa tập lúc 09:00 → vẫn 6 vì ngày chưa hết; nếu hết thứ tư vẫn không check-in, 00:00 thứ năm streak=0; thứ năm tập hợp lệ → streak=1. `highest_streak` vẫn giữ kỷ lục cũ.

### 12.4. Nhắc nhở bỏ tập

Đếm các ngày phải tập liên tiếp không có check-in. Bỏ qua ngày nghỉ hợp lệ; một ngày có check-in hợp lệ đưa bộ đếm bỏ lỡ về 0.

**Đã chốt ngưỡng 3:** thứ hai bỏ tập (1), thứ ba nghỉ (vẫn 1), thứ tư bỏ tập (2), thứ năm nghỉ (vẫn 2), thứ sáu bỏ tập (3) → đến ngưỡng sau khi chốt thứ sáu, lúc 00:00 thứ bảy. Nếu thứ bảy là ngày phải tập thì được nhắc; nếu thứ bảy là ngày nghỉ hợp lệ thì bỏ qua và nhắc vào ngày phải tập tiếp theo. Ngày nghỉ không tăng bộ đếm.

**Đã chốt:** sau 3 ngày phải tập bị bỏ lỡ, nhắc mỗi ngày phải tập tiếp theo khi vẫn chưa có check-in; tối đa một lần/ngày, bỏ qua ngày nghỉ hợp lệ. Một check-in hợp lệ đặt bộ đếm bỏ lỡ về 0 và kết thúc đợt nhắc. User chưa có lịch dùng mặc định ngày phải tập. Tài khoản khóa không được thực hiện bài; chính sách bảo toàn chuỗi trong thời gian khóa chưa chốt.

### 12.5. Chúc mừng và chống trùng

Điều kiện mốc:

```text
streak > 0 AND (
    streak chia hết cho 7
    OR streak thuộc {30, 60, 90, 100, 365}
)
```

Chỉ đánh giá khi streak tăng do check-in. Ngày nghỉ giữ nguyên 7 không được mỗi ngày gửi lại chúc mừng 7. Một ngày tập nhiều bài không được gửi lại cùng mốc.

**Đã chốt:** mỗi mốc chỉ chúc mừng một lần trong đời tài khoản. Đã chúc mừng 7 rồi chuỗi đứt, khi đạt lại 7 không gửi lại; lần đầu đạt 14 vẫn được chúc mừng nếu mốc 14 chưa từng gửi. Ngày nghỉ hoặc nhiều bài trong ngày không gửi trùng.

**Đề xuất triển khai:** lưu dấu bền vững với UNIQUE `(user_id, milestone_value)`, không đặt theo chuỗi; kiểm tra và ghi dấu cùng việc tạo thông báo. Xóa tin chat/đọc thông báo hay reset streak không xóa dấu đã chúc mừng. Danh sách mốc chúc mừng khác điều kiện cấp Badge; không tự tạo huy hiệu cho mọi mốc.

### 12.6. Những điều kiện kỹ thuật bắt buộc

- Không tính chuỗi chỉ bằng `today − last_check_in_date`: cách đó không hiểu ngày nghỉ ở giữa.
- Cần lịch sử ngày với lịch áp dụng, nghĩa vụ tập, check-in và trạng thái đã chốt.
- Mỗi user/ngày chỉ có một check-in; xử lý đồng thời nhiều tab phải không tăng hai lần.
- Job chạy lại phải cho cùng kết quả, không reset/tăng/nhắc lần nữa do retry.
- Nếu server tắt lúc 00:00, lúc chạy lại phải đối soát các ngày còn thiếu; khi đọc dashboard cũng không hiển thị streak cũ sai.
- Theo quy tắc phải hoàn thành trước 00:00 và không sửa lịch sử: bài bắt đầu 23:55, hoàn thành 00:05 chỉ được check-in cho ngày hoàn thành; ngày trước đã chốt không được bù. Buổi có thể tiếp tục qua ngày nhưng kết quả từng bài mang ngày công nhận thực tế.
- Đổi lịch không được âm thầm biến ngày bỏ lỡ quá khứ thành ngày nghỉ; chính sách hiệu lực phải rõ ràng.

## 13. Hệ thống huy hiệu

### 13.1. Cơ chế Data-Driven đã chốt

Một huy hiệu có tên, mô tả, icon, loại chỉ số cần xét (`metric_type`) và ngưỡng (`threshold_value`). Backend đo chỉ số tương ứng của user và so sánh `giá trị >= ngưỡng`. Khi đạt, tạo `user_badges` với thời điểm nhận.

**Ví dụ minh họa:** Admin tạo huy hiệu “Duy trì 7 ngày” với `STREAK_DAYS`, ngưỡng 7. User có streak 7 được cấp. Admin tạo thêm mốc 14 bằng một dòng dữ liệu mới cùng metric; không phải thêm câu `if` riêng cho tên huy hiệu đó.

### 13.2. Các metric hiện có và cách hiểu

| Metric | Giá trị cần đo | Chi tiết cần giữ rõ |
| --- | --- | --- |
| `STREAK_DAYS` | `current_streak` khi xét sự kiện | Ngày nghỉ không tập không cộng; xét sau check-in; cấp bù dùng kỷ lục đã từng đạt. |
| `TOTAL_WORKOUTS` | Tổng buổi đã `COMPLETED` | Một buổi có nhiều bài vẫn là một buổi; không đếm lần bấm bắt đầu. |
| `TOTAL_CALORIES_BURNED` | Tổng calo bài đã hoàn thành hợp lệ | Tính ngay theo bài, kể cả buổi vẫn đang mở/đóng băng. |
| `MAX_WEIGHT_LIFTED` | Mức tạ lớn nhất trong kết quả hợp lệ | Mức tạ tập, không phải cân nặng cơ thể hay tổng sets × reps × tạ. |
| `TOTAL_MEAL_LOGS` | Số ngày có ít nhất một món ăn hợp lệ | Không đếm log rỗng; xóa món cuối trong phạm vi cho phép thì ngày đó không còn đóng góp metric. |

Backend vẫn cần biết cách tính **từng loại metric**. Data-Driven ở đây là thêm/sửa huy hiệu dùng các metric đã hỗ trợ không sửa code Java. Thêm một loại metric hoàn toàn mới vẫn cần bổ sung logic và các ràng buộc tương ứng.

### 13.3. Luồng cấp huy hiệu

1. Sau sự kiện liên quan, lấy các badge user chưa nhận.
2. Nhóm theo metric để tránh truy vấn lại một chỉ số nhiều lần.
3. Tính chỉ số từ dữ liệu hợp lệ, so sánh với từng ngưỡng.
4. Đạt ngưỡng thì lưu `user_badges`; khóa `(user_id, badge_id)` ngăn cấp trùng.
5. Giao diện cập nhật danh sách huy hiệu và điều kiện đã đạt.

Các sự kiện đề xuất: check-in, hoàn thành buổi, lưu kết quả bài, ghi/sửa món. Sự kiện kiểm tra phải chạy sau khi dữ liệu nền đã lưu thành công. Không cấp huy hiệu cho thao tác sau đó bị rollback.

**Đã chốt:** huy hiệu đã nhận giữ vĩnh viễn; thay điều kiện không thu hồi người đã nhận. Huy hiệu mới phải cấp bù ngay cho người đã đạt điều kiện trước đó; hệ thống tự quét khi thêm, không bắt user tập thêm chỉ để kích hoạt lại kiểm tra.

Với `STREAK_DAYS`, người từng có chuỗi 14 nhưng hiện reset vẫn đã đạt ngưỡng 7 trong quá khứ. **Đề xuất triển khai:** cấp bù xét `highest_streak`/lịch sử, các metric khác lấy dữ liệu hợp lệ đã lưu; dùng khóa chống cấp trùng và lưu điều kiện tại lúc cấp. Đã nhận không bị mất nếu metric hiện tại giảm hoặc badge được ngừng hoạt động.

## 14. Chat với AI và khuyến nghị

### 14.1. Hai chức năng khác nhau

- **Chat:** trao đổi hỏi–đáp, lưu từng tin trong `chat_historigites`.
- **Khuyến nghị:** kế hoạch hoặc cảnh báo chính thức, lưu trong `ai_recommendations`; có trạng thái đã đọc.

Không phải mỗi câu AI trả lời đều tạo một khuyến nghị. Đọc câu “bạn có thể thử Squat” trong chat không tự thêm Squat vào lịch đang áp dụng.

### 14.2. Lưu lịch sử và bộ nhớ ngắn hạn

Chat có `user_id`, `session_id`, `sender`, nội dung và thời điểm. **Mỗi user chỉ có một luồng chat logic**: không có tạo phòng mới hoặc xóa toàn bộ luồng. `session_id` là kỹ thuật giới hạn ngữ cảnh, không phải phòng chat cho user quản lý.

**Đã chốt hành vi giao diện:** mỗi lần đăng nhập, vùng chat mở ra trống. Nếu có lịch sử, có nút “Xem 10 tin nhắn trước”; một lần bấm tải **10 tin**, không phải 10 cặp, từ các lần đăng nhập trước. Có thể tiếp tục tải các đợt cũ hơn; tin được trình bày theo thứ tự thời gian. Màn hình trống không xóa dữ liệu đã lưu.

Frontend tạo session trong `sessionStorage` và gửi kèm tin nhắn. Backend xác định user từ xác thực rồi lấy khoảng 10 cặp user–AI gần nhất trong **cùng user, cùng session**, sắp về thứ tự thời gian trước khi gửi Gemini. Tin nhắn hiện tại không được đính kèm hai lần.

Mỗi lần đăng nhập phải cấp phiên ngữ cảnh mới, không chỉ tái dùng key cũ còn trong sessionStorage. Việc bấm xem tin lịch sử không tự đính toàn bộ tin cũ vào prompt phiên hiện tại. Hồ sơ/mục tiêu/cân nặng mong muốn/lịch là dữ liệu có cấu trúc riêng nên vẫn được AI truy xuất phù hợp dù phiên chat mới bắt đầu trống.

“20 tin gần nhất” không luôn tương đương “10 cặp hoàn chỉnh” khi có lỗi AI hoặc nhiều tin user liên tiếp. **Đề xuất:** lưu định danh lượt trao đổi, ghép cặp rõ ràng; nếu chỉ giới hạn 20 tin thì phải mô tả đúng là 20 tin, không khẳng định luôn đủ 10 cặp.

Thay/đóng tab có thể làm phiên ngữ cảnh thay đổi, không xóa lịch sử đã lưu trong DB. `session_id` của chat là chuỗi, khác `session_id` số nguyên của buổi tập; không dùng nhầm để join hai phân hệ.

**Xóa từng tin:** user được xóa một tin USER hoặc AI trong luồng của mình, kể cả tin cũ; không được xóa luồng hay tin người khác. Xóa tin không xóa kế hoạch, mục tiêu hay kết quả đã được lưu từ nghiệp vụ trước đó. **Đề xuất:** đánh dấu xóa, loại khỏi giao diện/ngữ cảnh AI và dùng cursor để tải 10 tin chưa xóa; quyền Admin xem bản đã xóa và thời hạn lưu bản đó còn cần quyết định. Ngoại lệ xóa chat không trao quyền sửa lịch sử tập/ăn.

### 14.3. Luồng một lượt chat

```text
User gửi tin
    → Xác thực, kiểm tra quyền/giới hạn
    → Lưu tin USER và định danh lượt yêu cầu
    → Lấy ngữ cảnh + hồ sơ/số liệu cần thiết
    → Gọi Gemini
    → Kiểm tra phản hồi
    → Lưu tin AI
    → Gửi cho đúng user qua kênh realtime
```

Định hướng WebSocket/STOMP/SockJS từ trao đổi nguồn. Cần kênh riêng theo người dùng; không phát hội thoại cá nhân lên một topic công khai chung. Backend phải xác thực cả kết nối và quyền gửi/nhận message.

Nếu Gemini timeout: báo không nhận được phản hồi, cho thử lại và giữ trạng thái lượt chat rõ ràng. Không tạo câu trả lời giả như thể đến từ Gemini. Giới hạn gọi, retry có kiểm soát, Caffeine/Bucket4j là hướng triển khai cần bổ sung dependencies và cấu hình phù hợp 4.1.1.

### 14.4. Loại khuyến nghị và nguồn kích hoạt

| Type | Chức năng | Nguồn kích hoạt |
| --- | --- | --- |
| `MEAL_PLAN` | Thực đơn có bữa, food_id và số gram; kèm mục tiêu calo/macro | AI đề xuất hoặc user yêu cầu/chỉnh qua chat |
| `WORKOUT_PLAN` | Đề xuất bài tập và lịch tuần | Luồng lập lịch từ hồ sơ hoặc yêu cầu kế hoạch mới |
| `CALORIE_ALERT` | Báo thừa/thiếu calo theo điều kiện đã định nghĩa | Backend xét dữ liệu thực tế và target/TDEE; thêm trạng thái hết áp dụng |
| `INACTIVITY_ALERT` | Nhắc sau 3 ngày phải tập bị bỏ lỡ | Theo check-in; tiếp tục mỗi ngày phải tập, bỏ qua ngày nghỉ |
| `STREAK_MILESTONE` | Chúc mừng mốc streak | Lần check-in làm streak đạt mốc |

Yêu cầu “đánh giá tiến độ” có trong trao đổi nguồn nhưng type lưu riêng chưa có. Không tự thêm type hoặc nhét mọi đánh giá vào `WORKOUT_PLAN` khi chưa thống nhất.

**Đã chốt:** cảnh báo có thể dùng bộ câu mẫu hữu hạn theo hoàn cảnh hoặc do AI viết. Backend quyết định điều kiện, chọn mẫu/đóng gói số liệu; lỗi AI có thể dùng mẫu phù hợp, không tự bịa số. Tên model Gemini do chủ dự án cung cấp sau qua cấu hình. Lỗi AI không làm mất check-in/kết quả đã lưu.

### 14.6. Thực đơn và mục tiêu dinh dưỡng có cấu trúc

AI đề xuất thực đơn cụ thể theo các bữa, mỗi món trỏ `food_id` có thật/đang hoạt động và số gram. Backend tính kcal/macro từng món rồi cộng bữa/ngày, so với target AI đã đề xuất. Không coi phần chữ “1800 kcal” là bằng chứng tổng món thực sự bằng 1800.

Lưu bản kế hoạch riêng khỏi `daily_meal_logs`: kế hoạch là dự kiến; chỉ khi user ghi đã ăn mới trở thành nhật ký thực tế. Sửa thực đơn hiện tại/tương lai không sửa bữa đã ăn trong lịch sử. AI phải hỏi khi thiếu cân nặng đích cần thiết, không coi target tự suy ra là thông tin user đã khai.

### 14.7. Nhắc bài còn dở

AI/notification service đọc lịch đang áp dụng và danh sách bài user đã chọn trong buổi. Đã hoàn thành bài nào thì ghi nhận ngay; bài chưa xong còn lại được đưa vào lời nhắc, ví dụ “Bạn còn bài A chưa hoàn thành”. Khi buổi bị đóng băng, quay lại vẫn thấy danh sách này.

Có một bài hợp lệ đã đủ check-in nhưng vẫn có thể còn bài dự kiến chưa làm; nhắc bài còn thiếu không được báo rằng check-in hợp lệ đã mất. Kênh nhắc hiện là trong ứng dụng; không tự bổ sung email/push tập luyện chỉ vì đã có email xác thực tài khoản.

### 14.5. Quan hệ dữ liệu

Recommendation bắt buộc gắn `profile_id`, tùy chọn gắn `meal_log_id` và `chat_id`; có danh sách bài qua `ai_recommended_exercises`. Backend kiểm tra mọi tham chiếu cá nhân cùng chủ sở hữu vì backup đã không còn trigger kiểm tra việc đó.

Hiện không có FK trực tiếp recommendation → workout session. Yêu cầu mới chủ yếu là **lịch tương lai theo bài**, nên không tự bổ sung FK này như một yêu cầu đã chốt. Nếu sau này cần lời nhận xét riêng cho một buổi đã tập, có thể đề xuất quan hệ riêng và xác nhận phạm vi.

## 15. Thông báo và xử lý theo ngày

### 15.1. Chuông thông báo

Mỗi recommendation có `is_read=false` khi tạo. Chuông hiển thị số khuyến nghị chưa đọc của user; danh sách sắp từ mới tới cũ. Mở/đánh dấu đã đọc gọi backend và xác minh đúng chủ sở hữu. Đọc thông báo không có nghĩa đã đồng ý với lịch; hai trạng thái cần tách riêng.

`ai_recommendations` đang gộp khuyến nghị và thông báo. Thông báo cấp Badge chưa có type tương ứng; kênh hiển thị huy hiệu trên giao diện cần thiết kế, không tự gán thành `STREAK_MILESTONE` cho mọi badge.

**Đề xuất bổ sung trạng thái độc lập:** đã đọc/chưa đọc, còn áp dụng/hết áp dụng, đã phê duyệt/chưa phê duyệt. Một cảnh báo hết áp dụng không nhất thiết đã đọc; một kế hoạch đã đọc không nhất thiết đã được chấp nhận. Chống trùng theo user, ngày Việt Nam và hoàn cảnh/loại; không gửi lặp vì mở nhiều thiết bị.

### 15.2. Quy tắc thời gian

- Mọi phép xác định “hôm nay”, ngày nhật ký, ngày check-in và kỳ báo cáo dùng `Asia/Ho_Chi_Minh`.
- Thời điểm sự kiện có thể lưu dạng `Instant`/timestamp có múi giờ; khi gán ngày nghiệp vụ phải chuyển về giờ Việt Nam.
- Ngày sinh và ngày nhật ký là ngày lịch (`LocalDate`), không ép thành timestamp rồi làm lệch ngày.
- Khoảng một ngày báo cáo là `[00:00 ngày D, 00:00 ngày D+1)` tại Việt Nam: lấy đầu, không lấy cuối.
- `CURRENT_DATE` của PostgreSQL phụ thuộc session timezone. Backend phải gán ngày nghiệp vụ rõ ràng, không dựa mù quáng vào default DB khi DB chạy UTC.

**Ví dụ:** 18:30 UTC ngày 17/10 là 01:30 Việt Nam ngày 18/10. Sự kiện này thuộc ngày 18/10 trong website.

### 15.3. Công việc lúc chuyển ngày

**Đề xuất triển khai:** scheduler có `zone="Asia/Ho_Chi_Minh"`; nếu dùng Spring cron sáu trường, `0 0 0 * * *` là mốc nửa đêm. Scheduler gọi service đối soát, không chứa toàn bộ logic nghiệp vụ.

1. Xác định ngày vừa kết thúc và lịch cá nhân có hiệu lực.
2. Đối soát check-in/nghỉ/bỏ lỡ theo mục 12.
3. Reset streak của ngày phải tập bị bỏ lỡ, giữ nguyên ngày nghỉ.
4. Cập nhật bộ đếm bỏ lỡ; từ ngưỡng 3, nhắc tối đa một lần cho ngày hiện tại nếu phải tập và chưa check-in, bỏ qua ngày nghỉ.
5. Dashboard mặc định sang nhật ký ngày mới; không xóa lịch sử dinh dưỡng.
6. Ghi nhận ngày đã xử lý và tạo thông báo không trùng.

Ngày nhật ký ăn đã kết thúc được khóa ghi theo điều kiện ngày tại API, không cần đợi job ghi cờ. Việc nhắc bỏ tập trong ngày phải kiểm tra lại check-in/ngày nghỉ hiện tại để không nhắc một người vừa tập xong hoặc đang nghỉ hợp lệ.

Job có thể chạy lại sau gián đoạn. Nếu có nhiều instance ứng dụng, cần điều phối/khóa để không xử lý cùng ngày hai lần. Các truy vấn thời gian phải kiểm thử ngay sát 00:00, không chỉ vào giờ ban ngày.

## 16. Chức năng Admin

### 16.1. Quản trị dữ liệu chuẩn

| Mục quản lý | Thao tác | Ý nghĩa và yêu cầu |
| --- | --- | --- |
| Thực phẩm | Thêm/sửa/ngừng hoạt động, tìm kiếm | Tên, kcal và macro/100 g; sửa chỉ áp dụng dữ liệu mới, lịch sử giữ snapshot |
| Nhóm cơ/danh mục | Thêm/sửa/ngừng hoạt động | Phân loại bài tập; không xóa cascade các bài/kết quả đã dùng |
| Bài tập | Thêm/sửa/ngừng hoạt động, quản lý cấp độ, tải GIF/video | MET; cấp độ tùy đặt tên với set/reps/giây tập/nghỉ/thời gian dự kiến; mốc kết thúc sớm tự tính 2/3; chỉ thời gian chặn công nhận |
| Huy hiệu | Thêm/sửa/ngừng hoạt động | Quy tắc Data-Driven; cấp bù khi thêm; không thu hồi đã nhận |

Khi xóa món/bài đã có lịch sử, backup có các FK `RESTRICT`; frontend phải giải thích lý do từ chối. Với bảng nối `ai_recommended_exercises`, xóa exercise hiện có thể cascade xóa liên kết đề xuất.

**Đã chốt:** ngừng hoạt động để không chọn mới, giữ lịch sử. Bài ngừng hoạt động đang nằm trong lịch cá nhân phải yêu cầu AI đề xuất bài thay; AI không lấy bài đã ngừng làm ứng viên. Việc thay vẫn đi qua yêu cầu/phê duyệt phù hợp, không sửa buổi cũ. Không dùng `CascadeType.REMOVE` từ User/plan để vô tình xóa kho bài/thực phẩm dùng chung.

### 16.2. Người dùng và phân quyền

Admin xem danh sách tài khoản, hồ sơ thể trạng, streak và huy hiệu. Có tìm kiếm/phân trang để theo dõi tài khoản; các bộ lọc cụ thể là thiết kế giao diện.

Admin khóa/mở khóa và thay đổi `ROLE_USER`/`ROLE_ADMIN`; cũng dùng đầy đủ chức năng cá nhân. Không hiển thị password hash. Không khóa/gỡ quyền Admin cuối cùng; kiểm tra các phiên đang đăng nhập sau đổi quyền/khóa và ghi audit thao tác.

### 16.3. Theo dõi AI và hoạt động

Admin tra cứu chat theo user/thời gian để kiểm soát chất lượng và phát hiện lạm dụng. Xem số lượng, loại và thời điểm recommendation được tạo; có thể kiểm tra kế hoạch bị lỗi hoặc không được chấp nhận.

Thông tin phục vụ giám sát đề xuất: trạng thái gọi Gemini, thời gian phản hồi, lỗi phân tích cấu trúc, lượt retry, model được cấu hình. Không ghi API key/token trong log. Thời hạn lưu hội thoại, quyền truy xuất và thông báo cho user về quyền Admin đọc chat cần hoàn thiện trước khi đưa vào dùng thực tế.

### 16.4. Hướng dẫn Admin lần đầu

1. Đăng nhập bằng tài khoản được cấp quyền quản trị theo quy trình riêng.
2. Tạo categories, foods và exercises hợp lệ; kiểm tra đơn vị, video và cách đo từng bài.
3. Tạo badges bằng các metric có sẵn và ngưỡng mong muốn.
4. Kiểm tra kho đủ bài để AI lập lịch; không cho rằng AI sẽ tự bổ sung dữ liệu nền thiếu.
5. Theo dõi tài khoản, yêu cầu AI lỗi, cảnh báo và báo cáo.
6. Khi sửa dữ liệu đã được dùng, xem tác động tới lịch và lịch sử trước khi lưu/xóa.

## 17. Dashboard và thống kê

### 17.1. Dashboard cá nhân

Hiển thị lịch hôm nay (tập/nghỉ/chưa có lịch), bài cần thực hiện, trạng thái check-in, streak hiện tại/kỷ lục, huy hiệu, tổng calo/macro đã ghi, calo tập đã ghi và thông báo chưa đọc. Có bộ chọn ngày/khoảng thời gian cho lịch sử.

Các biểu đồ đề xuất: calo ăn và calo tập theo ngày; số buổi hoàn thành; thời lượng/quãng đường chạy; mức tạ theo bài; nhóm bài đã thực hiện. Biểu đồ cân nặng/chiều cao cần thêm lịch sử thể trạng trước, không suy ra từ một hồ sơ hiện tại.

Không gắn nhãn `calo ăn − calo tập` là mức thặng dư/thâm hụt năng lượng cả ngày. Nếu hiển thị phép trừ đó, gọi đúng là “chênh lệch giữa calo ăn và calo tập đã ghi”; đánh giá cả ngày cần mô hình TDEE và cách tránh đếm trùng vận động đã chốt.

### 17.2. Báo cáo toàn hệ thống

| Chỉ số yêu cầu | Định nghĩa đề xuất có thể triển khai | Điểm phải thống nhất |
| --- | --- | --- |
| User mới theo thời gian | Đếm `users.created_at` trong kỳ Việt Nam | Chỉ user thuộc phạm vi báo cáo |
| Tỷ lệ duy trì/tuân thủ | User/ngày phải tập có check-in ÷ user/ngày phải tập đã kết thúc × 100% | Bỏ ngày nghỉ; không gọi đây là retention D7/D30 |
| Bài tập nhiều nhất | Đếm các lần thực hiện hợp lệ theo `exercise_id` | Đếm lượt hoàn thành bài, không đếm lượt chọn |
| Calo trung bình/ngày | Tổng calo kết quả bài hợp lệ trong kỳ ÷ số ngày lịch trong kỳ | Tính ngay bài hoàn thành, không chờ buổi kết thúc |

**Đã chốt:** dùng các công thức trên, loại tài khoản có ROLE_ADMIN, tài khoản bị khóa và tài khoản thử nghiệm khỏi toàn bộ báo cáo hệ thống tương ứng. Cần trạng thái/cờ tài khoản để áp dụng cùng một bộ lọc trên user và dữ liệu thuộc user. Ngày nghỉ không phải ngày bỏ tập; user chưa có lịch dùng mặc định ngày phải tập. Báo cáo này không hạn chế Admin xem dashboard cá nhân của mình.

Backend tổng hợp theo khoảng ngày rõ ràng và trả DTO; frontend vẽ Chart.js. Ngày không dữ liệu, tổng 0 và chỉ số không xác định (mẫu số 0) phải hiển thị khác nhau. API thống kê toàn hệ thống chỉ dành cho Admin.

## 18. Từ điển database hiện có

Phần này mô tả **bản backup được cung cấp**, không khẳng định database live đã có cùng schema. Có 17 bảng; tên viết hoa trong ERD và tên snake_case trong PostgreSQL cùng chỉ các khái niệm tương ứng.

### 18.1. Danh sách bảng, cột và vai trò

| Bảng | Cột hiện có | Chức năng và khóa quan trọng |
| --- | --- | --- |
| `users` | `user_id`, `username`, `email`, `password_hash`, `created_at` | Tài khoản; PK user_id, UNIQUE username/email. |
| `roles` | `role_id`, `role_name` | Vai trò; PK role_id, UNIQUE role_name; hai role đã seed. |
| `user_roles` | `user_id`, `role_id` | Nối user–role; PK ghép hai cột, quan hệ nhiều–nhiều. |
| `user_profiles` | `profile_id`, `user_id`, `display_name`, `gender`, `birth_date`, `height_cm`, `weight_kg`, `activity_level`, `goal`, `calculated_bmr`, `calculated_tdee` | Hồ sơ; PK profile_id, UNIQUE user_id. Một user tối đa một profile, không phải tự bắt buộc mọi user đã có profile. |
| `foods` | `food_id`, `food_name`, `calories_per_100g`, `protein`, `carbs`, `fat` | Kho dinh dưỡng/100 g; UNIQUE food_name. |
| `daily_meal_logs` | `meal_log_id`, `user_id`, `log_date` | Nhật ký ngày; UNIQUE `(user_id, log_date)`. |
| `meal_details` | `meal_detail_id`, `meal_log_id`, `food_id`, `meal_type`, `weight_grams`, `calculated_calories`, `logged_at` | Món đã ghi vào một bữa/ngày. |
| `categories` | `category_id`, `category_name` | Phân loại bài; UNIQUE category_name. |
| `exercises` | `exercise_id`, `exercise_name`, `description`, `met_value`, `min_duration_minutes`, `recommended_sets`, `recommended_reps`, `ai_video_url`, `category_id` | Kho bài tập chuẩn; ngưỡng và video. |
| `workout_sessions` | `session_id`, `user_id`, `start_time`, `end_time`, `status` | Một buổi tập thực tế; end_time có thể chưa có. |
| `workout_details` | `detail_id`, `session_id`, `exercise_id`, `duration_minutes`, `sets`, `reps`, `weight_kg`, `calories_burned` | Kết quả bài trong buổi; weight_kg là mức tạ. |
| `streaks` | `user_id`, `current_streak`, `highest_streak`, `last_check_in_date` | Tổng hợp chuỗi hiện tại/kỷ lục; PK user_id. Chưa đủ lịch sử ngày nghỉ. |
| `badges` | `badge_id`, `badge_name`, `description`, `icon_class`, `metric_type`, `threshold_value` | Định nghĩa huy hiệu; UNIQUE badge_name. |
| `user_badges` | `user_id`, `badge_id`, `earned_at` | Huy hiệu đã cấp; PK `(user_id, badge_id)`. |
| `chat_histories` | `chat_id`, `user_id`, `session_id`, `sender`, `message_content`, `sent_at` | Mỗi dòng là một tin nhắn; session_id chuỗi, không phải buổi tập. |
| `ai_recommendations` | `recommendation_id`, `profile_id`, `meal_log_id`, `chat_id`, `type`, `content`, `is_read`, `created_at` | Kế hoạch/cảnh báo; meal_log/chat có thể null. |
| `ai_recommended_exercises` | `recommendation_id`, `exercise_id` | Nối khuyến nghị–bài; PK ghép, không có ngày trong tuần. |

Các ID đơn chủ yếu là integer identity; `roles.role_id` là smallint. `streaks.user_id` dùng ID user; các bảng nối dùng khóa ghép. Khi code Entity phải khớp kiểu và khóa, không tự dùng `Long`/BIGINT cho toàn bộ nếu chưa có lý do đổi schema.

### 18.2. Quan hệ chính

```text
users ──< user_roles >── roles
users ── 0..1 user_profiles
users ── 0..1 streaks
users ──< user_badges >── badges
users ──< daily_meal_logs ──< meal_details >── foods
users ──< workout_sessions ──< workout_details >── exercises >── categories
users ──< chat_histories
user_profiles ──< ai_recommendations
daily_meal_logs ──< ai_recommendations  [tham chiếu tùy chọn]
chat_histories ──< ai_recommendations   [tham chiếu tùy chọn]
ai_recommendations ──< ai_recommended_exercises >── exercises
```

`──<` biểu diễn một–nhiều. Quan hệ profile/streak “0..1” phản ánh user mới có thể chưa được tạo bản ghi liên quan; ràng buộc unique không đảm bảo tự sinh bản ghi.

### 18.3. Enum/check constraint

| Cột | Tập giá trị hiện tại |
| --- | --- |
| `roles.role_name` | `ROLE_ADMIN`, `ROLE_USER` |
| `user_profiles.gender` | `MALE`, `FEMALE`, `OTHER` |
| `user_profiles.activity_level` | `SEDENTARY`, `LIGHT`, `MODERATE`, `ACTIVE`, `VERY_ACTIVE` |
| `user_profiles.goal` | `LOSE_WEIGHT`, `GAIN_MUSCLE`, `MAINTAIN` |
| `meal_details.meal_type` | `BREAKFAST`, `LUNCH`, `AFTERNOON`, `DINNER` |
| `workout_sessions.status` | `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `chat_histories.sender` | `USER`, `AI` |
| `ai_recommendations.type` | `MEAL_PLAN`, `WORKOUT_PLAN`, `CALORIE_ALERT`, `INACTIVITY_ALERT`, `STREAK_MILESTONE` |
| `badges.metric_type` | `STREAK_DAYS`, `TOTAL_WORKOUTS`, `TOTAL_CALORIES_BURNED`, `MAX_WEIGHT_LIFTED`, `TOTAL_MEAL_LOGS` |

Các tập hiện tại chưa bao phủ mục tiêu và loại bài mới. `@Enumerated(EnumType.STRING)` phù hợp giá trị HOA nhưng đổi enum trong Java không phải bằng chứng constraint cũ trong PostgreSQL đã tự đổi.

### 18.4. Kiểu số và ràng buộc

| Nhóm | Precision/scale hoặc điều kiện hiện có |
| --- | --- |
| Thể trạng | height/weight `numeric(5,1)`; BMR/TDEE `numeric(7,1)` |
| Thực phẩm | calo/100 g `numeric(7,2)`; macro `numeric(6,2)`; không âm |
| Món đã ăn | gram `numeric(7,1)` >0; calo `numeric(8,1)` ≥0 |
| Bài tập | MET `numeric(4,1)` >0; min_duration `numeric(6,1)` >0; sets/reps >0 |
| Kết quả tập | duration `numeric(6,1)` >0; sets/reps >0; mức tạ `numeric(6,2)` ≥0; calo `numeric(8,1)` ≥0 |
| Badge | threshold `numeric(10,1)` >0 |
| Streak | current/highest ≥0 và highest ≥current |
| Buổi tập | end_time null hoặc ≥ start_time |

Ràng buộc giờ kết thúc hiện không đủ đảm bảo buổi `COMPLETED` có bài hợp lệ. Điều đó phải được Service kiểm tra cùng các quy tắc mới.

### 18.5. Hành vi xóa và index

- User bị xóa: các bản ghi sở hữu như profile, log, session, chat, streak và bảng nối bị cascade theo FK tương ứng. **Chức năng xóa tài khoản chưa được yêu cầu**, không tự thực thi hành vi này.
- Role đang được gán: FK `RESTRICT` ngăn xóa; category có exercise, food có meal detail, exercise có workout detail cũng có ràng buộc chặn tương ứng.
- Xóa profile làm mất recommendation liên quan; xóa log/chat đặt tham chiếu tùy chọn trong recommendation về null.
- Xóa recommendation hoặc exercise có thể xóa các dòng `ai_recommended_exercises` liên quan.
- Xóa badge trong backup hiện cascade tới `user_badges`; sản phẩm đã chốt chỉ ngừng hoạt động và giữ huy hiệu đã cấp, nên không dùng xóa cứng cho badge có lịch sử.

Index hiện có hỗ trợ tìm buổi theo user/thời gian, chat theo user/session/thời gian, recommendation theo profile/đã đọc/thời gian và các FK thường dùng. Các unique key cho log ngày và badge đã cấp là một phần chống trùng; lịch/check-in mới cần index/unique tương ứng.

ERD có các thuộc tính dẫn xuất như Age, tổng calo/macro ngày, tổng thời gian/calo buổi; chúng **không phải các cột vật lý tương ứng trong backup hiện tại**. Backend tổng hợp khi cần hoặc dùng read model nếu sau này có yêu cầu hiệu năng.

## 19. Dữ liệu cần bổ sung

Toàn bộ tên mới dưới đây là **đề xuất triển khai**, không phải các bảng/trường đã có hoặc tên đã được chủ dự án chốt. Có thể điều chỉnh cấu trúc khi code nếu vẫn đáp ứng nghiệp vụ.

### 19.1. Lịch cá nhân có phiên bản

| Khái niệm/tên đề xuất | Dữ liệu tối thiểu | Lý do |
| --- | --- | --- |
| `workout_plans` | ID, user/profile, recommendation nguồn, trạng thái đề xuất/đã chấp nhận/đã thay thế, accepted_at, effective_from/effective_to, phiên bản | Lịch lặp đến khi thay; giữ nguồn/hiệu lực cho lịch sử |
| `workout_plan_days` | Plan, day_of_week, loại ngày tập/nghỉ | Ngày nghỉ phải là dữ liệu rõ ràng, không suy ra từ thiếu bài |
| `workout_plan_items` | Ngày lịch, exercise, thứ tự, chỉ tiêu theo loại | Cùng một bài ở hai ngày có thể có lượng tập khác nhau |
| `workout_plan_overrides` | User, ngày cụ thể, bài/ngày nghỉ thay thế, nguồn chat, approved_at/effective_at | Đổi riêng hôm nay mà không đổi cả lịch tuần |

Mỗi ngày của một lịch cần xác định duy nhất. Nếu giới hạn một lịch có hiệu lực tại một thời điểm, phải kiểm tra không chồng khoảng hiệu lực cho cùng user. Lịch chưa được chấp nhận không được scheduler dùng để miễn ngày tập.

Quan hệ recommendation–exercise hiện có có thể làm danh sách tổng hợp bài được AI đề xuất; lịch chi tiết phải có một nguồn dữ liệu rõ ràng. Không duy trì hai danh sách có thể lệch nhau mà không có quy tắc đồng bộ.

**Đã chốt về hành vi:** lặp tuần đến khi có thay đổi; chỉnh từng bài/ngày qua AI; yêu cầu tạm thời chỉ tác động ngày được chỉ định; thay đổi đã xác nhận có hiệu lực ngay. Chưa có lịch vẫn tập tự do/check-in được. Khung giờ khuyến nghị 05:00–08:00, 15:00–18:00, 19:00–21:00 chỉ dùng tư vấn, không chặn thao tác. Quyền đổi hôm nay thành nghỉ sau khi đã bỏ buổi còn là chi tiết nghiệp vụ cần hoàn thiện ở mục 24.

### 19.2. Loại bài, chỉ tiêu và lần thực hiện

- Thêm loại đo, ví dụ `REPETITION`, `RUNNING`, `HOLD`; tên enum là đề xuất.
- Bổ sung chỉ tiêu thời gian giữ, nghỉ, quãng đường theo loại; không dùng một `recommended_reps` cho mọi trường hợp.
- Lưu bài đã chọn cho buổi, mốc bắt đầu bài, mốc kết thúc, trạng thái hợp lệ và định danh lần thực hiện. Có thể mở rộng `workout_details` hoặc dùng `workout_exercise_attempts`.
- Lưu các khoảng đang tập/nghỉ trong bài/pause/đóng băng, `counted_seconds`, mốc heartbeat cuối và thiết bị giữ quyền điều khiển. Ngưỡng gồm nghỉ trong bài, không gồm pause/đóng băng.
- Lưu mục tiêu user chọn (`target_metric`, `target_value`, đơn vị) và các chỉ số đo thực tế; sets/reps/quãng đường tự khai không phải điều kiện chứng minh cho check-in.
- Nếu có từng hiệp/chặng: lưu thứ tự, reps/thời gian giữ/thời gian chạy/quãng đường và nghỉ tương ứng trong chi tiết con. Không có GPS đã chốt; quãng đường nhập tay hay nguồn khác vẫn cần quyết định.
- Lưu ngưỡng áp dụng và chỉ tiêu kế hoạch tại lúc bắt đầu để Admin sửa bài giữa buổi không làm điều kiện thay đổi bất ngờ.
- Snapshot body weight, MET, ngưỡng, nguồn/phiên bản công thức và kết quả calo; lưu thời gian chi tiết bằng giây, đổi sang phút khi tính MET. Kết quả đã hoàn thành không sửa hồi tố.
- Kiểm tra trường theo loại; running/hold không bị ép reps và mức tạ vô nghĩa. Sets/reps tự khai không được trở thành trường bắt buộc để chặn công nhận bài chỉ dựa trên thời gian; chưa khai là chưa có dữ liệu, không điền giá trị giả để qua constraint.

### 19.3. Lịch sử ngày, check-in và đối soát

**Đề xuất `user_activity_days`:** user, ngày Việt Nam, phiên bản lịch áp dụng, loại nghĩa vụ tập/nghỉ, mốc check-in đầu tiên, bài làm căn cứ, trạng thái ngày và thời điểm chốt. UNIQUE `(user_id, activity_date)`.

Trạng thái gợi ý: đang mở, đã check-in, nghỉ hợp lệ, bỏ lỡ. Không đánh dấu bỏ lỡ khi ngày chưa hết. Nếu tách `check_ins` riêng, cũng phải đảm bảo mỗi user/ngày chỉ có một dòng check-in.

`streaks` là bản tổng hợp để đọc nhanh. Cần dữ liệu truy được chuỗi/bộ đếm bỏ tập và ngày đã đối soát; chỉ ba trường current/highest/last_date chưa đáp ứng lịch nghỉ. Lịch sử ngày giúp giải thích “vì sao bị reset”, dựng lại số khi cần và làm báo cáo.

### 19.4. Tài khoản, nhật ký quản trị và thông báo

- Trạng thái khóa/hoạt động, mốc khóa và thông tin phục vụ quản lý theo chính sách được chốt.
- Trạng thái xác minh email; token xác minh/reset một lần, thời hạn và dấu đã dùng; cờ tài khoản thử nghiệm để loại báo cáo.
- Dấu chống trùng cảnh báo theo user/hoàn cảnh/ngày; dấu chúc mừng UNIQUE user/mốc giữ suốt đời tài khoản, độc lập chuỗi mới.
- ID lượt yêu cầu AI, trạng thái xử lý/lỗi, thông tin phiên bản model/cấu hình phục vụ giám sát.
- Audit thao tác Admin: ai làm, làm gì, đối tượng, khi nào; không sao chép bí mật vào audit.
- Notification có `is_applicable`/mốc hết áp dụng, khóa chống trùng từng ngày/hoàn cảnh; tách khỏi `is_read` và phê duyệt kế hoạch.
- Chat có định danh lượt, phiên đăng nhập/ngữ cảnh, dấu xóa từng tin và cursor; không có chức năng xóa cả luồng hoặc tạo phòng mới.

**Đã yêu cầu email** cho xác minh tài khoản và quên mật khẩu. Chưa yêu cầu email/Telegram/push nhắc tập ngoài website; không tự mở rộng các kênh đó. Chọn/cấu hình nhà cung cấp email là công việc triển khai riêng, chưa có kết quả gửi thực tế.

### 19.5. Mục tiêu và lịch sử dinh dưỡng/thể trạng

- Mở rộng goal cho danh sách đã nêu; v1 vẫn một giá trị. Enum mở rộng hoặc bảng danh mục goal là lựa chọn triển khai; thiết kế nhiều mục tiêu chưa áp dụng ở v1.
- **Đã chốt snapshot** thực phẩm/calo/macro khi ghi món. Hiện calo được lưu nhưng macro đọc từ foods có thể thay đổi sau khi Admin sửa; mô hình mới giữ nhất quán lịch sử.
- Thêm lịch sử thể trạng nếu dashboard cần đường biểu diễn cân nặng/chiều cao theo thời gian. Không thể dựng lại lịch sử từ giá trị hiện tại.
- Metadata nguồn chỉ số, thời điểm tính và phiên bản công thức giúp giải thích kết quả khi thay quy tắc.
- Hồ sơ bổ sung kinh nghiệm, thiết bị/địa điểm, ngày/giờ/thời lượng có thể tập và hạn chế vận động; cân nặng đích từ chat được ghi có nguồn, không là trường bắt buộc trên form chọn mục tiêu.

### 19.6. Mục tiêu dinh dưỡng, thực đơn và thông tin AI ghi nhận

**Đề xuất mô hình:** `nutrition_targets` lưu phiên bản calo/gram protein/carbs/fat, ngày hiệu lực, nguồn đề xuất và thông tin hồ sơ dùng để phân tích. `meal_plans` và các dòng ngày/bữa/món lưu `food_id`, gram, snapshot dinh dưỡng và tổng backend tính. Thực đơn dự kiến không được nối tắt thành bữa đã ăn.

Các thông tin AI ghi nhận lâu dài như cân nặng đích, điều kiện tập và yêu cầu đổi riêng hôm nay phải lưu có cấu trúc, nguồn user/chat và mốc áp dụng. Xóa tin hoặc đăng nhập lại không tự mất các dữ liệu nghiệp vụ đó. Nếu cần định lượng vi chất cho mục tiêu chiều cao, phải có dữ liệu vi chất thực và bộ nhu cầu theo nhóm tuổi; schema hiện chưa đáp ứng.

### 19.7. Cấp độ bài tập và timer tự động — cập nhật 02/11/2026

`exercise_levels` liên kết `exercise_id`, có tên cấp độ, sets, reps nullable, seconds_per_set, rest_seconds, estimated_seconds và active. Một bài có nhiều cấp độ, tên không trùng trong cùng bài; Admin có thể ngừng/kích hoạt. `exercises.media_url` trỏ tới GIF/video được lưu trên máy chủ.

`workout_details` lưu level_id/name_snapshot, planned_sets/reps_snapshot, seconds_per_set_snapshot, estimated_seconds_snapshot, current_set_number, elapsed_in_set_millis, automatic_rest_phase và media_url_snapshot. Ngưỡng 2/3 tính từ estimated_seconds_snapshot theo millisecond; không lấy ngưỡng thủ công cũ để áp dụng vào cấp độ. Các bản ghi cũ có estimated_seconds_snapshot NULL tiếp tục luồng cũ. Level ID trong snapshot không tạo FK buộc phải giữ bản ghi master để đọc được lịch sử; thông số/tên/media đã được sao chép đầy đủ.

File SQL bổ sung và hợp đồng request/response ở docs/WORKOUT_AUTOMATION.md. SQL đã được chuẩn bị nhưng chưa chạy trên Neon, không tuyên bố mapping được nghiệm thu trên database thật chỉ dựa trên test metadata.

## 20. Kiến trúc và luồng xử lý

### 20.1. Công nghệ và trách nhiệm

| Thành phần | Trách nhiệm |
| --- | --- |
| Spring Boot 4.1.1 | Chạy backend và phục vụ tài nguyên frontend |
| Spring WebMVC | REST API cho hồ sơ, lịch, tập, dinh dưỡng, Admin |
| Spring Security + JWT (định hướng) | Xác thực, phân quyền HTTP/realtime |
| JPA/Hibernate | Ánh xạ Entity, quản lý persistence và schema |
| Spring Data JPA | Giữ theo quyết định chỉ bỏ Flyway; Repository, truy vấn, phân trang |
| PostgreSQL | Lưu dữ liệu, đảm bảo PK/FK/unique/check và transaction |
| Gemini API | Phân tích, chọn bài, đề xuất kế hoạch, hội thoại |
| Dịch vụ email cần cấu hình | Gửi xác minh tài khoản và reset mật khẩu; chưa chọn/cấu hình trong nhiệm vụ PLAN |
| WebSocket/STOMP | Kênh chat/phản hồi riêng từng người dùng |
| HTML/CSS/Vanilla JS | Giao diện SPA và đồng hồ hiển thị |
| Chart.js/Marked.js/KaTeX | Biểu đồ và trình bày phản hồi theo hướng đã trao đổi |

Version cụ thể của JWT, MapStruct, WebSocket client, Swagger và các thư viện bổ sung phải kiểm tra tương thích Spring Boot 4.1.1 khi cài; không sao chép toàn bộ version gợi ý cho Spring Boot 3.3.4 rồi coi như đã kiểm thử.

### 20.2. Các tầng trong một request

```text
Giao diện
    → Security: người gọi là ai, có quyền gì?
    → Controller: nhận DTO, kiểm tra định dạng, gọi nghiệp vụ
    → Service: quyền sở hữu, công thức, điều kiện, transaction
    → Repository: đọc/ghi/truy vấn
    → Hibernate + Entity: ánh xạ đối tượng và bảng
    → PostgreSQL
    → Response DTO → giao diện cập nhật
```

Entity mô tả dữ liệu lưu; Repository truy xuất; Service quyết định nghiệp vụ; Controller là cửa API; DTO là hợp đồng request/response; Mapper hỗ trợ chuyển dạng. Không đưa toàn bộ công thức/streak vào Controller hoặc để Entity getter gọi Gemini.

### 20.3. Cấu trúc package đề xuất

```text
vn.id.fitnessai
├── config          cấu hình Security, WebSocket, Gemini, múi giờ
├── security        token, user details, filter và phân quyền
├── entity          entity hiện có và các mô hình bổ sung
├── enums           role, goal, loại bài, trạng thái, metric
├── repository      truy xuất dữ liệu và tổng hợp
├── service         Auth, Profile, Nutrition, Plan, Workout, Streak, Badge, AI, Admin
├── dto             request/response theo chức năng
├── mapper          ánh xạ, nếu dùng MapStruct
├── controller      REST và WebSocket
├── exception       lỗi nghiệp vụ và xử lý tập trung
├── event           sự kiện sau thay đổi nghiệp vụ
├── scheduler       chốt ngày và đối soát
└── bootstrap       seed dữ liệu có kiểm soát
```

Có thể chia package theo tính năng nếu dự án lớn hơn; đây là đề xuất tổ chức, không phải cây mã đã tồn tại.

### 20.4. Transaction và dữ liệu đồng thời

Các thay đổi liên quan tới kết quả bài, check-in và streak phải có ranh giới transaction rõ; xác nhận cho user khi dữ liệu nền được lưu thành công. Dùng unique constraint kết hợp khóa/optimistic version hoặc cách tương đương để hai tab không tăng streak hai lần.

Nhiều thiết bị được đăng nhập nhưng chỉ một buổi/một bài đang thực hiện/user. Cần khóa/lease quyền điều khiển timer ở server để hai thiết bị không cộng hai lần cho cùng bài. Tải lại tiếp tục dữ liệu đã ghi nhận; khoảng mất kết nối không tự biến thành thời gian tập.

Cấp huy hiệu/thông báo có thể tách sau commit với cơ chế retry bền vững; nếu dùng Spring event trong bộ nhớ phải hiểu nó không tự đảm bảo còn event sau khi process tắt. Không gọi Gemini kéo dài trong transaction đang giữ khóa check-in.

Chấp nhận lịch phải nguyên tử: không có trạng thái nửa lịch mới, nửa lịch cũ. Tạo daily meal log cần xử lý cạnh tranh khi hai món đầu ngày được gửi đồng thời và cùng gặp “chưa có log”.

### 20.5. Hợp đồng API và lỗi

Nhóm API đề xuất: auth, profile, foods, nutrition logs, plans/acceptance, workout sessions/attempts, check-in summary, badges, chat, recommendations và admin. URL/DTO cụ thể chưa chốt, thiết kế sau khi xác định Entity và luồng.

Phản hồi cần phân biệt: chưa đăng nhập, thiếu quyền, không tìm thấy, dữ liệu sai, chưa đủ điều kiện hoàn thành, trạng thái xung đột, AI timeout. Trả lỗi dễ hiểu cho user; không lộ SQL, stack trace, mật khẩu hay khóa API.

Đầu ra AI Markdown phải được xử lý an toàn khi render; không đưa HTML chưa kiểm soát vào trang. Video URL và các trường Admin nhập cũng phải kiểm tra theo nhu cầu sử dụng, tránh nội dung không hợp lệ làm hỏng giao diện.

## 21. Quản lý schema bằng Hibernate

### 21.1. Quyết định và việc cần làm

**Đã chốt:** chỉ bỏ Flyway, vẫn Spring Data JPA + Hibernate; quản lý schema từ Entity. Khi triển khai sẽ bỏ dependencies/cấu hình Flyway phù hợp; nhiệm vụ cập nhật PLAN này chưa thay `pom.xml` hoặc cấu hình.

Khởi tạo ánh xạ đúng schema đã khảo sát, bắt đầu bằng sửa `User` khớp `user_id`, `username`, `password_hash`, `created_at` và các unique/nullable cần thiết. Không để Hibernate tạo thêm cột `id`/`password` rồi tưởng đó là dữ liệu cùng nghĩa.

### 21.2. Giới hạn cần hiểu đúng

Hibernate có thể sinh schema từ mapping và kiểm tra/schema update khi khởi động. `update` không phải cam kết mọi đổi tên cột, xóa cột, đổi enum/check hay chuyển dữ liệu cũ đều được xử lý đúng ý. Phải kiểm tra schema thực tế sau đổi Entity. [Tài liệu Hibernate, mục 2.8 Automatic schema export](https://docs.hibernate.org/orm/current/introduction/html_single/).

Không tự dùng `ddl-auto=create`/`create-drop` trên database chứa dữ liệu cần giữ. Nếu thay constraint cũ hoặc cần backfill dữ liệu mà schema update không xử lý, báo rõ tác vụ cần làm và môi trường; không tuyên bố “chỉ Entity là không bao giờ phải kiểm tra database”.

**Đề xuất vận hành trong phạm vi Hibernate:** phát triển trên database riêng; kiểm tra SQL/schema sinh ra; sao lưu trước thay đổi dữ liệu; có thể dùng `validate` để kiểm tra mapping ở môi trường cần kiểm soát. Không tự đưa Flyway trở lại trái quyết định của chủ dự án.

### 21.3. Seed và cấu hình

Seed hai role cùng dữ liệu chuẩn bằng code khởi tạo có kiểm tra tồn tại; chạy lại không tạo bản ghi trùng và không ghi đè sửa đổi của Admin. Backup chưa có thực phẩm/bài tập/huy hiệu để dùng ngay, nên nguồn seed là công việc thật cần thực hiện.

Thông tin kết nối PostgreSQL, Gemini key và JWT secret cần lấy từ cấu hình riêng/biến môi trường; PLAN không chứa giá trị thật. Chỉ cấu hình timezone JVM/DB chưa thay thế việc chuyển ngày nghiệp vụ rõ ràng trong Service.

SPA dùng đường dẫn sạch cần fallback về trang chính cho route giao diện khi truy cập trực tiếp, đồng thời giữ API và file tĩnh đúng hành vi. Port hiện tại là 8081; các ví dụ localhost sau này phải theo cấu hình thực tế.

## 22. Trình tự triển khai

Đây là thứ tự công việc dự kiến, không phải các bước đã hoàn thành. Mỗi giai đoạn có đầu ra kiểm tra được trước khi chuyển sang phần phụ thuộc.

### Giai đoạn 0 — Chốt các quy tắc chặn thiết kế

Rà các chi tiết còn cần thiết kế ở mục 24.3, nhất là hiệu lực thay ngày nghỉ trong ngày, công thức calo nghỉ trong bài và điều kiện cảnh báo thiếu/thừa. Năm câu hỏi tiếp nối đã chốt tại mục 24.2; không hỏi lại khung giờ, khóa nhật ký ngày cũ, ngưỡng nhắc 3 ngày, mốc Plank hay chúc mừng lại.

**Đầu ra:** đặc tả cập nhật; không còn điểm mơ hồ làm sai check-in/streak hoặc mô hình lịch.

### Giai đoạn 1 — Cấu hình và mô hình dữ liệu

- Giữ Spring Boot 4.1.1, xác minh bộ dependencies tương thích; xử lý Flyway theo quyết định đã chốt.
- Chuẩn hóa cấu hình bí mật, timezone, database phát triển và logging.
- Xây enums và Entity độc lập: User, Role, Category, Food, Badge.
- Xây Entity phụ thuộc: Profile, log/detail, Exercise, session/attempt/detail, Streak, UserBadge, ChatHistory, Recommendation, các mô hình plan/ngày/check-in.
- Chốt khóa ghép, quan hệ sở hữu, nullable tùy loại bài, precision, index, unique và chính sách cascade.
- Kiểm tra mapping/schema; seed role và bộ dữ liệu thử có nguồn rõ.

**Đầu ra:** schema kiểm tra được, Entity khớp; chưa tuyên bố nghiệp vụ hoàn chỉnh chỉ vì app khởi động.

### Giai đoạn 2 — Repository, API nền và xác thực

Viết repository/truy vấn theo nhu cầu thật: user/email, hồ sơ, log ngày, lịch có hiệu lực, lần thực hiện bài, ngày hoạt động, metric huy hiệu, chat phiên và thông báo chưa đọc. Thêm DTO, xử lý ngoại lệ và phân trang.

Xây đăng ký/đăng nhập, xác minh/quên mật khẩu qua email, password hash, Security, quyền Admin và trạng thái khóa. Cho nhiều phiên đăng nhập; bảo vệ Admin cuối cùng. Tạo cách kiểm thử API; nếu dùng Swagger phải chọn thư viện tương thích 4.1.1.

**Đầu ra:** một user không đọc/sửa được dữ liệu của user khác; user thường không gọi API Admin.

### Giai đoạn 3 — Hồ sơ và dinh dưỡng

Xây form/API profile 15–65, thông tin cá nhân hóa, công thức OTHER −78/hệ số TDEE đã chốt và làm tròn. Xây kho thực phẩm/ngừng hoạt động, nhật ký với phạm vi sửa được phép, snapshot, tổng calo/macro và chuyển ngày đúng giờ Việt Nam.

**Đầu ra:** kiểm chứng được ví dụ công thức; dữ liệu mỗi user/ngày tách biệt; lịch sử không bị xóa khi sang ngày mới.

### Giai đoạn 4 — Kho bài tập và thực hiện bài

Xây category/exercise, loại đo, chỉ tiêu và video. Xây session, bài đã chọn, timer gồm tập/nghỉ, pause/đóng băng, khôi phục và một bài/buổi đang thực hiện/user. Ghi reps/chặng/hiệp và tính calo ngay khi hoàn thành bài; chỉ thời gian tối thiểu chặn check-in.

**Đầu ra:** bài reps, chạy bộ và plank có dữ liệu đúng loại; không thể check-in chỉ bằng bấm bắt đầu rồi kết thúc.

### Giai đoạn 5 — AI lập lịch và chấp nhận

Tích hợp Gemini với đầu ra có cấu trúc; chỉ dùng ID bài/thực phẩm hợp lệ. Xây lịch lặp, chỉnh qua chat và override hôm nay, mục tiêu calo/macro, thực đơn và cân nặng đích AI hỏi/ghi nhận. Lưu phê duyệt, phiên bản/hiệu lực/ngày nghỉ; thay đổi không sửa quá khứ.

**Đầu ra:** hai user có lịch khác nhau cho cùng bài; lịch chưa đồng ý không có hiệu lực; lỗi AI không sinh lịch giả.

Giai đoạn 4 có thể kiểm tra kết quả bài độc lập bằng dữ liệu thử; không mở cơ chế streak đầy đủ trước khi quy tắc lịch/ngày nghỉ đã sẵn sàng.

### Giai đoạn 6 — Check-in, streak và huy hiệu

Kết nối bài hợp lệ đầu tiên tới check-in; lịch sử ngày, đối soát, reset và bộ đếm bỏ lỡ. Xây mốc chúc mừng, badge engine, unique/idempotency và xử lý chạy lại sau gián đoạn.

**Đầu ra:** mọi ví dụ ở mục 12 chạy đúng, kể cả ngày nghỉ xen kẽ, hai tab và job bỏ lỡ lúc 00:00.

### Giai đoạn 7 — Chat, thông báo và giám sát

Xây chat riêng qua WebSocket, phiên mới khi login, màn hình trống/nút tải 10 tin cũ, xóa từng tin, bộ nhớ ngắn hạn riêng. Xây chuông, đã đọc/hết áp dụng, câu mẫu hoặc AI viết, nhắc bài dở, cảnh báo calo/bỏ tập/mốc và Admin giám sát.

**Đầu ra:** đúng ngữ cảnh/đúng người nhận; không tạo thông báo trùng vì retry; lỗi AI không phá giao dịch tập/ăn.

### Giai đoạn 8 — Giao diện hoàn chỉnh và báo cáo

Hoàn thiện SPA và điều hướng; các trạng thái chờ/trống/lỗi; responsive; dashboard cá nhân, lịch sử, Admin và biểu đồ theo định nghĩa đã chốt. Có thể dựng giao diện từng module từ các giai đoạn trước, không cần chờ toàn bộ backend mới bắt đầu frontend.

**Đầu ra:** một người mới có thể tự thực hiện hành trình tại mục 6.2 và hiểu các số đang xem.

### Giai đoạn 9 — Kiểm thử tích hợp và đóng gói

Kiểm tra end-to-end, quyền truy cập, mốc ngày, dữ liệu đồng thời và lỗi phụ thuộc. Build/test phù hợp, kiểm tra file JAR và cấu hình triển khai thực tế, cấu hình SSL/kết nối cloud theo môi trường đã xác nhận. Không coi build thành công là đã deploy.

**Đầu ra:** phiên bản chạy được, dữ liệu thử và hướng dẫn sử dụng; ghi rõ kiểm thử đã chạy, môi trường, kết quả và phần chưa triển khai.

## 23. Kịch bản nghiệm thu

Đây là checklist cần thực hiện **khi code**; chưa được chạy trong nhiệm vụ tạo PLAN. Công thức minh họa dùng dữ liệu giả định.

### 23.1. Hồ sơ, mục tiêu và dinh dưỡng

| Mã | Tình huống | Kết quả mong đợi |
| --- | --- | --- |
| P01 | Hồ sơ nam 20 tuổi, 70 kg, 170 cm | BMR 1667.5 theo công thức đã mô tả |
| P02 | BMR 1667.5, MODERATE 1.55 | TDEE 2584.625 trước làm tròn |
| P03 | Sinh nhật ngày mai | Tuổi chưa tăng sớm |
| P04 | V1 gửi nhiều mục tiêu trong request | Không âm thầm chấp nhận nhiều mục tiêu |
| P05 | OTHER, 20 tuổi, 70 kg, 170 cm | Dùng quy ước −78, BMR 1584.5; không gắn nhãn hệ số nghiên cứu |
| P06 | Tuổi tròn 14/15/65/66 | Chỉ 15 và 65 thuộc phạm vi tuổi đã chốt |
| P07 | AI hỏi cân nặng đích, user trả lời qua chat | Lưu thông tin có cấu trúc, không ép form ban đầu và không mất khi login lại |
| N01 | 200 g thực phẩm minh họa 165 kcal/100 g | Calo 330; macro tính theo cùng khối lượng |
| N02 | Hai món đầu ngày gửi cùng lúc | Một daily log, hai detail hợp lệ |
| N03 | Sang 00:00 Việt Nam | Ngày mới độc lập; log ngày trước còn nguyên |
| N04 | User sửa/xóa món của user khác | Từ chối |
| N05 | Admin sửa thông số thực phẩm đã dùng | Snapshot lịch sử giữ nguyên; thông số mới áp dụng bản ghi mới |
| N06 | AI đề xuất thực đơn có món/gram | Backend kiểm tra food_id đang hoạt động và tính lại tổng kcal/macro |
| N07 | User chỉ đồng ý thực đơn | Không tự tạo nhật ký đã ăn |
| N08 | Cảnh báo calo đã gửi, điều kiện hết do chỉnh món được phép | Đánh dấu hết áp dụng; không thu hồi huy hiệu, không gửi trùng trong ngày |
| N09 | Sửa/xóa món của hôm nay | Cho phép với đúng chủ sở hữu; tính lại tổng và trạng thái áp dụng của cảnh báo |
| N10 | Sửa/xóa/ghi bổ sung vào nhật ký ngày đã qua | Từ chối; ngày cũ chỉ đọc, huy hiệu và snapshot giữ nguyên |
| N11 | Mở form 23:59, gửi lưu sửa món lúc 00:01 | Từ chối sửa ngày vừa kết thúc dù form cũ vẫn còn mở hoặc job chưa chạy |

### 23.2. Lịch, tập luyện và check-in

| Mã | Tình huống | Kết quả mong đợi |
| --- | --- | --- |
| W01 | Gemini trả exercise_id không tồn tại | Không kích hoạt lịch lỗi |
| W02 | Người dùng chỉ xem đề xuất | Chưa thay lịch có hiệu lực |
| W03 | Lịch A có Monday, lịch B có Wednesday cho cùng bài | Lịch độc lập, kho exercise không bị đổi theo A/B |
| W04 | Chọn bài → bắt đầu → kết thúc ngay | Không check-in |
| W05 | Chọn ba bài, hoàn thành hợp lệ một bài | Được check-in, không bắt hoàn thành cả ba |
| W06 | Chỉ chờ lâu ở buổi, chưa bắt đầu bài B | Không dùng thời gian đó để công nhận B |
| W07 | Request tự khai đủ thời gian nhưng server chưa đủ | Từ chối hoàn thành sớm |
| W08 | 3 hiệp × 10 reps | Lưu reps mỗi hiệp là 10, không nhầm thành 30 |
| W09 | Chạy 5 phút + nghỉ 1 + chạy 5 | Tách active 10, rest 1; lưu quãng đường |
| W10 | Plank 3 × 30 giây, hai lần nghỉ 30 giây | Tách active 90 giây, rest 60 giây; không ép reps giả |
| W11 | MET 6, body weight 70, active 10 phút | Calo minh họa 73.5; mức tạ không tham gia thay body weight |
| W12 | Cùng kết quả gửi hai lần | Một lần công nhận; không nhân đôi calo/check-in |
| W13 | Hoàn thành nhiều bài/nhiều buổi trong ngày | Một check-in, streak tăng tối đa 1 |
| W14 | Tập 6 phút, nghỉ trong bài 2 phút, pause 5 phút; ngưỡng 10 | Chỉ tính 8 phút, chưa được công nhận |
| W15 | Đóng tab 20 phút rồi quay lại buổi dở | Giữ danh sách bài; không cộng 20 phút đóng băng; có nhắc bài chưa xong |
| W16 | Hai thiết bị cùng bắt đầu bài/buổi khác nhau | Chỉ một buổi/một bài đang thực hiện được server cho phép |
| W17 | Đủ ngưỡng thời gian nhưng chưa khai sets/reps | Không từ chối check-in chỉ vì thiếu/chưa đạt sets/reps |
| W18 | “Hôm nay muốn chạy bộ” qua chat | Đổi riêng ngày hôm nay sau xác nhận; không tự đổi các tuần sau |
| W19 | Chưa có lịch, user chọn bài và hoàn thành đủ thời gian | Vẫn check-in được; danh sách chọn dùng để nhắc bài còn dở |
| W20 | Admin ngừng hoạt động bài trong lịch | Không chọn mới; AI được yêu cầu đề xuất thay, giữ kết quả cũ |
| W21 | Bắt đầu/tiếp tục tập lúc 22:00 ngoài khung khuyến nghị | AI có thể nhắc, chức năng vẫn dùng được; đủ điều kiện vẫn check-in được |
| W22 | User đặt mục tiêu Plank | Chỉ có thời gian giữ tư thế làm mốc; hiệp/nghỉ để hướng dẫn hoặc theo dõi |

Ca bổ sung cho cấp độ tự động ngày 02/11/2026: chọn level thiếu/sai bài/inactive phải bị từ chối; heartbeat qua nhiều pha phân bổ active/rest đúng một lần; ngưỡng dự kiến 1 giây từ chối ở 666ms và cho phép từ 667ms; chưa đủ ngưỡng không hiện nút; tới mốc đầy đủ chỉ đổi nút; chưa bấm thì không complete/check-in; cả kết thúc sớm/full đều chọn bài tiếp NOT_STARTED sau lưu thành công. Kiểm tra offline nghỉ tới hạn nhưng không cộng active, recover/token/pause, snapshot bất biến, Admin upload/User chỉ xem, đường dẫn/dung lượng/định dạng và HTTP Range cho video. Các test mock/fixture đã chạy không thay thế nghiệm thu Neon.

### 23.3. Ngày nghỉ, streak, thông báo và quyền

| Mã | Tình huống | Kết quả mong đợi |
| --- | --- | --- |
| S01 | Streak 6, hôm nay nghỉ hợp lệ và không tập | Vẫn 6, không check-in giả, không chúc mừng 7 |
| S02 | Ngày phải tập chưa check-in lúc 09:00 | Chưa reset chỉ vì ngày đang mở |
| S03 | Hết ngày phải tập không check-in | 00:00 ngày kế reset về 0; highest giữ nguyên |
| S04 | Check-in sau một ngày bỏ lỡ | Bắt đầu lại từ 1 |
| S05 | Bỏ tập — nghỉ — bỏ tập — nghỉ — bỏ tập | Bộ đếm =3; bắt đầu đợt nhắc ở ngày phải tập tiếp theo, không gửi ngày nghỉ |
| S06 | Hai ngày bỏ lỡ rồi một ngày check-in | Bộ đếm bỏ lỡ về 0 |
| S07 | Streak đạt 7/14/30/100/365 | Xét chúc mừng đúng mốc, không nhân đôi trong ngày |
| S08 | Ngày nghỉ giữ streak 7; job chạy lại | Không gửi thêm chúc mừng 7 |
| S09 | Server tắt qua nửa đêm rồi chạy lại | Đối soát ngày thiếu, số hiển thị đúng |
| S10 | Hai tab hoàn thành bài cùng lúc | Không tăng streak hai lần |
| S11 | Đổi lịch sau ngày đã bỏ lỡ | Không tự biến quá khứ thành ngày nghỉ để phục hồi chuỗi |
| S12 | Ngày nghỉ, streak 6, vẫn hoàn thành bài hợp lệ | Check-in và streak 7; chỉ tăng một lần |
| S13 | Bắt đầu 23:55, hoàn thành 00:05 | Chỉ check-in ngày mới, không cứu ngày cũ đã bị chốt bỏ lỡ |
| S14 | Mốc 7 đã chúc mừng, đứt chuỗi rồi đạt lại 7 | Không chúc mừng lại; dấu user/mốc vẫn còn |
| S15 | Đạt mốc 14 lần đầu sau khi từng đạt lại 7 | Chúc mừng 14 nếu chưa gửi mốc này trước đó |
| S16 | Bỏ lỡ 1 hoặc 2 ngày phải tập | Streak reset theo ngày bỏ lỡ, nhưng chưa gửi nhắc theo ngưỡng 3 |
| S17 | Đã bỏ lỡ 3 ngày, tiếp tục bỏ tập/nghỉ rồi quay lại | Nhắc mỗi ngày phải tập, bỏ ngày nghỉ; check-in hợp lệ xóa bộ đếm bỏ lỡ |
| B01 | Thêm badge mới cùng metric và ngưỡng đã hỗ trợ | Cấp bằng dữ liệu, không thêm if theo tên badge |
| B02 | Event cấp badge gửi lại | Một user_badge duy nhất |
| B03 | Admin thêm badge, user đã đạt ngưỡng trước đó | Cấp bù; STREAK_DAYS có thể xét kỷ lục đã đạt |
| B04 | Admin tăng ngưỡng/ngừng badge đã cấp | User giữ huy hiệu vĩnh viễn |
| A01 | Hai user dùng session_id chat giống nhau | Ngữ cảnh vẫn tách theo user |
| A02 | Gemini timeout | Báo lỗi thật, không trả nội dung AI giả |
| A03 | User đọc thông báo plan | Chỉ cập nhật đã đọc; chưa chấp nhận lịch |
| A04 | Login lại khi có lịch sử chat | Màn hình trống, nút tải 10 tin; dữ liệu cũ không mất |
| A05 | Bấm xem 10 tin trước | Tải 10 tin chưa xóa theo cursor, không phải 10 cặp và không tự nạp hết vào prompt |
| A06 | Xóa một tin USER hoặc AI của mình | Tin không hiển thị/không đưa vào ngữ cảnh; không xóa luồng, kế hoạch và mục tiêu đã lưu |
| R01 | User gọi API Admin hoặc đổi ID người sở hữu | Từ chối |
| R02 | Tài khoản bị khóa còn giữ JWT cũ | Không tiếp tục thao tác được bảo vệ |
| R03 | Khóa/gỡ quyền Admin cuối cùng | Từ chối, kể cả hai thao tác đồng thời |
| R04 | Admin dùng chức năng cá nhân | Được dùng đầy đủ nhưng dữ liệu Admin bị loại báo cáo hệ thống |
| R05 | Đăng ký hoặc quên mật khẩu | Có email xác minh/reset; token một lần, lỗi gửi được báo đúng |
| R06 | Báo cáo có Admin, user khóa, user thử nghiệm | Loại cả tài khoản và dữ liệu thuộc ba nhóm đó khỏi chỉ số tương ứng |
| T01 | Sự kiện 18:30 UTC ngày 17/10 | Thuộc ngày 18/10 giờ Việt Nam |

Các kịch bản sửa nhật ký hôm nay, ngoài khung giờ tập, ngưỡng nhắc 3 ngày, mốc Plank và chúc mừng một lần đã được bổ sung theo câu trả lời tiếp nối. Mọi bảng nghiệm thu trên là yêu cầu cần chạy khi triển khai, chưa phải kết quả test đã chạy.

## 24. Đối chiếu quyết định và phần cần hoàn thiện

### 24.1. Đối chiếu 15 nhóm trả lời đã cập nhật

| Nhóm trả lời | Nội dung đã chốt | Phần đã cập nhật |
| --- | --- | --- |
| 1 | Tuổi 15–65, 5 hệ số TDEE, OTHER −78 là quy ước | 3, 8, 23 |
| 2 | Tất cả đầu vào cá nhân hóa được bổ sung | 8, 10, 19 |
| 3 | Target calo/macro, thực đơn món/gram, cân nặng đích qua AI, hướng nội dung chiều cao | 8, 14, 19 |
| 4 | Lịch để hướng dẫn/nhắc; tự đề xuất hoặc sửa qua chat; lặp tuần; đổi riêng hôm nay; hiệu lực hiện tại | 10, 14, 19 |
| 5 | Không có lịch vẫn chọn bài tự do/check-in | 6, 10, 12 |
| 6 | Thời gian tối thiểu gồm nghỉ; sets/reps không chặn; chạy chọn thời gian/quãng đường, Plank chọn thời gian; user duyệt nghỉ | 11, 19, 23 |
| 7 | Pause, khôi phục, không tự hoàn thành offline; một bài/buổi; đóng băng khi đóng trình duyệt | 11, 20, 23 |
| 8 | Tập ngày nghỉ tăng streak; không tập nghỉ thì giữ; phải hoàn thành trước 00:00 để giữ ngày đó | 12, 23 |
| 9 | Lịch sử không sửa, snapshot, dữ liệu mới áp dụng hiện tại/tương lai | 8–11, 19, 23 |
| 10 | Nhắc hằng ngày sau ngưỡng; calo/bài tối đa một lần/ngày; mẫu hoặc AI viết; đánh dấu hết áp dụng | 9, 12, 14–15 |
| 11 | Badge vĩnh viễn/cấp bù, không thu hồi khi đổi điều kiện; log rỗng không tính; calo ghi ngay bài | 11, 13, 23 |
| 12 | Ngừng dữ liệu chuẩn; Admin dùng User; bảo vệ Admin cuối; AI thay bài ngừng hoạt động | 5, 7, 16, 23 |
| 13 | Email xác minh/reset, nhiều thiết bị; xóa từng tin, không xóa luồng; login trống/tải 10 tin; model cung cấp sau | 7, 14, 19 |
| 14 | Chốt công thức báo cáo; loại Admin, khóa, thử nghiệm | 17, 19, 23 |
| 15 | Chỉ bỏ Flyway, giữ Spring Data JPA/Hibernate | 3, 20–22 |

### 24.2. Năm quyết định tiếp nối đã chốt

| Mã | Quyết định của chủ dự án | Phần đã đồng bộ |
| --- | --- | --- |
| F01 | Chỉ sửa/xóa nhật ký ăn hôm nay; ngày cũ khóa từ 00:00 giờ Việt Nam | 3, 9, 15, 23 |
| F02 | Nên tập 05:00–08:00, 15:00–18:00, 19:00–21:00; ngoài giờ AI chỉ nhắc, không khóa chức năng | 3, 10, 19, 23 |
| F03 | Nhắc từ 3 ngày phải tập bị bỏ lỡ, sau đó mỗi ngày phải tập; bỏ qua ngày nghỉ | 3, 12, 14–15, 23 |
| F04 | Plank chỉ chọn thời gian giữ tư thế làm mốc | 3, 11, 23 |
| F05 | Mỗi mốc streak chỉ chúc mừng một lần/user, không gửi lại ở chuỗi mới | 3, 12, 19, 23 |

Cả năm câu hỏi tiếp nối đã được trả lời; không còn chờ xác nhận cho F01–F05. Những chi tiết ở mục 24.3 vẫn được phân biệt với quyết định đã chốt, không tự coi là đã được phê duyệt.

### 24.3. Chi tiết còn cần thiết kế/rà soát khi triển khai

- Phân biệt chỉ tiêu AI đề xuất với điều kiện chặn check-in; chỉ đủ min duration không tự mang nghĩa đạt quãng đường/mọi mục tiêu đã đặt.
- Chính sách đổi hôm nay thành ngày nghỉ sau khi đã bắt đầu/bỏ buổi, hiệu lực trong ngày và hồ sơ chưa đủ để tính chỉ số; không cho sửa nghĩa vụ ngày quá khứ đã chốt.
- Công thức calo cho nghỉ trong bài và bài kháng lực, nguồn quãng đường, snapshot chi tiết từng hiệp; không lấy ngưỡng check-in gồm nghỉ thay thẳng vào MET chạy bộ.
- Ngưỡng/dung sai thừa/thiếu so với target/TDEE, thời điểm nhận xét thiếu calo, giới hạn dinh dưỡng theo tuổi/hạn chế và dữ liệu vi chất nếu định lượng. AI có thể đề xuất nhưng cần backend kiểm tra.
- Nội dung chiều cao phải chỉnh lý theo mục 8.8: không cam kết 1–3 cm, không coi 22:00–02:00 là quy luật GH cố định hay tuổi 23 là chẩn đoán đóng sụn. Đây là phần kiểm chứng nội dung, không đảo ngược yêu cầu có mục tiêu chiều cao.
- Quyền Admin xem tin đã xóa, thời hạn lưu hội thoại/log/audit; việc xóa từng tin phải tách khỏi xóa dữ liệu kế hoạch đã lưu.
- Type lưu đánh giá tiến độ/nhắc bài dở, cờ tài khoản thử nghiệm, bộ lọc báo cáo theo thời điểm và chính sách streak khi tài khoản bị khóa.
- Thời hạn JWT/token email, gửi lại email, nhà cung cấp email, heartbeat/dung sai, giới hạn API và model Gemini. Đây là cấu hình triển khai; tên model sẽ do chủ dự án bổ sung sau, không tự ghi một model đã được chọn.

Không coi 15 nhóm quyết định và 5 quyết định tiếp nối là đã chạy trong mã nguồn. Mục 4 vẫn là hiện trạng lúc khảo sát; cập nhật lần này chỉ sửa tài liệu.

## 25. Thuật ngữ và nguồn tham khảo

### 25.1. Thuật ngữ

| Thuật ngữ | Giải thích |
| --- | --- |
| BMR/REE | Chỉ số năng lượng nền/nghỉ ước tính; trong dự án tên lưu là calculated_bmr |
| TDEE | Năng lượng cả ngày ước tính từ thể trạng và mức vận động |
| MET | Mức tiêu hao tương đối của hoạt động, dùng ước tính calo |
| Macro | Protein, carbohydrate và chất béo, biểu diễn bằng gram |
| Set/rep | Hiệp/lần lặp trong mỗi hiệp |
| Active/rest/elapsed | Thời gian vận động/nghỉ/toàn bộ khoảng thời gian |
| Plan/session/attempt | Lịch dự kiến/buổi tập thực tế/lần thực hiện bài |
| Check-in | Ghi nhận ngày có ít nhất một bài được công nhận |
| Streak | Chuỗi các ngày check-in, được bảo toàn qua ngày nghỉ hợp lệ |
| Master data | Kho chuẩn foods/categories/exercises/badges do Admin quản lý |
| Data-Driven | Quy tắc huy hiệu nằm trong dữ liệu; Java có bộ tính các metric hỗ trợ |
| Entity/Repository/Service/Controller/DTO | Mô hình lưu trữ/truy xuất/nghiệp vụ/cửa API/hợp đồng dữ liệu |
| Snapshot | Bản lưu thông số tại thời điểm ghi nhận để giữ ý nghĩa lịch sử |
| Idempotency | Gửi/xử lý lại cùng thao tác không nhân đôi kết quả nghiệp vụ |
| Transaction | Nhóm thay đổi được xác nhận cùng nhau hoặc không ghi một phần |

### 25.2. Nguồn đối chiếu

- [Cuộc trò chuyện Gemini do chủ dự án cung cấp](https://share.gemini.google/mNOqJUxaVToU): yêu cầu ban đầu và phương án đã trao đổi; các câu trả lời trực tiếp ngày 17/10/2026 trong chat dự án ưu tiên hơn nội dung nguồn cũ.
- `fitness_ai.backup`, `fitnessai_erd_updated.drawio`: nguồn khảo sát cấu trúc đã mô tả ở mục 18. Đường dẫn Temp của máy gốc không phải điều kiện để đọc hiểu PLAN; mục 18 đã chép lại các bảng, trường và quan hệ cần thiết.
- Mã nguồn repository tại ngày khảo sát: `pom.xml`, `application.properties`, class ứng dụng, `entity/User.java` và test `contextLoads`; đây là nguồn cho mục hiện trạng, không phải bằng chứng đã triển khai chức năng tương lai.
- [Mifflin et al., 1990 — nghiên cứu công thức REE](https://pubmed.ncbi.nlm.nih.gov/2305711/): đối chiếu công thức nam/nữ rút gọn ở mục 8; không cung cấp hệ số `OTHER` hoặc bộ hệ số TDEE trong PLAN.
- [Compendium of Physical Activities](https://pacompendium.com/): đối chiếu định nghĩa MET; dữ liệu MET thực cho từng exercise phải được quản trị có nguồn, không lấy ví dụ tính toán làm giá trị chuẩn.
- [Hibernate ORM — mục 2.8 Automatic schema export](https://docs.hibernate.org/orm/current/introduction/html_single/): đối chiếu cơ chế sinh/quản lý schema. Trang tài liệu current có thể thay đổi phiên bản; khi code phải kiểm tra đúng Hibernate được Spring Boot 4.1.1 quản lý.
- [AAOS — Healthy Bones at Every Age](https://www.orthoinfo.org/staying-healthy/healthy-bones-at-every-age/) và [NIDDK — GH/sụn tăng trưởng](https://www.niddk.nih.gov/health-information/endocrine-diseases/acromegaly): đối chiếu cách diễn đạt phát triển xương, không dùng tuổi phân nhóm như chẩn đoán cá nhân.
- [Takahashi et al. — Growth hormone secretion during sleep](https://www.jci.org/articles/view/105893) và [NHLBI — Why Is Sleep Important?](https://www.nhlbi.nih.gov/health/sleep/why-sleep-important): đối chiếu vai trò ngủ và đỉnh GH theo giấc ngủ, không khẳng định một khung giờ cố định hoặc cam kết tăng cm.

### 25.3. Điều kiện bàn giao sản phẩm theo PLAN

Sản phẩm được coi là hoàn thiện trong phạm vi đã thống nhất khi: người mới thực hiện được hành trình sử dụng; Admin vận hành được kho dữ liệu và tài khoản; lịch được chấp nhận mới có hiệu lực; check-in/streak/ngày nghỉ đúng quy tắc; tính toán có nguồn và kiểm chứng; lịch sử theo giờ Việt Nam nhất quán; AI lỗi không tạo dữ liệu giả; dữ liệu cá nhân được phân quyền; các kịch bản nghiệm thu liên quan đã có kết quả chạy thật.

Khi có quyết định mới, cập nhật mục 3, mục chức năng tương ứng, mô hình dữ liệu và kịch bản nghiệm thu cùng nhau. Không chỉ sửa một ví dụ rồi để quy tắc ở phần khác mâu thuẫn.

Bổ sung 02/11/2026 cho mục 11.7/19.7: Admin được dùng media_url là link ảnh/GIF/video trực tiếp HTTP/HTTPS bên cạnh file upload. GIF/PNG/JPG/JPEG/WebP dùng hiển thị ảnh; MP4/WebM dùng trình phát. Link GitHub trang xem tệp /blob/ được chuẩn hóa sang raw khi lưu/hiển thị. Không tải binary về backend khi dùng link ngoài; nguồn ngoài có thể hết hạn/chặn nhúng, UI cần báo lỗi và có liên kết mở riêng. URL chọn vào kế hoạch được snapshot; request cũ không gửi mediaUrl giữ dữ liệu đã upload, chuỗi rỗng mới gỡ liên kết. Không bổ sung bảng/cột cho thay đổi này.
