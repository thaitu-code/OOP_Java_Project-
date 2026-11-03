// In-memory HTTP fixture for UI tests only. Does not read credentials or connect to a database.
const { randomUUID } = require("node:crypto");

function fixture(now = () => Date.now(), { automation = false } = {}) {
  const categories = [{ categoryId: 1, categoryName: "Toàn thân", active: true }, { categoryId: 2, categoryName: "Sức bền", active: true }];
  const exercises = [
    { exerciseId: 1, exerciseName: "Squat", categoryId: 1, categoryName: "Toàn thân", description: "Đứng vững hai chân, hạ người có kiểm soát và trở về vị trí ban đầu.", metValue: 5, minDurationMinutes: 0.1, recommendedSets: 3, recommendedReps: 12, active: true },
    { exerciseId: 2, exerciseName: "Chạy bộ", categoryId: 2, categoryName: "Sức bền", description: "Giữ nhịp chạy phù hợp với thể trạng và hít thở đều.", metValue: 7, minDurationMinutes: 0.1, active: true },
    { exerciseId: 3, exerciseName: "Plank", categoryId: 1, categoryName: "Toàn thân", description: "Giữ thân người ổn định, không võng lưng.", metValue: 3, minDurationMinutes: 0.1, active: true }
  ];
  if (automation) exercises[0].levels = [{ id: 1, name: "Cấp kiểm thử", sets: 3, reps: 10,
    secondsPerSet: 3, restSeconds: 2, estimatedSeconds: 15, active: true }];
  const workouts = [], details = [];
  const iso = () => new Date(now()).toISOString();
  const fail = (status, message) => ({ status, data: { message } });
  const response = (data, status = 200) => ({ status, data });
  const summary = w => {
    const finished = details.filter(d => d.workoutId === w.workoutId && d.status === "COMPLETED");
    return { ...w, totalDurationMinutes: +finished.reduce((sum, d) => sum + d.durationMinutes, 0).toFixed(1), totalCaloriesBurned: +finished.reduce((sum, d) => sum + d.caloriesBurned, 0).toFixed(1) };
  };
  const timer = d => ({ detail: d, timerToken: d._token, lastRecordedAt: d._last ? new Date(d._last).toISOString() : null,
    lastHeartbeatAt: d._last ? new Date(d._last).toISOString() : null, activeDurationMillis: d.activeDurationMillis,
    restDurationMillis: d.restDurationMillis, restStartedAt: d._restStart ? new Date(d._restStart).toISOString() : null,
    restEndsAt: d._restEnd ? new Date(d._restEnd).toISOString() : null });
  const clean = value => JSON.parse(JSON.stringify(value, (key, item) => key.startsWith("_") ? undefined : item));
  function automaticPosition(d) {
    const a = d.automation;
    const total = d.activeDurationMillis + d.restDurationMillis;
    const set = a.secondsPerSet * 1000, rest = d.approvedRestSecondsSnapshot * 1000;
    const cycles = Math.min(a.plannedSets - 1, Math.floor(total / (set + rest)));
    const within = total - cycles * (set + rest), last = cycles === a.plannedSets - 1;
    d.activeDurationMillis = cycles * set + (last ? within : Math.min(within, set));
    d.restDurationMillis = total - d.activeDurationMillis;
    const resting = !last && within >= set;
    d.status = resting ? "RESTING" : "IN_PROGRESS";
    a.currentSet = cycles + 1; a.elapsedInSetMillis = Math.min(within, set);
    a.phaseRemainingMillis = resting ? set + rest - within : Math.max(0, set - within);
    a.phaseEndsAt = a.phaseRemainingMillis ? new Date(d._last + a.phaseRemainingMillis).toISOString() : null;
    a.canFinishEarly = total >= a.minimumMillis; a.fullTargetReached = total >= a.estimatedSeconds * 1000;
    d._restStart = resting ? d._last - (within - set) : null;
    d._restEnd = resting ? d._last + a.phaseRemainingMillis : null;
  }
  function record(d) {
    if (d.automation && ["IN_PROGRESS", "RESTING"].includes(d.status)) {
      if (now() - d._last > 30000) { d.status = "FROZEN"; d._token = null; return; }
      d.activeDurationMillis += now() - d._last; d._last = now(); automaticPosition(d); return;
    }
    if (d.status === "IN_PROGRESS") {
      if (now() - d._last > 30000) { d.status = "FROZEN"; d._token = null; return; }
      d.activeDurationMillis += now() - d._last;
    } else if (d.status === "RESTING") d.restDurationMillis += Math.max(0, Math.min(now(), d._restEnd) - d._last);
    d._last = d.status === "RESTING" ? Math.min(now(), d._restEnd) : now();
  }
  function route(method, url, body = {}) {
    const u = new URL(url, "http://localhost"); const p = u.pathname;
    if (method === "GET" && p === "/exercises") return response(exercises);
    if (method === "GET" && p === "/exercise-categories") return response(categories);
    if (method === "GET" && p === "/check-ins/summary") { const done = details.some(d => d.status === "COMPLETED"); return response({ currentStreak: done ? 1 : 0, highestStreak: done ? 1 : 0, checkedInToday: done, today: iso().slice(0, 10) }); }
    if (method === "GET" && p === "/workouts/current") { const w = workouts.find(w => w.status === "IN_PROGRESS"); return w ? response(summary(w)) : response(null, 404); }
    if (method === "GET" && p === "/workouts/current/state") {
      const w = workouts.find(w => w.status === "IN_PROGRESS");
      if (!w) return response(null, 404);
      const rows = details.filter(d => d.workoutId === w.workoutId);
      const active = rows.find(d => ["IN_PROGRESS", "RESTING", "PAUSED", "FROZEN"].includes(d.status));
      const view = active ? timer(active) : null;
      if (view) view.timerToken = null;
      return response({ workout: summary(w), details: rows, timer: view });
    }
    if (p === "/workouts" && method === "POST") {
      if (workouts.some(w => w.status === "IN_PROGRESS")) return fail(409, "Bạn đang có một buổi tập chưa kết thúc");
      const w = { workoutId: workouts.length + 1, userId: 999, status: "IN_PROGRESS", startTime: iso(), endTime: null }; workouts.push(w); return response(summary(w), 201);
    }
    if (p === "/workouts" && method === "GET") {
      const list = workouts.filter(w => !u.searchParams.get("status") || w.status === u.searchParams.get("status")).slice().reverse();
      const page = Number(u.searchParams.get("page") || 0), size = Number(u.searchParams.get("size") || 10);
      return response({ content: list.slice(page * size, (page + 1) * size).map(summary), page, size, totalPages: Math.ceil(list.length / size), totalElements: list.length });
    }
    const match = p.match(/^\/workouts\/(\d+)(?:\/(.*))?$/);
    if (match) {
      const w = workouts.find(w => w.workoutId === Number(match[1])); if (!w) return response(null, 404);
      const suffix = match[2];
      if (!suffix && method === "GET") return response(summary(w));
      if (suffix === "details" && method === "GET") return response(details.filter(d => d.workoutId === w.workoutId));
      if (["complete", "cancel"].includes(suffix)) {
        if (suffix === "complete" && !details.some(d => d.workoutId === w.workoutId && d.status === "COMPLETED")) return fail(409, "Cần hoàn thành ít nhất một bài");
        w.status = suffix === "complete" ? "COMPLETED" : "CANCELLED"; w.endTime = iso();
        details.filter(d => d.workoutId === w.workoutId && !["COMPLETED", "CANCELLED"].includes(d.status)).forEach(d => { d.status = "CANCELLED"; d.cancelledAt = iso(); });
        return response(summary(w));
      }
      if (method === "POST" && suffix === "details") {
        const e = exercises.find(e => e.exerciseId === body.exerciseId && e.active); if (!e) return response(null, 404);
        if (details.some(d => d.workoutId === w.workoutId && d.exerciseIdSnapshot === e.exerciseId)) return fail(400, "Bài đã có trong buổi");
        const d = { detailId: details.length + 1, workoutId: w.workoutId, exerciseIdSnapshot: e.exerciseId, exerciseNameSnapshot: e.exerciseName,
          categoryIdSnapshot: e.categoryId, categoryNameSnapshot: e.categoryName, metValueSnapshot: e.metValue, minDurationMinutesSnapshot: null,
          approvedRestSecondsSnapshot: null, status: "NOT_STARTED", durationMinutes: 0, caloriesBurned: 0, activeDurationMillis: 0, restDurationMillis: 0, startedAt: null, completedAt: null };
        if (e.levels?.length) {
          const l = e.levels.find(l => l.id === body.levelId && l.active);
          if (!l) return fail(400, "Hãy chọn cấp độ hợp lệ");
          d.automation = { levelId: l.id, levelName: l.name, plannedSets: l.sets, reps: l.reps,
            secondsPerSet: l.secondsPerSet, estimatedSeconds: l.estimatedSeconds,
            minimumMillis: Math.ceil(l.estimatedSeconds * 2000 / 3), currentSet: 1, elapsedInSetMillis: 0,
            phaseRemainingMillis: l.secondsPerSet * 1000, phaseEndsAt: null, canFinishEarly: false, fullTargetReached: false };
          d.approvedRestSecondsSnapshot = l.restSeconds;
        }
        details.push(d); return response(d, 201);
      }
      const dm = suffix?.match(/^details\/(\d+)\/(.+)$/);
      if (dm) {
        const d = details.find(d => d.workoutId === w.workoutId && d.detailId === Number(dm[1])); if (!d) return response(null, 404);
        const action = dm[2];
        if (action === "timer" && method === "GET") { const view = timer(d); view.timerToken = null; return response(view); }
        if (action === "rest-duration") { d.approvedRestSecondsSnapshot = body.restSeconds; return response({ detailId: d.detailId, workoutId: w.workoutId, approvedRestSecondsSnapshot: body.restSeconds }); }
        if (action === "start") {
          d.status = "IN_PROGRESS"; d.startedAt = iso(); d._last = now(); d._token = randomUUID(); d.minDurationMinutesSnapshot = exercises.find(e => e.exerciseId === d.exerciseIdSnapshot).minDurationMinutes;
          if (d.automation) automaticPosition(d);
        } else if (action === "recover") { if (d.status === "RESTING") record(d); else if (d.status !== "PAUSED") d.status = "IN_PROGRESS"; d._last = d.status === "RESTING" ? Math.min(now(), d._restEnd) : now(); d._token = randomUUID(); }
        else {
          if (!d._token || body.timerToken !== d._token) return fail(409, "Quyền điều khiển đã thay đổi");
          if (action === "resume") { if (d.status === "RESTING") { if (now() < d._restEnd) return fail(409, "Chưa hết thời gian nghỉ"); record(d); } d.status = "IN_PROGRESS"; d._last = now(); d._token = randomUUID(); }
          else {
            record(d);
            if (action === "pause") d.status = "PAUSED";
            if (action === "rest") { if (!d.approvedRestSecondsSnapshot) return fail(409, "Chưa duyệt nghỉ"); d.status = "RESTING"; d._restStart = now(); d._restEnd = now() + d.approvedRestSecondsSnapshot * 1000; }
            if (action === "complete") {
              if (d.activeDurationMillis + d.restDurationMillis < (d.automation?.minimumMillis ?? d.minDurationMinutesSnapshot * 60000)) return fail(409, "Chưa đủ thời gian tối thiểu");
              d.status = "COMPLETED"; d.completedAt = iso(); d._token = null; d.durationMinutes = +((d.activeDurationMillis + d.restDurationMillis) / 60000).toFixed(1);
              d.caloriesBurned = +(d.metValueSnapshot * 3.5 * 60 * d.activeDurationMillis / 12000000).toFixed(1);
              Object.assign(d, { sets: body.sets, reps: body.reps, weightKg: body.weightKg }); return response(d);
            }
          }
        }
        return response(timer(d));
      }
    }
    const cat = p.match(/^\/admin\/exercise-categories(?:\/(\d+)(\/activate)?)?$/);
    if (cat) {
      if (method === "POST") { const c = { ...body, categoryId: Math.max(0, ...categories.map(c => c.categoryId)) + 1, active: true }; categories.push(c); return response(c, 201); }
      const c = categories.find(c => c.categoryId === Number(cat[1])); if (!c) return response(null, 404);
      if (method === "DELETE") c.active = false; else if (cat[2]) c.active = true; else c.categoryName = body.categoryName;
      return response(c);
    }
    const ex = p.match(/^\/admin\/exercises(?:\/(\d+)(?:\/(activate|deactivate))?)?$/);
    const levelPath = p.match(/^\/admin\/exercises\/(\d+)\/levels(?:\/(\d+)(?:\/(activate|deactivate))?)?$/);
    if (levelPath) {
      const e = exercises.find(e => e.exerciseId === Number(levelPath[1])); if (!e) return response(null, 404);
      e.levels ||= [];
      if (method === "POST") { const level = { ...body, id: Math.max(0, ...e.levels.map(l => l.id)) + 1, active: true }; e.levels.push(level); return response(level, 201); }
      const level = e.levels.find(l => l.id === Number(levelPath[2])); if (!level) return response(null, 404);
      if (method === "PUT") Object.assign(level, body); else level.active = levelPath[3] === "activate";
      return response(level);
    }
    if (ex) {
      if (method === "POST") { const e = { ...body, exerciseId: Math.max(0, ...exercises.map(e => e.exerciseId)) + 1, active: true, categoryName: categories.find(c => c.categoryId === body.categoryId)?.categoryName }; exercises.push(e); return response(e, 201); }
      const i = exercises.findIndex(e => e.exerciseId === Number(ex[1])); if (i < 0) return response(null, 404);
      if (method === "DELETE") { exercises.splice(i, 1); return response(null, 204); }
      if (ex[2]) exercises[i].active = ex[2] === "activate"; else Object.assign(exercises[i], body, { categoryName: categories.find(c => c.categoryId === body.categoryId)?.categoryName });
      return response(exercises[i]);
    }
    return fail(404, "Unmapped fixture route");
  }
  return { route: (...args) => { const result = route(...args); return { ...result, data: clean(result.data) }; }, exercises, categories, workouts, details };
}
module.exports = { fixture };
