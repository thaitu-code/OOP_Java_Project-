// Dán toàn bộ vào Console tại /workout, sau khi đăng nhập Admin.
// Dữ liệu giả kiểm thử; MET và định mức không phải hướng dẫn tập luyện.
(async () => {
  const guard = "__fitnessSeedWorkout10Running";
  if (window[guard]) throw new Error("Script tạo bài đang chạy, hãy đợi hoàn tất.");
  window[guard] = true;
  try {
    const root = document.getElementById("workout-app");
    if (!root) throw new Error("Hãy mở trang /workout rồi chạy lại.");
    if (root.dataset.admin !== "true") throw new Error("Hãy đăng nhập bằng tài khoản Admin.");
    const base = (root.dataset.base || "/").replace(/\/$/, "");
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";
    if (!token) throw new Error("Thiếu CSRF token. Tải lại trang /workout sau khi đăng nhập.");

    async function api(path, method = "GET", body) {
      const headers = { Accept: "application/json" };
      if (method !== "GET") headers[csrfHeader] = token;
      if (body !== undefined) headers["Content-Type"] = "application/json";
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 20000);
      try {
        const response = await fetch(base + path, {
          method, headers, credentials: "same-origin", cache: "no-store",
          body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal
        });
        if (response.redirected || response.status === 401) throw new Error("Phiên đăng nhập hết hạn. Đăng nhập Admin rồi chạy lại.");
        const raw = await response.text();
        let data;
        try { data = raw ? JSON.parse(raw) : null; }
        catch { throw new Error(`${method} ${path}: HTTP ${response.status}, phản hồi không phải JSON.`); }
        if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}. ${data?.message || data?.error || "Yêu cầu thất bại"}`);
        return data;
      } finally { clearTimeout(timeout); }
    }

    const groupName = "TEST_WT_10 - Nhóm kiểm thử";
    const [groups, exercises] = await Promise.all([api("/exercise-categories"), api("/exercises")]);
    if (!Array.isArray(groups) || !Array.isArray(exercises)) throw new Error("API danh sách trả sai định dạng.");
    let group = groups.find(g => g.categoryName === groupName);
    if (group && !group.active) throw new Error(`Nhóm kiểm thử ${group.categoryId} đang ngừng hoạt động. Kích hoạt nhóm rồi chạy lại.`);
    if (!group) group = await api("/admin/exercise-categories", "POST", { categoryName: groupName });

    const labels = ["Squat", "Chống đẩy", "Lunge", "Gập bụng", "Jumping Jack",
      "Chạy tại chỗ", "Chạy bộ", "Plank", "Glute Bridge", "Bird Dog"];
    const rows = [];
    for (let i = 0; i < labels.length; i++) {
      const name = `TEST_WT_${String(i + 1).padStart(2, "0")} - ${labels[i]}`;
      let exercise = exercises.find(e => e.categoryId === group.categoryId && e.exerciseName === name);
      let action = "Có sẵn";
      if (!exercise) {
        exercise = await api("/admin/exercises", "POST", {
          exerciseName: name, categoryId: group.categoryId,
          description: "DỮ LIỆU GIẢ KIỂM THỬ WORKOUT. Không dùng làm chỉ dẫn tập luyện. Ngưỡng hoàn thành: 1 phút.",
          metValue: 1.0, minDurationMinutes: 1.0,
          recommendedSets: i >= 5 && i <= 7 ? null : 3,
          recommendedReps: i >= 5 && i <= 7 ? null : 10,
          aiVideoUrl: null
        });
        exercises.push(exercise); action = "Đã tạo";
      } else if (!exercise.active) {
        exercise = await api(`/admin/exercises/${exercise.exerciseId}/activate`, "PATCH");
        action = "Đã kích hoạt lại";
      }
      rows.push({ id: exercise.exerciseId, ten: exercise.exerciseName, active: exercise.active,
        MET: exercise.metValue, nguongPhut: exercise.minDurationMinutes, ketQua: action });
      console.log(`[${i + 1}/10] ${action}: ${exercise.exerciseName}`);
    }
    console.table(rows);
    console.log("Hoàn tất. Bấm Làm mới trên giao diện để tải lại thư viện.");
    return rows;
  } catch (error) {
    console.error("Dừng tạo dữ liệu:", error.name === "AbortError"
      ? "Request quá 20 giây. Kết quả ghi có thể đã được lưu; chạy lại để kiểm tra và bổ sung phần còn thiếu."
      : error.message);
    throw error;
  } finally { delete window[guard]; }
})();
