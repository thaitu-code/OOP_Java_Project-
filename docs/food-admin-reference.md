# Dữ liệu tham khảo để thêm thực phẩm bằng tài khoản admin

Đối chiếu ngày 17/11/2026. Danh sách lấy từ commit `a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c` của [Fitness_AI_Assets/foods](https://github.com/namrooney10/Fitness_AI_Assets/tree/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods).

Có 31 ảnh, tương ứng 30 thực phẩm; Chuoi-tay.avif và Chuoi-tay.webp là một món, chọn WebP để phù hợp định dạng ảnh được form hỗ trợ. README trong thư mục trống, không có công thức hay số liệu dinh dưỡng.

## Đơn vị và cách nhập

Form hiện tại yêu cầu tên, nhóm, kcal/100 g, đạm g/100 g, carbohydrate g/100 g, chất béo g/100 g; có thêm mô tả, nguồn và URL ảnh. Toàn bộ số dưới đây tính trên **100 g phần ăn được**, theo trạng thái được mô tả. Nếu cần kcal/g, chia cột kcal cho 100. Ví dụ cơm trắng 130 kcal/100 g = 1,30 kcal/g.

Ô “Tinh bột” của ứng dụng đang lưu `carbsPer100g`: nhập **carbohydrate tổng**, không chỉ tinh bột. Không cộng thêm đường hoặc chất xơ vào số carbohydrate tổng của USDA. Không đường có nghĩa là không thêm đường, không đồng nghĩa carbohydrate bằng 0.

Các nhóm trong tài liệu là gợi ý để chọn/tạo trong admin; không giả định ID nhóm trong cơ sở dữ liệu. Với món chín, cân phần ăn được sau nấu, bỏ xương/vỏ/hạt. Với yến mạch khô và cá ngừ tươi, cân đúng trạng thái ghi trong tên. Sữa dùng gram; không coi 100 ml bằng chính xác 100 g. Nếu dùng nhãn ghi trên 100 ml, phải quy đổi theo khối lượng thực tế hoặc tỷ trọng phù hợp trước khi nhập.

## Bảng tổng hợp

| STT | Tên thực phẩm | Nhóm | kcal/100 g | Đạm (g) | Carbs (g) | Béo (g) | Loại số liệu |
|---:|---|---|---:|---:|---:|---:|---|
| 1 | Bánh mì đen (lúa mạch đen) | Ngũ cốc và tinh bột | 259.00 | 8.50 | 48.30 | 3.30 | Tham chiếu |
| 2 | Bơ đậu phộng mịn | Hạt và chất béo | 598.00 | 22.21 | 22.31 | 51.36 | Tham chiếu |
| 3 | Bò lúc lắc | Thịt, cá, trứng và đậu | 200.46 | 20.83 | 4.18 | 11.35 | Ước tính công thức |
| 4 | Bơ sáp | Rau củ và trái cây | 160.00 | 2.00 | 8.53 | 14.66 | Tham chiếu giống gần tương đương |
| 5 | Cà chua | Rau củ và trái cây | 18.00 | 0.88 | 3.89 | 0.20 | Tham chiếu |
| 6 | Cá hồi áp chảo | Thịt, cá, trứng và đậu | 226.34 | 21.44 | 0.00 | 14.98 | Ước tính công thức |
| 7 | Cá ngừ đại dương (tươi) | Thịt, cá, trứng và đậu | 109.00 | 24.40 | 0.00 | 0.49 | Tham chiếu loài gần tương đương |
| 8 | Chuối tây | Rau củ và trái cây | 89.00 | 1.09 | 22.84 | 0.33 | Tham chiếu giống gần tương đương |
| 9 | Cơm gạo lứt | Ngũ cốc và tinh bột | 123.00 | 2.74 | 25.58 | 0.97 | Tham chiếu |
| 10 | Cơm trắng | Ngũ cốc và tinh bột | 130.00 | 2.69 | 28.17 | 0.28 | Tham chiếu |
| 11 | Đậu hũ non | Thịt, cá, trứng và đậu | 61.00 | 7.17 | 1.18 | 3.69 | Tham chiếu |
| 12 | Đu đủ chín | Rau củ và trái cây | 43.00 | 0.47 | 10.82 | 0.26 | Tham chiếu |
| 13 | Đùi gà nướng (có da) | Thịt, cá, trứng và đậu | 191.00 | 23.35 | 0.00 | 10.15 | Tham chiếu, chưa tính sốt |
| 14 | Hạnh nhân | Hạt và chất béo | 579.00 | 21.15 | 21.55 | 49.93 | Tham chiếu |
| 15 | Khoai lang luộc | Ngũ cốc và tinh bột | 76.00 | 1.37 | 17.72 | 0.14 | Tham chiếu |
| 16 | Măng tây nướng | Rau củ và trái cây | 47.86 | 2.33 | 3.99 | 3.21 | Ước tính, có nguyên liệu thay thế |
| 17 | Nấm rơm xào | Rau củ và trái cây | 74.60 | 3.64 | 4.41 | 5.65 | Ước tính, có nguyên liệu thay thế |
| 18 | Phô mai Con Bò Cười — Original UK (tham khảo) | Sữa và chế phẩm từ sữa | 232.00 | 11.00 | 6.50 | 18.00 | Chỉ áp dụng cho Original UK |
| 19 | Salad dưa chuột, cà chua và trứng | Rau củ và trái cây | 58.18 | 2.83 | 3.16 | 4.02 | Ước tính công thức |
| 20 | Sữa chua không đường (nguyên kem) | Sữa và chế phẩm từ sữa | 61.00 | 3.47 | 4.66 | 3.25 | Tham chiếu |
| 21 | Sữa tươi không đường (nguyên kem) | Sữa và chế phẩm từ sữa | 61.00 | 3.15 | 4.80 | 3.25 | Tham chiếu, không phải nhãn thương hiệu |
| 22 | Súp lơ xanh luộc | Rau củ và trái cây | 35.00 | 2.38 | 7.18 | 0.41 | Tham chiếu |
| 23 | Táo tây | Rau củ và trái cây | 52.00 | 0.26 | 13.81 | 0.17 | Tham chiếu |
| 24 | Bò hầm khoai tây, cà rốt | Thịt, cá, trứng và đậu | 137.83 | 16.73 | 5.74 | 5.47 | Ước tính công thức |
| 25 | Thịt ba chỉ luộc | Thịt, cá, trứng và đậu | 444.00 | 13.00 | 0.00 | 43.60 | Tham chiếu phần thịt tương đương |
| 26 | Tôm luộc | Thịt, cá, trứng và đậu | 99.00 | 23.98 | 0.20 | 0.28 | Tham chiếu |
| 27 | Trứng gà luộc | Thịt, cá, trứng và đậu | 155.00 | 12.58 | 1.12 | 10.61 | Tham chiếu |
| 28 | Ức gà luộc (bỏ da) | Thịt, cá, trứng và đậu | 151.00 | 28.98 | 0.00 | 3.03 | Tham chiếu cách nấu gần tương đương |
| 29 | Xúc xích bò (tham khảo) | Thịt, cá, trứng và đậu | 315.00 | 11.69 | 2.97 | 28.10 | Tham chiếu, chưa xác nhận sản phẩm |
| 30 | Yến mạch cán dẹt (khô) | Ngũ cốc và tinh bột | 379.00 | 13.15 | 67.70 | 6.52 | Tham chiếu |

Giá trị tham chiếu là trung bình của thực phẩm tương đương, không phải phép đo món trong ảnh. Các công thức ước tính bên dưới do người tổng hợp tự đặt để có giả định rõ ràng; **không phải công thức được tác giả ảnh cung cấp**. Nguyên liệu của công thức được cân ở trạng thái đã chín và cộng thành 100 g món; dầu là lượng còn lại trong món. Không làm tròn lại năng lượng theo 4–4–9 vì cơ sở dữ liệu có thể dùng hệ số năng lượng riêng.

## Thông tin chi tiết để sao chép vào form

### 1. Bánh mì đen (lúa mạch đen)

- **Tên thực phẩm:** Bánh mì đen (lúa mạch đen)
- **Nhóm gợi ý:** Ngũ cốc và tinh bột
- **Calo:** 259.00 kcal/100 g
- **Đạm:** 8.50 g/100 g
- **Carbohydrate (ô Tinh bột):** 48.30 g/100 g
- **Chất béo:** 3.30 g/100 g
- **Mô tả:** Tham chiếu bánh mì rye thông thường; chưa tính lớp hạt phủ trong ảnh.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 18060
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Banh-mi-den.webp

### 2. Bơ đậu phộng mịn

- **Tên thực phẩm:** Bơ đậu phộng mịn
- **Nhóm gợi ý:** Hạt và chất béo
- **Calo:** 598.00 kcal/100 g
- **Đạm:** 22.21 g/100 g
- **Carbohydrate (ô Tinh bột):** 22.31 g/100 g
- **Chất béo:** 51.36 g/100 g
- **Mô tả:** Bơ đậu phộng mịn có muối, loại phổ thông. Nếu dùng thương hiệu cụ thể, thay số liệu bằng nhãn sản phẩm.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 16098
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Bo-dau-phong.webp

### 3. Bò lúc lắc

- **Tên thực phẩm:** Bò lúc lắc
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 200.46 kcal/100 g
- **Đạm:** 20.83 g/100 g
- **Carbohydrate (ô Tinh bột):** 4.18 g/100 g
- **Chất béo:** 11.35 g/100 g
- **Mô tả:** Ước tính cho 100 g thành phẩm gồm 70 g bò thăn chín, 13 g hành tây chín, 10 g ớt chuông chín, 5 g dầu và 2 g đường. Chưa tính nước sốt khác.
- **Loại số liệu:** Ước tính công thức
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 23439,11283,11334,04053,19335 — Ước tính từ công thức trong mô tả.
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Bo-luc-lac.jpg

### 4. Bơ sáp

- **Tên thực phẩm:** Bơ sáp
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 160.00 kcal/100 g
- **Đạm:** 2.00 g/100 g
- **Carbohydrate (ô Tinh bột):** 8.53 g/100 g
- **Chất béo:** 14.66 g/100 g
- **Mô tả:** Phần thịt quả bơ tươi, bỏ vỏ và hạt. Tham chiếu bơ nói chung, chưa có số liệu riêng cho giống bơ sáp Việt Nam.
- **Loại số liệu:** Tham chiếu giống gần tương đương
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 09037
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Bo-sap.jpg

### 5. Cà chua

- **Tên thực phẩm:** Cà chua
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 18.00 kcal/100 g
- **Đạm:** 0.88 g/100 g
- **Carbohydrate (ô Tinh bột):** 3.89 g/100 g
- **Chất béo:** 0.20 g/100 g
- **Mô tả:** Cà chua đỏ chín, ăn sống, phần ăn được.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 11529
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Ca-chua.jpg

### 6. Cá hồi áp chảo

- **Tên thực phẩm:** Cá hồi áp chảo
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 226.34 kcal/100 g
- **Đạm:** 21.44 g/100 g
- **Carbohydrate (ô Tinh bột):** 0.00 g/100 g
- **Chất béo:** 14.98 g/100 g
- **Mô tả:** Ước tính cho 100 g thành phẩm gồm 97 g cá hồi Đại Tây Dương nuôi đã chín và 3 g dầu được giữ lại trong món. Chưa tính bơ và sốt.
- **Loại số liệu:** Ước tính công thức
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 15237,04053 — Ước tính từ công thức trong mô tả.
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Ca-hoi-ap-chao.jpg

### 7. Cá ngừ đại dương (tươi)

- **Tên thực phẩm:** Cá ngừ đại dương (tươi)
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 109.00 kcal/100 g
- **Đạm:** 24.40 g/100 g
- **Carbohydrate (ô Tinh bột):** 0.00 g/100 g
- **Chất béo:** 0.49 g/100 g
- **Mô tả:** Tham chiếu cá ngừ vây vàng tươi sống, không xương. Nếu cân cá sau nấu, phải dùng bản ghi cá chín.
- **Loại số liệu:** Tham chiếu loài gần tương đương
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 15127
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Ca-ngu-dai-duong.webp

### 8. Chuối tây

- **Tên thực phẩm:** Chuối tây
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 89.00 kcal/100 g
- **Đạm:** 1.09 g/100 g
- **Carbohydrate (ô Tinh bột):** 22.84 g/100 g
- **Chất béo:** 0.33 g/100 g
- **Mô tả:** Chuối chín, bỏ vỏ; tham chiếu chuối nói chung, chưa có số liệu riêng cho giống chuối tây.
- **Loại số liệu:** Tham chiếu giống gần tương đương
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 09040
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Chuoi-tay.webp

### 9. Cơm gạo lứt

- **Tên thực phẩm:** Cơm gạo lứt
- **Nhóm gợi ý:** Ngũ cốc và tinh bột
- **Calo:** 123.00 kcal/100 g
- **Đạm:** 2.74 g/100 g
- **Carbohydrate (ô Tinh bột):** 25.58 g/100 g
- **Chất béo:** 0.97 g/100 g
- **Mô tả:** Gạo lứt hạt dài đã nấu chín bằng nước, không thêm dầu.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 20037
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Com-gao-lut.jpg

### 10. Cơm trắng

- **Tên thực phẩm:** Cơm trắng
- **Nhóm gợi ý:** Ngũ cốc và tinh bột
- **Calo:** 130.00 kcal/100 g
- **Đạm:** 2.69 g/100 g
- **Carbohydrate (ô Tinh bột):** 28.17 g/100 g
- **Chất béo:** 0.28 g/100 g
- **Mô tả:** Gạo trắng hạt dài đã nấu chín bằng nước, không thêm dầu.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 20445
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Com-trang.jpg

### 11. Đậu hũ non

- **Tên thực phẩm:** Đậu hũ non
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 61.00 kcal/100 g
- **Đạm:** 7.17 g/100 g
- **Carbohydrate (ô Tinh bột):** 1.18 g/100 g
- **Chất béo:** 3.69 g/100 g
- **Mô tả:** Đậu hũ mềm dùng calcium sulfate và nigari, không chiên; sản phẩm thực tế có thể khác do lượng nước.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 16127
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Dau-hu-non.jpg

### 12. Đu đủ chín

- **Tên thực phẩm:** Đu đủ chín
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 43.00 kcal/100 g
- **Đạm:** 0.47 g/100 g
- **Carbohydrate (ô Tinh bột):** 10.82 g/100 g
- **Chất béo:** 0.26 g/100 g
- **Mô tả:** Đu đủ chín tươi, bỏ vỏ và hạt.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 09226
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Du-du-chin.webp

### 13. Đùi gà nướng (có da)

- **Tên thực phẩm:** Đùi gà nướng (có da)
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 191.00 kcal/100 g
- **Đạm:** 23.35 g/100 g
- **Carbohydrate (ô Tinh bột):** 0.00 g/100 g
- **Chất béo:** 10.15 g/100 g
- **Mô tả:** Phần thịt và da của đùi dưới gà đã nướng, bỏ xương. Chưa cộng dầu, mật ong hoặc sốt ướp.
- **Loại số liệu:** Tham chiếu, chưa tính sốt
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 05069
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Dui-ga-nuong.jpg

### 14. Hạnh nhân

- **Tên thực phẩm:** Hạnh nhân
- **Nhóm gợi ý:** Hạt và chất béo
- **Calo:** 579.00 kcal/100 g
- **Đạm:** 21.15 g/100 g
- **Carbohydrate (ô Tinh bột):** 21.55 g/100 g
- **Chất béo:** 49.93 g/100 g
- **Mô tả:** Hạnh nhân nguyên vị, không thêm dầu, đường hoặc lớp phủ.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 12061
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Hanh-nhan.jpg

### 15. Khoai lang luộc

- **Tên thực phẩm:** Khoai lang luộc
- **Nhóm gợi ý:** Ngũ cốc và tinh bột
- **Calo:** 76.00 kcal/100 g
- **Đạm:** 1.37 g/100 g
- **Carbohydrate (ô Tinh bột):** 17.72 g/100 g
- **Chất béo:** 0.14 g/100 g
- **Mô tả:** Khoai lang đã luộc, bỏ vỏ; giống khoai và lượng nước làm số liệu thay đổi.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 11510
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Khoai-lang-luoc.jpg

### 16. Măng tây nướng

- **Tên thực phẩm:** Măng tây nướng
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 47.86 kcal/100 g
- **Đạm:** 2.33 g/100 g
- **Carbohydrate (ô Tinh bột):** 3.99 g/100 g
- **Chất béo:** 3.21 g/100 g
- **Mô tả:** Ước tính cho 100 g món gồm 97 g măng tây chín và 3 g dầu. Dùng dữ liệu măng tây luộc làm gần đúng cho phần rau chín; chưa hiệu chỉnh mất nước khi nướng.
- **Loại số liệu:** Ước tính, có nguyên liệu thay thế
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 11012,04053 — Ước tính từ công thức trong mô tả.
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Mang-tay-nuong.jpg

### 17. Nấm rơm xào

- **Tên thực phẩm:** Nấm rơm xào
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 74.60 kcal/100 g
- **Đạm:** 3.64 g/100 g
- **Carbohydrate (ô Tinh bột):** 4.41 g/100 g
- **Chất béo:** 5.65 g/100 g
- **Mô tả:** Ước tính cho 100 g món gồm 95 g nấm rơm và 5 g dầu. Dùng nấm rơm đóng hộp để ráo làm gần đúng do nguồn đang dùng không có nấm rơm tươi xào; chưa tính sốt.
- **Loại số liệu:** Ước tính, có nguyên liệu thay thế
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 11989,04053 — Ước tính từ công thức trong mô tả.
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Nam-rom-xao.jpg

### 18. Phô mai Con Bò Cười — Original UK (tham khảo)

- **Tên thực phẩm:** Phô mai Con Bò Cười — Original UK (tham khảo)
- **Nhóm gợi ý:** Sữa và chế phẩm từ sữa
- **Calo:** 232.00 kcal/100 g
- **Đạm:** 11.00 g/100 g
- **Carbohydrate (ô Tinh bột):** 6.50 g/100 g
- **Chất béo:** 18.00 g/100 g
- **Mô tả:** Dữ liệu công bố cho The Laughing Cow Original tại Anh, không xác nhận là phiên bản Việt Nam trong ảnh. Chỉ dùng nếu chọn đúng phiên bản này; với sản phẩm Việt Nam cần đọc nhãn trước khi nhập.
- **Loại số liệu:** Chỉ áp dụng cho Original UK
- **Nguồn thông tin:** https://www.thelaughingcow.co.uk/products/cheese-spread/original/
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Pho-mai-con-bo-cuoi.jpg

### 19. Salad dưa chuột, cà chua và trứng

- **Tên thực phẩm:** Salad dưa chuột, cà chua và trứng
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 58.18 kcal/100 g
- **Đạm:** 2.83 g/100 g
- **Carbohydrate (ô Tinh bột):** 3.16 g/100 g
- **Chất béo:** 4.02 g/100 g
- **Mô tả:** Ước tính cho 100 g món gồm 60 g dưa chuột nguyên vỏ, 20 g cà chua, 18 g trứng luộc đã bóc vỏ và 2 g dầu. Chưa tính mayonnaise, đường và sốt khác.
- **Loại số liệu:** Ước tính công thức
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 11205,11529,01129,04053 — Ước tính từ công thức trong mô tả.
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Salad-dua-chuot.jpg

### 20. Sữa chua không đường (nguyên kem)

- **Tên thực phẩm:** Sữa chua không đường (nguyên kem)
- **Nhóm gợi ý:** Sữa và chế phẩm từ sữa
- **Calo:** 61.00 kcal/100 g
- **Đạm:** 3.47 g/100 g
- **Carbohydrate (ô Tinh bột):** 4.66 g/100 g
- **Chất béo:** 3.25 g/100 g
- **Mô tả:** Sữa chua trắng nguyên kem thông thường, không thêm đường; không dùng thay cho sữa chua Hy Lạp. Ưu tiên nhãn nếu xác định được thương hiệu.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 01116
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Sua-chua-khong-duong.webp

### 21. Sữa tươi không đường (nguyên kem)

- **Tên thực phẩm:** Sữa tươi không đường (nguyên kem)
- **Nhóm gợi ý:** Sữa và chế phẩm từ sữa
- **Calo:** 61.00 kcal/100 g
- **Đạm:** 3.15 g/100 g
- **Carbohydrate (ô Tinh bột):** 4.80 g/100 g
- **Chất béo:** 3.25 g/100 g
- **Mô tả:** Sữa nguyên kem 3,25% béo, số liệu theo 100 g. Ảnh minh họa là TH true MILK; các số này là tham chiếu sữa nói chung, không phải nhãn TH.
- **Loại số liệu:** Tham chiếu, không phải nhãn thương hiệu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 01077
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Sua-tuoi-khong-duong.jpg

### 22. Súp lơ xanh luộc

- **Tên thực phẩm:** Súp lơ xanh luộc
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 35.00 kcal/100 g
- **Đạm:** 2.38 g/100 g
- **Carbohydrate (ô Tinh bột):** 7.18 g/100 g
- **Chất béo:** 0.41 g/100 g
- **Mô tả:** Súp lơ xanh/bông cải xanh đã luộc để ráo, không thêm dầu. Ảnh là broccoli, không phải súp lơ trắng.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 11091
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Sup-lo-luoc.png

### 23. Táo tây

- **Tên thực phẩm:** Táo tây
- **Nhóm gợi ý:** Rau củ và trái cây
- **Calo:** 52.00 kcal/100 g
- **Đạm:** 0.26 g/100 g
- **Carbohydrate (ô Tinh bột):** 13.81 g/100 g
- **Chất béo:** 0.17 g/100 g
- **Mô tả:** Táo tươi còn vỏ, bỏ lõi và hạt.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 09003
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Tao-tay.jpg

### 24. Bò hầm khoai tây, cà rốt

- **Tên thực phẩm:** Bò hầm khoai tây, cà rốt
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 137.83 kcal/100 g
- **Đạm:** 16.73 g/100 g
- **Carbohydrate (ô Tinh bột):** 5.74 g/100 g
- **Chất béo:** 5.47 g/100 g
- **Mô tả:** Ước tính cho 100 g món gồm 50 g bò nạc hầm chín, 20 g khoai tây chín, 15 g cà rốt chín, 5 g hành tây chín, 2 g dầu và 8 g nước. Chưa tính đường hoặc bột làm sánh.
- **Loại số liệu:** Ước tính công thức
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 23090,11367,11125,11283,04053 — Ước tính từ công thức trong mô tả.
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Thit-bo-ham.webp

### 25. Thịt ba chỉ luộc

- **Tên thực phẩm:** Thịt ba chỉ luộc
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 444.00 kcal/100 g
- **Đạm:** 13.00 g/100 g
- **Carbohydrate (ô Tinh bột):** 0.00 g/100 g
- **Chất béo:** 43.60 g/100 g
- **Mô tả:** Tham chiếu pork belly, boiled (ba chỉ luộc) của DOST-FNRI. Ảnh có lớp mỡ rõ; số liệu này đại diện ba chỉ nhiều mỡ, không áp dụng cho thịt nạc luộc.
- **Loại số liệu:** Tham chiếu phần thịt tương đương
- **Nguồn thông tin:** https://i.fnri.dost.gov.ph/fct/library/report/3858 — Food ID F151
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Thit-lon-luoc.jpg

### 26. Tôm luộc

- **Tên thực phẩm:** Tôm luộc
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 99.00 kcal/100 g
- **Đạm:** 23.98 g/100 g
- **Carbohydrate (ô Tinh bột):** 0.20 g/100 g
- **Chất béo:** 0.28 g/100 g
- **Mô tả:** Tôm đã nấu chín, bóc vỏ và bỏ đầu; dữ liệu tôm chín chưa cấp đông trước đó.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 15271
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Tom-luoc.jpg

### 27. Trứng gà luộc

- **Tên thực phẩm:** Trứng gà luộc
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 155.00 kcal/100 g
- **Đạm:** 12.58 g/100 g
- **Carbohydrate (ô Tinh bột):** 1.12 g/100 g
- **Chất béo:** 10.61 g/100 g
- **Mô tả:** Trứng nguyên quả đã luộc chín kỹ, bóc vỏ.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 01129
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Trung-ga-luoc.jpg

### 28. Ức gà luộc (bỏ da)

- **Tên thực phẩm:** Ức gà luộc (bỏ da)
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 151.00 kcal/100 g
- **Đạm:** 28.98 g/100 g
- **Carbohydrate (ô Tinh bột):** 0.00 g/100 g
- **Chất béo:** 3.03 g/100 g
- **Mô tả:** Tham chiếu ức gà chín bằng nước (stewed), chỉ phần thịt, bỏ da và xương; dùng gần đúng cho ức gà luộc.
- **Loại số liệu:** Tham chiếu cách nấu gần tương đương
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 05065
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Uc-ga-luoc.jpeg

### 29. Xúc xích bò (tham khảo)

- **Tên thực phẩm:** Xúc xích bò (tham khảo)
- **Nhóm gợi ý:** Thịt, cá, trứng và đậu
- **Calo:** 315.00 kcal/100 g
- **Đạm:** 11.69 g/100 g
- **Carbohydrate (ô Tinh bột):** 2.97 g/100 g
- **Chất béo:** 28.10 g/100 g
- **Mô tả:** Tham chiếu xúc xích bò frankfurter ở trạng thái bán sẵn, chưa hâm lại. Ảnh không cho biết thương hiệu; chưa cộng dầu chiên hoặc sốt.
- **Loại số liệu:** Tham chiếu, chưa xác nhận sản phẩm
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 07022
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Xuc-xich-bo.webp

### 30. Yến mạch cán dẹt (khô)

- **Tên thực phẩm:** Yến mạch cán dẹt (khô)
- **Nhóm gợi ý:** Ngũ cốc và tinh bột
- **Calo:** 379.00 kcal/100 g
- **Đạm:** 13.15 g/100 g
- **Carbohydrate (ô Tinh bột):** 67.70 g/100 g
- **Chất béo:** 6.52 g/100 g
- **Mô tả:** Yến mạch cán dẹt/ăn liền không tăng cường vi chất, dạng khô, không đường. Cân trước khi pha nước; không dùng số liệu này cho cháo yến mạch chín.
- **Loại số liệu:** Tham chiếu
- **Nguồn thông tin:** https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/ — USDA SR28 NDB 08120
- **URL ảnh:** https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Yen-mach.webp

## Nguồn và lưu ý về độ phù hợp

- [USDA SR28](https://www.ars.usda.gov/northeast-area/beltsville-md-bhnrc/beltsville-human-nutrition-research-center/methods-and-application-of-food-composition-laboratory/mafcl-site-pages/sr11-sr28/): dùng bảng ABBREV.txt trong sr28abbr.zip, các mã NDB được ghi cho từng món. Đây là dữ liệu tham chiếu lịch sử, không phải nhãn sản phẩm hiện hành. [Bản ghi bộ dữ liệu trên data.gov](https://catalog.data.gov/dataset/composition-of-foods-raw-processed-prepared-usda-national-nutrient-database-for-standar-28).
- [DOST-FNRI PhilFCT, pork belly boiled, F151](https://i.fnri.dost.gov.ph/fct/library/report/3858): dùng cho thịt ba chỉ luộc; lượng mỡ của miếng thịt thực tế có thể khác nhiều.
- [The Laughing Cow Original UK](https://www.thelaughingcow.co.uk/products/cheese-spread/original/): 232 kcal, đạm 11 g, carbohydrate 6,5 g, béo 18 g/100 g. **Không xác nhận cho Con Bò Cười bán tại Việt Nam**. Nếu muốn giữ tên “Phô mai Con Bò Cười vị truyền thống Việt Nam”, cần thay cả bốn số theo nhãn đúng sản phẩm; chưa nên dùng hàng này như dữ liệu Việt Nam đã xác minh.

Các món cần chú ý thêm: bánh mì đen đang dùng rye thường, không tính lớp hạt; bơ sáp và chuối tây dùng loại quả tương đương; cá ngừ mặc định vây vàng tươi; ức gà dùng cách nấu bằng nước gần tương đương; măng tây và nấm rơm xào có nguyên liệu tham chiếu thay thế; sữa, sữa chua, bơ đậu phộng và xúc xích cần ưu tiên nhãn nếu chọn thương hiệu cụ thể. Thịt ba chỉ luộc không dùng thay thịt nạc luộc. Súp lơ là loại xanh theo ảnh.

Phạm vi thực hiện: chuẩn bị tài liệu nhập tay; chưa tạo món, nhóm, hay thay đổi dữ liệu ứng dụng.

