/* Nutrition UI. Kết quả đã lưu và tổng dinh dưỡng luôn do backend xác nhận. */
(() => {
  "use strict";
  const app = document.getElementById("nutrition-app");
  if (!app) return;
  const $ = id => document.getElementById(id);
  const admin = app.dataset.admin === "true";
  const base = (app.dataset.base || "/").replace(/\/$/, "");
  const meals = { BREAKFAST: ["Bữa sáng", "☀"], LUNCH: ["Bữa trưa", "◉"], AFTERNOON: ["Bữa phụ chiều", "◷"], DINNER: ["Bữa tối", "☾"] };
  const nutrients = ["calories", "protein", "carbs", "fat"];
  const numberFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });
  const dayFormat = new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" });
  const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  const fmt = value => value === null || value === undefined || !Number.isFinite(Number(value)) ? "—" : numberFormat.format(Number(value));
  const dateLabel = value => /^\d{4}-\d{2}-\d{2}$/.test(value || "") ? value.split("-").reverse().join("/") : "—";
  function today() {
    const parts = Object.fromEntries(dayFormat.formatToParts(new Date()).map(p => [p.type, p.value]));
    return `${parts.year}-${parts.month}-${parts.day}`;
  }
  const state = {
    tab: "diary", date: today(), day: null, busy: false, authenticated: true, uncertain: false,
    categories: [], adminCategories: [], foods: [], adminFoods: [],
    foodPage: 0, historyPage: 0, managePage: 0,
    epochs: {}, loaded: {}, pages: {}, dialogSubmit: null, dialogEpoch: 0,
    draft: null, pickerPage: 0, pending: new Set()
  };
  const report = (error, type = "error") => notice(error.message || "Không thể thực hiện thao tác.", type);
  function notice(message, type = "info") {
    $("nutrition-notice").textContent = message;
    $("nutrition-notice").className = `n-notice ${type}`;
    $("nutrition-notice").hidden = false;
  }
  function modalError(message) {
    $("nutrition-dialog-error").textContent = message;
    $("nutrition-dialog-error").hidden = !message;
  }
  function safeImage(value) {
    if (!value) return null;
    try {
      let url = new URL(value);
      if (!["http:", "https:"].includes(url.protocol) || !url.hostname || url.username || url.password) return null;
      // Link /blob/ là trang HTML của GitHub. Dùng raw khi hiển thị và lưu URL mới.
      if (url.hostname.toLowerCase() === "github.com") {
        if (!/^\/[^/]+\/[^/]+\/(blob|raw)\/[^/]+\/.+/.test(url.pathname)) return null;
        const path = url.pathname.replace(/^(\/[^/]+\/[^/]+)\/(blob|raw)\//, "$1/");
        url = new URL("https://raw.githubusercontent.com" + path + url.search + url.hash);
      }
      return /\.(png|jpe?g|webp|gif)$/i.test(url.pathname) ? url.href : null;
    } catch { return null; }
  }
  function image(value, name, thumbnail = false) {
    const url = safeImage(value);
    const fallback = `<span class="${thumbnail ? "n-thumb-placeholder" : "n-food-placeholder"}" aria-hidden="true">◌</span>`;
    return url ? `<img class="${thumbnail ? "n-thumb" : "n-food-image"}" src="${esc(url)}" alt="${esc(name)}" loading="lazy" referrerpolicy="no-referrer"><span class="${thumbnail ? "n-thumb-placeholder" : "n-food-placeholder"}" aria-hidden="true" hidden>◌</span>` : fallback;
  }
  const button = (action, label, id = "", style = "secondary", extra = "") => `<button class="n-btn small ${style}" type="button" data-action="${action}" data-id="${esc(id)}" ${extra}>${label}</button>`;
  const empty = message => `<div class="n-empty"><span class="n-empty-symbol" aria-hidden="true">◌</span>${esc(message)}</div>`;
  const nutri = (value, snapshot = false) => `<div class="n-nutrients"><span>Đạm <b>${fmt(value[snapshot ? "proteinPer100gSnapshot" : "proteinPer100g"])}</b> g</span><span>Tinh bột <b>${fmt(value[snapshot ? "carbsPer100gSnapshot" : "carbsPer100g"])}</b> g</span><span>Chất béo <b>${fmt(value[snapshot ? "fatPer100gSnapshot" : "fatPer100g"])}</b> g</span></div>`;
  const items = () => state.day?.meals?.flatMap(meal => meal.items) || [];
  const currentEntry = id => items().find(item => item.detailId === id);
  const editable = () => state.authenticated && state.date === today() && state.day?.logDate === state.date && !state.loaded.diaryLoading;

  async function api(path, method = "GET", body) {
    const writing = method !== "GET";
    const headers = { Accept: "application/json" };
    if (writing) {
      const token = document.querySelector('meta[name="_csrf"]')?.content;
      if (!token) throw new Error("Không có mã bảo vệ phiên. Hãy tải lại trang sau khi đăng nhập.");
      headers[document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN"] = token;
    }
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), credentials: "same-origin", cache: "no-store", signal: controller.signal });
      if (response.redirected || response.status === 401) {
        state.authenticated = false;
        throw new Error("Phiên đăng nhập đã hết. Hãy đăng nhập lại để tiếp tục.");
      }
      const text = await response.text();
      let data = null;
      try { data = text ? JSON.parse(text) : null; }
      catch {
        const error = new Error("Máy chủ trả dữ liệu không hợp lệ. Hãy tải lại trang.");
        error.uncertain = writing; throw error;
      }
      if (!response.ok) {
        const fallback = { 400: "Dữ liệu chưa hợp lệ. Kiểm tra các trường vừa nhập.", 403: "Bạn không có quyền thao tác hoặc phiên bảo vệ đã hết hạn. Hãy tải lại trang.", 404: "Dữ liệu không còn tồn tại. Hãy làm mới trước khi thao tác lại.", 409: "Dữ liệu đã thay đổi. Đóng hộp thoại và mở lại từ nhật ký mới nhất." };
        const message = response.status < 500 && typeof data?.message === "string" && !data.trace ? data.message : fallback[response.status];
        const error = new Error(message || `Không thể thực hiện thao tác (HTTP ${response.status}).`);
        error.status = response.status;
        error.uncertain = writing && response.status >= 500;
        throw error;
      }
      if (!data || typeof data !== "object") {
        const error = new Error("Máy chủ chưa trả về kết quả hợp lệ. Hãy làm mới để kiểm tra.");
        error.uncertain = writing; throw error;
      }
      return data;
    } catch (error) {
      if (error.name === "AbortError" || error instanceof TypeError) {
        const failure = new Error(writing ? "Chưa xác nhận được kết quả lưu. Hãy đóng hộp thoại và làm mới để kiểm tra trước khi gửi lại." : "Không tải được dữ liệu. Kiểm tra kết nối và thử làm mới.");
        failure.uncertain = writing; throw failure;
      }
      throw error;
    } finally { clearTimeout(timeout); syncControls(); }
  }
  function begin(key) {
    state.epochs[key] = (state.epochs[key] || 0) + 1;
    return state.epochs[key];
  }
  function pageData(data) {
    if (!Array.isArray(data?.content) || !data.page || !Number.isInteger(data.page.number) || !Number.isInteger(data.page.totalPages)) throw new Error("Dữ liệu phân trang không hợp lệ.");
    return data;
  }
  function pagination(key, data) {
    state.pages[key] = data.page;
    $(`${key}-page`).textContent = data.page.totalElements ? `Trang ${data.page.number + 1} / ${data.page.totalPages} · ${data.page.totalElements} ${key === "history" ? "ngày" : "món"}` : "Chưa có dữ liệu";
    syncControls();
  }
  function syncControls() {
    $("nutrition-activity").hidden = !state.busy;
    app.setAttribute("aria-busy", String(state.busy));
    $("diary-date").disabled = state.busy || !state.authenticated;
    document.querySelectorAll("[data-tab]").forEach(node => { node.disabled = state.busy; });
    document.querySelectorAll("[data-action]").forEach(node => {
      node.disabled = state.busy || !state.authenticated || (node.dataset.todayOnly === "true" && !editable());
    });
    for (const key of ["foods", "history", ...(admin ? ["manage"] : [])]) {
      const page = state.pages[key];
      $(`${key}-prev`).disabled = state.busy || !state.authenticated || state.loaded[`${key}Loading`] || !page || page.number <= 0;
      $(`${key}-next`).disabled = state.busy || !state.authenticated || state.loaded[`${key}Loading`] || !page || page.number + 1 >= page.totalPages;
    }
    $("nutrition-dialog-close").disabled = state.busy;
    const form = $("nutrition-dialog-content").querySelector("form");
    if (form) {
      form.querySelectorAll("input, select, textarea").forEach(node => { node.disabled = state.busy; });
      const submit = form.querySelector('[type="submit"]');
      if (submit) submit.disabled = state.busy || state.uncertain || !state.authenticated || form.dataset.stale === "true";
    }
  }
  function renderDiary() {
    const day = state.day;
    $("diary-date").value = state.date;
    $("diary-readonly").hidden = state.date === today();
    for (const key of nutrients) $(`total-${key}`).textContent = day ? fmt(day.totals[key]) : "—";
    $("diary-caption").textContent = day ? `${dateLabel(day.logDate)} · ${items().length} món đã ghi${day.logId ? "" : " · Chưa có món ăn"}` : "Chưa tải được nhật ký.";
    if (!day) { $("diary-meals").innerHTML = empty("Không tải được nhật ký. Bấm Làm mới để thử lại."); syncControls(); return; }
    $("diary-meals").innerHTML = Object.entries(meals).map(([type, [label, symbol]]) => {
      const meal = day.meals.find(item => item.mealType === type);
      if (!meal) throw new Error("Nhật ký chưa trả đủ bốn bữa ăn.");
      return `<section class="n-card"><div class="n-card-heading"><div class="n-meal-title"><span class="n-meal-symbol" aria-hidden="true">${symbol}</span><div><h3>${label}</h3><span class="n-note">${meal.items.length} món</span></div></div>${editable() ? button("add-meal", "＋ Thêm món", type, "secondary", 'data-today-only="true"') : ""}</div><div class="n-item-list">${meal.items.length ? meal.items.map(d => `<article class="n-item"><div class="n-item-top">${image(d.imageUrlSnapshot, d.foodNameSnapshot, true)}<div class="n-item-info"><strong>${esc(d.foodNameSnapshot)}</strong><p class="n-note">${esc(d.categoryNameSnapshot)} · ${fmt(d.weightGrams)} g</p></div><span class="n-item-calories">${fmt(d.calculatedCalories)} kcal</span></div><div class="n-nutrients"><span>Đạm ${fmt(d.calculatedProtein)} g</span><span>Tinh bột ${fmt(d.calculatedCarbs)} g</span><span>Chất béo ${fmt(d.calculatedFat)} g</span></div>${d.note ? `<p class="n-note n-item-note">${esc(d.note)}</p>` : ""}${editable() ? `<div class="n-buttons">${button("edit-entry", "Sửa món", d.detailId)}${button("delete-entry", "Xóa", d.detailId, "danger")}</div>` : ""}</article>`).join("") : '<p class="n-note">Chưa có món ăn trong bữa này.</p>'}</div><div class="n-meal-subtotal"><strong>${fmt(meal.totals.calories)} kcal</strong> · Đạm ${fmt(meal.totals.protein)} g · Tinh bột ${fmt(meal.totals.carbs)} g · Chất béo ${fmt(meal.totals.fat)} g</div></section>`;
    }).join("");
    syncControls();
  }
  async function loadDiary(date = state.date) {
    const epoch = begin("diary");
    state.date = date;
    state.day = null;
    state.loaded.diaryLoading = true;
    $("diary-date").value = date;
    $("diary-meals").innerHTML = '<p class="n-loading">Đang tải các bữa ăn…</p>';
    $("diary-meals").setAttribute("aria-busy", "true");
    for (const key of nutrients) $(`total-${key}`).textContent = "—";
    syncControls();
    try {
      const day = await api(`/nutrition-logs?date=${encodeURIComponent(date)}`);
      if (epoch !== state.epochs.diary) return;
      if (day.logDate !== date || !Array.isArray(day.meals) || !day.totals) throw new Error("Dữ liệu nhật ký không khớp ngày đang xem.");
      state.day = day;
      state.loaded.diary = true;
    } catch (error) { if (epoch === state.epochs.diary) report(error); }
    finally {
      if (epoch === state.epochs.diary) {
        state.loaded.diaryLoading = false;
        $("diary-meals").setAttribute("aria-busy", "false");
        renderDiary();
      }
    }
  }
  async function loadCategories(all = false) {
    if (all && !admin) return;
    const key = all ? "adminCategories" : "categories";
    const epoch = begin(key);
    const data = await api(all ? "/admin/food-categories" : "/food-categories");
    if (epoch !== state.epochs[key]) return;
    if (!Array.isArray(data)) throw new Error("Danh sách nhóm thực phẩm không hợp lệ.");
    state[key] = data;
    if (all) renderCategories();
    else {
      const selected = $("food-category").value;
      $("food-category").innerHTML = '<option value="">Tất cả nhóm</option>' + data.map(c => `<option value="${c.categoryId}">${esc(c.categoryName)}</option>`).join("");
      $("food-category").value = data.some(c => String(c.categoryId) === selected) ? selected : "";
    }
  }
  function renderCategories() {
    $("manage-categories").innerHTML = state.adminCategories.length ? state.adminCategories.map(c => `<article class="n-category-card"><h4>${esc(c.categoryName)}</h4><span class="n-pill ${c.active ? "enabled" : "disabled"}">${c.active ? "Đang hoạt động" : "Đã ngừng"}</span><p class="n-note">${esc(c.description || "Chưa có mô tả.")}</p><div class="n-buttons">${button("edit-category", "Sửa", c.categoryId)}${button("toggle-category", c.active ? "Ngừng nhóm" : "Kích hoạt", c.categoryId)}</div></article>`).join("") : empty("Chưa có nhóm thực phẩm. Tạo nhóm trước khi thêm thực phẩm.");
    syncControls();
  }
  async function loadFoods(manage = false) {
    if (manage && !admin) return;
    const key = manage ? "manage" : "foods";
    const epoch = begin(key);
    const page = manage ? state.managePage : state.foodPage;
    const keyword = $(manage ? "manage-food-search" : "food-search").value.trim();
    const params = new URLSearchParams({ keyword, page: String(page), size: manage ? "10" : "9" });
    if (!manage && $("food-category").value) params.set("categoryId", $("food-category").value);
    state.loaded[`${key}Loading`] = true;
    state.loaded[key] = false;
    const target = $(manage ? "manage-food-rows" : "food-grid");
    target.innerHTML = manage ? '<tr><td colspan="4" class="n-loading">Đang tải thực phẩm…</td></tr>' : '<p class="n-loading">Đang tải thực phẩm…</p>';
    syncControls();
    try {
      const data = pageData(await api(`${manage ? "/admin/foods" : "/foods"}?${params}`));
      if (epoch !== state.epochs[key]) return;
      if (!data.content.length && data.page.totalElements && page > 0) {
        if (manage) state.managePage = Math.max(0, data.page.totalPages - 1); else state.foodPage = Math.max(0, data.page.totalPages - 1);
        return await loadFoods(manage);
      }
      state[manage ? "adminFoods" : "foods"] = data.content;
      if (manage) renderAdminFoods(data.content);
      else {
        $("food-count").textContent = `${data.page.totalElements} thực phẩm phù hợp · Dinh dưỡng trên 100 g`;
        target.innerHTML = data.content.length ? data.content.map(f => `<article class="n-card n-food-card"><div class="n-food-cover">${image(f.imageUrl, f.foodName)}</div><div class="n-food-content"><span class="n-category-label">${esc(f.categoryName)}</span><h3>${esc(f.foodName)}</h3><p class="n-note">${esc((f.description || "Chưa có mô tả.").slice(0, 130))}</p><p class="n-calories">${fmt(f.caloriesPer100g)} <small>kcal / 100 g</small></p>${nutri(f)}<div class="n-buttons">${button("food-info", "Chi tiết", f.foodId)}${button("add-food", "＋ Ghi món", f.foodId, "")}</div></div></article>`).join("") : empty("Chưa có thực phẩm phù hợp. Thử tên khác hoặc chọn nhóm khác.");
      }
      state.loaded[key] = true;
      pagination(key, data);
    } catch (error) {
      if (epoch !== state.epochs[key]) return;
      state.pages[key] = null;
      target.innerHTML = manage ? '<tr><td colspan="4">Không tải được thực phẩm. Bấm Làm mới để thử lại.</td></tr>' : empty("Không tải được thực phẩm. Bấm Làm mới để thử lại.");
      report(error);
    } finally { if (epoch === state.epochs[key]) { state.loaded[`${key}Loading`] = false; syncControls(); } }
  }
  function renderAdminFoods(rows) {
    $("manage-food-rows").innerHTML = rows.length ? rows.map(f => `<tr><td><strong>${esc(f.foodName)}</strong><span class="n-note">${esc(f.categoryName)}</span></td><td><strong>${fmt(f.caloriesPer100g)} kcal</strong><span class="n-note">Đạm ${fmt(f.proteinPer100g)} g · Tinh bột ${fmt(f.carbsPer100g)} g · Béo ${fmt(f.fatPer100g)} g</span></td><td><span class="n-pill ${f.active ? "enabled" : "disabled"}">${f.active ? "Đang hoạt động" : "Đã ngừng"}</span>${!f.categoryActive ? '<span class="n-note">Nhóm đã ngừng · Ẩn khỏi chọn món</span>' : ""}</td><td><div class="n-buttons">${button("edit-food", "Sửa", f.foodId)}${button("toggle-food", f.active ? "Ngừng" : "Kích hoạt", f.foodId)}</div></td></tr>`).join("") : '<tr><td colspan="4">Chưa có thực phẩm phù hợp.</td></tr>';
  }
  async function loadHistory() {
    const epoch = begin("history");
    state.loaded.historyLoading = true;
    state.loaded.history = false;
    $("nutrition-history-rows").innerHTML = '<tr><td colspan="7" class="n-loading">Đang tải lịch sử…</td></tr>';
    syncControls();
    try {
      const data = pageData(await api(`/nutrition-logs/history?page=${state.historyPage}&size=10`));
      if (epoch !== state.epochs.history) return;
      $("nutrition-history-rows").innerHTML = data.content.length ? data.content.map(row => `<tr><td><strong>${dateLabel(row.logDate)}</strong></td><td>${fmt(row.itemCount)}</td>${nutrients.map(key => `<td>${fmt(row.totals[key])}</td>`).join("")}<td>${button("view-day", "Xem nhật ký", row.logDate)}</td></tr>`).join("") : '<tr><td colspan="7">Bạn chưa có lịch sử nhật ký ăn uống.</td></tr>';
      state.loaded.history = true;
      pagination("history", data);
    } catch (error) {
      if (epoch !== state.epochs.history) return;
      state.pages.history = null;
      $("nutrition-history-rows").innerHTML = '<tr><td colspan="7">Không tải được lịch sử. Bấm Làm mới để thử lại.</td></tr>';
      report(error);
    } finally { if (epoch === state.epochs.history) { state.loaded.historyLoading = false; syncControls(); } }
  }
  function setTab(tab) {
    if (!["diary", "foods", "history", ...(admin ? ["manage"] : [])].includes(tab)) tab = "diary";
    state.tab = tab;
    document.querySelectorAll("[data-tab]").forEach(node => {
      const active = node.dataset.tab === tab;
      node.classList.toggle("active", active);
      if (active) node.setAttribute("aria-current", "page"); else node.removeAttribute("aria-current");
    });
    document.querySelectorAll(".n-panel").forEach(node => { node.hidden = node.id !== `n-panel-${tab}`; });
    history.replaceState(null, "", `#${tab}`);
  }
  async function activateTab(tab, force = false) {
    setTab(tab);
    if (state.tab === "diary" && (force || !state.loaded.diary)) await loadDiary();
    if (state.tab === "foods" && (force || !state.loaded.foods)) await Promise.all([loadCategories(), loadFoods()]);
    if (state.tab === "history" && (force || !state.loaded.history)) await loadHistory();
    if (state.tab === "manage" && (force || !state.loaded.manage)) await Promise.all([loadCategories(true), loadFoods(true)]);
  }
  function openDialog(title, html, submit = null) {
    state.dialogEpoch++;
    state.dialogSubmit = submit;
    $("nutrition-dialog-title").textContent = title;
    $("nutrition-dialog-content").innerHTML = html;
    modalError("");
    if (!$("nutrition-dialog").open) $("nutrition-dialog").showModal();
    syncControls();
    updatePickerButtons();
  }
  function closeDialog() { if (!state.busy) $("nutrition-dialog").close(); }
  function invalidateCatalog() { state.loaded.foods = false; state.loaded.manage = false; }
  function confirmAction(title, message, callback) {
    openDialog(title, `<p class="n-description">${esc(message)}</p><form class="n-form"><div class="n-buttons n-span-all"><button class="n-btn" type="submit">Xác nhận</button>${button("close-dialog", "Quay lại")}</div></form>`, callback);
  }
  async function mutate(write, success) {
    if (state.busy) return;
    if (state.uncertain) throw new Error("Hãy đóng hộp thoại và làm mới dữ liệu để xác nhận kết quả trước khi gửi tiếp.");
    state.busy = true;
    syncControls();
    let saved = false;
    try {
      const result = await write();
      saved = true;
      // Mở khóa trước khi đóng modal; callback chỉ nhận kết quả đã được server lưu.
      state.busy = false;
      success(result);
    } catch (error) {
      if (saved) {
        error = new Error("Đã nhận phản hồi lưu nhưng chưa hiển thị được kết quả. Hãy đóng hộp thoại và làm mới trước khi gửi tiếp.");
        error.uncertain = true;
      }
      state.uncertain = Boolean(error.uncertain);
      const form = $("nutrition-dialog-content").querySelector("form");
      if (form && [404, 409].includes(error.status)) form.dataset.stale = "true";
      modalError(error.message);
      report(error);
      if (error.uncertain || [404, 409].includes(error.status)) {
        state.loaded.history = false;
        invalidateCatalog();
        await loadDiary();
      }
    } finally { state.busy = false; syncControls(); }
  }
  function acceptDiary(day, message) {
    if (!day?.logDate || !day.totals || !Array.isArray(day.meals) || day.meals.length !== 4) {
      throw new Error("Kết quả nhật ký không hợp lệ.");
    }
    begin("diary");
    state.day = day; state.date = day.logDate;
    state.loaded.diary = true; state.loaded.diaryLoading = false; state.loaded.history = false;
    $("diary-meals").setAttribute("aria-busy", "false");
    closeDialog(); setTab("diary"); renderDiary(); notice(message, "success");
  }
  function requireToday() {
    if (!editable()) throw new Error("Chỉ được sửa nhật ký hôm nay. Hãy bấm Hôm nay và tải lại dữ liệu.");
  }
  function rememberDraft() {
    const form = $("nutrition-dialog-content").querySelector("#entry-form");
    if (!form || !state.draft) return;
    state.draft.mealType = form.elements.mealType.value;
    state.draft.weightGrams = form.elements.weightGrams.value;
    state.draft.note = form.elements.note.value;
  }
  function entryForm(food, entry = null, mealType = "BREAKFAST") {
    const draft = state.draft;
    const existing = entry || draft?.entry || null;
    const sameFood = existing?.foodId === food.foodId;
    state.draft = { entry: existing, food, mealType: draft?.mealType || existing?.mealType || mealType, weightGrams: draft?.weightGrams ?? existing?.weightGrams ?? "100", note: draft?.note ?? existing?.note ?? "" };
    const d = state.draft;
    const name = sameFood ? existing.foodNameSnapshot : food.foodName;
    const category = sameFood ? existing.categoryNameSnapshot : food.categoryName;
    const calories = sameFood ? existing.caloriesPer100gSnapshot : food.caloriesPer100g;
    openDialog(existing ? "Sửa món ăn" : "Ghi món ăn hôm nay", `<div class="n-summary"><strong>${esc(name)}</strong><p>${esc(category)} · ${fmt(calories)} kcal / 100 g</p>${nutri(sameFood ? existing : food, sameFood)}<p class="n-note">${sameFood ? "Giữ thông tin dinh dưỡng đã lưu khi sửa khối lượng. Đổi thực phẩm sẽ dùng thông tin của món mới." : "Dinh dưỡng sẽ được tính theo khối lượng khi bạn lưu món."}</p>${button("change-food", "Đổi thực phẩm")}</div><form id="entry-form" class="n-form"><label>Bữa ăn<select name="mealType" required>${Object.entries(meals).map(([key, [label]]) => `<option value="${key}" ${key === d.mealType ? "selected" : ""}>${label}</option>`).join("")}</select></label><label>Khối lượng (g)<input name="weightGrams" type="number" required min="0.01" max="99999.99" step="0.01" value="${esc(d.weightGrams)}"></label><label class="n-span-all">Ghi chú<textarea name="note" maxlength="500" placeholder="Ví dụ: bữa ăn sau buổi tập…">${esc(d.note)}</textarea></label><p class="n-note n-span-all">Ghi vào nhật ký ngày ${dateLabel(today())} theo giờ Việt Nam.</p><div class="n-buttons n-span-all"><button class="n-btn" type="submit">${existing ? "Lưu thay đổi" : "Ghi món ăn"}</button>${button("close-dialog", "Hủy")}</div></form>`, async form => {
      requireToday();
      const body = { foodId: food.foodId, mealType: form.elements.mealType.value, weightGrams: Number(form.elements.weightGrams.value), note: form.elements.note.value.trim() || null };
      if (existing) {
        if (!Number.isSafeInteger(existing.version) || existing.version < 0) throw new Error("Version món ăn không hợp lệ. Hãy tải lại nhật ký.");
        body.expectedVersion = existing.version;
      }
      await mutate(() => api(`/nutrition-logs/today/details${existing ? "/" + existing.detailId : ""}`, existing ? "PUT" : "POST", body), day => acceptDiary(day, existing ? "Đã cập nhật món ăn và tổng dinh dưỡng." : "Đã ghi món ăn vào nhật ký hôm nay."));
    });
  }
  function picker() {
    openDialog("Chọn thực phẩm", `<div class="n-filters"><label class="n-search">Tìm theo tên<input id="picker-search" type="search" maxlength="150" placeholder="Nhập tên thực phẩm…"></label><label>Nhóm thực phẩm<select id="picker-category"><option value="">Tất cả nhóm</option></select></label></div><div id="picker-list" class="n-picker-list"></div><nav class="n-pagination" aria-label="Phân trang chọn thực phẩm">${button("picker-prev", "← Trước") }<span id="picker-page">—</span>${button("picker-next", "Sau →")}</nav>${state.draft?.food ? button("back-entry", "← Trở lại món đang nhập") : ""}`);
    state.pickerPage = 0;
    state.pages.picker = null;
    const epoch = state.dialogEpoch;
    api("/food-categories").then(categories => {
      if (epoch !== state.dialogEpoch || !$("picker-category")) return;
      state.categories = categories;
      $("picker-category").innerHTML = '<option value="">Tất cả nhóm</option>' + categories.map(c => `<option value="${c.categoryId}">${esc(c.categoryName)}</option>`).join("");
    }).catch(error => { if (epoch === state.dialogEpoch) modalError(error.message); });
    loadPicker().catch(error => modalError(error.message));
  }
  async function loadPicker() {
    const epoch = begin("picker");
    const dialogEpoch = state.dialogEpoch;
    const params = new URLSearchParams({ keyword: $("picker-search").value.trim(), page: String(state.pickerPage), size: "5" });
    if ($("picker-category").value) params.set("categoryId", $("picker-category").value);
    $("picker-list").innerHTML = '<p class="n-loading">Đang tải thực phẩm…</p>';
    state.pages.picker = null;
    updatePickerButtons();
    const data = pageData(await api(`/foods?${params}`));
    if (epoch !== state.epochs.picker || dialogEpoch !== state.dialogEpoch) return;
    state.pages.picker = data.page;
    $("picker-list").innerHTML = data.content.length ? data.content.map(f => `<article class="n-picker-item">${image(f.imageUrl, f.foodName, true)}<div><strong>${esc(f.foodName)}</strong><p class="n-note">${esc(f.categoryName)} · ${fmt(f.caloriesPer100g)} kcal / 100 g</p></div>${button("pick-food", "Chọn món", f.foodId, "")}</article>`).join("") : empty("Không có món phù hợp. Thử từ khóa hoặc nhóm khác.");
    $("picker-page").textContent = data.page.totalElements ? `Trang ${data.page.number + 1} / ${data.page.totalPages}` : "Chưa có dữ liệu";
    updatePickerButtons();
  }
  function updatePickerButtons() {
    const page = state.pages.picker;
    for (const [action, disabled] of [["picker-prev", !page || !page.number], ["picker-next", !page || page.number + 1 >= page.totalPages]]) {
      const node = $("nutrition-dialog-content").querySelector(`[data-action="${action}"]`);
      if (node) node.disabled = Boolean(disabled);
    }
  }
  async function startAdd(mealType = "BREAKFAST") {
    if (state.date !== today() || !state.day || state.loaded.diaryLoading) await loadDiary(today());
    requireToday();
    state.draft = { entry: null, food: null, mealType, weightGrams: "100", note: "" };
    picker();
  }
  function editCategory(id) {
    const c = state.adminCategories.find(item => item.categoryId === id);
    if (id && !c) throw new Error("Hãy làm mới danh sách nhóm trước khi sửa.");
    openDialog(c ? "Sửa nhóm thực phẩm" : "Tạo nhóm thực phẩm", `<form class="n-form"><label class="n-span-all">Tên nhóm<input name="categoryName" required maxlength="100" value="${esc(c?.categoryName)}"></label><label class="n-span-all">Mô tả<textarea name="description" maxlength="1000">${esc(c?.description)}</textarea></label><div class="n-buttons n-span-all"><button class="n-btn" type="submit">${c ? "Lưu thay đổi" : "Tạo nhóm"}</button>${button("close-dialog", "Hủy")}</div></form>`, async form => {
      const body = { categoryName: form.elements.categoryName.value.trim(), description: form.elements.description.value.trim() || null };
      if (!body.categoryName) throw new Error("Tên nhóm không được để trống.");
      await mutate(() => api(`/admin/food-categories${c ? "/" + c.categoryId : ""}`, c ? "PUT" : "POST", body), () => catalogSaved("Đã lưu nhóm thực phẩm."));
    });
  }
  async function editFood(id) {
    const epoch = state.dialogEpoch;
    await loadCategories(true);
    if (epoch !== state.dialogEpoch) return;
    const f = state.adminFoods.find(item => item.foodId === id);
    if (id && !f) throw new Error("Hãy làm mới danh sách thực phẩm trước khi sửa.");
    const categories = state.adminCategories.filter(c => c.active || c.categoryId === f?.categoryId);
    if (!categories.length) throw new Error("Hãy tạo hoặc kích hoạt một nhóm trước khi thêm thực phẩm.");
    const input = (name, label, attrs = "", full = false) => `<label${full ? ' class="n-span-all"' : ""}>${label}<input name="${name}" value="${esc(f?.[name])}" ${attrs}></label>`;
    openDialog(f ? "Sửa thực phẩm" : "Thêm thực phẩm", `<form class="n-form">${input("foodName", "Tên thực phẩm", 'required maxlength="150"')}<label>Nhóm thực phẩm<select name="categoryId" required>${categories.map(c => `<option value="${c.categoryId}" ${c.categoryId === f?.categoryId ? "selected" : ""}>${esc(c.categoryName)}${c.active ? "" : " · Nhóm hiện tại đã ngừng"}</option>`).join("")}</select></label><p class="n-note n-span-all">Nhập thông tin dinh dưỡng trên 100 g thực phẩm.</p>${input("caloriesPer100g", "Calo (kcal / 100 g)", 'type="number" min="0" max="99999.99" step="0.01" required')}${input("proteinPer100g", "Đạm (g / 100 g)", 'type="number" min="0" max="9999.99" step="0.01" required')}${input("carbsPer100g", "Tinh bột (g / 100 g)", 'type="number" min="0" max="9999.99" step="0.01" required')}${input("fatPer100g", "Chất béo (g / 100 g)", 'type="number" min="0" max="9999.99" step="0.01" required')}<label class="n-span-all">Mô tả<textarea name="description" maxlength="2000">${esc(f?.description)}</textarea></label>${input("source", "Nguồn thông tin", 'maxlength="500"', true)}${input("imageUrl", "URL ảnh trực tiếp (không bắt buộc)", 'type="url" maxlength="500" placeholder="https://.../thuc-pham.jpg"', true)}<p class="n-note n-span-all">Hỗ trợ ảnh PNG, JPG, JPEG, WebP hoặc GIF qua HTTP/HTTPS.</p><div class="n-buttons n-span-all"><button class="n-btn" type="submit">${f ? "Lưu thay đổi" : "Thêm thực phẩm"}</button>${button("close-dialog", "Hủy")}</div></form>`, async form => {
      const body = Object.fromEntries(new FormData(form));
      body.foodName = body.foodName.trim();
      if (!body.foodName) throw new Error("Tên thực phẩm không được để trống.");
      for (const key of ["description", "source", "imageUrl"]) body[key] = body[key].trim() || null;
      if (body.imageUrl) {
        const imageUrl = safeImage(body.imageUrl);
        if (!imageUrl) throw new Error("Dùng URL ảnh PNG/JPG/JPEG/WebP/GIF trực tiếp qua HTTP hoặc HTTPS, hoặc link file ảnh GitHub.");
        body.imageUrl = imageUrl;
      }
      for (const key of ["categoryId", "caloriesPer100g", "proteinPer100g", "carbsPer100g", "fatPer100g"]) body[key] = Number(body[key]);
      await mutate(() => api(`/admin/foods${f ? "/" + f.foodId : ""}`, f ? "PUT" : "POST", body), () => catalogSaved("Đã lưu thực phẩm. Nhật ký đã ghi giữ nguyên thông tin."));
    });
  }
  function catalogSaved(message) {
    closeDialog(); invalidateCatalog(); notice(message, "success");
    Promise.all([loadCategories(), ...(admin ? [loadCategories(true), loadFoods(true)] : [])]).catch(error => report(error, "warning"));
  }
  const actions = {
    refresh: async () => {
      if ($("nutrition-dialog").open) closeDialog();
      if (state.uncertain) {
        await loadDiary();
        if (!state.day) throw new Error("Chưa xác nhận được nhật ký. Hãy thử làm mới lại.");
        state.uncertain = false;
      }
      await activateTab(state.tab, true);
    },
    today: async () => { setTab("diary"); await loadDiary(today()); },
    "view-day": async date => { setTab("diary"); await loadDiary(date); },
    "add-today": () => startAdd(),
    "add-meal": type => startAdd(type),
    "add-food": async id => {
      const epoch = state.dialogEpoch;
      if (state.date !== today() || !state.day) await loadDiary(today());
      requireToday(); state.draft = null;
      const food = await api(`/foods/${id}`);
      if (epoch !== state.dialogEpoch) return;
      entryForm(food);
    },
    "edit-entry": id => {
      requireToday(); const entry = currentEntry(id);
      if (!entry) throw new Error("Món ăn không còn trong nhật ký. Hãy làm mới.");
      state.draft = null; entryForm({ foodId: entry.foodId }, entry);
    },
    "delete-entry": id => {
      requireToday(); const entry = currentEntry(id);
      if (!entry || !Number.isSafeInteger(entry.version)) throw new Error("Không tìm thấy món hoặc version hợp lệ. Hãy làm mới.");
      confirmAction("Xóa món ăn?", `Xóa ${entry.foodNameSnapshot} (${fmt(entry.weightGrams)} g) khỏi nhật ký hôm nay? Tổng dinh dưỡng sẽ được cập nhật.`, async () => {
        requireToday();
        await mutate(() => api(`/nutrition-logs/today/details/${id}?expectedVersion=${entry.version}`, "DELETE"), day => acceptDiary(day, "Đã xóa món ăn và cập nhật tổng dinh dưỡng."));
      });
    },
    "change-food": () => { rememberDraft(); picker(); },
    "back-entry": () => entryForm(state.draft.food),
    "pick-food": async id => {
      const epoch = state.dialogEpoch;
      const food = await api(`/foods/${id}`);
      if (epoch === state.dialogEpoch) entryForm(food);
    },
    "picker-prev": () => { if (state.pages.picker?.number > 0) { state.pickerPage--; return loadPicker(); } },
    "picker-next": () => { if (state.pages.picker && state.pages.picker.number + 1 < state.pages.picker.totalPages) { state.pickerPage++; return loadPicker(); } },
    "food-info": async id => {
      const epoch = state.dialogEpoch;
      const f = await api(`/foods/${id}`);
      if (epoch !== state.dialogEpoch) return;
      openDialog(f.foodName, `${safeImage(f.imageUrl) ? `<img class="n-detail-image" src="${esc(safeImage(f.imageUrl))}" alt="${esc(f.foodName)}" referrerpolicy="no-referrer">` : ""}<p class="n-category-label">${esc(f.categoryName)}</p><p class="n-description">${esc(f.description || "Chưa có mô tả.")}</p><div class="n-summary"><strong>${fmt(f.caloriesPer100g)} kcal / 100 g</strong>${nutri(f)}</div>${f.source ? `<p class="n-note">Nguồn thông tin: ${esc(f.source)}</p>` : ""}<div class="n-buttons">${button("add-food", "＋ Ghi món hôm nay", f.foodId, "")}${button("close-dialog", "Đóng")}</div>`);
    },
    "new-category": () => editCategory(), "edit-category": editCategory,
    "new-food": () => editFood(), "edit-food": editFood,
    "toggle-category": id => {
      const c = state.adminCategories.find(item => item.categoryId === id);
      if (!c) throw new Error("Hãy làm mới danh sách nhóm.");
      confirmAction(c.active ? "Ngừng nhóm thực phẩm?" : "Kích hoạt nhóm?", c.active ? `Thực phẩm trong nhóm ${c.categoryName} sẽ bị ẩn khỏi thư viện chọn món. Nhật ký đã lưu được giữ nguyên.` : `Cho phép chọn thực phẩm đang hoạt động trong nhóm ${c.categoryName} trở lại.`, () => mutate(() => api(`/admin/food-categories/${id}/${c.active ? "deactivate" : "activate"}`, "PATCH"), () => catalogSaved("Đã cập nhật trạng thái nhóm.")));
    },
    "toggle-food": id => {
      const f = state.adminFoods.find(item => item.foodId === id);
      if (!f) throw new Error("Hãy làm mới danh sách thực phẩm.");
      confirmAction(f.active ? "Ngừng thực phẩm?" : "Kích hoạt thực phẩm?", f.active ? `${f.foodName} sẽ không còn được chọn mới. Các món đã ghi giữ nguyên thông tin.` : `Kích hoạt ${f.foodName}. Món chỉ xuất hiện để chọn khi nhóm của nó cũng đang hoạt động.`, () => mutate(() => api(`/admin/foods/${id}/${f.active ? "deactivate" : "activate"}`, "PATCH"), () => catalogSaved("Đã cập nhật trạng thái thực phẩm.")));
    },
    "close-dialog": closeDialog
  };
  async function run(key, work) {
    if (state.busy || state.pending.has(key)) return;
    state.pending.add(key);
    try { await work(); }
    catch (error) { report(error); if ($("nutrition-dialog").open) modalError(error.message); }
    finally { state.pending.delete(key); syncControls(); updatePickerButtons(); }
  }
  const adminActions = new Set(["new-category", "edit-category", "toggle-category", "new-food", "edit-food", "toggle-food"]);
  document.addEventListener("click", event => {
    const tab = event.target.closest("[data-tab]");
    if (tab && !tab.disabled) { activateTab(tab.dataset.tab).catch(report); return; }
    const node = event.target.closest("[data-action]");
    if (!node || node.disabled || state.busy || !state.authenticated) return;
    const action = node.dataset.action;
    if (!actions[action] || (!admin && adminActions.has(action))) return;
    const id = ["view-day", "add-meal"].includes(action) ? node.dataset.id : Number(node.dataset.id);
    run("action", () => actions[action](id));
  });
  $("nutrition-dialog-close").addEventListener("click", closeDialog);
  $("nutrition-dialog").addEventListener("cancel", event => { if (state.busy) event.preventDefault(); });
  $("nutrition-dialog").addEventListener("close", () => { state.dialogEpoch++; state.dialogSubmit = null; state.draft = null; });
  $("nutrition-dialog-content").addEventListener("submit", event => {
    event.preventDefault();
    if (!event.target.checkValidity() || !state.dialogSubmit || state.busy || state.uncertain || event.target.dataset.stale === "true") return;
    const submit = state.dialogSubmit;
    run("submit", () => submit(event.target));
  });
  function debounce(callback, wait = 300) {
    let timer;
    return () => { clearTimeout(timer); timer = setTimeout(() => { if (!state.busy) callback(); }, wait); };
  }
  $("food-search").addEventListener("input", debounce(() => { state.foodPage = 0; loadFoods().catch(report); }));
  $("food-category").addEventListener("change", () => { state.foodPage = 0; loadFoods().catch(report); });
  if (admin) $("manage-food-search").addEventListener("input", debounce(() => { state.managePage = 0; loadFoods(true).catch(report); }));
  const pickerSearch = debounce(() => { if ($("picker-search")) { state.pickerPage = 0; loadPicker().catch(error => modalError(error.message)); } });
  $("nutrition-dialog-content").addEventListener("input", event => { if (event.target.id === "picker-search") pickerSearch(); });
  $("nutrition-dialog-content").addEventListener("change", event => { if (event.target.id === "picker-category") { state.pickerPage = 0; loadPicker().catch(error => modalError(error.message)); } });
  $("diary-date").addEventListener("change", event => {
    if (state.busy) return;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(event.target.value)) { event.target.value = state.date; return; }
    loadDiary(event.target.value).catch(report);
  });
  for (const key of ["foods", "history", ...(admin ? ["manage"] : [])]) {
    for (const [direction, offset] of [["prev", -1], ["next", 1]]) {
      $(`${key}-${direction}`).addEventListener("click", () => {
        if (state.busy || $(`${key}-${direction}`).disabled) return;
        const field = key === "foods" ? "foodPage" : key === "history" ? "historyPage" : "managePage";
        state[field] += offset;
        (key === "history" ? loadHistory() : loadFoods(key === "manage")).catch(report);
      });
    }
  }
  document.addEventListener("error", event => {
    const img = event.target;
    if (!img.matches?.(".n-thumb, .n-food-image, .n-detail-image")) return;
    img.hidden = true;
    if (img.nextElementSibling?.matches(".n-thumb-placeholder, .n-food-placeholder")) img.nextElementSibling.hidden = false;
  }, true);
  let lastToday = today();
  function checkDay() {
    const current = today();
    if (current === lastToday) return;
    lastToday = current;
    state.loaded.history = false;
    if (state.day) renderDiary();
    notice("Đã sang ngày mới theo giờ Việt Nam. Bấm Hôm nay để ghi món cho ngày mới.", "info");
  }
  document.addEventListener("visibilitychange", () => { if (!document.hidden) checkDay(); });
  setInterval(checkDay, 30000);
  window.addEventListener("offline", () => notice("Bạn đang ngoại tuyến. Dữ liệu chưa gửi sẽ không tự động được lưu. Hãy kiểm tra kết nối trước khi tiếp tục.", "warning"));
  window.addEventListener("pageshow", event => {
    if (event.persisted) { state.loaded = {}; state.day = null; closeDialog(); activateTab(state.tab, true).catch(report); }
  });
  $("diary-date").value = state.date;
  const initialTab = location.hash.slice(1) || (app.dataset.manage === "true" ? "manage" : "diary");
  activateTab(initialTab).catch(report);
})();
