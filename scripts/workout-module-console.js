/*
 * Copy TOAN BO file vao Console cua trang ung dung da dang nhap.
 * Chua tu chay: await FitnessModuleTest.run({ mode: "admin" })
 * Tai khoan thu nghiem, co can nang trong /profile, khong co buoi dang mo.
 * Dong bo scripts/sql/workout_tracking_sync.sql truoc khi chay.
 * Khong luu/in timerToken. Lich su, check-in va huy hieu test se duoc giu lai.
 */
(() => {
    "use strict";
    if (globalThis.FitnessModuleTest?.running) {
        throw new Error("Script đang chạy. Dùng FitnessModuleTest.stop() trước khi dán lại.");
    }
    const KEY = "fitness.workout.module-test.v1";
    const SEED_KEY = "fitness.workout.user-fixtures.v1";
    let saved = null;
    try { saved = JSON.parse(sessionStorage.getItem(KEY) || "null"); } catch { /* optional storage */ }
    let seed = null;
    try { seed = JSON.parse(sessionStorage.getItem(SEED_KEY) || "null"); } catch { /* optional storage */ }
    const state = { running: false, abort: null, rows: [], manifest: saved, csrf: null };
    let config;
    const assert = (value, message) => { if (!value) throw new Error(message); };
    const equal = (actual, expected, message) => assert(actual === expected, message);
    const near = (actual, expected, message, tolerance = 1e-7) =>
        assert(Number.isFinite(Number(actual)) && Math.abs(Number(actual) - expected) <= tolerance, message);
    const uuid = () => globalThis.crypto?.randomUUID?.() || "00000000-0000-4000-8000-000000000000";
    const canonical = value => JSON.stringify(value, (_, item) => {
        if (item && typeof item === "object" && !Array.isArray(item)) {
            return Object.fromEntries(Object.keys(item).sort().map(key => [key, item[key]]));
        }
        return item;
    });
    const unchanged = (a, b, label) => equal(canonical(a), canonical(b), label);
    const save = () => { try { sessionStorage.setItem(KEY, JSON.stringify(state.manifest)); } catch { /* optional */ } };
    const context = () => JSON.parse(JSON.stringify(state.manifest));
    const userFixtures = () => JSON.parse(JSON.stringify(seed));
    const saveSeed = () => { try { sessionStorage.setItem(SEED_KEY, JSON.stringify(seed)); } catch { /* optional */ } };
    const hasRecords = manifest => manifest && [manifest.categories, manifest.exercises, manifest.workouts]
        .some(items => Array.isArray(items) && items.length > 0);
    const row = (name, result, detail = "") => {
        state.rows.push({ test: name, result, detail });
        console[result === "FAIL" ? "error" : "log"](`[${result}] ${name}${detail ? ": " + detail : ""}`);
    };
    const skip = (name, reason) => row(name, "SKIP", reason);
    const report = () => {
        console.table(state.rows);
        const counts = { PASS: 0, FAIL: 0, SKIP: 0 };
        state.rows.forEach(item => counts[item.result]++);
        console.log("Tổng kết:", counts);
        console.log("ID dữ liệu test (không có token):", context());
        return { ...counts, context: context() };
    };
    async function check(name, fn) {
        try {
            assert(!state.abort?.signal.aborted, "Đã dừng theo yêu cầu.");
            const value = await fn();
            row(name, "PASS");
            return value;
        } catch (error) {
            row(name, "FAIL", error.message);
            throw error;
        }
    }
    function sleep(ms) {
        assert(!state.abort?.signal.aborted, "Đã dừng theo yêu cầu.");
        return new Promise((resolve, reject) => {
            const signal = state.abort?.signal;
            const stop = () => { clearTimeout(timer); reject(new Error("Đã dừng theo yêu cầu.")); };
            const timer = setTimeout(() => { signal?.removeEventListener("abort", stop); resolve(); }, ms);
            signal?.addEventListener("abort", stop, { once: true });
        });
    }
    async function request(method, path, body, expected = [200], options = {}) {
        assert(!state.abort?.signal.aborted, "Đã dừng theo yêu cầu.");
        if (!["GET", "HEAD"].includes(method) && options.csrf !== "omit") {
            assert(state.csrf, "Thiếu CSRF: mở /profile sau khi đăng nhập rồi dán lại script.");
        }
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);
        const onStop = () => controller.abort();
        state.abort?.signal.addEventListener("abort", onStop, { once: true });
        const headers = { Accept: options.html ? "text/html" : "application/json" };
        if (body !== undefined) headers["Content-Type"] = "application/json";
        if (!["GET", "HEAD"].includes(method) && options.csrf !== "omit") {
            headers["X-CSRF-TOKEN"] = options.csrf === "invalid" ? "invalid-csrf-test" : state.csrf;
        }
        try {
            const response = await fetch(path, {
                method, credentials: options.anonymous ? "omit" : "same-origin",
                cache: "no-store", headers, signal: controller.signal,
                ...(body === undefined ? {} : { body: JSON.stringify(body) })
            });
            if (options.loginRedirect) {
                assert(response.redirected && new URL(response.url).pathname === "/login",
                    "Request không có cookie chưa bị chuyển về /login.");
                return { status: response.status, data: null };
            }
            assert(!response.redirected, "Request bị chuyển trang; hãy kiểm tra đăng nhập đúng tài khoản.");
            const text = await response.text();
            let data = null;
            if (options.html) data = text;
            else if (text) {
                try { data = JSON.parse(text); }
                catch { throw new Error(`${method} ${new URL(path, location.origin).href}: HTTP ${response.status}, phản hồi không phải JSON.`); }
            }
            assert(expected.includes(response.status),
                `${method} ${new URL(path, location.origin).href}: HTTP ${response.status}, cần ${expected.join("/")}. ` +
                String(data?.message || data?.error || "").slice(0, 220));
            return { status: response.status, data };
        } finally {
            clearTimeout(timeout);
            state.abort?.signal.removeEventListener("abort", onStop);
        }
    }
    async function profile(requireWeight = true) {
        const result = await request("GET", "/profile", undefined, [200], { html: true });
        const doc = new DOMParser().parseFromString(result.data, "text/html");
        state.csrf = doc.querySelector('input[name="_csrf"]')?.value
            || document.querySelector('input[name="_csrf"]')?.value
            || document.querySelector('meta[name="_csrf"]')?.content;
        assert(state.csrf, "Không đọc được CSRF từ /profile hoặc trang hiện tại.");
        const weight = Number(doc.querySelector('input[name="weightKg"]')?.value);
        if (requireWeight) assert(weight > 0, "Hãy lưu hồ sơ đầy đủ với cân nặng > 0 ở /profile trước khi chạy.");
        return weight;
    }
    const D = (w, d, suffix = "") => `/workouts/${w}/details/${d}${suffix}`;
    const summary = async () => (await request("GET", "/check-ins/summary")).data;
    const timer = async (w, d) => (await request("GET", D(w, d, "/timer"))).data;
    const details = async w => (await request("GET", `/workouts/${w}/details`)).data;
    function trackWorkout(workout) {
        assert(workout?.workoutId && workout.userId, "Response tạo buổi thiếu ID.");
        const manifest = state.manifest;
        if (manifest.ownerUserId == null) manifest.ownerUserId = workout.userId;
        equal(workout.userId, manifest.ownerUserId, "Tài khoản đã thay đổi trong khi test.");
        if (!manifest.workouts.some(w => w.id === workout.workoutId)) {
            manifest.workouts.push({ id: workout.workoutId, userId: workout.userId, detailIds: [] });
        }
        save();
        return workout.workoutId;
    }
    function trackDetail(w, detail) {
        assert(detail?.detailId, "Response chọn bài thiếu detailId.");
        const workout = state.manifest.workouts.find(item => item.id === w);
        if (!workout.detailIds.includes(detail.detailId)) workout.detailIds.push(detail.detailId);
        save();
        return detail;
    }
    async function add(w, exerciseId) {
        const result = await request("POST", `/workouts/${w}/details`, { exerciseId }, [201]);
        return trackDetail(w, result.data);
    }
    async function missingId(path) {
        for (let id = 2147483647; id > 2147483637; id--) {
            if ((await request("GET", `${path}/${id}`, undefined, [200, 404])).status === 404) return id;
        }
        throw new Error("Không tìm được ID trống để test 404.");
    }
    function bodyFor(exercise, overrides = {}) {
        return {
            exerciseName: exercise.exerciseName, description: exercise.description || "Dữ liệu kiểm thử",
            metValue: exercise.metValue, minDurationMinutes: exercise.minDurationMinutes,
            recommendedSets: exercise.recommendedSets || 2, recommendedReps: exercise.recommendedReps || 10,
            aiVideoUrl: exercise.aiVideoUrl || "https://example.com/fitness-test-video",
            categoryId: exercise.categoryId, ...overrides
        };
    }
    async function prepareAdmin() {
        const m = state.manifest;
        const makeCategory = async tag => {
            const result = await request("POST", "/admin/exercise-categories",
                { categoryName: `${m.prefix}_${tag}` }, [201]);
            m.categories.push(result.data.categoryId); save();
            return result.data;
        };
        const a = await check("Nhóm: tạo A", () => makeCategory("A"));
        const b = await check("Nhóm: tạo B", () => makeCategory("B"));
        for (const categoryName of ["  ", "x".repeat(101)]) {
            await check(`Nhóm: tên không hợp lệ (${categoryName.length} ký tự)`, () =>
                request("POST", "/admin/exercise-categories", { categoryName }, [400]));
        }
        await check("Nhóm: chặn tên trùng", () => request("POST", "/admin/exercise-categories",
            { categoryName: a.categoryName }, [400]));
        await check("Nhóm: danh sách có A/B", async () => {
            const list = (await request("GET", "/exercise-categories")).data;
            assert([a, b].every(c => list.some(item => item.categoryId === c.categoryId)), "Thiếu nhóm vừa tạo.");
        });
        await check("Nhóm: đổi tên và GET đối chiếu", async () => {
            a.categoryName = `${m.prefix}_A_updated`;
            await request("PATCH", `/admin/exercise-categories/${a.categoryId}`, { categoryName: a.categoryName });
            equal((await request("GET", `/exercise-categories/${a.categoryId}`)).data.categoryName,
                a.categoryName, "Tên nhóm chưa được lưu.");
        });
        await check("Nhóm: chặn sửa thành tên trùng", () => request("PATCH",
            `/admin/exercise-categories/${b.categoryId}`, { categoryName: a.categoryName }, [400]));
        await check("Nhóm: ID sai/không tồn tại", async () => {
            await request("GET", "/exercise-categories/0", undefined, [400]);
            const id = await missingId("/exercise-categories");
            await request("PATCH", `/admin/exercise-categories/${id}`, { categoryName: `${m.prefix}_missing` }, [404]);
            await request("DELETE", `/admin/exercise-categories/${id}`, undefined, [404]);
            await request("PATCH", `/admin/exercise-categories/${id}/activate`, undefined, [404]);
            await request("PATCH", `/admin/exercise-categories/${a.categoryId}`, { categoryName: " " }, [400]);
        });
        const exercises = [];
        for (let index = 0; index < 5; index++) {
            const input = {
                exerciseName: `${m.prefix}_E${index + 1}`, categoryId: index === 0 ? a.categoryId : b.categoryId,
                metValue: index === 0 ? 6 : 4, minDurationMinutes: index === 0 ? 1 : 0.1,
                recommendedSets: 2, recommendedReps: 10, aiVideoUrl: "https://example.com/fitness-test-video"
            };
            exercises.push(await check(`Bài tập: tạo E${index + 1}`, async () => {
                const item = (await request("POST", "/admin/exercises", input, [201])).data;
                m.exercises.push(item.exerciseId); save(); return item;
            }));
        }
        const [e1, , , e4] = exercises;
        for (const [field, value] of [["exerciseName", " "], ["metValue", 0], ["minDurationMinutes", 0],
            ["recommendedSets", 0], ["recommendedReps", 0], ["aiVideoUrl", "x".repeat(501)], ["categoryId", 0]]) {
            await check(`Bài tập: validation ${field}`, () => request("POST", "/admin/exercises",
                bodyFor(e1, { [field]: value }), [400]));
        }
        await check("Bài tập: GET danh sách/chi tiết/ID sai", async () => {
            const list = (await request("GET", "/exercises")).data;
            assert(list.some(item => item.exerciseId === e1.exerciseId), "Thiếu bài vừa tạo.");
            equal((await request("GET", `/exercises/${e1.exerciseId}`)).data.exerciseName, e1.exerciseName, "Sai chi tiết bài.");
            await request("GET", "/exercises/0", undefined, [400]);
            m.missingExerciseId = await missingId("/exercises"); save();
            await request("PUT", `/admin/exercises/${m.missingExerciseId}`, bodyFor(e1), [404]);
            await request("PATCH", `/admin/exercises/${m.missingExerciseId}/activate`, undefined, [404]);
            await request("PATCH", `/admin/exercises/${m.missingExerciseId}/deactivate`, undefined, [404]);
            await request("DELETE", `/admin/exercises/${m.missingExerciseId}`, undefined, [404]);
        });
        await check("Bài tập: cập nhật MET/video và GET đối chiếu", async () => {
            const updated = bodyFor(e1, { metValue: 7, description: "Đã cập nhật", aiVideoUrl: "https://example.com/updated-video" });
            await request("PUT", `/admin/exercises/${e1.exerciseId}`, updated);
            Object.assign(e1, (await request("GET", `/exercises/${e1.exerciseId}`)).data);
            equal(e1.metValue, 7, "MET chưa đổi."); equal(e1.aiVideoUrl, updated.aiVideoUrl, "Video chưa đổi.");
        });
        await check("Nhóm: ngừng/kích hoạt lại", async () => {
            equal((await request("DELETE", `/admin/exercise-categories/${a.categoryId}`)).data.active, false, "Nhóm chưa ngừng.");
            equal((await request("PATCH", `/admin/exercise-categories/${a.categoryId}/activate`)).data.active, true, "Nhóm chưa kích hoạt.");
            await request("DELETE", `/admin/exercise-categories/${a.categoryId}`);
        });
        await check("Nhóm ngừng: chặn gán mới/chuyển nhóm, giữ bài hiện có", async () => {
            await request("POST", "/admin/exercises", bodyFor(e1, { exerciseName: `${m.prefix}_blocked` }), [400]);
            await request("PUT", `/admin/exercises/${exercises[1].exerciseId}`,
                bodyFor(exercises[1], { categoryId: a.categoryId }), [400]);
            await request("PUT", `/admin/exercises/${e1.exerciseId}`, bodyFor(e1));
        });
        await check("Bài tập: ngừng/kích hoạt lại", async () => {
            equal((await request("PATCH", `/admin/exercises/${e4.exerciseId}/deactivate`)).data.active, false, "Chưa ngừng bài.");
            equal((await request("PATCH", `/admin/exercises/${e4.exerciseId}/activate`)).data.active, true, "Chưa bật lại bài.");
            await request("PATCH", `/admin/exercises/${e4.exerciseId}/deactivate`);
        });
        return exercises;
    }
    async function prepareUser(exerciseIds) {
        const all = (await request("GET", "/exercises")).data;
        const active = all.filter(e => e.active && e.minDurationMinutes > 0 && e.minDurationMinutes <= 2)
            .sort((a, b) => a.minDurationMinutes - b.minDurationMinutes);
        const chosen = exerciseIds ? exerciseIds.map(id => active.find(e => e.exerciseId === id)) : active.slice(0, 3);
        assert(chosen.length >= 3 && chosen.every(Boolean) && new Set(chosen.map(e => e.exerciseId)).size === chosen.length,
            "User cần ít nhất 3 bài active khác nhau, ngưỡng tối đa 2 phút. Nhờ Admin tạo fixture nếu chưa có.");
        await check("User: bị chặn API quản trị", async () => {
            await request("POST", "/admin/exercise-categories", { categoryName: `${state.manifest.prefix}_blocked` }, [403]);
            await request("PUT", `/admin/exercises/${chosen[0].exerciseId}`, bodyFor(chosen[0]), [403]);
        });
        skip("CRUD nhóm/bài, ngừng nhóm, hard-delete", "Chạy mode admin để kiểm tra bằng fixture riêng.");
        return chosen;
    }
    async function pump(w, d, lease) {
        const started = performance.now();
        let last = lease;
        const required = Number(last.detail.minDurationMinutesSnapshot) * 60000;
        assert(required > 0 && required <= 120000, "Ngưỡng fixture phải từ > 0 đến 2 phút.");
        while (last.activeDurationMillis + last.restDurationMillis < required + 150) {
            assert(performance.now() - started < 180000, "Quá thời gian chờ hoàn thành; kiểm tra timer/mạng.");
            const remaining = required + 200 - last.activeDurationMillis - last.restDurationMillis;
            await sleep(Math.max(500, Math.min(config.heartbeatMs, remaining)));
            assert(!document.hidden, "Giữ tab kiểm thử ở phía trước để tránh trình duyệt làm chậm heartbeat.");
            last = (await request("PATCH", D(w, d, "/heartbeat"), { timerToken: last.timerToken })).data;
            equal(last.detail.status, "IN_PROGRESS", "Timer bị đóng băng trong khi đang chờ; kiểm tra độ trễ/tab.");
            console.log("Đang ghi nhận (giây):", { active: last.activeDurationMillis / 1000, rest: last.restDurationMillis / 1000 });
        }
        return last;
    }
    function verifyResult(result, bodyWeight) {
        equal(result.status, "COMPLETED", "Bài chưa được công nhận.");
        assert(result.activeDurationMillis + result.restDurationMillis >= result.minDurationMinutesSnapshot * 60000,
            "Công nhận khi chưa đủ ngưỡng tối thiểu.");
        const elapsed = BigInt(result.activeDurationMillis) + BigInt(result.restDurationMillis);
        const expectedMinutes = Number((elapsed + 3000n) / 6000n) / 10;
        const numerator = BigInt(Math.round(result.metValueSnapshot * 10)) * 35n
            * BigInt(Math.round(bodyWeight * 100)) * BigInt(result.activeDurationMillis);
        const denominator = 12000000000n;
        const expectedCalories = Number((numerator + denominator / 2n) / denominator) / 10;
        near(result.durationMinutes, expectedMinutes, "Sai thời lượng.");
        near(result.caloriesBurned, expectedCalories,
            "Sai calo tính từ active; không được cộng thời gian nghỉ.");
    }
    async function verifyCheckIn(before, name) {
        const after = await summary();
        if (before.today !== after.today) { skip(name, "Đã qua ngày Việt Nam; cần test riêng ranh giới ngày."); return after; }
        await check(name, async () => {
            equal(after.checkedInToday, true, "Chưa check-in sau bài hoàn thành.");
            const expectedStreak = before.currentStreak + (before.checkedInToday ? 0 : 1);
            equal(after.currentStreak, expectedStreak, "Streak tăng sai hoặc bị ghi trùng.");
            equal(after.highestStreak, Math.max(before.highestStreak, expectedStreak), "Highest streak sai.");
            equal(after.consecutiveMissedDays, 0, "Check-in hợp lệ chưa reset số ngày bỏ lỡ.");
        });
        return after;
    }
    async function verifyTotals(w) {
        const rows = await details(w);
        const completed = rows.filter(d => d.status === "COMPLETED");
        const result = (await request("GET", `/workouts/${w}`)).data;
        near(result.totalDurationMinutes, completed.reduce((n, d) => n + Number(d.durationMinutes), 0), "Tổng phút sai.");
        near(result.totalCaloriesBurned, completed.reduce((n, d) => n + Number(d.caloriesBurned), 0), "Tổng calo sai.");
        return result;
    }
    async function core(exercises, before) {
        const [e1, e2, e3, e4, e5] = exercises;
        let emptyWorkout;
        await check("Buổi: hai request tạo đồng thời chỉ một thành công", async () => {
            const results = await Promise.all([0, 1].map(async () => {
                const result = await request("POST", "/workouts", undefined, [201, 409]);
                if (result.status === 201) trackWorkout(result.data);
                return result;
            }));
            equal(results.filter(r => r.status === 201).length, 1, "Không bảo đảm một buổi mở/user.");
            emptyWorkout = results.find(r => r.status === 201).data.workoutId;
        });
        await check("Buổi rỗng: tổng 0/danh sách rỗng/không cho complete", async () => {
            const w = (await request("GET", `/workouts/${emptyWorkout}`)).data;
            near(w.totalDurationMinutes, 0, "Phút buổi rỗng khác 0."); near(w.totalCaloriesBurned, 0, "Calo buổi rỗng khác 0.");
            equal((await details(emptyWorkout)).length, 0, "Buổi mới không rỗng.");
            await request("PATCH", `/workouts/${emptyWorkout}/complete`, undefined, [409]);
        });
        const oldDetail = await add(emptyWorkout, e3.exerciseId);
        await check("Hủy buổi chưa có kết quả và gọi lại", async () => {
            const first = (await request("PATCH", `/workouts/${emptyWorkout}/cancel`)).data;
            equal(first.status, "CANCELLED", "Chưa hủy buổi.");
            unchanged(first, (await request("PATCH", `/workouts/${emptyWorkout}/cancel`)).data, "Hủy lại đổi kết quả.");
            equal((await details(emptyWorkout))[0].status, "CANCELLED", "Bài chưa bắt đầu chưa bị hủy.");
            await request("PATCH", `/workouts/${emptyWorkout}/complete`, undefined, [409]);
        });
        const w = await check("Buổi: tạo mới sau khi đóng buổi cũ", async () =>
            trackWorkout((await request("POST", "/workouts", undefined, [201])).data));
        await check("Buổi: xem current và chặn tạo thêm", async () => {
            equal((await request("GET", "/workouts/current")).data.workoutId, w, "Sai buổi đang mở.");
            await request("POST", "/workouts", undefined, [409]);
        });
        await check("CSRF thiếu/sai bị chặn trước nghiệp vụ", async () => {
            await request("POST", "/workouts", undefined, [403], { csrf: "omit" });
            await request("POST", "/workouts", undefined, [403], { csrf: "invalid" });
        });
        await check("Buổi/detail: ID và body không hợp lệ", async () => {
            await request("GET", "/workouts/0", undefined, [400]);
            for (const exerciseId of [null, 0, -1]) await request("POST", `/workouts/${w}/details`, { exerciseId }, [400]);
            const missing = state.manifest.missingExerciseId || await missingId("/exercises");
            await request("POST", `/workouts/${w}/details`, { exerciseId: missing }, [404]);
            await request("GET", D(w, oldDetail.detailId, "/timer"), undefined, [404]);
            await request("PATCH", D(w, oldDetail.detailId, "/start"), undefined, [404]);
        });
        let a;
        await check("Chọn cùng bài đồng thời: một detail duy nhất", async () => {
            const responses = await Promise.all([0, 1].map(async () => {
                const result = await request("POST", `/workouts/${w}/details`, { exerciseId: e1.exerciseId }, [201, 409]);
                if (result.status === 201) trackDetail(w, result.data);
                return result;
            }));
            equal(responses.filter(r => r.status === 201).length, 1, "Chọn trùng tạo nhiều detail.");
            a = responses.find(r => r.status === 201).data;
            equal(a.status, "NOT_STARTED", "Chọn bài đã tự bắt đầu.");
            near(a.caloriesBurned, 0, "Chọn bài đã sinh calo."); equal(a.startedAt, null, "Chọn bài đã có startedAt.");
        });
        const b = await check("Chọn bài B", () => add(w, e2.exerciseId));
        const c = await check("Chọn bài C", () => add(w, e3.exerciseId));
        if (config.mode === "admin") {
            await check("Bài ngừng: không thêm/bắt đầu; bật lại dùng tiếp", async () => {
                await request("POST", `/workouts/${w}/details`, { exerciseId: e4.exerciseId }, [409]);
                await request("PATCH", `/admin/exercises/${e3.exerciseId}/deactivate`);
                await request("PATCH", D(w, c.detailId, "/start"), undefined, [409]);
                await request("PATCH", `/admin/exercises/${e3.exerciseId}/activate`);
            });
            await check("Không xóa bài nằm trong buổi mở", () => request("DELETE", `/admin/exercises/${e1.exerciseId}`, undefined, [409]));
            await check("Xóa hẳn bài chưa dùng", async () => {
                await request("DELETE", `/admin/exercises/${e4.exerciseId}`, undefined, [204]);
                await request("GET", `/exercises/${e4.exerciseId}`, undefined, [404]);
                await request("DELETE", `/admin/exercises/${e4.exerciseId}`, undefined, [404]);
            });
        }
        await check("Cấu hình nghỉ: chặn null/0/301", async () => {
            for (const restSeconds of [null, -1, 0, 301]) await request("PATCH", D(w, a.detailId, "/rest-duration"), { restSeconds }, [400]);
            await request("PATCH", D(w, c.detailId, "/recover"), undefined, [409]);
        });
        let lease = await check("Bắt đầu A (kể cả nhóm đã ngừng ở mode admin)", async () => {
            const result = (await request("PATCH", D(w, a.detailId, "/start"))).data;
            equal(result.detail.status, "IN_PROGRESS", "Không bắt đầu được bài A.");
            assert(result.timerToken, "Không cấp token."); return result;
        });
        const startedAt = lease.detail.startedAt;
        await check("Chặn start lại và bắt đầu B khi A đang tập", async () => {
            await request("PATCH", D(w, a.detailId, "/start"), undefined, [409]);
            await request("PATCH", D(w, b.detailId, "/start"), undefined, [409]);
            await request("PATCH", D(w, a.detailId, "/resume"), { timerToken: lease.timerToken }, [409]);
        });
        await check("GET timer không cấp token hoặc đổi startedAt", async () => {
            const read = await timer(w, a.detailId);
            equal(read.timerToken, null, "GET đã lộ token."); equal(read.detail.startedAt, startedAt, "GET đổi mốc bắt đầu.");
        });
        if (e1.minDurationMinutes >= 0.5) {
            await check("Complete quá sớm không được check-in", () => request("PATCH", D(w, a.detailId, "/complete"),
                { timerToken: lease.timerToken }, [409]));
        } else skip("Complete quá sớm", "Bài User có ngưỡng quá ngắn; mode Admin dùng fixture 1 phút.");
        const earlySummary = await summary();
        if (before.today === earlySummary.today) {
            await check("Chọn/start/bài chưa xong không tạo check-in", async () => {
                equal(earlySummary.checkedInToday, before.checkedInToday, "Đã tạo check-in trước bài hợp lệ.");
                equal(earlySummary.currentStreak, before.currentStreak, "Đã tăng streak trước bài hợp lệ.");
            });
        } else skip("Chưa xong không tạo check-in", "Đã qua ngày Việt Nam; kiểm tra riêng ranh giới ngày.");
        await check("Token sai/thiếu và thống kê âm bị chặn", async () => {
            await request("PATCH", D(w, a.detailId, "/heartbeat"), {}, [400]);
            await request("PATCH", D(w, a.detailId, "/heartbeat"), { timerToken: uuid() }, [409]);
            await request("PATCH", D(w, a.detailId, "/complete"), { timerToken: lease.timerToken, sets: -1 }, [400]);
        });
        await sleep(2000);
        lease = await check("Heartbeat cộng thời gian tập", async () => {
            const result = (await request("PATCH", D(w, a.detailId, "/heartbeat"), { timerToken: lease.timerToken })).data;
            assert(result.activeDurationMillis >= 1500, "Heartbeat không tăng active."); return result;
        });
        lease = await check("Tạm dừng và không cộng thời gian pause", async () => {
            const paused = (await request("PATCH", D(w, a.detailId, "/pause"), { timerToken: lease.timerToken })).data;
            equal(paused.detail.status, "PAUSED", "Chưa pause."); await sleep(2000);
            near((await timer(w, a.detailId)).activeDurationMillis, paused.activeDurationMillis, "Pause vẫn cộng active.");
            await request("PATCH", D(w, a.detailId, "/heartbeat"), { timerToken: paused.timerToken }, [409]);
            await request("PATCH", D(w, b.detailId, "/start"), undefined, [409]); return paused;
        });
        lease = await check("Recover PAUSED giữ pause, thu hồi token cũ; resume cấp token mới", async () => {
            const old = lease.timerToken;
            const recovered = (await request("PATCH", D(w, a.detailId, "/recover"))).data;
            equal(recovered.detail.status, "PAUSED", "Recover PAUSED đã tự tập."); assert(recovered.timerToken !== old, "Token chưa đổi.");
            await request("PATCH", D(w, a.detailId, "/resume"), { timerToken: old }, [409]);
            const resumed = (await request("PATCH", D(w, a.detailId, "/resume"), { timerToken: recovered.timerToken })).data;
            near(resumed.activeDurationMillis, recovered.activeDurationMillis, "Resume cộng thời gian pause.");
            assert(resumed.timerToken !== recovered.timerToken, "Resume chưa đổi token."); return resumed;
        });
        await check("Chưa duyệt nghỉ thì không được /rest", () => request("PATCH", D(w, a.detailId, "/rest"),
            { timerToken: lease.timerToken }, [409]));
        await check("Duyệt nghỉ chấp nhận 1 và 300 giây, sau đó dùng thời lượng test", async () => {
            for (const restSeconds of [1, 300, config.restSeconds]) {
                equal((await request("PATCH", D(w, a.detailId, "/rest-duration"), { restSeconds })).data.approvedRestSecondsSnapshot,
                    restSeconds, "Lưu thời lượng nghỉ sai.");
            }
        });
        lease = await check("Bắt đầu nghỉ theo snapshot", async () => {
            const result = (await request("PATCH", D(w, a.detailId, "/rest"), { timerToken: lease.timerToken })).data;
            equal(result.detail.status, "RESTING", "Không vào nghỉ.");
            near(Date.parse(result.restEndsAt) - Date.parse(result.restStartedAt), config.restSeconds * 1000, "Deadline nghỉ sai.", 2);
            await request("PATCH", D(w, a.detailId, "/resume"), { timerToken: result.timerToken }, [409]);
            await request("PATCH", D(w, a.detailId, "/pause"), { timerToken: result.timerToken }, [409]);
            await request("PATCH", D(w, b.detailId, "/start"), undefined, [409]); return result;
        });
        const restActive = lease.activeDurationMillis;
        const restBefore = lease.restDurationMillis;
        const deadline = lease.restEndsAt;
        await check("Đổi cấu hình không thay khoảng nghỉ hiện tại", async () => {
            await request("PATCH", D(w, a.detailId, "/rest-duration"), { restSeconds: 1 });
            equal((await timer(w, a.detailId)).restEndsAt, deadline, "Đổi cấu hình đã sửa deadline đang nghỉ.");
        });
        await sleep(2000);
        lease = await check("Recover trước hết nghỉ: còn RESTING và token cũ bị từ chối", async () => {
            const old = lease.timerToken;
            const result = (await request("PATCH", D(w, a.detailId, "/recover"))).data;
            equal(result.detail.status, "RESTING", "Recover đã tự chuyển sang tập."); equal(result.restEndsAt, deadline, "Recover đổi deadline.");
            await request("PATCH", D(w, a.detailId, "/heartbeat"), { timerToken: old }, [409]); return result;
        });
        console.log(`Giả lập không gửi heartbeat lúc nghỉ: chờ ${config.restSeconds} giây...`);
        await sleep(config.restSeconds * 1000 + 500);
        lease = await check("Nghỉ offline chỉ tính đến deadline, không tự tập", async () => {
            const result = (await request("PATCH", D(w, a.detailId, "/recover"))).data;
            equal(result.detail.status, "RESTING", "Hết nghỉ đã tự tập."); near(result.activeDurationMillis, restActive, "Offline nghỉ cộng active.");
            near(result.restDurationMillis - restBefore, config.restSeconds * 1000, "Nghỉ vượt/thiếu deadline.", 10);
            const repeat = (await request("PATCH", D(w, a.detailId, "/heartbeat"), { timerToken: result.timerToken })).data;
            near(repeat.restDurationMillis, result.restDurationMillis, "Cộng trùng nghỉ sau deadline."); return repeat;
        });
        lease = (await request("PATCH", D(w, a.detailId, "/resume"), { timerToken: lease.timerToken })).data;
        const activeBeforeOffline = lease.activeDurationMillis;
        console.log(`Giả lập mất heartbeat lúc tập: chờ ${(config.timeoutMs + config.scanMs + 3000) / 1000} giây...`);
        await sleep(config.timeoutMs + config.scanMs + 3000);
        await check("Scheduler tự đóng băng; bài B vẫn bị chặn", async () => {
            const read = await timer(w, a.detailId);
            equal(read.detail.status, "FROZEN", "Scheduler chưa chuyển FROZEN. Kiểm tra cấu hình/log/backend.");
            near(read.activeDurationMillis, activeBeforeOffline, "Mất mạng vẫn cộng active.");
            await request("PATCH", D(w, a.detailId, "/heartbeat"), { timerToken: lease.timerToken }, [409]);
            await request("PATCH", D(w, b.detailId, "/start"), undefined, [409]);
        });
        lease = await check("Recover FROZEN không cộng bù, cấp token mới", async () => {
            const result = (await request("PATCH", D(w, a.detailId, "/recover"))).data;
            equal(result.detail.status, "IN_PROGRESS", "Chưa khôi phục.");
            near(result.activeDurationMillis, activeBeforeOffline, "Recover cộng thời gian mất mạng."); return result;
        });
        lease = await pump(w, a.detailId, lease);
        const weightA = await profile();
        const first = await check("Hoàn thành A: ngưỡng, duration, calo active và snapshot", async () => {
            const value = (await request("PATCH", D(w, a.detailId, "/complete"),
                { timerToken: lease.timerToken, sets: 2, reps: 10, weightKg: 5 })).data;
            verifyResult(value, weightA); equal(value.startedAt, startedAt, "StartedAt bị đổi."); return value;
        });
        let after = await verifyCheckIn(before, "Bài đầu: check-in/streak tăng tối đa một lần");
        await check("Complete A gọi lại trả nguyên kết quả", async () => {
            unchanged(first, (await request("PATCH", D(w, a.detailId, "/complete"), { timerToken: lease.timerToken })).data,
                "Complete gọi lại đã đổi JSON/thời gian/kết quả.");
            const replaySummary = await summary();
            if (after.today === replaySummary.today) unchanged(after, replaySummary, "Complete gọi lại đã thay đổi check-in/streak.");
            else skip("Check-in sau replay A", "Đã qua ngày Việt Nam; cần kiểm tra riêng.");
        });
        await check("Danh sách, bài còn thiếu và tổng buổi sau A", async () => {
            equal((await details(w)).length, 3, "Sai số detail.");
            const remaining = (await request("GET", `/workouts/${w}/details/remaining`)).data;
            assert(remaining.length === 2 && remaining.every(d => d.detailId !== a.detailId), "Bài hoàn thành còn trong remaining.");
            await verifyTotals(w);
        });
        let leaseB = (await request("PATCH", D(w, b.detailId, "/start"))).data;
        leaseB = await pump(w, b.detailId, leaseB);
        const weightB = await profile();
        let second;
        await check("Hai complete B đồng thời: cùng kết quả, không ghi trùng", async () => {
            const responses = await Promise.all([0, 1].map(() => request("PATCH", D(w, b.detailId, "/complete"),
                { timerToken: leaseB.timerToken })));
            second = responses[0].data; verifyResult(second, weightB);
            unchanged(second, responses[1].data, "Complete đồng thời trả kết quả khác nhau.");
        });
        after = await verifyCheckIn(after, "Bài thứ hai cùng ngày: không tăng streak lần nữa");
        const leaseC = (await request("PATCH", D(w, c.detailId, "/start"))).data;
        let closed;
        await check("Kết thúc buổi: hủy C dở, giữ A/B và tổng kết", async () => {
            closed = (await request("PATCH", `/workouts/${w}/complete`)).data;
            equal(closed.status, "COMPLETED", "Buổi chưa hoàn thành.");
            const rows = await details(w);
            unchanged(rows.find(d => d.detailId === a.detailId), first, "Kết thúc buổi đổi bài A.");
            equal(rows.find(d => d.detailId === c.detailId).status, "CANCELLED", "C chưa bị hủy.");
            assert(rows.find(d => d.detailId === c.detailId).cancelledAt, "C thiếu cancelledAt.");
            unchanged(closed, await verifyTotals(w), "Tổng kết lưu/đọc khác nhau.");
            equal((await request("GET", `/workouts/${w}/details/remaining`)).data.length, 0, "Buổi đóng vẫn còn bài phải tập.");
        });
        await check("Buổi đã đóng: gọi complete lại bất biến, chặn ghi mới", async () => {
            unchanged(closed, (await request("PATCH", `/workouts/${w}/complete`)).data, "EndTime/tổng đổi khi gọi lại.");
            await request("PATCH", `/workouts/${w}/cancel`, undefined, [409]);
            await request("POST", `/workouts/${w}/details`, { exerciseId: e1.exerciseId }, [409]);
            await request("PATCH", D(w, c.detailId, "/heartbeat"), { timerToken: leaseC.timerToken }, [409]);
            await request("PATCH", D(w, c.detailId, "/recover"), undefined, [409]);
            await request("GET", "/workouts/current", undefined, [404]);
            unchanged(first, (await request("PATCH", D(w, a.detailId, "/complete"), { timerToken: lease.timerToken })).data,
                "Replay bài A trong buổi đóng bị đổi.");
        });
        await check("Lịch sử/phân trang/lọc trạng thái và input sai", async () => {
            const page = (await request("GET", "/workouts?page=0&size=100&status=COMPLETED")).data;
            const found = page.content.find(item => item.workoutId === w);
            assert(found, "Không tìm thấy buổi trong lịch sử."); unchanged(found, closed, "Tổng lịch sử khác tổng chi tiết.");
            for (const query of ["page=-1", "size=0", "size=101", "status=INVALID"]) {
                await request("GET", `/workouts?${query}`, undefined, [400]);
            }
        });
        if (config.mode === "admin") {
            await check("Đổi master không làm thay đổi snapshot lịch sử", async () => {
                await request("PUT", `/admin/exercises/${e1.exerciseId}`, bodyFor(e1, {
                    exerciseName: `${state.manifest.prefix}_E1_changed`, metValue: 9, categoryId: e2.categoryId
                }));
                unchanged((await details(w)).find(d => d.detailId === a.detailId), first, "Master làm đổi lịch sử.");
            });
            await check("Hard-delete sau đóng buổi vẫn xem được snapshot", async () => {
                const beforeDeletion = await details(w);
                for (const exercise of [e1, e3]) {
                    await request("DELETE", `/admin/exercises/${exercise.exerciseId}`, undefined, [204]);
                    await request("GET", `/exercises/${exercise.exerciseId}`, undefined, [404]);
                }
                const rows = await details(w);
                unchanged(rows, beforeDeletion, "Xóa master làm đổi snapshot/kết quả của danh sách lịch sử.");
                unchanged(rows.find(d => d.detailId === a.detailId), first, "Xóa master làm mất/đổi lịch sử A.");
                equal(rows.find(d => d.detailId === c.detailId).exerciseIdSnapshot, e3.exerciseId, "Mất snapshot bài C.");
            });
        }
        const w2 = trackWorkout((await request("POST", "/workouts", undefined, [201])).data);
        const kept = await add(w2, e2.exerciseId);
        const unfinished = await add(w2, (e5 || e3).exerciseId);
        let lease2 = (await request("PATCH", D(w2, kept.detailId, "/start"))).data;
        lease2 = await pump(w2, kept.detailId, lease2);
        const weight2 = await profile();
        const keptResult = (await request("PATCH", D(w2, kept.detailId, "/complete"), { timerToken: lease2.timerToken })).data;
        verifyResult(keptResult, weight2);
        await request("PATCH", D(w2, unfinished.detailId, "/start"));
        await check("Hủy buổi có kết quả: giữ bài hoàn thành, hủy bài dở, tổng đúng", async () => {
            const cancelled = (await request("PATCH", `/workouts/${w2}/cancel`)).data;
            equal(cancelled.status, "CANCELLED", "Buổi chưa hủy.");
            const rows = await details(w2);
            unchanged(rows.find(d => d.detailId === kept.detailId), keptResult, "Hủy đã đổi bài hoàn thành.");
            equal(rows.find(d => d.detailId === unfinished.detailId).status, "CANCELLED", "Bài dở chưa hủy.");
            unchanged(cancelled, await verifyTotals(w2), "Tổng buổi hủy sai.");
            unchanged(cancelled, (await request("PATCH", `/workouts/${w2}/cancel`)).data, "Hủy lại đổi kết quả.");
            await request("PATCH", `/workouts/${w2}/complete`, undefined, [409]);
            unchanged(keptResult, (await request("PATCH", D(w2, kept.detailId, "/complete"), { timerToken: lease2.timerToken })).data,
                "Replay bài hoàn thành sau hủy khác kết quả.");
        });
        await verifyCheckIn(after, "Hủy buổi và bài hoàn thành tiếp theo không xóa/nhân check-in");
        await check("Lịch sử buổi hủy, trang kế tiếp và không còn buổi mở", async () => {
            await request("GET", "/workouts/current", undefined, [404]);
            const cancelled = (await request("GET", "/workouts?page=0&size=100&status=CANCELLED")).data;
            assert(cancelled.content.some(item => item.workoutId === w2), "Thiếu buổi hủy trong lịch sử.");
            assert(cancelled.content.every(item => item.status === "CANCELLED"), "Lọc CANCELLED trả sai trạng thái.");
            const firstPage = (await request("GET", "/workouts?page=0&size=1")).data;
            const nextPage = (await request("GET", "/workouts?page=1&size=1")).data;
            equal(firstPage.content[0]?.workoutId, w2, "Buổi mới nhất không đứng đầu.");
            equal(nextPage.page, 1, "Sai số trang."); equal(nextPage.content.length, 1, "Trang kế tiếp thiếu dữ liệu.");
            assert(nextPage.content[0].workoutId !== w2 && nextPage.totalElements >= 3, "Phân trang bị lặp/sai tổng.");
        });
    }
    async function anonymousCheck() {
        await check("Không có cookie đăng nhập: chuyển về /login", () => request("GET", "/exercises", undefined,
            [200], { anonymous: true, loginRedirect: true }));
    }
    async function cleanOwnFixtures() {
        const m = state.manifest;
        assert(m?.prefix?.startsWith("TEST_WT_"), "Không có manifest fixture để dọn.");
        m.cleaned = false; save();
        const missingResource = (result, path) => {
            if (result.status !== 404) return false;
            assert(result.data === null,
                `${path}: 404 có body lỗi, chưa chứng minh dữ liệu không tồn tại. Hãy kiểm tra API/restart; không đánh dấu đã dọn.`);
            return true;
        };
        for (const item of m.workouts) {
            const path = `/workouts/${item.id}`;
            const result = await request("GET", path, undefined, [200, 404]);
            if (missingResource(result, path)) continue;
            assert(result.data?.workoutId === item.id && result.data.userId === m.ownerUserId,
                "ID/chủ buổi không khớp manifest; không dọn.");
            if (result.data.status === "IN_PROGRESS") {
                const cancelled = (await request("PATCH", `${path}/cancel`)).data;
                assert(cancelled?.workoutId === item.id && cancelled.status === "CANCELLED",
                    "API cancel chưa trả đúng buổi CANCELLED; không đánh dấu đã dọn.");
            }
            const verified = (await request("GET", path)).data;
            assert(verified?.workoutId === item.id && verified.userId === m.ownerUserId
                && ["COMPLETED", "CANCELLED"].includes(verified.status) && verified.endTime,
                "Đọc lại vẫn thấy buổi mở/thiếu endTime hoặc dữ liệu sai; chưa dọn thành công.");
        }
        for (const id of m.exercises) {
            const path = `/exercises/${id}`;
            const result = await request("GET", path, undefined, [200, 404]);
            if (missingResource(result, path)) continue;
            assert(result.data.exerciseName.startsWith(m.prefix), "Tên bài không còn thuộc fixture; không tự xóa.");
            await request("DELETE", `/admin/exercises/${id}`, undefined, [204]);
            const deleted = await request("GET", path, undefined, [200, 404]);
            assert(missingResource(deleted, path), "Xóa bài xong nhưng GET vẫn còn; chưa dọn thành công.");
        }
        for (const id of m.categories) {
            const path = `/exercise-categories/${id}`;
            const result = await request("GET", path, undefined, [200, 404]);
            if (missingResource(result, path)) continue;
            assert(result.data.categoryName.startsWith(m.prefix), "Nhóm không khớp fixture; không tự sửa.");
            const inactive = (await request("DELETE", `/admin/exercise-categories/${id}`)).data;
            assert(inactive?.categoryId === id && inactive.active === false, "API chưa ngừng đúng nhóm fixture.");
            equal((await request("GET", path)).data.active, false, "Đọc lại nhóm vẫn đang active.");
        }
        m.cleaned = true; save();
    }
    async function exclusive(fn) {
        assert(!state.running, "Một lượt test đang chạy.");
        state.running = true; state.abort = new AbortController(); state.rows = [];
        try { await fn(); }
        catch (error) {
            if (!state.rows.some(r => r.result === "FAIL")) row("Dừng kiểm thử", "FAIL", error.message);
            if (state.manifest && !hasRecords(state.manifest)) { state.manifest = null; save(); }
            console.error("Đã dừng. Xem report()/context(); bộ bài mẫu xem userFixtures(). Các hàm này không chứa token.");
        } finally { state.abort?.abort(); state.running = false; state.abort = null; }
        return report();
    }
    async function run(options = {}) {
        return exclusive(async () => {
            config = { mode: "admin", heartbeatMs: 10000, timeoutMs: 30000, scanMs: 10000, restSeconds: 45, ...options };
            assert(["admin", "user"].includes(config.mode), "mode phải là admin hoặc user.");
            assert(config.heartbeatMs > 0 && config.heartbeatMs < config.timeoutMs && config.timeoutMs > 0
                && config.scanMs > 0 && Number.isInteger(config.restSeconds) && config.restSeconds <= 300
                && config.restSeconds * 1000 > config.timeoutMs + 5000, "Cấu hình thời gian kiểm thử không hợp lệ.");
            assert(location.protocol === "http:" || location.protocol === "https:", "Mở Console trên trang ứng dụng.");
            await check("Tiền điều kiện: đăng nhập, CSRF, hồ sơ có cân nặng", () => profile());
            await check("Tiền điều kiện: không tự hủy buổi đang mở sẵn", async () => {
                const current = await request("GET", "/workouts/current", undefined, [200, 404]);
                assert(current.status !== 404 || current.data === null,
                    "GET /workouts/current trả 404 có body lỗi; API có thể chưa được nạp ở bản đang chạy. Hãy Stop/Run lại đúng dự án.");
                assert(current.status === 404, `Đang có buổi ${current.data?.workoutId}. Tự đóng buổi đó, hoặc cleanup() nếu là fixture lần trước.`);
            });
            await check("Tiền điều kiện: đúng phiên bản API lịch sử", async () => {
                const page = (await request("GET", "/workouts?page=0&size=1")).data;
                assert(Array.isArray(page.content) && typeof page.totalElements === "number",
                    "API /workouts không đúng contract mới; kiểm tra branch và restart ứng dụng.");
            });
            if (state.manifest && !hasRecords(state.manifest)) { state.manifest = null; save(); }
            assert(!state.manifest || state.manifest.cleaned || state.manifest.finished,
                "Còn manifest lần trước chưa dọn. Dùng FitnessModuleTest.context()/cleanup() trước lượt mới.");
            await check("Tiền điều kiện: đúng mode quyền Admin/User", async () => {
                const probe = await request("GET", "/admin/__fitness_workout_permission_probe__", undefined, [403, 404]);
                equal(probe.status, config.mode === "admin" ? 404 : 403, "Sai mode so với quyền hiện tại; đổi mode/tài khoản.");
            });
            state.manifest = {
                prefix: `TEST_WT_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
                mode: config.mode, ownerUserId: null, categories: [], exercises: [], workouts: [],
                finished: false, cleaned: false
            };
            save();
            const before = await summary();
            const earnedBefore = await check("Đọc huy hiệu hiện có", async () => {
                assert(Array.isArray((await request("GET", "/badges")).data), "Danh mục huy hiệu không phải mảng.");
                const mine = (await request("GET", "/badges/mine")).data;
                assert(Array.isArray(mine), "Danh sách huy hiệu cá nhân không phải mảng.");
                return mine.map(item => item.badgeId);
            });
            const userIds = options.exerciseIds || (seed?.ready ? seed.exerciseIds : undefined);
            const exercises = config.mode === "admin" ? await prepareAdmin() : await prepareUser(userIds);
            await check("User/Admin đọc được nhóm và chi tiết bài", async () => {
                assert(Array.isArray((await request("GET", "/exercise-categories")).data), "Danh sách nhóm không phải mảng.");
                equal((await request("GET", `/exercise-categories/${exercises[0].categoryId}`)).data.categoryId,
                    exercises[0].categoryId, "Sai chi tiết nhóm.");
                equal((await request("GET", `/exercises/${exercises[0].exerciseId}`)).data.exerciseId,
                    exercises[0].exerciseId, "Sai chi tiết bài.");
            });
            await anonymousCheck();
            await core(exercises, before);
            await check("Huy hiệu đã nhận vẫn được giữ", async () => {
                const ids = new Set((await request("GET", "/badges/mine")).data.map(item => item.badgeId));
                assert(earnedBefore.every(id => ids.has(id)), "Một huy hiệu đã nhận không còn được hiển thị.");
            });
            if (config.mode === "admin") await check("Dọn fixture: xóa bài test/ngừng nhóm test, giữ lịch sử", cleanOwnFixtures);
            else state.manifest.cleaned = true;
            state.manifest.finished = true; save();
            skip("Khác chủ (kể cả Admin)", "Đăng nhập tài khoản B, dán lại script, chạy testForeignAccess() bằng ID trong context().");
            skip("Tài khoản bị khóa/chưa xác minh, mốc 00:00/streak dài ngày", "Cần tài khoản/trạng thái hoặc thời điểm riêng; không sửa tài khoản/đồng hồ bằng script này.");
            skip("Schema/index/khóa DB/rollback chính xác", "Cần đối chiếu SQL; HTTP concurrency chỉ kiểm tra kết quả quan sát được.");
        });
    }
    async function testForeignAccess({ workoutId, detailId } = {}) {
        return exclusive(async () => {
            assert(Number.isInteger(workoutId) && workoutId > 0 && Number.isInteger(detailId) && detailId > 0, "Cần workoutId/detailId thật của tài khoản A.");
            await profile(false);
            await check("Tiền điều kiện khác chủ (chỉ GET trước mọi lệnh ghi)", async () => {
                const result = await request("GET", `/workouts/${workoutId}`, undefined, [200, 403, 404]);
                equal(result.status, 403, "Không phải buổi khác chủ còn tồn tại: dừng trước mọi lệnh ghi.");
            });
            const token = { timerToken: uuid() };
            const cases = [
                ["GET", `/workouts/${workoutId}`], ["GET", `/workouts/${workoutId}/details`],
                ["GET", `/workouts/${workoutId}/details/remaining`], ["GET", D(workoutId, detailId, "/timer")],
                ["POST", `/workouts/${workoutId}/details`, { exerciseId: 1 }],
                ["PATCH", D(workoutId, detailId, "/start")], ["PATCH", D(workoutId, detailId, "/heartbeat"), token],
                ["PATCH", D(workoutId, detailId, "/rest-duration"), { restSeconds: 60 }],
                ["PATCH", D(workoutId, detailId, "/rest"), token], ["PATCH", D(workoutId, detailId, "/pause"), token],
                ["PATCH", D(workoutId, detailId, "/resume"), token], ["PATCH", D(workoutId, detailId, "/recover")],
                ["PATCH", D(workoutId, detailId, "/complete"), token],
                ["PATCH", `/workouts/${workoutId}/complete`], ["PATCH", `/workouts/${workoutId}/cancel`]
            ];
            for (const [method, path, body] of cases) await check(`Khác chủ: ${method} ${path}`, () => request(method, path, body, [403]));
        });
    }
    async function closeExistingWorkout(workoutId) {
        return exclusive(async () => {
            assert(Number.isInteger(workoutId) && workoutId > 0, "Phải chỉ định ID buổi muốn đóng.");
            await profile(false);
            await check(`Chủ động đóng buổi cũ ${workoutId}, giữ kết quả đã hoàn thành`, async () => {
                const path = `/workouts/${workoutId}`;
                const original = (await request("GET", path)).data;
                equal(original?.workoutId, workoutId, "GET trả sai ID buổi; không thao tác.");
                if (["COMPLETED", "CANCELLED"].includes(original.status)) {
                    assert(original.endTime, "Buổi đã đóng nhưng thiếu endTime; cần kiểm tra dữ liệu.");
                    return;
                }
                equal(original.status, "IN_PROGRESS", "Buổi không có trạng thái hợp lệ để đóng.");
                const current = (await request("GET", "/workouts/current")).data;
                assert(current?.workoutId === workoutId && current.userId === original.userId,
                    "ID/chủ buổi không khớp buổi đang mở của phiên hiện tại; không thao tác.");
                const rows = await details(workoutId);
                assert(Array.isArray(rows), "Danh sách detail không đúng contract.");
                const action = rows.some(detail => detail.status === "COMPLETED") ? "complete" : "cancel";
                console.log("Đóng buổi cũ theo lựa chọn rõ ràng:", { workoutId, action,
                    completed: rows.filter(detail => detail.status === "COMPLETED").length });
                const closed = (await request("PATCH", `${path}/${action}`)).data;
                const expectedStatus = action === "complete" ? "COMPLETED" : "CANCELLED";
                assert(closed?.workoutId === workoutId && closed.status === expectedStatus && closed.endTime,
                    "Response chưa xác nhận đóng đúng buổi.");
                const verified = (await request("GET", path)).data;
                unchanged(closed, verified, "Đọc lại không khớp kết quả đóng buổi; chưa báo thành công.");
            });
        });
    }
    async function seedUserFixtures() {
        return exclusive(async () => {
            await profile(false);
            await check("Tạo bài mẫu: yêu cầu phiên Admin", async () => {
                const probe = await request("GET", "/admin/__fitness_workout_permission_probe__", undefined, [403, 404]);
                equal(probe.status, 404, "Cần đăng nhập Admin để tạo bài mẫu; User không được gọi API quản trị.");
            });
            if (!seed) {
                seed = { prefix: `TEST_WT_USER_${Date.now()}`, categoryId: null, exerciseIds: [], ready: false };
                saveSeed();
            }
            if (!seed.categoryId) {
                await check("Tạo nhóm riêng cho bài mẫu User", async () => {
                    const category = (await request("POST", "/admin/exercise-categories",
                        { categoryName: seed.prefix }, [201])).data;
                    assert(Number.isInteger(category.categoryId) && category.categoryId > 0, "Response thiếu ID nhóm mẫu hợp lệ.");
                    seed.categoryId = category.categoryId; saveSeed();
                });
            }
            await check("Xác minh nhóm mẫu vẫn khả dụng", async () => {
                const category = (await request("GET", `/exercise-categories/${seed.categoryId}`)).data;
                assert(category.active && category.categoryName === seed.prefix,
                    "Nhóm mẫu đã bị sửa/ngừng; xem userFixtures() và khôi phục dữ liệu trước khi tiếp tục.");
            });
            for (let index = seed.exerciseIds.length; index < 3; index++) {
                await check(`Tạo bài mẫu User ${index + 1}`, async () => {
                    const exercise = (await request("POST", "/admin/exercises", {
                        exerciseName: `${seed.prefix}_E${index + 1}`, categoryId: seed.categoryId,
                        metValue: index === 0 ? 6 : 4, minDurationMinutes: index === 0 ? 1 : 0.1,
                        recommendedSets: 2, recommendedReps: 10
                    }, [201])).data;
                    assert(Number.isInteger(exercise.exerciseId) && exercise.exerciseId > 0
                        && !seed.exerciseIds.includes(exercise.exerciseId), "Response thiếu/trùng ID bài mẫu.");
                    seed.exerciseIds.push(exercise.exerciseId); saveSeed();
                });
            }
            await check("Bộ ba bài mẫu User sẵn sàng", async () => {
                const rows = [];
                for (const id of seed.exerciseIds) {
                    const exercise = (await request("GET", `/exercises/${id}`)).data;
                    assert(exercise.active && exercise.exerciseName.startsWith(seed.prefix)
                        && exercise.minDurationMinutes > 0 && exercise.minDurationMinutes <= 2,
                        "Bài mẫu đã thay đổi/ngừng; xem userFixtures() để kiểm tra.");
                    rows.push({ exerciseId: id, name: exercise.exerciseName, minMinutes: exercise.minDurationMinutes });
                }
                seed.ready = true; saveSeed(); console.table(rows);
            });
            console.log("Bộ bài mẫu giữ lại cho User:", userFixtures());
            console.log('Đăng nhập User, dán lại file script cùng tab, chạy await FitnessModuleTest.run({ mode: "user" })');
        });
    }
    globalThis.FitnessModuleTest = {
        run, testForeignAccess, seedUserFixtures, userFixtures, closeExistingWorkout,
        testAnonymous: () => exclusive(anonymousCheck),
        cleanup: () => exclusive(async () => { await profile(false); await check("Dọn đúng fixture trong manifest", cleanOwnFixtures); }),
        stop: () => state.abort?.abort(), report, context,
        get running() { return state.running; }
    };
    console.log('Đã nạp. Chạy: await FitnessModuleTest.run({ mode: "admin" })');
})();
