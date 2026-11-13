/* Workout UI: backend xác nhận thời gian, kết quả và check-in. Token chỉ ở bộ nhớ tab. */
(() => {
  "use strict";
  const app = document.getElementById("workout-app");
  if (!app) return;
  const $ = id => document.getElementById(id);
  const admin = app.dataset.admin === "true";
  const base = app.dataset.base.replace(/\/$/, "");
  const heartbeatMs = Math.max(1000, Number(app.dataset.heartbeat || 10) * 1000);
  const timeoutMs = Math.max(heartbeatMs, Number(app.dataset.timeout || 30) * 1000);
  const ongoing = ["IN_PROGRESS", "RESTING", "PAUSED", "FROZEN"];
  const labels = { NOT_STARTED: "Chưa bắt đầu", IN_PROGRESS: "Đang tập", RESTING: "Đang nghỉ", PAUSED: "Tạm dừng", FROZEN: "Mất kết nối", COMPLETED: "Hoàn thành", CANCELLED: "Đã hủy" };
  const state = { exercises: [], categories: [], workout: null, details: [], selected: null,
    timer: null, token: null, busy: false, ready: false, authenticated: true, lastSync: 0,
    nextPoll: 0, page: 0, totalPages: 0, tab: "session", modalSubmit: null, detached: false,
    queued: 0, background: false, pendingActions: new Set(), catalogVersion: 0,
    historyCache: new Map(), historyRequests: new Map(), historyEpoch: 0, hiddenAt: 0,
    entryGeneration: 0, recoveredEntry: -1 };
  let workQueue = Promise.resolve();
  let noticeTimeout;
  let libraryRenderKey = "";
  let detailsRenderKey = "";
  const numberFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });
  const dateFormat = new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" });
  const esc = value => String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
  const fmt = value => numberFormat.format(Number(value) || 0);
  const date = value => value ? dateFormat.format(new Date(value)) : "—";
  const setText = (id, value) => { if ($(id).textContent !== value) $(id).textContent = value; };
  const clock = value => { const seconds = Math.max(0, Math.floor((Number(value) || 0) / 1000)); return `${Math.floor(seconds / 60).toString().padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`; };
  const pill = value => `<span class="w-pill ${esc(value)}">${esc(labels[value] || value)}</span>`;
  const detail = () => state.details.find(item => item.detailId === state.selected);
  const timerBusy = () => [...state.pendingActions].some(key => /^(entry-recover:|start:|pause:|rest:|resume:|rest-duration$|select-detail:|refresh:|complete-detail$|modal-submit$)/.test(key));
  const path = () => `/workouts/${state.workout.workoutId}/details/${state.selected}`;
  const safeVideo = value => { try { const url = new URL(value); return ["https:", "http:"].includes(url.protocol) ? url.href : null; } catch { return null; } };
  const videoLink = value => { const url = safeVideo(value); return url ? `<a class="w-btn small secondary" href="${esc(url)}" target="_blank" rel="noopener noreferrer">Xem video ↗</a>` : ""; };
  const directMedia = value => {
    if (!value) return null;
    if (/^\/exercise-media\/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\.(gif|mp4|webm)$/.test(value))
      return { url: base + value, video: /\.(mp4|webm)$/.test(value) };
    try {
      let url = new URL(value);
      if (!["https:", "http:"].includes(url.protocol) || !url.hostname || url.username || url.password) return null;
      if (url.hostname.toLowerCase() === "github.com" && /^\/[^/]+\/[^/]+\/(blob|raw)\/.+/.test(url.pathname)) {
        const path = url.pathname.replace(/^(\/[^/]+\/[^/]+)\/(blob|raw)\//, "$1/");
        url = new URL("https://raw.githubusercontent.com" + path + url.search + url.hash);
      }
      if (!/\.(gif|png|jpe?g|webp|mp4|webm)$/i.test(url.pathname)) return null;
      return { url: url.href, video: /\.(mp4|webm)$/i.test(url.pathname) };
    } catch { return null; }
  };
  const mediaMarkup = value => {
    const media = directMedia(value); if (!media) return "";
    const url = esc(media.url);
    const element = media.video ? `<video class="inline-exercise-media" src="${url}" controls playsinline preload="metadata" aria-label="Video động tác bài tập"></video>`
      : `<img class="inline-exercise-media" src="${url}" alt="Động tác bài tập" loading="lazy" referrerpolicy="no-referrer">`;
    return `<div class="motion-media">${element}<p class="media-failure w-note" hidden>Không tải được ảnh/video. Link có thể hết hạn hoặc không cho hiển thị trực tiếp. <a href="${url}" target="_blank" rel="noopener noreferrer">Mở liên kết ↗</a></p></div>`;
  };

  function notice(message, type = "info") {
    clearTimeout(noticeTimeout);
    setText("app-notice", message);
    $("app-notice").className = `w-notice w-toast ${type}`;
    const close = document.createElement("button");
    close.type = "button"; close.className = "toast-close"; close.textContent = "×";
    close.setAttribute("aria-label", "Ẩn thông báo");
    close.addEventListener("click", () => { $("app-notice").hidden = true; });
    $("app-notice").append?.(close);
    $("app-notice").hidden = false;
    if (type !== "error") noticeTimeout = setTimeout(() => { $("app-notice").hidden = true; }, 8000);
  }
  async function api(url, method = "GET", body, empty404 = false) {
    const headers = { Accept: "application/json" };
    if (method !== "GET") {
      const csrf = document.querySelector('meta[name="_csrf"]')?.content;
      if (!csrf) throw new Error("Không có mã bảo vệ phiên. Hãy tải lại trang sau khi đăng nhập.");
      headers[document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN"] = csrf;
    }
    const multipart = typeof FormData !== "undefined" && body instanceof FormData;
    if (body !== undefined && !multipart) headers["Content-Type"] = "application/json";
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), multipart ? 120000 : 15000);
    try {
      const response = await fetch(base + url, { method, headers, body: multipart ? body : body === undefined ? undefined : JSON.stringify(body), credentials: "same-origin", cache: "no-store", signal: controller.signal });
      if (response.redirected || response.status === 401) {
        state.authenticated = false;
        state.token = null;
        throw new Error("Phiên đăng nhập đã hết. Vui lòng đăng nhập lại để tiếp tục.");
      }
      const text = await response.text();
      if (empty404 && response.status === 404 && !text.trim()) return null;
      let data;
      try { data = text ? JSON.parse(text) : null; } catch { throw new Error("Ứng dụng không trả dữ liệu hợp lệ. Kiểm tra phiên đăng nhập hoặc tải lại trang."); }
      if (!response.ok) {
        const fallback = { 400: "Dữ liệu chưa hợp lệ. Kiểm tra các trường vừa nhập.", 403: "Bạn không có quyền thao tác hoặc phiên bảo vệ đã hết hạn. Hãy tải lại trang.", 404: "Không tìm thấy dữ liệu. Hãy làm mới trang.", 409: "Trạng thái đã thay đổi. Hãy làm mới và thử lại." };
        // Không đưa trace hoặc HTML lỗi server vào giao diện.
        const message = typeof data?.message === "string" && !data.trace ? data.message : fallback[response.status];
        const error = new Error(message || `Không thể thực hiện thao tác (HTTP ${response.status}).`);
        error.status = response.status;
        throw error;
      }
      return data;
    } catch (error) {
      if (error.name === "AbortError") throw new Error("Yêu cầu quá thời gian chờ. Hãy làm mới để kiểm tra kết quả trước khi thử lại.");
      if (error instanceof TypeError) throw new Error("Mất kết nối tới máy chủ. Hãy kiểm tra mạng và khôi phục điều khiển khi quay lại.");
      throw error;
    } finally { clearTimeout(timeout); }
  }

  function updateActivity() {
    app.setAttribute("aria-busy", String(state.queued > 0));
    const activity = $("workout-activity");
    if (activity) {
      activity.hidden = !state.queued;
      activity.textContent = state.background && state.queued
        ? "Đã nhận thao tác, đang chờ đồng bộ hoàn tất…" : "Đang xử lý…";
    }
    updateControls();
  }
  function perform(work, timerChange = false, { background = false, button, key } = {}) {
    if (!state.authenticated || (background && (state.busy || state.queued))) return Promise.resolve();
    if (key && state.pendingActions.has(key)) return Promise.resolve();
    if (key) state.pendingActions.add(key);
    const oldLabel = button?.textContent;
    if (button) { button.disabled = true; button.textContent = "Đang xử lý…"; }
    if (!background) { state.queued++; $("app-notice").hidden = true; clearTimeout(noticeTimeout); }
    if (key?.startsWith("add:")) { renderDetails(); renderLibrary(); }
    updateActivity();
    const task = workQueue.then(async () => {
      state.busy = true;
      state.background = background;
      updateActivity();
      try { if (state.authenticated && !state.detached) await work(); }
      catch (error) {
        if (timerChange) state.token = null;
        notice(error.message, "error");
        // Chỉ đọc lại khi kết quả ghi chưa chắc chắn; không tự gửi lại request ghi.
        if (state.authenticated && (state.ready || key?.startsWith("entry-recover:"))) {
          try { await loadSession(); } catch { /* Giữ lỗi ban đầu. */ }
        }
      } finally {
        state.busy = false;
        state.background = false;
        if (!background) state.queued--;
        if (key) state.pendingActions.delete(key);
        if (button) {
          button.disabled = false;
          if (button.textContent === "Đang xử lý…") button.textContent = oldLabel;
        }
        if (key?.startsWith("add:")) { renderDetails(); renderLibrary(); }
        updateActivity();
      }
    });
    workQueue = task.catch(() => {});
    return task;
  }

  async function loadCatalog() {
    const [exercises, categories] = await Promise.all([api("/exercises"), api("/exercise-categories")]);
    state.exercises = exercises;
    state.categories = categories;
    state.catalogVersion++;
    renderCatalog();
  }
  async function loadCheckIn() {
    const summary = await api("/check-ins/summary");
    setText("stat-checkin", summary.checkedInToday ? "Đã check-in ✓" : "Chưa check-in");
    setText("stat-streak", `${summary.currentStreak} ngày`);
  }
  async function loadSession() {
    const snapshot = await api("/workouts/current/state", "GET", undefined, true);
    const workout = snapshot?.workout || null;
    if (state.workout?.workoutId !== workout?.workoutId) {
      state.selected = null; state.token = null; state.timer = null;
    }
    state.workout = workout;
    state.details = snapshot?.details || [];
    if (!state.details.some(d => d.detailId === state.selected)) {
      state.selected = (state.details.find(d => ongoing.includes(d.status)) || state.details.find(d => d.status === "NOT_STARTED") || state.details[0])?.detailId ?? null;
      state.token = null; state.timer = null;
    }
    if (snapshot?.timer?.detail.detailId === state.selected) applyTimer(snapshot.timer, false);
    else state.timer = null;
    renderSession();
  }
  async function loadTimer() {
    if (!state.workout || !state.selected) return;
    const workoutId = state.workout.workoutId;
    const selectedId = state.selected;
    const result = await api(path() + "/timer");
    if (state.workout?.workoutId === workoutId && state.selected === selectedId) applyTimer(result, false);
  }
  function recoverOnEntry({ read = false } = {}) {
    const generation = state.entryGeneration;
    if (state.detached || !state.authenticated || state.recoveredEntry === generation) return Promise.resolve();
    state.recoveredEntry = generation;
    // Chỉ một lần cho mỗi lần vào trang; polling/lỗi token không gọi đường này.
    return perform(async () => {
      if (read) await loadSession();
      if (generation !== state.entryGeneration || state.detached || state.token) return;
      const active = state.details.find(d => ongoing.includes(d.status));
      if (!state.workout || !active) return;
      const workoutId = state.workout.workoutId;
      state.selected = active.detailId; renderDetails(); renderTimer();
      const result = await api(`/workouts/${workoutId}/details/${active.detailId}/recover`, "PATCH");
      if (generation !== state.entryGeneration || state.detached
          || state.workout?.workoutId !== workoutId || state.selected !== active.detailId) return;
      applyTimer(result, true);
      state.nextPoll = Date.now() + heartbeatMs;
      renderSession();
    }, true, { key: `entry-recover:${generation}` });
  }
  function applyTimer(timer, takeToken) {
    state.timer = timer;
    if (takeToken) state.token = state.detached ? null : timer.timerToken || null;
    const index = state.details.findIndex(d => d.detailId === timer.detail.detailId);
    if (index !== -1) state.details[index] = timer.detail;
    if (!ongoing.includes(timer.detail.status) || timer.detail.status === "FROZEN") state.token = null;
    state.lastSync = Date.now();
  }
  function acceptDetail(result) {
    invalidateHistory();
    const index = state.details.findIndex(d => d.detailId === result.detailId);
    if (index < 0) state.details.push(result); else state.details[index] = result;
    state.details.sort((a, b) => a.detailId - b.detailId);
    if (!state.selected) { state.selected = result.detailId; state.timer = null; }
    // Chỉ cộng kết quả đã được backend xác nhận; không ước tính calo hay thời gian tập.
    if (state.workout) {
      const completed = state.details.filter(d => d.status === "COMPLETED");
      state.workout.totalDurationMinutes = completed.reduce((sum, d) => sum + Math.round(Number(d.durationMinutes) * 10), 0) / 10;
      state.workout.totalCaloriesBurned = completed.reduce((sum, d) => sum + Math.round(Number(d.caloriesBurned) * 10), 0) / 10;
    }
    renderSession();
  }
  function acceptClosedWorkout() {
    invalidateHistory();
    state.workout = null; state.details = []; state.selected = null; state.timer = null; state.token = null;
    renderSession();
  }
  function acceptCategory(result) {
    state.catalogVersion++;
    const index = state.categories.findIndex(c => c.categoryId === result.categoryId);
    if (index < 0) state.categories.push(result); else state.categories[index] = result;
    state.exercises.forEach(e => { if (e.categoryId === result.categoryId) e.categoryName = result.categoryName; });
    renderCatalog();
  }
  function acceptExercise(result) {
    state.catalogVersion++;
    const index = state.exercises.findIndex(e => e.exerciseId === result.exerciseId);
    if (index < 0) state.exercises.push(result); else state.exercises[index] = result;
    renderCatalog();
  }
  function renderCatalog() {
    const selected = $("category-filter").value;
    $("category-filter").innerHTML = '<option value="">Tất cả nhóm</option>' + state.categories.map(c => `<option value="${c.categoryId}">${esc(c.categoryName)}${c.active ? "" : " · Đã ngừng nhóm"}</option>`).join("");
    $("category-filter").value = selected;
    renderLibrary(); if (admin) renderManagement();
  }

  function renderSession() {
    const workout = state.workout;
    $("hero-start").disabled = !state.ready;
    setText("hero-start", workout ? "Về buổi đang tập →" : "Bắt đầu buổi tập");
    $("empty-session").hidden = Boolean(workout);
    $("live-session").hidden = !workout;
    setText("session-caption", workout ? `Buổi #${workout.workoutId} · Bắt đầu ${date(workout.startTime)} · Giờ Việt Nam` : "Một buổi tập mới, một bước gần hơn tới mục tiêu.");
    setText("stat-duration", workout ? `${fmt(workout.totalDurationMinutes)} phút` : "—");
    setText("stat-calories", workout ? `${fmt(workout.totalCaloriesBurned)} kcal` : "—");
    setText("detail-count", state.details.length);
    renderDetails();
    renderTimer();
    renderLibrary();
  }
  function renderDetails() {
    const pending = [...state.pendingActions].filter(key => key.startsWith("add:"))
      .map(key => state.exercises.find(e => e.exerciseId === Number(key.slice(4))))
      .filter(e => e && !state.details.some(d => d.exerciseIdSnapshot === e.exerciseId));
    const renderKey = JSON.stringify([state.selected, state.details.map(d => [d.detailId, d.exerciseNameSnapshot,
      d.categoryNameSnapshot, d.status, d.durationMinutes, d.caloriesBurned, d.plannedSets, d.plannedReps]), pending.map(e => e.exerciseId)]);
    if (renderKey === detailsRenderKey) return;
    detailsRenderKey = renderKey;
    $("session-details").innerHTML = state.details.length ? state.details.map((d, index) => `<article class="detail-item ${d.detailId === state.selected ? "selected" : ""}"><span class="detail-number">${String(index + 1).padStart(2, "0")}</span><div class="detail-info"><strong>${esc(d.exerciseNameSnapshot)}</strong><p class="w-note">${esc(d.categoryNameSnapshot)}${d.plannedSets ? ` · Dự kiến ${d.plannedSets} hiệp × ${d.plannedReps ?? "—"} lần` : ""}${d.status === "COMPLETED" ? ` · ${fmt(d.durationMinutes)} phút · ${fmt(d.caloriesBurned)} kcal` : ""}</p>${pill(d.status)}</div><button class="w-btn small secondary" type="button" data-action="select-detail" data-id="${d.detailId}">${d.status === "COMPLETED" || d.status === "CANCELLED" ? "Xem" : "Chọn"}</button></article>`).join("") : '<p class="w-note">Buổi tập chưa có bài nào. Chọn “Thêm bài” để mở thư viện.</p>';
    if (pending.length) {
      if (!state.details.length) $("session-details").innerHTML = "";
      const previews = pending.map(e => `<article class="detail-item pending-item" aria-busy="true"><span class="pending-spinner" aria-hidden="true"></span><div class="detail-info"><strong>${esc(e.exerciseName)}</strong><p class="w-note">Đang thêm vào buổi · chờ máy chủ xác nhận</p></div></article>`).join("");
      $("session-details").insertAdjacentHTML?.("beforeend", previews);
    }
  }
  function renderTimer() {
    const d = detail();
    const changed = $("complete-form").dataset.detail !== String(d?.detailId ?? "");
    if (changed) {
      $("complete-form").reset(); $("rest-form").reset();
      $("complete-form").dataset.detail = d ? String(d.detailId) : "";
      if (d) {
        for (const field of ["sets", "reps", "weightKg"]) {
          const suggested = field === "sets" ? d.plannedSets : field === "reps" ? d.plannedReps : null;
          $("complete-form").elements[field].value = d[field] ?? suggested ?? "";
        }
        $("rest-seconds").value = d.approvedRestSecondsSnapshot ?? "";
      }
    }
    setText("timer-name", d?.exerciseNameSnapshot || "Chọn một bài để bắt đầu");
    setText("timer-category", d ? `${d.categoryNameSnapshot} · MET ${fmt(d.metValueSnapshot)}` : "Các kết quả sẽ được lưu cho từng bài tập.");
    const plan = d?.automation;
    setText("timer-plan", plan ? `${plan.levelName} · Set ${plan.currentSet}/${plan.plannedSets} · ${plan.reps ?? "—"} reps/set · ${plan.secondsPerSet}s/set · Nghỉ ${d.approvedRestSecondsSnapshot}s · Mục tiêu ${clock(plan.estimatedSeconds * 1000)}` : d?.plannedSets ? `Kế hoạch AI: ${d.plannedSets} hiệp × ${d.plannedReps} lần/hiệp` : "");
    const motion = plan ? plan.mediaUrl || "" : state.exercises.find(e => e.exerciseId === d?.exerciseIdSnapshot)?.mediaUrl || "";
    if ($("timer-media").dataset.source !== motion) { $("timer-media").dataset.source = motion; $("timer-media").innerHTML = mediaMarkup(motion); }
    setText("timer-status", d ? labels[d.status] : "Chưa chọn bài");
    $("timer-status").className = `w-pill ${d?.status || ""}`;
    setText("approved-rest", d?.approvedRestSecondsSnapshot ? `Đã duyệt: ${d.approvedRestSecondsSnapshot} giây mỗi lần. Thay đổi chỉ áp dụng cho lần nghỉ tiếp theo.` : "Chưa duyệt thời lượng nghỉ. Nhập số giây và xác nhận trước khi nghỉ.");
    updateControls();
    tickDisplay();
  }
  function updateControls() {
    const d = detail();
    const status = d?.status;
    const owns = Boolean(state.token);
    const running = ongoing.includes(status);
    const blocked = state.details.some(item => item.detailId !== state.selected && ongoing.includes(item.status));
    const controlsBusy = timerBusy();
    const recovering = [...state.pendingActions].some(key => key.startsWith("entry-recover:"));
    const show = (id, visible, disabled = false) => { $(id).hidden = !visible; $(id).disabled = controlsBusy || !state.authenticated || disabled; };
    show("start-detail", status === "NOT_STARTED", blocked);
    show("recover-detail", running && !owns && !recovering);
    show("pause-detail", status === "IN_PROGRESS" && owns);
    show("rest-detail", !d?.automation && status === "IN_PROGRESS" && owns, !d?.approvedRestSecondsSnapshot);
    const restRemaining = Math.max(0, new Date(state.timer?.restEndsAt || 0).getTime() - Date.now());
    show("resume-detail", owns && (status === "PAUSED" || (!d?.automation && status === "RESTING")), status === "RESTING" && restRemaining > 0);
    $("rest-form").hidden = Boolean(d?.automation) || !d || !["NOT_STARTED", ...ongoing].includes(status);
    $("complete-form").hidden = !owns || (d?.automation ? !["IN_PROGRESS", "RESTING"].includes(status) || !d.automation.canFinishEarly : status !== "IN_PROGRESS");
    setText("complete-detail", d?.automation ? (d.automation.fullTargetReached ? "Kết thúc & bài tiếp theo" : "Kết thúc bài tập sớm") : "Hoàn thành bài tập");
    $("complete-detail").disabled = controlsBusy || !state.authenticated;
    $("rest-form").querySelector("button").disabled = state.pendingActions.has("rest-duration") || controlsBusy || !state.authenticated;
    $("finish-workout").disabled = controlsBusy || !state.details.some(item => item.status === "COMPLETED");
    $("lease-note").hidden = !recovering && !(running && !owns) && !(status === "NOT_STARTED" && blocked);
    setText("lease-note", recovering ? "Đang tự khôi phục bộ đếm cho bài đang dở…" : running && !owns ? "Trang này chưa điều khiển bộ đếm. Khôi phục sẽ thu hồi quyền điều khiển ở tab hoặc thiết bị khác." : "Hãy tiếp tục và hoàn thành bài đang dở trước khi bắt đầu bài này.");
  }
  function tickDisplay() {
    const d = detail();
    if (!d) {
      setText("timer-clock", "00:00");
      $("active-time").textContent = "00:00"; $("rest-time").textContent = "00:00";
      $("minimum-progress").style.width = "0%";
      return;
    }
    const active = Number(d.activeDurationMillis) || 0;
    const rest = Number(d.restDurationMillis) || 0;
    const sinceRecorded = Math.max(0, Date.now() - new Date(state.timer?.lastRecordedAt || state.lastSync).getTime());
    const stale = sinceRecorded > timeoutMs;
    let displayActive = active;
    if (d.status === "IN_PROGRESS" && state.token && !document.hidden && !stale) displayActive += sinceRecorded;
    const restLeft = d.status === "RESTING" ? Math.max(0, new Date(state.timer?.restEndsAt || 0).getTime() - Date.now()) : 0;
    setText("timer-mode", d.status === "RESTING" ? (restLeft ? "Nghỉ giữa hiệp · còn lại" : "Đã hết giờ nghỉ") : "Thời gian tập");
    setText("timer-clock", clock(d.status === "RESTING" ? (restLeft ? restLeft + 999 : 0) : displayActive));
    setText("active-time", clock(active));
    setText("rest-time", clock(rest));
    setText("timer-sync", d.status === "RESTING" ? (restLeft ? "Thời gian nghỉ vẫn chạy khi mất kết nối" : "Hết giờ nghỉ, bấm Tiếp tục tập khi sẵn sàng") : d.status === "COMPLETED" ? `${fmt(d.durationMinutes)} phút · ${fmt(d.caloriesBurned)} kcal đã lưu` : stale && state.token ? "Chưa đồng bộ gần đây · thời gian chờ xác nhận" : state.token && d.status === "IN_PROGRESS" ? "Bộ đếm hiển thị tạm tính · đồng bộ với máy chủ" : labels[d.status]);
    const source = state.exercises.find(e => e.exerciseId === d.exerciseIdSnapshot);
    const minimum = d.minDurationMinutesSnapshot ?? (d.status === "NOT_STARTED" ? source?.minDurationMinutes : null);
    $("minimum-progress").style.width = minimum ? `${Math.min(100, (active + rest) / (Number(minimum) * 60000) * 100)}%` : "0%";
    setText("minimum-note", minimum ? `Đã ghi ${clock(active + rest)} / tối thiểu ${fmt(minimum)} phút (tập + nghỉ).` : "Bài này chưa có ngưỡng thời gian đã lưu; backend sẽ kiểm tra khi bắt đầu/hoàn thành.");
    if (d.automation) {
      const plan = d.automation;
      const remaining = Math.max(0, plan.phaseRemainingMillis - (state.token && ["IN_PROGRESS", "RESTING"].includes(d.status) && !stale ? Date.now() - state.lastSync : 0));
      setText("timer-mode", d.status === "RESTING" ? "Nghỉ giữa set · tự tiếp tục khi kết nối hợp lệ" : plan.phaseRemainingMillis > 0 ? `Set ${plan.currentSet}/${plan.plannedSets} · còn lại` : "Đã hết các set · tiếp tục tập đến khi kết thúc");
      setText("timer-clock", plan.phaseRemainingMillis > 0 ? clock(remaining + 999) : clock(displayActive));
      setText("minimum-note", `Đã ghi ${clock(active + rest)} · Kết thúc sớm từ ${clock(plan.minimumMillis + 999)} (2/3) · Đầy đủ từ ${clock(plan.estimatedSeconds * 1000)}. Tính cả tập và nghỉ.`);
      $("minimum-progress").style.width = `${Math.min(100, (active + rest) / (plan.estimatedSeconds * 1000) * 100)}%`;
      setText("timer-sync", d.status === "FROZEN" ? "Mất kết nối · khôi phục để tiếp tục, không cộng bù thời gian tập" : "Tự động ghi nhận tập/nghỉ. Bấm kết thúc khi sẵn sàng.");
    }
    if (d.status === "RESTING") $("resume-detail").disabled = timerBusy() || !state.token || restLeft > 0;
  }

  function renderLibrary() {
    const keyword = $("exercise-search").value.trim().toLocaleLowerCase("vi");
    const category = $("category-filter").value;
    const active = admin ? $("active-filter").value : "active";
    const items = state.exercises.filter(e => (!category || String(e.categoryId) === category) && (active === "all" || e.active === (active === "active")) && `${e.exerciseName} ${e.categoryName}`.toLocaleLowerCase("vi").includes(keyword));
    setText("library-count", `${items.length} bài tập${state.workout ? ` · Thêm vào buổi #${state.workout.workoutId}` : " · Chọn bài để tạo buổi mới"}`);
    const renderKey = JSON.stringify([state.catalogVersion, keyword, category, active]);
    if (renderKey === libraryRenderKey) {
      for (const button of $("exercise-library").querySelectorAll?.('[data-action="add"]') || []) {
        const id = Number(button.dataset.id);
        const e = state.exercises.find(e => e.exerciseId === id);
        const added = state.details.some(d => d.exerciseIdSnapshot === id);
        const pending = state.pendingActions.has(`add:${id}`);
        button.disabled = !e?.active || added || pending;
        const label = !e?.active ? "Đã ngừng" : added ? "Đã chọn ✓" : pending ? "Đang thêm…" : state.workout ? "＋ Thêm vào buổi" : "Tạo buổi & thêm";
        if (button.textContent !== label) button.textContent = label;
      }
      return;
    }
    libraryRenderKey = renderKey;
    $("exercise-library").innerHTML = items.length ? items.map(e => {
      const added = state.details.some(d => d.exerciseIdSnapshot === e.exerciseId);
      const pending = state.pendingActions.has(`add:${e.exerciseId}`);
      return `<article class="w-card exercise-card"><div class="exercise-cover"><span class="exercise-category">${esc(e.categoryName)}</span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" aria-hidden="true"><path d="M6.5 6.5v11M17.5 6.5v11M3 9v6M21 9v6M6.5 12h11"/></svg></div><div class="exercise-card-content"><h3>${esc(e.exerciseName)}</h3><p class="exercise-description">${esc((e.description || "Chưa có mô tả hướng dẫn.").slice(0, 140))}</p><div class="exercise-meta"><span>MET <strong>${fmt(e.metValue)}</strong></span><span>${e.levels?.length ? "Mốc theo cấp độ" : `Tối thiểu <strong>${fmt(e.minDurationMinutes)} phút</strong>`}</span></div><p class="w-note">${e.levels?.length ? `${e.levels.filter(l => l.active).length} cấp độ · chọn khi thêm bài` : `${e.recommendedSets ? `${e.recommendedSets} hiệp đề xuất` : ""}${e.recommendedReps ? ` · ${e.recommendedReps} reps / hiệp` : ""}`}</p><div class="button-row"><button class="w-btn small secondary" data-action="exercise-info" data-id="${e.exerciseId}" type="button">Hướng dẫn</button><button class="w-btn small" data-action="add" data-id="${e.exerciseId}" type="button" ${!e.active || added || pending ? "disabled" : ""}>${!e.active ? "Đã ngừng" : added ? "Đã chọn ✓" : pending ? "Đang thêm…" : state.workout ? "＋ Thêm vào buổi" : "Tạo buổi & thêm"}</button></div></div></article>`;
    }).join("") : '<div class="w-card empty-state"><h3>Chưa có bài tập phù hợp</h3><p>Thử tên khác hoặc chọn một nhóm khác.</p></div>';
  }
  function invalidateHistory() { state.historyCache.clear(); state.historyEpoch++; }
  async function loadHistory({ force = false } = {}) {
    const filter = $("history-status").value;
    const page = state.page;
    const key = `${page}:${filter}`;
    const cached = state.historyCache.get(key);
    if (!force && cached && Date.now() - cached.at < 15000) { renderHistory(cached.data); return; }
    if (cached) renderHistory(cached.data);
    else $("history-rows").innerHTML = '<tr><td colspan="6" class="history-loading">Đang tải lịch sử tập luyện…</td></tr>';
    const epoch = state.historyEpoch;
    const requestKey = `${epoch}:${key}`;
    let request = state.historyRequests.get(requestKey);
    if (!request) {
      request = api(`/workouts?page=${page}&size=10${filter ? `&status=${encodeURIComponent(filter)}` : ""}`);
      state.historyRequests.set(requestKey, request);
    }
    let history;
    try { history = await request; }
    finally { if (state.historyRequests.get(requestKey) === request) state.historyRequests.delete(requestKey); }
    if (epoch !== state.historyEpoch) return;
    state.historyCache.set(key, { at: Date.now(), data: history });
    if (page === state.page && filter === $("history-status").value) renderHistory(history);
  }
  function renderHistory(history) {
    state.totalPages = history.totalPages;
    $("history-rows").innerHTML = history.content.length ? history.content.map(w => `<tr><td><strong>Buổi #${w.workoutId}</strong></td><td>${esc(date(w.startTime))}</td><td>${pill(w.status)}</td><td>${fmt(w.totalDurationMinutes)} phút</td><td>${fmt(w.totalCaloriesBurned)} kcal</td><td><button class="w-btn small secondary" data-action="history-detail" data-id="${w.workoutId}" type="button">Chi tiết</button></td></tr>`).join("") : '<tr><td colspan="6">Chưa có buổi tập trong mục này.</td></tr>';
    setText("history-page", history.totalElements ? `Trang ${history.page + 1} / ${history.totalPages} · ${history.totalElements} buổi` : "Chưa có lịch sử");
    $("history-prev").disabled = !state.page;
    $("history-next").disabled = state.page + 1 >= history.totalPages;
  }
  function setTab(tab) {
    if (tab === "manage" && !admin) tab = "session";
    if (!["session", "ai-calendar", "library", "history", "manage"].includes(tab)) tab = "session";
    state.tab = tab;
    document.querySelectorAll("[data-tab]").forEach(button => {
      const active = button.dataset.tab === tab;
      button.classList.toggle("active", active);
      if (active) button.setAttribute("aria-current", "page"); else button.removeAttribute("aria-current");
    });
    document.querySelectorAll(".workout-panel").forEach(panel => { panel.hidden = panel.id !== `panel-${tab}`; });
    history.replaceState(null, "", `#${tab}`);
  }
  function openDialog(title, html, submit) {
    setText("dialog-title", title);
    $("dialog-content").innerHTML = html;
    state.modalSubmit = submit || null;
    if (!$("workout-dialog").open) $("workout-dialog").showModal();
  }
  function confirmAction(title, message, label, callback) {
    openDialog(title, `<p class="dialog-description">${esc(message)}</p><form id="modal-form"><div class="button-row"><button class="w-btn" type="submit">${esc(label)}</button><button class="w-btn secondary" data-action="close-dialog" type="button">Quay lại</button></div></form>`, callback);
  }
  async function refreshAll() {
    invalidateHistory();
    await Promise.all([loadCatalog(), loadSession(), loadCheckIn()]);
    renderLibrary();
    if (state.tab === "history") await loadHistory();
  }
  async function createWorkout() {
    state.workout = await api("/workouts", "POST");
    invalidateHistory();
    state.details = []; state.selected = null; state.token = null; state.timer = null;
    renderSession();
  }
  async function timerAction(action, body) {
    const result = await api(path() + "/" + action, "PATCH", body);
    applyTimer(result, true);
    state.nextPoll = Date.now() + heartbeatMs;
    renderDetails(); renderTimer();
    if (result.detail.status === "FROZEN") notice("Kết nối đã quá hạn. Thời gian mất kết nối không được cộng; hãy khôi phục để tập tiếp.", "warning");
  }
  function renderManagement() {
    $("manage-categories").innerHTML = state.categories.length ? state.categories.map(c => `<article class="category-item"><strong>${esc(c.categoryName)}</strong> <span class="w-pill ${c.active ? "enabled" : "disabled"}">${c.active ? "Hoạt động" : "Đã ngừng"}</span><div class="button-row"><button class="w-btn small secondary" data-action="edit-category" data-id="${c.categoryId}" type="button">Đổi tên</button><button class="w-btn small secondary" data-action="toggle-category" data-id="${c.categoryId}" type="button">${c.active ? "Ngừng nhóm" : "Kích hoạt"}</button></div></article>`).join("") : '<p class="w-note">Chưa có nhóm bài tập. Tạo nhóm trước khi tạo bài.</p>';
    const keyword = $("manage-search").value.trim().toLocaleLowerCase("vi");
    const items = state.exercises.filter(e => `${e.exerciseName} ${e.categoryName}`.toLocaleLowerCase("vi").includes(keyword));
    $("manage-exercises").innerHTML = items.length ? items.map(e => `<tr><td><strong>${esc(e.exerciseName)}</strong><span class="w-note">${esc(e.categoryName)}</span></td><td>${fmt(e.metValue)}</td><td>${fmt(e.minDurationMinutes)} phút</td><td><span class="w-pill ${e.active ? "enabled" : "disabled"}">${e.active ? "Hoạt động" : "Đã ngừng"}</span></td><td><div class="button-row"><button class="w-btn small secondary" data-action="edit-exercise" data-id="${e.exerciseId}" type="button">Sửa</button><button class="w-btn small secondary" data-action="manage-levels" data-id="${e.exerciseId}" type="button">Cấp độ</button><button class="w-btn small secondary" data-action="upload-media" data-id="${e.exerciseId}" type="button">GIF / Video</button><button class="w-btn small secondary" data-action="toggle-exercise" data-id="${e.exerciseId}" type="button">${e.active ? "Ngừng" : "Kích hoạt"}</button><button class="w-btn small danger-quiet" data-action="delete-exercise" data-id="${e.exerciseId}" type="button">Xóa hẳn</button></div></td></tr>`).join("") : '<tr><td colspan="5">Chưa có bài tập phù hợp.</td></tr>';
  }
  function editCategory(id) {
    const c = state.categories.find(item => item.categoryId === id);
    openDialog(c ? "Đổi tên nhóm bài tập" : "Tạo nhóm bài tập", `<form id="modal-form" class="dialog-form"><label class="span-all">Tên nhóm<input name="categoryName" required maxlength="100" value="${esc(c?.categoryName)}"></label><button type="submit" class="w-btn span-all">${c ? "Lưu thay đổi" : "Tạo nhóm"}</button></form>`, async form => {
      const name = form.elements.categoryName.value.trim();
      if (!name) throw new Error("Tên nhóm không được để trống.");
      acceptCategory(await api(`/admin/exercise-categories${c ? "/" + id : ""}`, c ? "PATCH" : "POST", { categoryName: name }));
      $("workout-dialog").close(); notice("Đã lưu nhóm bài tập.", "success");
    });
  }
  function editExercise(id) {
    const e = state.exercises.find(item => item.exerciseId === id);
    const options = state.categories.filter(c => c.active || c.categoryId === e?.categoryId);
    if (!options.length) { notice("Hãy tạo hoặc kích hoạt một nhóm trước khi tạo bài tập.", "warning"); return; }
    const input = (name, label, attrs = "") => `<label>${label}<input name="${name}" value="${esc(e?.[name])}" ${attrs}></label>`;
    openDialog(e ? "Cập nhật bài tập" : "Thêm bài tập", `<form id="modal-form" class="dialog-form">${input("exerciseName", "Tên bài tập", 'required maxlength="150"') }<label>Nhóm bài tập<select name="categoryId" required>${options.map(c => `<option value="${c.categoryId}" ${c.categoryId === e?.categoryId ? "selected" : ""}>${esc(c.categoryName)}${c.active ? "" : " · Đã ngừng (nhóm hiện tại)"}</option>`).join("")}</select></label><label class="span-all">Mô tả<textarea name="description">${esc(e?.description)}</textarea></label>${input("metValue", "MET", 'type="number" min="0.1" max="999.9" step="0.1" required')}${input("minDurationMinutes", "Ngưỡng thủ công cũ (phút, không áp dụng cho cấp độ)", 'type="number" min="0.1" max="99999.9" step="0.1" required')}${input("recommendedSets", "Số hiệp đề xuất", 'type="number" min="1" step="1"')}${input("recommendedReps", "Reps mỗi hiệp đề xuất", 'type="number" min="1" step="1"')}<label class="span-all">Link ảnh/GIF/video hiển thị trực tiếp<input type="text" inputmode="url" name="mediaUrl" maxlength="500" placeholder="https://.../dong-tac.gif" value="${esc(e?.mediaUrl)}"></label><p class="w-note span-all">Dùng link tệp GIF, PNG, JPG, WebP, MP4 hoặc WebM công khai. Để trống để gỡ media; tệp upload cũ vẫn được giữ cho lịch sử.</p><label class="span-all">Link video hướng dẫn (http/https)<input type="url" name="aiVideoUrl" maxlength="500" value="${esc(e?.aiVideoUrl)}"></label><button class="w-btn span-all" type="submit">${e ? "Lưu thay đổi" : "Tạo bài tập"}</button></form>`, async form => {
      const data = Object.fromEntries(new FormData(form));
      data.exerciseName = data.exerciseName.trim();
      if (!data.exerciseName) throw new Error("Tên bài tập không được để trống.");
      data.mediaUrl = data.mediaUrl.trim();
      if (data.mediaUrl && !directMedia(data.mediaUrl)) throw new Error("Dùng link tệp trực tiếp GIF/PNG/JPG/WebP/MP4/WebM qua HTTP hoặc HTTPS.");
      if (data.aiVideoUrl && !safeVideo(data.aiVideoUrl)) throw new Error("Video phải dùng liên kết http hoặc https.");
      for (const key of ["categoryId", "metValue", "minDurationMinutes", "recommendedSets", "recommendedReps"]) data[key] = data[key] === "" ? null : Number(data[key]);
      acceptExercise(await api(`/admin/exercises${e ? "/" + id : ""}`, e ? "PUT" : "POST", data));
      $("workout-dialog").close(); notice("Đã lưu bài tập. Kết quả tập cũ được giữ nguyên.", "success");
    });
  }

  function acceptLevel(exerciseId, level) {
    const e = state.exercises.find(item => item.exerciseId === exerciseId);
    const levels = [...(e.levels || [])];
    const index = levels.findIndex(item => item.id === level.id);
    if (index < 0) levels.push(level); else levels[index] = level;
    acceptExercise({ ...e, levels });
  }
  function showLevels(exerciseId) {
    const e = state.exercises.find(item => item.exerciseId === exerciseId);
    openDialog(`Cấp độ · ${e.exerciseName}`, `<p class="w-note">Mỗi bài có cấp độ riêng. Thời gian kết thúc sớm = 2/3 thời gian dự kiến (tập + nghỉ). Sửa cấu hình chỉ áp dụng cho lần chọn mới.</p><button class="w-btn" data-action="new-level" data-id="${exerciseId}" type="button">＋ Cấp độ</button><div class="level-list">${(e.levels || []).map(l => `<article class="category-item"><strong>${esc(l.name)}</strong><p>${l.sets} set · ${esc(l.reps ?? "—")} reps/set · ${l.secondsPerSet}s/set · Nghỉ ${l.restSeconds}s · Dự kiến ${clock(l.estimatedSeconds * 1000)} · ${l.active ? "Hoạt động" : "Đã ngừng"}</p><div class="button-row"><button class="w-btn small secondary" data-action="edit-level" data-id="${exerciseId}" data-level="${l.id}" type="button">Sửa</button><button class="w-btn small secondary" data-action="toggle-level" data-id="${exerciseId}" data-level="${l.id}" type="button">${l.active ? "Ngừng" : "Kích hoạt"}</button></div></article>`).join("") || '<p class="w-note">Chưa có cấp độ. Bài chưa có cấp độ vẫn dùng chế độ thủ công cũ.</p>'}</div>`);
  }
  function editLevel(exerciseId, levelId) {
    const e = state.exercises.find(item => item.exerciseId === exerciseId);
    const l = e.levels?.find(item => item.id === levelId);
    const field = (name, label, attrs = "") => `<label>${label}<input name="${name}" value="${esc(l?.[name])}" ${attrs}></label>`;
    openDialog(l ? "Sửa cấp độ" : "Thêm cấp độ", `<form id="modal-form" class="dialog-form">${field("name", "Tên cấp độ", 'required maxlength="100"')}${field("sets", "Số set", 'type="number" min="1" max="100" step="1" required')}${field("reps", "Reps/set (bỏ trống cho bài theo thời gian)", 'type="number" min="1" step="1"')}${field("secondsPerSet", "Thời gian mỗi set (giây)", 'type="number" min="1" max="86400" step="1" required')}${field("restSeconds", "Nghỉ giữa set (giây)", 'type="number" min="1" max="300" step="1" required')}${field("estimatedSeconds", "Thời gian hoàn thành dự kiến (giây, tập + nghỉ)", 'type="number" min="1" max="86400" step="1" required')}<p class="w-note span-all">Ngưỡng kết thúc sớm tự tính chính xác bằng 2/3 thời gian dự kiến. Hết set cuối vẫn tiếp tục đếm nếu chưa bấm kết thúc.</p><button class="w-btn span-all" type="submit">Lưu cấp độ</button></form>`, async form => {
      const data = Object.fromEntries(new FormData(form)); data.name = data.name.trim();
      for (const key of ["sets", "reps", "secondsPerSet", "restSeconds", "estimatedSeconds"]) data[key] = data[key] === "" ? null : Number(data[key]);
      const level = await api(`/admin/exercises/${exerciseId}/levels${l ? "/" + l.id : ""}`, l ? "PUT" : "POST", data);
      acceptLevel(exerciseId, level); showLevels(exerciseId); notice("Đã lưu cấp độ. Các lần tập đã chọn giữ nguyên cấu hình.", "success");
    });
  }
  function uploadMedia(exerciseId) {
    openDialog("Tải GIF / video động tác", '<form id="modal-form" class="dialog-form"><label class="span-all">Tệp GIF, MP4 hoặc WebM (tối đa 50 MB mặc định)<input name="file" type="file" accept="image/gif,video/mp4,video/webm" required></label><p class="w-note span-all">Tệp lưu trên máy chủ ứng dụng. Không đóng trang trong khi tải.</p><button class="w-btn span-all" type="submit">Tải lên</button></form>', async form => {
      const file = form.elements.file.files[0];
      if (!file) throw new Error("Hãy chọn một tệp.");
      const data = new FormData(); data.append("file", file);
      acceptExercise(await api(`/admin/exercises/${exerciseId}/media`, "POST", data));
      $("workout-dialog").close(); notice("Đã lưu GIF/video hướng dẫn.", "success");
    });
  }
  async function addWithLevel(id, levelId) {
    if (!state.workout) await createWorkout();
    const body = { exerciseId: id }; if (levelId) body.levelId = levelId;
    const added = await api(`/workouts/${state.workout.workoutId}/details`, "POST", body);
    acceptDetail(added); $("workout-dialog").close();
    notice(`Đã thêm ${added.exerciseNameSnapshot}${added.automation ? " · " + added.automation.levelName : ""}. Chọn Bắt đầu khi sẵn sàng.`, "success");
  }

  const actions = {
    refresh: async () => { await refreshAll(); state.ready = true; renderSession(); notice("Đã đồng bộ dữ liệu mới nhất.", "success"); },
    create: async () => { if(window.FitnessWizard){await window.FitnessWizard.open();return;} await createWorkout(); setTab("library"); notice("Đã tạo buổi tập. Chọn các bài bạn muốn tập.", "success"); },
    browse: () => setTab("library"),
    add: async id => {
      if(!state.workout&&window.FitnessWizard){await window.FitnessWizard.open(id);return;}
      const e = state.exercises.find(item => item.exerciseId === id);
      if (e.levels?.length) {
        const levels = e.levels.filter(l => l.active);
        if (!levels.length) throw new Error("Bài tập chưa có cấp độ đang hoạt động. Hãy liên hệ Admin.");
        openDialog(`Chọn cấp độ · ${e.exerciseName}`, `<form id="modal-form" class="dialog-form"><label class="span-all">Cấp độ<select name="levelId" required>${levels.map(l => `<option value="${l.id}">${esc(l.name)} · ${l.sets} set · ${l.reps ?? "—"} reps/set · ${l.secondsPerSet}s/set · Nghỉ ${l.restSeconds}s · ${clock(l.estimatedSeconds * 1000)}</option>`).join("")}</select></label><p class="w-note span-all">Tập/nghỉ tự động. Có thể kết thúc sớm từ 2/3 thời gian dự kiến; server xác nhận thời gian.</p><button class="w-btn span-all" type="submit">Thêm vào buổi</button></form>`, form => addWithLevel(id, Number(form.elements.levelId.value)));
      } else await addWithLevel(id);
    },
    "select-detail": async id => {
      if (id === state.selected) return;
      if (timerBusy()) { notice("Đang lưu thay đổi của bài đang chọn. Hãy đợi phản hồi trước khi chuyển bài.", "info"); return; }
      if (state.token && ongoing.includes(detail()?.status)) {
        notice("Bài đang dở vẫn được theo dõi. Hãy hoàn thành bài này trước khi chuyển sang bài khác.", "warning");
        return;
      }
      state.selected = id; state.token = null;
      if (state.timer?.detail.detailId !== id) state.timer = null;
      renderDetails(); renderTimer();
      if (ongoing.includes(detail()?.status) && !state.timer) { await loadTimer(); renderDetails(); renderTimer(); }
    },
    start: () => timerAction("start"),
    recover: () => confirmAction("Khôi phục điều khiển?", "Tab này sẽ nhận quyền điều khiển mới và ngừng quyền điều khiển ở các tab hoặc thiết bị khác. Thời gian tập mất kết nối không được cộng bù.", "Khôi phục", async () => { $("workout-dialog").close(); await timerAction("recover"); }),
    pause: () => timerAction("pause", { timerToken: state.token }),
    rest: () => timerAction("rest", { timerToken: state.token }),
    resume: () => timerAction("resume", { timerToken: state.token }),
    finish: () => confirmAction("Hoàn thành buổi tập?", "Giữ kết quả những bài đã hoàn thành và hủy những bài còn dở. Bạn có thể xem lại toàn bộ trong lịch sử.", "Hoàn thành buổi", async () => {
      const result = await api(`/workouts/${state.workout.workoutId}/complete`, "PATCH");
      if (result.status !== "COMPLETED") throw new Error("Buổi tập chưa được hoàn thành. Hãy làm mới trạng thái.");
      $("workout-dialog").close(); acceptClosedWorkout(); notice("Đã lưu buổi tập. Cảm ơn bạn đã dành thời gian cho sức khỏe hôm nay!", "success");
    }),
    "cancel-workout": () => confirmAction("Hủy buổi tập?", "Các bài chưa xong sẽ bị hủy. Kết quả và check-in từ những bài đã hoàn thành vẫn được giữ lại.", "Xác nhận hủy buổi", async () => {
      const result = await api(`/workouts/${state.workout.workoutId}/cancel`, "PATCH");
      if (result.status !== "CANCELLED") throw new Error("Buổi tập chưa được hủy. Hãy làm mới trạng thái.");
      $("workout-dialog").close(); acceptClosedWorkout(); notice("Đã hủy buổi tập và giữ nguyên những kết quả đã hoàn thành.", "success");
    }),
    "exercise-info": id => {
      const e = state.exercises.find(item => item.exerciseId === id);
      if (!e) return;
      openDialog(e.exerciseName, `<span class="exercise-category">${esc(e.categoryName)}</span><p class="dialog-description">${esc(e.description || "Chưa có mô tả hướng dẫn cho bài này.")}</p><div class="dialog-summary">MET: ${fmt(e.metValue)} · Tối thiểu: ${fmt(e.minDurationMinutes)} phút<br>Hiệp đề xuất: ${esc(e.recommendedSets ?? "Chưa có")} · Reps mỗi hiệp: ${esc(e.recommendedReps ?? "Chưa có")}</div>${mediaMarkup(e.mediaUrl)}${videoLink(e.aiVideoUrl)}`);
    },
    "history-detail": async id => {
      const [workout, details] = await Promise.all([api(`/workouts/${id}`), api(`/workouts/${id}/details`)]);
      openDialog(`Buổi tập #${id}`, `${pill(workout.status)}<div class="dialog-summary">Bắt đầu: ${esc(date(workout.startTime))}<br>Kết thúc: ${esc(date(workout.endTime))}<br>${fmt(workout.totalDurationMinutes)} phút · ${fmt(workout.totalCaloriesBurned)} kcal · Giờ Việt Nam</div><div class="table-scroll"><table class="w-table"><thead><tr><th>Bài tập</th><th>Trạng thái</th><th>Thời lượng / Calo</th><th>Kết quả</th></tr></thead><tbody>${details.map(d => `<tr><td><strong>${esc(d.exerciseNameSnapshot)}</strong><span class="w-note">${esc(d.categoryNameSnapshot)} · MET ${fmt(d.metValueSnapshot)}</span>${d.automation ? `<span class="w-note">${esc(d.automation.levelName)} · ${d.automation.plannedSets} set · ${d.automation.secondsPerSet}s/set · ${d.automation.fullTargetReached ? "Đạt mốc đầy đủ" : "Kết thúc sớm"}</span>` : ""}<span class="w-note">${d.completedAt ? "Hoàn thành " + esc(date(d.completedAt)) : ""}</span></td><td>${pill(d.status)}</td><td>${fmt(d.durationMinutes)} phút<br>${fmt(d.caloriesBurned)} kcal</td><td>${esc(d.sets ?? "—")} hiệp<br>${esc(d.reps ?? "—")} reps/hiệp<br>${esc(d.weightKg ?? "—")} kg tải</td></tr>`).join("") || '<tr><td colspan="4">Buổi chưa có bài tập.</td></tr>'}</tbody></table></div>`);
    },
    "new-category": () => editCategory(), "edit-category": editCategory,
    "new-exercise": () => editExercise(), "edit-exercise": editExercise,
    "manage-levels": showLevels, "new-level": id => editLevel(id), "edit-level": editLevel,
    "upload-media": uploadMedia,
    "toggle-level": async (id, levelId) => {
      const l = state.exercises.find(e => e.exerciseId === id).levels.find(l => l.id === levelId);
      perform(async () => { acceptLevel(id, await api(`/admin/exercises/${id}/levels/${levelId}/${l.active ? "deactivate" : "activate"}`, "PATCH")); showLevels(id); }, false, { key: `level:${levelId}` });
    },
    "toggle-category": id => {
      const c = state.categories.find(item => item.categoryId === id);
      confirmAction(c.active ? "Ngừng nhóm bài tập?" : "Kích hoạt nhóm?", c.active ? "Nhóm sẽ không nhận bài gán mới. Các bài hiện có trong nhóm vẫn được chọn tập." : "Nhóm sẽ cho phép gán bài tập mới trở lại.", "Xác nhận", async () => {
        acceptCategory(await api(`/admin/exercise-categories/${id}${c.active ? "" : "/activate"}`, c.active ? "DELETE" : "PATCH"));
        $("workout-dialog").close(); notice("Đã cập nhật trạng thái nhóm.", "success");
      });
    },
    "toggle-exercise": id => {
      const e = state.exercises.find(item => item.exerciseId === id);
      confirmAction(e.active ? "Ngừng bài tập?" : "Kích hoạt bài tập?", e.active ? "Bài sẽ không được chọn hoặc bắt đầu mới. Kết quả tập cũ được giữ nguyên." : "Bài sẽ được chọn tập trở lại.", "Xác nhận", async () => {
        acceptExercise(await api(`/admin/exercises/${id}/${e.active ? "deactivate" : "activate"}`, "PATCH"));
        $("workout-dialog").close(); notice("Đã cập nhật trạng thái bài tập.", "success");
      });
    },
    "delete-exercise": id => confirmAction("Xóa hẳn bài tập?", "Bài sẽ bị xóa khỏi thư viện; lịch sử giữ thông tin đã lưu. Nếu bài còn trong buổi chưa kết thúc, hệ thống sẽ chặn xóa.", "Xóa hẳn", async () => {
      await api(`/admin/exercises/${id}`, "DELETE");
      state.exercises = state.exercises.filter(e => e.exerciseId !== id);
      state.catalogVersion++;
      $("workout-dialog").close(); renderCatalog(); notice("Đã xóa bài khỏi thư viện. Lịch sử được giữ nguyên.", "success");
    }),
    "close-dialog": () => $("workout-dialog").close()
  };
  document.addEventListener("click", event => {
    const tab = event.target.closest("[data-tab]");
    if (tab) { setTab(tab.dataset.tab); if (state.ready && state.tab === "history") loadHistory().catch(error => notice(error.message, "error")); return; }
    const button = event.target.closest("[data-action]");
    if (!button || button.disabled || (!state.ready && button.dataset.action !== "refresh")) return;
    const action = button.dataset.action;
    if (!admin && /category|exercise$/.test(action) && action !== "exercise-info") return;
    if (!actions[action]) return;
    const id = Number(button.dataset.id);
    const local = ["browse", "select-detail", "exercise-info", "new-category", "edit-category", "new-exercise", "edit-exercise", "manage-levels", "new-level", "edit-level", "toggle-level", "upload-media", "finish", "cancel-workout", "recover", "toggle-category", "toggle-exercise", "delete-exercise", "close-dialog"];
    if (!admin && ["manage-levels", "new-level", "edit-level", "toggle-level", "upload-media"].includes(action)) return;
    if (local.includes(action)) { Promise.resolve(actions[action](id, Number(button.dataset.level))).catch(error => notice(error.message, "error")); return; }
    perform(() => actions[action](id), ["start", "pause", "rest", "resume"].includes(action), { button, key: `${action}:${id || ""}` });
  });
  document.addEventListener("error", event => {
    const media = event.target;
    if (!media?.classList?.contains?.("inline-exercise-media")) return;
    const message = media.closest(".motion-media")?.querySelector(".media-failure");
    if (message) { media.hidden = true; message.hidden = false; }
  }, true);
  $("hero-start").addEventListener("click", () => {
    if (state.workout) setTab("session");
    else perform(actions.create, false, { button: $("hero-start"), key: "create:" });
  });
  $("dialog-close").addEventListener("click", () => $("workout-dialog").close());
  $("workout-dialog").addEventListener("close", () => { state.modalSubmit = null; });
  $("dialog-content").addEventListener("submit", event => {
    event.preventDefault();
    if (!event.target.checkValidity() || !state.modalSubmit) return;
    const callback = state.modalSubmit;
    perform(async () => {
      try { await callback(event.target); }
      catch (error) {
        let warning = $("dialog-error");
        if (!warning) { warning = document.createElement("p"); warning.id = "dialog-error"; warning.className = "w-notice error"; warning.setAttribute("role", "alert"); $("dialog-content").prepend(warning); }
        warning.textContent = error.message;
        throw error;
      }
    }, false, { button: event.submitter || event.target.querySelector('button[type="submit"]'), key: "modal-submit" });
  });
  $("rest-form").addEventListener("submit", event => {
    event.preventDefault();
    if (!event.target.checkValidity()) return;
    perform(async () => {
      const response = await api(path() + "/rest-duration", "PATCH", { restSeconds: Number($("rest-seconds").value) });
      detail().approvedRestSecondsSnapshot = response.approvedRestSecondsSnapshot;
      renderTimer(); notice("Đã duyệt thời lượng nghỉ cho những lần nghỉ tiếp theo.", "success");
    }, false, { button: event.submitter || event.target.querySelector("button"), key: "rest-duration" });
  });
  $("complete-form").addEventListener("submit", event => {
    event.preventDefault();
    if (!event.target.checkValidity()) return;
    perform(async () => {
      const body = { timerToken: state.token };
      for (const key of ["sets", "reps", "weightKg"]) body[key] = event.target.elements[key].value === "" ? null : Number(event.target.elements[key].value);
      const result = await api(path() + "/complete", "PATCH", body);
      if (result.status !== "COMPLETED") { state.token = null; await loadTimer(); renderDetails(); renderTimer(); notice("Bài chưa được công nhận. Khôi phục điều khiển để tiếp tục tập.", "warning"); return; }
      state.token = null; acceptDetail(result);
      if (result.automation) {
        const next = state.details.find(d => d.detailId > result.detailId && d.status === "NOT_STARTED")
          || state.details.find(d => d.status === "NOT_STARTED");
        if (next) { state.selected = next.detailId; state.timer = null; setTab("session"); renderSession(); }
      }
      notice(`Đã hoàn thành ${result.exerciseNameSnapshot}: ${fmt(result.durationMinutes)} phút, ${fmt(result.caloriesBurned)} kcal. Check-in hôm nay đã được ghi nhận.`, "success");
      // Kết quả đã được backend lưu; thống kê đọc lại không giữ hàng đợi thao tác workout.
      loadCheckIn().catch(error => notice(error.message, "warning"));
    }, true, { button: $("complete-detail"), key: "complete-detail" });
  });
  for (const id of ["exercise-search", "category-filter", "active-filter"]) $(id).addEventListener(id === "exercise-search" ? "input" : "change", renderLibrary);
  if (admin) $("manage-search").addEventListener("input", renderManagement);
  $("history-status").addEventListener("change", () => { state.page = 0; loadHistory().catch(error => notice(error.message, "error")); });
  $("history-prev").addEventListener("click", () => { state.page = Math.max(0, state.page - 1); loadHistory().catch(error => notice(error.message, "error")); });
  $("history-next").addEventListener("click", () => { state.page++; loadHistory().catch(error => notice(error.message, "error")); });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) state.hiddenAt = Date.now();
    else if (state.ready && Date.now() - state.hiddenAt >= timeoutMs) perform(loadSession, false, { background: true });
  });
  window.addEventListener("pagehide", () => { state.entryGeneration++; state.token = null; state.detached = true; });
  window.addEventListener("pageshow", () => {
    const returning = state.detached;
    state.detached = false;
    if (returning && state.ready) recoverOnEntry({ read: true });
  });
  window.addEventListener("offline", () => { state.token = null; updateControls(); notice("Bạn đang ngoại tuyến. Khi có mạng, khôi phục điều khiển để tiếp tục tập.", "warning"); });
  setInterval(() => {
    tickDisplay();
    const plan = detail()?.automation;
    const confirmedTotal = Number(detail()?.activeDurationMillis || 0) + Number(detail()?.restDurationMillis || 0);
    const confirmations = plan ? [plan.phaseRemainingMillis,
      plan.canFinishEarly ? 0 : plan.minimumMillis - confirmedTotal,
      plan.fullTargetReached ? 0 : plan.estimatedSeconds * 1000 - confirmedTotal].filter(ms => ms > 0) : [];
    const boundaryDue = state.token && confirmations.length > 0 && ["IN_PROGRESS", "RESTING"].includes(detail()?.status)
      && Date.now() >= state.lastSync + Math.min(...confirmations);
    if (!state.ready || state.busy || state.queued || state.detached || !state.authenticated || !state.workout || !ongoing.includes(detail()?.status) || (!boundaryDue && Date.now() < state.nextPoll)) return;
    state.nextPoll = Date.now() + heartbeatMs;
    perform(async () => {
      if (state.token && ["IN_PROGRESS", "RESTING"].includes(detail()?.status)) await timerAction("heartbeat", { timerToken: state.token });
      else { await loadTimer(); renderDetails(); renderTimer(); }
    }, true, { background: true });
  }, 250);
  const hour = Number(new Intl.DateTimeFormat("en-GB", { hour: "2-digit", hourCycle: "h23", timeZone: "Asia/Ho_Chi_Minh" }).format(new Date()));
  $("hours-note").hidden = (hour >= 5 && hour < 8) || (hour >= 15 && hour < 18) || (hour >= 19 && hour < 21);
  $("active-filter-label").hidden = !admin;
  // AI Apply works on this page; refresh the in-memory session instead of changing only the URL hash.
  window.addEventListener("fitnessai:workout-updated", event => {
    setTab("session");
    perform(async () => {
      await loadSession();
      renderSession();
      notice(event.detail?.message || "Đã cập nhật buổi tập từ lịch AI.", "success");
    }, false, { key: "ai:refresh-session" });
  });
  window.addEventListener("hashchange", () => {
    setTab(location.hash.slice(1));
    if (state.tab === "session" && state.ready) {
      perform(async () => { await loadSession(); renderSession(); }, false, { key: "hash:refresh-session" });
    }
  });
  setTab(location.hash.slice(1) || (app.dataset.manage === "true" ? "manage" : "session"));
  // Các vùng tải độc lập; summary chậm không khóa thao tác thư viện/buổi tập.
  const initialSession = loadSession().then(async () => {
    await recoverOnEntry({ read: state.entryGeneration > 0 });
    // Thư viện chậm/lỗi không được ngăn heartbeat hoặc nút điều khiển của buổi đã khôi phục.
    state.ready = true; renderSession();
  });
  Promise.all([loadCatalog(), initialSession]).then(async () => {
    // Nếu rời/quay lại trong lúc khởi tạo chưa xong, xử lý đúng lần vào mới nhất.
    await recoverOnEntry({ read: state.entryGeneration > 0 });
    state.ready = true; renderSession();
    state.nextPoll = Date.now() + heartbeatMs;
    if (state.tab === "history") await loadHistory();
  }).catch(error => notice(error.message, "error"));
  loadCheckIn().catch(error => notice(error.message, "error"));
})();
