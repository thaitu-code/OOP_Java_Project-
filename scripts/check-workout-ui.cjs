// Executes the exact frontend JS with a minimal DOM and in-memory HTTP fixture, no real server/DB.
const fs = require("node:fs");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const { fixture } = require("./workout-ui-fixture.cjs");
const source = fs.readFileSync("src/main/resources/static/js/workout.js", "utf8");
const template = fs.readFileSync("src/main/resources/templates/workout.html", "utf8");
const settle = async () => { for (let i = 0; i < 15; i++) await new Promise(resolve => setImmediate(resolve)); };

function environment({ admin = false, wrong404 = false, csrf = true, prefix = "", holdSummary = false, automation = false, shared, holdRecovery = false, manage = false, hash = "", holdCatalog = false } = {}) {
  const clockState = shared?.clock || { now: new Date("2026-11-01T10:00:00Z").getTime() };
  class TestDate extends Date { constructor(...args) { super(...(args.length ? args : [clockState.now])); } static now() { return clockState.now; } }
  const listeners = new Map(), nodes = new Map(), interval = [], calls = [], gates = [];
  function element(id = "") {
    const events = {};
    const node = { id, events, dataset: {}, style: {}, elements: {}, hidden: false, disabled: false, value: "", content: "", textContent: "", open: false, htmlWrites: 0,
      classList: { toggle() {} }, setAttribute() {}, removeAttribute() {}, checkValidity: () => true,
      insertAdjacentHTML(position, html) { this.innerHTML += html; },
      addEventListener: (event, fn) => events[event] = fn, querySelector: () => ({ disabled: false }),
      reset() { for (const field of Object.values(this.elements)) field.value = ""; },
      showModal() { this.open = true; }, close() { this.open = false; this.events.close?.(); },
      prepend(node) { nodes.set(node.id, node); }, closest(selector) { return selector === "[data-action]" && this.dataset.action || selector === "[data-tab]" && this.dataset.tab ? this : null; } };
    let html = "";
    Object.defineProperty(node, "innerHTML", { get: () => html, set(value) { html = value; node.htmlWrites++; } });
    return node;
  }
  for (const match of template.matchAll(/\bid="([^"]+)"/g)) nodes.set(match[1], element(match[1]));
  const app = nodes.get("workout-app"); app.dataset = { base: prefix + "/", admin: String(admin), manage: String(manage), heartbeat: "10", timeout: "30" };
  nodes.get("active-filter").value = "active";
  const tabs = ["session", "library", "history", ...(admin ? ["manage"] : [])].map(tab => { const el = element(); el.dataset.tab = tab; return el; });
  const panels = [...nodes.values()].filter(node => node.id.startsWith("panel-"));
  for (const key of ["sets", "reps", "weightKg"]) nodes.get("complete-form").elements[key] = { value: "" };
  const backend = shared?.backend || fixture(() => clockState.now, { automation });
  class TestFormData {
    constructor(form) { this.values = form ? Object.entries(form.elements).map(([key, field]) => [key, field.value]) : []; }
    [Symbol.iterator]() { return this.values[Symbol.iterator](); }
    append(key, value) { this.values.push([key, value]); }
  }
  let releaseSummary;
  if (holdSummary) gates.push({ match: p => p === "/check-ins/summary", used: false,
    promise: new Promise(resolve => { releaseSummary = resolve; }) });
  let releaseRecovery;
  if (holdRecovery) gates.push({ match: p => p.endsWith("/recover"), used: false,
    promise: new Promise(resolve => { releaseRecovery = resolve; }) });
  let releaseCatalog;
  if (holdCatalog) gates.push({ match: p => p === "/exercises", used: false,
    promise: new Promise(resolve => { releaseCatalog = resolve; }) });
  const document = { hidden: false, getElementById: id => nodes.get(id), createElement: () => element(),
    querySelector(selector) { return { content: selector.includes("_csrf_header") ? "X-CSRF-TOKEN" : csrf ? "mock-csrf" : "" }; },
    querySelectorAll(selector) { return selector === "[data-tab]" ? tabs : selector === ".workout-panel" ? panels : []; },
    addEventListener(event, fn) { listeners.set(event, fn); } };
  const sandbox = { document, Date: TestDate, Intl, URL, AbortController, FormData: TestFormData, setTimeout: (fn, ms) => { const handle = setTimeout(fn, ms); handle.unref(); return handle; }, clearTimeout,
    setInterval: callback => interval.push(callback), history: { replaceState() {} }, location: { hash },
    window: { addEventListener(event, fn) { listeners.set("window:" + event, fn); } },
    fetch: async (url, options) => {
      calls.push({ url, ...options });
      assert(url.startsWith(prefix + "/"));
      const p = url.slice(prefix.length);
      const gate = gates.find(g => !g.used && g.match(p, options));
      if (gate) { gate.used = true; await gate.promise; }
      if (wrong404 && p === "/workouts/current/state") return { ok: false, status: 404, text: async () => JSON.stringify({ message: "No static resource workouts/current/state." }) };
      const result = backend.route(options.method, p, options.body && JSON.parse(options.body));
      return { ok: result.status >= 200 && result.status < 300, status: result.status, redirected: false, text: async () => result.data == null ? "" : JSON.stringify(result.data) };
    } };
  vm.runInNewContext(source, sandbox);
  return { nodes, backend, calls, document, releaseSummary, releaseRecovery, releaseCatalog, shared: { backend, clock: clockState },
    hold(match) { let release; const promise = new Promise(resolve => { release = resolve; }); gates.push({ match, promise, used: false }); return release; },
    async click(action, id) { const target = element(); target.dataset = { action, id: String(id || "") }; listeners.get("click")({ target }); await settle(); },
    async tab(name) { listeners.get("click")({ target: tabs.find(tab => tab.dataset.tab === name) }); await settle(); },
    async submit(id) { const target = nodes.get(id); target.elements.restSeconds = nodes.get("rest-seconds"); target.checkValidity = () => true; target.events.submit?.({ preventDefault() {}, target }); await settle(); },
    async modal(fields = {}) { const target = element(); for (const [key, value] of Object.entries(fields)) target.elements[key] = { value }; nodes.get("dialog-content").events.submit({ preventDefault() {}, target }); await settle(); },
    async tick(ms = 10000) { clockState.now += ms; for (const callback of interval) callback(); await settle(); },
    async hide() { document.hidden = true; listeners.get("visibilitychange")(); await settle(); },
    async show() { document.hidden = false; listeners.get("visibilitychange")(); await settle(); },
    async leave() { listeners.get("window:pagehide")(); await settle(); },
    async returnToPage() { listeners.get("window:pageshow")(); await settle(); }
  };
}

(async () => {
  const ui = environment({ prefix: "/fitness" }); await settle();
  assert(ui.calls.every(c => c.method === "GET"), "Loading only reads; no automatic workout/recover");
  assert.equal(ui.nodes.get("empty-session").hidden, false);
  await ui.click("create");
  const beforeAdd = ui.calls.length;
  await ui.click("add", 1);
  assert.equal(ui.calls.length - beforeAdd, 1, "Adding an exercise uses only the POST response, no three follow-up GET requests");
  await ui.click("add", 2);
  assert.equal(ui.backend.details.length, 2);
  ui.nodes.get("rest-seconds").value = "3"; await ui.submit("rest-form");
  await ui.click("start");
  assert.equal(ui.nodes.get("pause-detail").hidden, false);
  await ui.tick(9000); await ui.click("browse"); await ui.tick(1000);
  const firstHb = ui.calls.find(c => c.url.endsWith("/heartbeat"));
  assert(firstHb); assert.equal(firstHb.headers["X-CSRF-TOKEN"], "mock-csrf");
  assert.deepEqual(Object.keys(JSON.parse(firstHb.body)), ["timerToken"]);
  assert(!ui.nodes.get("active-time").textContent.startsWith("00:00"));
  await ui.click("select-detail", 2);
  assert.equal(ui.nodes.get("timer-name").textContent, "Squat", "Cannot abandon the controlling timer by selecting another exercise");
  await ui.click("pause"); assert.equal(ui.nodes.get("resume-detail").hidden, false);
  const activeBeforePause = ui.backend.details[0].activeDurationMillis;
  await ui.tick(10000); assert.equal(ui.backend.details[0].activeDurationMillis, activeBeforePause);
  await ui.click("resume"); await ui.click("rest"); await ui.tick(4000);
  assert.equal(ui.nodes.get("resume-detail").disabled, false);
  assert.equal(ui.backend.details[0].status, "RESTING", "Rest never automatically starts active time");
  await ui.click("resume");
  await ui.hide(); await ui.tick(10000);
  assert.equal(ui.backend.details[0].status, "IN_PROGRESS", "Switching tabs alone does not end an exercise");
  await ui.show(); await ui.leave(); const writesBefore = ui.calls.filter(c => c.method !== "GET").length;
  await ui.tick(40000); assert.equal(ui.calls.filter(c => c.method !== "GET").length, writesBefore, "Page exit stops the controlling timer");
  const beforeRecoveryActive = ui.backend.details[0].activeDurationMillis;
  await ui.returnToPage(); assert.equal(ui.nodes.get("recover-detail").hidden, true);
  const recoverBefore = ui.calls.filter(c => c.url.endsWith("/recover")).length;
  assert.equal(recoverBefore, 1, "Returning after pagehide automatically recovers once");
  assert.equal(ui.backend.details[0].activeDurationMillis, beforeRecoveryActive, "Recovery never backfills offline active time");
  await ui.returnToPage();
  assert.equal(ui.calls.filter(c => c.url.endsWith("/recover")).length, 1, "Duplicate pageshow does not recover twice");
  await ui.tick(10000);
  await ui.submit("complete-form");
  const completedBody = JSON.parse(ui.calls.find(c => c.url.endsWith("/complete")).body);
  assert.deepEqual(Object.keys(completedBody).sort(), ["reps", "sets", "timerToken", "weightKg"]);
  assert.equal(ui.backend.details[0].status, "COMPLETED");
  assert.equal(ui.nodes.get("stat-checkin").textContent, "Đã check-in ✓");
  await ui.click("finish"); await ui.modal();
  assert.equal(ui.backend.workouts[0].status, "COMPLETED");
  assert.equal(ui.backend.details[1].status, "CANCELLED");
  assert.equal(ui.nodes.get("empty-session").hidden, false);

  const completedWithSlowSummary = environment(); await settle();
  await completedWithSlowSummary.click("create");
  await completedWithSlowSummary.click("add", 1); await completedWithSlowSummary.click("add", 2);
  await completedWithSlowSummary.click("start"); await completedWithSlowSummary.tick(10000);
  const releaseCompletedSummary = completedWithSlowSummary.hold(p => p === "/check-ins/summary");
  await completedWithSlowSummary.submit("complete-form");
  assert.equal(completedWithSlowSummary.backend.details[0].status, "COMPLETED");
  assert.equal(completedWithSlowSummary.nodes.get("workout-activity").hidden, true, "Confirmed completion releases workout actions while summary is still loading");
  await completedWithSlowSummary.click("finish"); await completedWithSlowSummary.modal();
  assert.equal(completedWithSlowSummary.backend.workouts[0].status, "COMPLETED", "Finishing the workout does not wait for a slow summary read");
  releaseCompletedSummary(); await settle();
  assert.equal(completedWithSlowSummary.nodes.get("stat-checkin").textContent, "Đã check-in ✓");

  const missing = environment({ wrong404: true }); await settle();
  assert.match(missing.nodes.get("app-notice").textContent, /No static resource/);
  assert(missing.calls.every(c => c.method === "GET"), "Unmapped route is not mistaken for an empty workout");
  const noCsrf = environment({ csrf: false }); await settle(); await noCsrf.click("create");
  assert(noCsrf.calls.every(c => c.method === "GET"), "Missing CSRF blocks writes before fetch");

  const revoked = environment(); await settle();
  await revoked.click("create"); await revoked.click("add", 1); await revoked.click("start");
  revoked.backend.details[0]._token = "rotated-by-another-tab";
  await revoked.tick(10000);
  assert.equal(revoked.nodes.get("recover-detail").hidden, false);
  assert(!revoked.calls.some(c => c.url.endsWith("/recover")), "A revoked tab must not automatically steal control back");

  const firstTab = environment({ automation: true }); await settle();
  await firstTab.click("create"); await firstTab.click("add", 1); await firstTab.modal({ levelId: "1" });
  await firstTab.click("start"); await firstTab.tick(3000);
  const oldLease = firstTab.backend.details[0]._token;
  const reloaded = environment({ shared: firstTab.shared }); await settle();
  assert.equal(reloaded.calls.filter(c => c.url.endsWith("/recover")).length, 1, "Initial load automatically recovers the existing exercise");
  assert.notEqual(firstTab.backend.details[0]._token, oldLease);
  assert.equal(firstTab.backend.details[0].status, "RESTING", "Automatic recovery preserves the remaining planned rest");
  assert.equal(reloaded.nodes.get("workout-dialog").open, false, "No recovery confirmation dialog on entry");
  assert.equal(reloaded.nodes.get("recover-detail").hidden, true);
  await firstTab.tick(10000);
  assert.equal(firstTab.calls.filter(c => c.url.endsWith("/recover")).length, 0, "Old tab does not fight the newly loaded tab for its token");
  await firstTab.hide(); await firstTab.show(); await firstTab.tick(10000);
  assert.equal(firstTab.calls.filter(c => c.url.endsWith("/recover")).length, 0, "Visibility and polling cannot claim control back");

  const pausedBase = environment(); await settle();
  await pausedBase.click("create"); await pausedBase.click("add", 1); await pausedBase.click("start");
  await pausedBase.tick(10000); await pausedBase.click("pause");
  const pausedActive = pausedBase.backend.details[0].activeDurationMillis;
  const pausedReload = environment({ shared: pausedBase.shared }); await settle();
  assert.equal(pausedReload.calls.filter(c => c.url.endsWith("/recover")).length, 1);
  assert.equal(pausedReload.backend.details[0].status, "PAUSED", "Acquiring the lease must not undo an intentional pause");
  await pausedReload.tick(10000);
  assert.equal(pausedReload.backend.details[0].activeDurationMillis, pausedActive);
  assert.equal(pausedReload.nodes.get("resume-detail").hidden, false);

  const interrupted = environment({ shared: pausedBase.shared, holdRecovery: true }); await settle();
  assert.equal(interrupted.calls.filter(c => c.url.endsWith("/recover")).length, 1);
  await interrupted.leave(); await interrupted.returnToPage();
  interrupted.releaseRecovery(); await settle();
  assert.equal(interrupted.calls.filter(c => c.url.endsWith("/recover")).length, 2, "Leaving during startup schedules only one recovery for each actual entry");
  assert.equal(interrupted.nodes.get("recover-detail").hidden, true);

  const noCsrfReload = environment({ shared: pausedBase.shared, csrf: false }); await settle();
  assert(!noCsrfReload.calls.some(c => c.method !== "GET"), "Automatic recovery respects CSRF and never sends an unprotected PATCH");
  assert.equal(noCsrfReload.nodes.get("recover-detail").hidden, false, "Failed recovery leaves the explicit retry available");
  await noCsrfReload.tick(30000); await noCsrfReload.returnToPage();
  assert(!noCsrfReload.calls.some(c => c.method !== "GET"), "Failure does not create an automatic recovery loop");

  const idleBase = environment(); await settle(); await idleBase.click("create"); await idleBase.click("add", 1);
  const idleReload = environment({ shared: idleBase.shared }); await settle();
  assert(idleReload.calls.every(c => c.method === "GET"), "Loading a selected but not started exercise must not start or recover it");
  idleBase.backend.details[0].status = "COMPLETED";
  const completedReload = environment({ shared: idleBase.shared }); await settle();
  assert(completedReload.calls.every(c => c.method === "GET"), "Completed details must never be recovered on entry");

  const adminSession = environment({ admin: true, manage: true, hash: "#session" }); await settle();
  assert.equal(adminSession.nodes.get("panel-session").hidden, false, "Reloading the admin URL with #session preserves the user's workout tab");
  const defaultAdmin = environment({ admin: true, manage: true }); await settle();
  assert.equal(defaultAdmin.nodes.get("panel-manage").hidden, false, "Admin library without a hash still opens management");

  const delayedBase = environment(); await settle(); await delayedBase.click("create"); await delayedBase.click("add", 1); await delayedBase.click("start");
  const delayedCatalog = environment({ shared: delayedBase.shared, holdCatalog: true }); await settle();
  assert.equal(delayedCatalog.calls.filter(c => c.url.endsWith("/recover")).length, 1);
  await delayedCatalog.tick(10000);
  assert.equal(delayedCatalog.calls.filter(c => c.url.endsWith("/heartbeat")).length, 1, "A slow library must not freeze the recovered timer by blocking heartbeat");
  await delayedCatalog.click("pause");
  assert.equal(delayedCatalog.backend.details[0].status, "PAUSED", "Recovered workout controls work before the library finishes loading");
  delayedCatalog.releaseCatalog(); await settle();

  const slow = environment(); await settle(); await slow.click("create");
  const releaseAdd = slow.hold((p, o) => p.endsWith("/details") && o.method === "POST");
  await slow.click("add", 1);
  assert.equal(slow.nodes.get("workout-activity").hidden, false, "Feedback appears before the delayed server response");
  assert.equal(slow.backend.details.length, 0, "Do not claim success before the server response");
  assert.match(slow.nodes.get("session-details").innerHTML, /chờ máy chủ xác nhận/, "Pending exercise is visible immediately without a fake completed detail");
  await slow.click("add", 1);
  assert.equal(slow.calls.filter(c => c.method === "POST" && c.url.endsWith("/details")).length, 1, "Double click does not create duplicate requests");
  releaseAdd(); await settle();
  assert.equal(slow.backend.details.length, 1);
  assert.equal(slow.nodes.get("workout-activity").hidden, true);
  await slow.click("start");
  const detailsWrites = slow.nodes.get("session-details").htmlWrites;
  const libraryWrites = slow.nodes.get("exercise-library").htmlWrites;
  slow.nodes.get("complete-form").elements.sets.value = "4";
  const releaseHeartbeat = slow.hold(p => p.endsWith("/heartbeat"));
  await slow.tick(10000);
  assert.equal(slow.nodes.get("pause-detail").disabled, false, "Background heartbeat does not disable user controls");
  await slow.click("pause");
  assert.equal(slow.calls.filter(c => c.url.endsWith("/pause")).length, 0, "Mutation waits for the in-flight heartbeat");
  assert.equal(slow.nodes.get("workout-activity").hidden, false);
  await slow.click("browse");
  assert.equal(slow.nodes.get("panel-library").hidden, false, "Local navigation reacts immediately during a slow API request");
  releaseHeartbeat(); await settle();
  assert.equal(slow.calls.filter(c => c.url.endsWith("/pause")).length, 1, "The user click is executed once instead of being silently dropped");
  assert.equal(slow.backend.details[0].status, "PAUSED");
  assert.equal(slow.nodes.get("complete-form").elements.sets.value, "4", "Heartbeat does not reset an input being edited");
  assert.equal(slow.nodes.get("exercise-library").htmlWrites, libraryWrites, "Adding/status updates do not rebuild the whole exercise grid");
  const pausedWrites = slow.nodes.get("session-details").htmlWrites;
  await slow.tick(10000);
  assert.equal(slow.nodes.get("session-details").htmlWrites, pausedWrites, "Unchanged timer polls preserve exercise row nodes");
  assert(pausedWrites > detailsWrites, "An actual state transition still renders");

  const initial = environment({ holdSummary: true }); await settle();
  assert.equal(initial.nodes.get("hero-start").disabled, false, "Slow summary does not block core workout interaction");
  await initial.click("create"); assert.equal(initial.backend.workouts.length, 1);
  assert.equal(initial.calls.filter(c => c.url === "/workouts/current/state").length, 1);
  assert(!initial.calls.some(c => c.url === "/workouts/current"), "Startup uses one combined session request");
  initial.releaseSummary(); await settle();

  const cached = environment(); await settle();
  await cached.tab("history"); await cached.tab("session"); await cached.tab("history");
  assert.equal(cached.calls.filter(c => c.url.startsWith("/workouts?")).length, 1, "Returning to recent history uses its cached page");
  await cached.tick(16000); await cached.tab("history");
  assert.equal(cached.calls.filter(c => c.url.startsWith("/workouts?")).length, 2, "Expired history is revalidated");
  await cached.click("create");
  const releaseHistory = cached.hold(p => p.startsWith("/workouts?"));
  await cached.tab("history"); await cached.tab("session"); await cached.tab("history");
  const historyRequests = cached.calls.filter(c => c.url.startsWith("/workouts?")).length;
  assert.equal(historyRequests, 3, "Repeated navigation shares the in-flight history request");
  await cached.click("add", 1);
  assert.equal(cached.backend.details.length, 1, "A slow history read does not block a workout mutation");
  releaseHistory(); await settle();

  const management = environment({ admin: true }); await settle();
  const beforeCategoryStatus = management.calls.length;
  await management.click("toggle-category", 1); await management.modal();
  assert.equal(management.calls.length - beforeCategoryStatus, 1, "Category status reuses the server response instead of reloading the catalog");
  assert.equal(management.backend.categories[0].active, false);
  assert.match(management.nodes.get("manage-categories").innerHTML, /Đã ngừng/);
  const beforeExerciseStatus = management.calls.length;
  await management.click("toggle-exercise", 1); await management.modal();
  assert.equal(management.calls.length - beforeExerciseStatus, 1, "Exercise status uses one API request");
  assert.equal(management.backend.exercises[0].active, false);
  const beforeDelete = management.calls.length;
  await management.click("delete-exercise", 3); await management.modal();
  assert.equal(management.calls.length - beforeDelete, 1, "Delete updates the catalog only after HTTP success");
  assert(!management.backend.exercises.some(e => e.exerciseId === 3));

  const automated = environment({ automation: true }); await settle();
  await automated.click("create"); await automated.click("add", 1);
  assert.equal(automated.backend.details.length, 0, "Configured exercise waits for an explicit level choice");
  await automated.modal({ levelId: "1" }); await automated.click("add", 2);
  const plannedPost = automated.calls.find(c => c.method === "POST" && c.url.endsWith("/details"));
  assert.equal(JSON.parse(plannedPost.body).levelId, 1);
  await automated.click("start");
  assert.equal(automated.nodes.get("complete-form").hidden, true, "Early finish is hidden before the server threshold");
  assert.equal(automated.nodes.get("rest-detail").hidden, true, "Automatic plans do not expose manual rest override");
  await automated.tick(3000);
  assert.equal(automated.backend.details[0].status, "RESTING", "Boundary triggers heartbeat without waiting for 10-second poll");
  await automated.tick(2000);
  assert.equal(automated.backend.details[0].status, "IN_PROGRESS");
  assert.equal(automated.backend.details[0].automation.currentSet, 2);
  assert(!automated.calls.some(c => c.url.endsWith("/rest") || c.url.endsWith("/resume")), "Server heartbeat owns automatic transitions");
  await automated.tick(5000);
  assert.equal(automated.nodes.get("complete-form").hidden, false);
  assert.equal(automated.nodes.get("complete-detail").textContent, "Kết thúc bài tập sớm");
  await automated.submit("complete-form");
  assert.equal(automated.backend.details[0].status, "COMPLETED");
  assert.equal(automated.nodes.get("timer-name").textContent, "Chạy bộ");
  assert.equal(automated.backend.details[1].status, "NOT_STARTED", "Next exercise is selected but never automatically started");

  const full = environment({ automation: true }); await settle();
  await full.click("create"); await full.click("add", 1); await full.modal({ levelId: "1" }); await full.click("start");
  await full.tick(10000); await full.tick(10000);
  assert.equal(full.backend.details[0].status, "IN_PROGRESS", "Reaching the full target does not complete or create check-in");
  assert.equal(full.nodes.get("complete-detail").textContent, "Kết thúc & bài tiếp theo");
  assert.equal(full.nodes.get("stat-checkin").textContent, "Chưa check-in");
  const activeAfterTarget = full.backend.details[0].activeDurationMillis;
  await full.tick(10000);
  assert(full.backend.details[0].activeDurationMillis > activeAfterTarget, "Active time continues past the last set until the user finishes");

  const milestone = environment({ automation: true }); await settle();
  Object.assign(milestone.backend.exercises[0].levels[0], { sets: 1, secondsPerSet: 1, estimatedSeconds: 7 });
  await milestone.click("refresh"); await milestone.click("create"); await milestone.click("add", 1);
  await milestone.modal({ levelId: "1" }); await milestone.click("start");
  await milestone.tick(1000); await milestone.tick(3667);
  assert.equal(milestone.nodes.get("complete-detail").textContent, "Kết thúc bài tập sớm");
  assert.equal(milestone.nodes.get("complete-form").hidden, false, "The 2/3 milestone is confirmed without waiting for the next regular poll");
  await milestone.tick(2333);
  assert.equal(milestone.nodes.get("complete-detail").textContent, "Kết thúc & bài tiếp theo", "Full milestone also triggers a timely confirmation");

  const adminLevels = environment({ admin: true }); await settle();
  await adminLevels.click("new-level", 1);
  await adminLevels.modal({ name: "Tùy chọn riêng", sets: "2", reps: "", secondsPerSet: "30", restSeconds: "60", estimatedSeconds: "120" });
  assert.equal(adminLevels.backend.exercises[0].levels[0].name, "Tùy chọn riêng");
  assert.equal(adminLevels.backend.exercises[0].levels[0].reps, null);
  assert.match(adminLevels.nodes.get("dialog-content").innerHTML, /Tùy chọn riêng/);

  const xss = environment(); await settle();
  xss.backend.exercises[0].mediaUrl = "https://cdn.example.test/MOVE.GIF?v=1&size=small";
  await xss.click("refresh"); await xss.click("exercise-info", 1);
  assert.match(xss.nodes.get("dialog-content").innerHTML, /<img class="inline-exercise-media"/);
  assert(xss.nodes.get("dialog-content").innerHTML.includes("MOVE.GIF?v=1&amp;size=small"));
  await xss.click("close-dialog"); await xss.click("add", 1);
  assert.match(xss.nodes.get("timer-media").innerHTML, /<img class="inline-exercise-media"/, "Direct image is displayed inside the timer too");
  xss.backend.exercises[0].mediaUrl = "https://github.com/team/assets/blob/main/exercises/move.gif";
  await xss.click("refresh"); await xss.click("exercise-info", 1);
  assert(xss.nodes.get("dialog-content").innerHTML.includes('src="https://raw.githubusercontent.com/team/assets/main/exercises/move.gif"'), "Existing GitHub page links are rendered as raw media without rewriting history");
  assert(xss.nodes.get("timer-media").innerHTML.includes('src="https://raw.githubusercontent.com/team/assets/main/exercises/move.gif"'));
  xss.backend.exercises[0].mediaUrl = "https://cdn.example.test/move.mp4?version=2";
  await xss.click("refresh"); await xss.click("exercise-info", 1);
  assert.match(xss.nodes.get("dialog-content").innerHTML, /<video class="inline-exercise-media"/);
  xss.backend.exercises[0].mediaUrl = "javascript:alert(1)";
  await xss.click("close-dialog"); await xss.click("refresh"); await xss.click("exercise-info", 1);
  assert(!xss.nodes.get("dialog-content").innerHTML.includes('src="javascript:'));
  await xss.click("close-dialog");
  xss.backend.exercises[0].exerciseName = '<img src=x onerror="alert(1)">';
  xss.backend.exercises[0].aiVideoUrl = "javascript:alert(1)";
  await xss.click("refresh");
  assert(xss.nodes.get("exercise-library").innerHTML.includes("&lt;img"));
  await xss.click("exercise-info", 1);
  assert(!xss.nodes.get("dialog-content").innerHTML.includes('href="javascript:'));
  console.log("PASS: Workout UI lifecycle/security/media/automatic cycles, slow-network controls and entry recovery (reload/Back, one recovery per entry, pause/rest preserved, no token fighting, CSRF and failure guard).");
})().catch(error => { console.error(error); process.exitCode = 1; });
