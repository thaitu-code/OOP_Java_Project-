// Disposable HTTP data for frontend checks. No credentials, proxy, database, or real nutritional advice.
function fixture(now = () => Date.now()) {
  const clone = value => JSON.parse(JSON.stringify(value));
  const iso = () => new Date(now()).toISOString();
  const today = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(now()));
  const mealTypes = ["BREAKFAST", "LUNCH", "AFTERNOON", "DINNER"];
  const categories = [
    { categoryId: 1, categoryName: "Thực phẩm mẫu", description: "Dữ liệu mô phỏng để kiểm thử giao diện", active: true },
    { categoryId: 2, categoryName: "Nhóm đã ngừng", description: null, active: false }
  ];
  const foods = Array.from({ length: 12 }, (_, i) => ({
    foodId: i + 1, foodName: ["Cơm trắng", "Ức gà", "Trứng", "Bông cải xanh", "Chuối", "Sữa chua", "Yến mạch", "Khoai lang", "Cá hồi", "Táo", "Đậu phụ", "Bơ"][i],
    description: "Thực phẩm dùng trong kiểm thử giao diện. Các chỉ số dưới đây là dữ liệu mô phỏng.", source: "Fixture kiểm thử; không dùng để tư vấn dinh dưỡng", imageUrl: null,
    caloriesPer100g: 123.45, proteinPer100g: 12.34, carbsPer100g: 23.45, fatPer100g: 4.56,
    categoryId: i === 11 ? 2 : 1, categoryName: i === 11 ? categories[1].categoryName : categories[0].categoryName,
    categoryActive: i !== 11, active: true, createdAt: iso(), updatedAt: iso()
  }));
  const logs = [], details = [];
  let nextDetail = 1;
  const ok = (data, status = 200) => ({ status, data: clone(data) });
  const fail = (status, message) => ok(message ? { message } : null, status);
  const totals = rows => Object.fromEntries(["Calories", "Protein", "Carbs", "Fat"].map(key => [key.toLowerCase(), rows.reduce((sum, d) => sum + Math.round(d[`calculated${key}`] * 100), 0) / 100]));
  function daily(date) {
    const log = logs.find(l => l.logDate === date);
    const rows = details.filter(d => d.logId === log?.logId);
    return { logId: log?.logId ?? null, logDate: date, createdAt: log?.createdAt ?? null, totals: totals(rows), meals: mealTypes.map(mealType => ({ mealType, items: rows.filter(d => d.mealType === mealType), totals: totals(rows.filter(d => d.mealType === mealType)) })) };
  }
  const calculate = (value, grams) => Math.floor((Math.round(value * 100) * Math.round(grams * 100) + 5000) / 10000) / 100;
  function apply(detail, food, body) {
    if (detail.foodId !== food.foodId) {
      Object.assign(detail, { foodId: food.foodId, foodNameSnapshot: food.foodName, categoryNameSnapshot: food.categoryName, imageUrlSnapshot: food.imageUrl });
      for (const nutrient of ["calories", "protein", "carbs", "fat"]) detail[`${nutrient}Per100gSnapshot`] = food[`${nutrient}Per100g`];
    }
    Object.assign(detail, { mealType: body.mealType, weightGrams: body.weightGrams, note: body.note ?? null, updatedAt: iso(), lastModifiedSource: "USER" });
    for (const key of ["Calories", "Protein", "Carbs", "Fat"]) detail[`calculated${key}`] = calculate(detail[`${key.toLowerCase()}Per100gSnapshot`], detail.weightGrams);
  }
  function page(rows, u) {
    const number = Number(u.searchParams.get("page") || 0), size = Number(u.searchParams.get("size") || 20);
    return { content: rows.slice(number * size, (number + 1) * size), page: { number, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) } };
  }
  function route(method, url, body = {}) {
    const u = new URL(url, "http://localhost"), p = u.pathname;
    if (p === "/nutrition-logs" && method === "GET") return ok(daily(u.searchParams.get("date") || today()));
    if (p === "/nutrition-logs/history" && method === "GET") return ok(page([...logs].sort((a, b) => b.logDate.localeCompare(a.logDate)).map(l => ({ ...l, itemCount: details.filter(d => d.logId === l.logId).length, totals: daily(l.logDate).totals })), u));
    if (["/food-categories", "/admin/food-categories"].includes(p) && method === "GET") return ok(categories.filter(c => p.startsWith("/admin") || c.active));
    if (["/foods", "/admin/foods"].includes(p) && method === "GET") {
      const keyword = (u.searchParams.get("keyword") || "").toLowerCase();
      return ok(page(foods.filter(f => (p.startsWith("/admin") || f.active && f.categoryActive) && f.foodName.toLowerCase().includes(keyword) && (!u.searchParams.get("categoryId") || f.categoryId === Number(u.searchParams.get("categoryId")))), u));
    }
    const foodPath = p.match(/^\/foods\/(\d+)$/);
    if (foodPath && method === "GET") {
      const f = foods.find(f => f.foodId === Number(foodPath[1]) && f.active && f.categoryActive);
      return f ? ok(f) : fail(404);
    }
    if (p === "/nutrition-logs/today/details" && method === "POST") {
      const food = foods.find(f => f.foodId === body.foodId && f.active && f.categoryActive);
      if (!food) return fail(400, "Thực phẩm không khả dụng");
      let log = logs.find(l => l.logDate === today());
      if (!log) { log = { logId: logs.length + 1, logDate: today(), createdAt: iso() }; logs.push(log); }
      const detail = { detailId: nextDetail++, logId: log.logId, loggedAt: iso(), createdSource: "USER", version: 0 };
      apply(detail, food, body); details.push(detail);
      return ok(daily(today()), 201);
    }
    const entry = p.match(/^\/nutrition-logs\/today\/details\/(\d+)$/);
    if (entry) {
      const detail = details.find(d => d.detailId === Number(entry[1]) && logs.find(l => l.logId === d.logId)?.logDate === today());
      if (!detail) return fail(404);
      const version = method === "DELETE" ? Number(u.searchParams.get("expectedVersion")) : body.expectedVersion;
      if (version !== detail.version) return fail(409, "Món ăn đã thay đổi. Vui lòng tải lại nhật ký trước khi sửa hoặc xóa");
      if (method === "DELETE") details.splice(details.indexOf(detail), 1);
      else if (method === "PUT") {
        const food = foods.find(f => f.foodId === body.foodId);
        if (!food || food.foodId !== detail.foodId && (!food.active || !food.categoryActive)) return fail(400, "Thực phẩm không khả dụng");
        apply(detail, food, body); detail.version++;
      } else return fail(405);
      return ok(daily(today()));
    }
    const categoryPath = p.match(/^\/admin\/food-categories(?:\/(\d+))?(?:\/(activate|deactivate))?$/);
    if (categoryPath && method !== "GET") {
      let c = categories.find(c => c.categoryId === Number(categoryPath[1]));
      if (categoryPath[1] && !c) return fail(404);
      if (method === "PATCH") { c.active = categoryPath[2] === "activate"; foods.filter(f => f.categoryId === c.categoryId).forEach(f => { f.categoryActive = c.active; }); }
      else {
        if (categories.some(other => other !== c && other.categoryName.toLowerCase() === body.categoryName.toLowerCase())) return fail(400, "Tên nhóm thực phẩm đã tồn tại");
        if (!c) { c = { categoryId: categories.length + 1, active: true }; categories.push(c); }
        Object.assign(c, body, { updatedAt: iso() });
        foods.filter(f => f.categoryId === c.categoryId).forEach(f => { f.categoryName = c.categoryName; });
      }
      return ok(c, method === "POST" ? 201 : 200);
    }
    const adminFood = p.match(/^\/admin\/foods(?:\/(\d+))?(?:\/(activate|deactivate))?$/);
    if (adminFood && method !== "GET") {
      let f = foods.find(f => f.foodId === Number(adminFood[1]));
      if (adminFood[1] && !f) return fail(404);
      if (method === "PATCH") f.active = adminFood[2] === "activate";
      else {
        const c = categories.find(c => c.categoryId === body.categoryId);
        if (!c || c.categoryId !== f?.categoryId && !c.active) return fail(400, "Nhóm thực phẩm đã ngừng hoạt động");
        if (!f) { f = { foodId: foods.length + 1, active: true, createdAt: iso() }; foods.push(f); }
        Object.assign(f, body, { categoryName: c.categoryName, categoryActive: c.active, updatedAt: iso() });
      }
      return ok(f, method === "POST" ? 201 : 200);
    }
    return fail(404);
  }
  return { route, foods, categories, logs, details, today, daily };
}
module.exports = { fixture };
