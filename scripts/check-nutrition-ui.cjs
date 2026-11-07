// Runs the actual nutrition.js against a small DOM and disposable in-memory HTTP fixtures.
// This verifies client behavior, not PostgreSQL or the production API implementation.
const fs = require("node:fs");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const { fixture } = require("./nutrition-ui-fixture.cjs");
const source = fs.readFileSync("src/main/resources/static/js/nutrition.js", "utf8");
const template = fs.readFileSync("src/main/resources/templates/nutrition.html", "utf8");
const settle = async () => { for (let i = 0; i < 20; i++) await new Promise(resolve => setImmediate(resolve)); };
function environment({ admin = false, csrf = true, prefix = "/fitness", hash = "", manage = false, redirected = false, malformedWrite = false } = {}) {
  const time = { now: new Date("2026-11-07T10:00:00Z").getTime() };
  class TestDate extends Date { constructor(...args) { super(...(args.length ? args : [time.now])); } static now() { return time.now; } }
  const backend = fixture(() => time.now), calls = [], nodes = new Map(), allNodes = [], listeners = {}, intervals = [], gates = [];
  const attrs = text => Object.fromEntries([...text.matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
  function node(attributes = {}) {
    const result = { id: attributes.id || "", dataset: {}, events: {}, elements: {}, children: [], connected: true,
      disabled: false, hidden: false, open: false, value: attributes.value || "", textContent: "", className: attributes.class || "",
      classList: { toggle() {} }, setAttribute(key, value) { this[key] = value; }, removeAttribute(key) { delete this[key]; },
      addEventListener(event, fn) { this.events[event] = fn; }, checkValidity() { return true; },
      querySelectorAll(selector) { return this.children.filter(n => n.connected && matches(n, selector)); },
      querySelector(selector) { return this.querySelectorAll(selector)[0] || null; },
      showModal() { this.open = true; }, close() { this.open = false; this.events.close?.(); },
      closest(selector) { return matches(this, selector) ? this : null; },
      matches(selector) { return matches(this, selector); }
    };
    for (const [key, value] of Object.entries(attributes)) {
      if (key.startsWith("data-")) result.dataset[key.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = value;
    }
    let html = "";
    Object.defineProperty(result, "innerHTML", { get: () => html, set(value) {
      html = value; for (const child of result.children) child.connected = false;
      result.children = [];
      // Forms are parsed separately so their controls can be addressed through form.elements.
      for (const match of value.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form>/g)) {
        const form = node({ ...attrs(match[1]), tag: "form" }); form.tag = "form";
        form.innerHTML = match[2]; result.children.push(form, ...form.children);
        for (const child of form.children) if (child.name) form.elements[child.name] = child;
      }
      const withoutForms = value.replace(/<form\b[^>]*>[\s\S]*?<\/form>/g, "");
      for (const match of withoutForms.matchAll(/<(button|input|select|textarea)\b([^>]*)>(?:([\s\S]*?)<\/\1>)?/g)) {
        const child = node(attrs(match[2])); child.tag = match[1]; child.type = attrs(match[2]).type;
        child.name = attrs(match[2]).name;
        if (child.tag === "textarea") child.value = match[3] || "";
        if (child.tag === "select") {
          const options = [...(match[3] || "").matchAll(/<option\b([^>]*)>/g)];
          const option = options.find(o => /\bselected\b/.test(o[1])) || options[0];
          child.value = option ? attrs(option[1]).value || "" : "";
        }
        child.textContent = match[3] || "";
        result.children.push(child);
      }
    } });
    if (result.id) nodes.set(result.id, result);
    allNodes.push(result); return result;
  }
  function matches(n, selector) {
    if (selector === "form") return n.tag === "form";
    if (selector === "input, select, textarea") return ["input", "select", "textarea"].includes(n.tag);
    if (selector.startsWith("#")) return n.id === selector.slice(1);
    if (selector === '[type="submit"]') return n.type === "submit";
    if (selector === ".n-panel") return n.className.includes("n-panel");
    const data = selector.match(/^\[data-([\w-]+)(?:="([^"]+)")?\]$/);
    if (data) {
      const key = data[1].replace(/-([a-z])/g, (_, c) => c.toUpperCase());
      return key in n.dataset && (data[2] === undefined || n.dataset[key] === data[2]);
    }
    return false;
  }
  for (const match of template.matchAll(/<([\w-]+)\b([^>]*\bid="[^"]+"[^>]*)>/g)) { const el = node(attrs(match[2])); el.tag = match[1]; }
  for (const tab of ["diary", "foods", "history", ...(admin ? ["manage"] : [])]) node({ "data-tab": tab });
  nodes.get("nutrition-app").dataset = { base: prefix + "/", admin: String(admin), manage: String(manage) };
  nodes.get("food-category").value = "";
  const document = { hidden: false, getElementById: id => nodes.get(id) || null,
    querySelector: selector => ({ content: selector.includes("_csrf_header") ? "X-CSRF-TOKEN" : csrf ? "fixture-token" : "" }),
    querySelectorAll: selector => allNodes.filter(n => n.connected && matches(n, selector)),
    addEventListener: (event, fn) => { listeners[event] = fn; }
  };
  class TestFormData { constructor(form) { this.values = Object.entries(form.elements).map(([key, field]) => [key, field.value]); } [Symbol.iterator]() { return this.values[Symbol.iterator](); } }
  const sandbox = { document, Date: TestDate, Intl, URL, URLSearchParams, AbortController, TypeError, FormData: TestFormData,
    location: { hash }, history: { replaceState() {} }, window: { addEventListener: (event, fn) => { listeners[`window:${event}`] = fn; } },
    setTimeout: (fn, ms) => { const timer = setTimeout(fn, ms); timer.unref(); return timer; }, clearTimeout,
    setInterval: fn => intervals.push(fn),
    fetch: async (url, options) => {
      assert(url.startsWith(prefix + "/")); const p = url.slice(prefix.length);
      calls.push({ path: p, ...options, body: options.body ? JSON.parse(options.body) : undefined });
      const gate = gates.find(g => !g.used && g.match(p, options));
      if (gate) { gate.used = true; await gate.promise; if (gate.failure) throw new TypeError("network"); }
      if (options.method !== "GET") assert.equal(options.headers["X-CSRF-TOKEN"], "fixture-token");
      const result = backend.route(options.method, p, options.body ? JSON.parse(options.body) : undefined);
      if (malformedWrite && options.method === "POST") result.data = {};
      return { redirected, status: result.status, ok: result.status >= 200 && result.status < 300, text: async () => result.data === null ? "" : JSON.stringify(result.data) };
    }
  };
  vm.runInNewContext(source, sandbox, { filename: "nutrition.js" });
  const controls = () => nodes.get("nutrition-dialog-content").querySelector("form");
  return { backend, calls, nodes, time,
    hold(match, failure = false) { let release; const promise = new Promise(resolve => { release = resolve; }); gates.push({ match, failure, promise, used: false }); return release; },
    async action(action, id = "") { const target = node({ "data-action": action, "data-id": String(id) }); listeners.click({ target }); await settle(); },
    async tab(tab) { listeners.click({ target: allNodes.find(n => n.dataset.tab === tab) || node({ "data-tab": tab }) }); await settle(); },
    async submit(fields = {}) { const form = controls(); assert(form, "Expected modal form"); for (const [key, value] of Object.entries(fields)) form.elements[key].value = String(value); nodes.get("nutrition-dialog-content").events.submit({ preventDefault() {}, target: form }); await settle(); },
    form: controls,
    async date(value) { nodes.get("diary-date").value = value; nodes.get("diary-date").events.change({ target: nodes.get("diary-date") }); await settle(); },
    async tick(ms) { time.now += ms; intervals.forEach(fn => fn()); await settle(); }
  };
}
let passed = 0;
async function test(name, work) { await work(); passed++; console.log(`PASS ${name}`); }
(async () => {
  await test("initial diary reads only, renders four meals, context path", async () => {
    const ui = environment(); await settle();
    assert(ui.calls.every(c => c.method === "GET")); assert.equal(ui.backend.logs.length, 0);
    assert.equal((ui.nodes.get("diary-meals").innerHTML.match(/<section/g) || []).length, 4);
    assert.equal(ui.nodes.get("total-calories").textContent, "0");
  });
  await test("add, edit snapshot, switch food, delete with current version", async () => {
    const ui = environment(); await settle(); await ui.action("add-food", 1);
    await ui.submit({ mealType: "LUNCH", weightGrams: 150, note: "test" });
    assert.equal(ui.backend.details.length, 1); assert.equal(ui.backend.daily(ui.backend.today()).totals.calories, 185.18);
    assert.equal(ui.calls.filter(c => c.method === "POST").length, 1);
    ui.backend.foods[0].caloriesPer100g = 999; ui.backend.foods[0].foodName = "New master name";
    await ui.action("edit-entry", 1);
    assert(ui.nodes.get("nutrition-dialog-content").innerHTML.includes("Cơm trắng"));
    await ui.submit({ weightGrams: 200 }); assert.equal(ui.backend.details[0].calculatedCalories, 246.9);
    assert.equal(ui.calls.findLast(c => c.method === "PUT").body.expectedVersion, 0);
    await ui.action("edit-entry", 1); await ui.action("change-food"); await ui.action("pick-food", 2);
    await ui.submit({ weightGrams: 75 }); assert.equal(ui.backend.details[0].foodId, 2);
    assert.equal(ui.backend.details[0].version, 2);
    await ui.action("delete-entry", 1); await ui.submit();
    assert.equal(ui.backend.details.length, 0); assert.equal(ui.backend.logs.length, 1);
    assert(ui.calls.some(c => c.path.endsWith("expectedVersion=2")));
    await ui.tab("history"); assert(ui.nodes.get("nutrition-history-rows").innerHTML.includes("07/11/2026"));
  });
  await test("stale version reloads diary and blocks resubmission", async () => {
    const ui = environment(); await settle(); await ui.action("add-food", 1); await ui.submit();
    await ui.action("edit-entry", 1); ui.backend.details[0].version++;
    await ui.submit({ weightGrams: 200 });
    assert.equal(ui.backend.details[0].weightGrams, 100); assert.equal(ui.form().dataset.stale, "true");
    const count = ui.calls.length; await ui.submit({ weightGrams: 300 }); assert.equal(ui.calls.length, count);
    assert.match(ui.nodes.get("nutrition-dialog-error").textContent, /thay đổi/);
  });
  await test("read-only past day; midnight disables changes", async () => {
    const ui = environment(); await settle(); await ui.date("2026-11-06");
    assert(!ui.nodes.get("diary-meals").innerHTML.includes('data-action="add-meal"'));
    await ui.action("today"); await ui.tick(18 * 3600000);
    assert(!ui.nodes.get("diary-meals").innerHTML.includes('data-action="add-meal"'));
  });
  await test("admin category/food forms and PATCH status endpoints", async () => {
    const ui = environment({ admin: true, manage: true }); await settle();
    await ui.action("new-category"); await ui.submit({ categoryName: "New group", description: "Fixture" });
    assert(ui.backend.categories.some(c => c.categoryName === "New group"));
    await ui.action("new-food");
    await ui.submit({ categoryId: 3, foodName: "New food", description: "", source: "", imageUrl: "", caloriesPer100g: 200, proteinPer100g: 20, carbsPer100g: 30, fatPer100g: 5 });
    assert.equal(ui.backend.foods.at(-1).foodName, "New food");
    await ui.action("toggle-category", 3); await ui.submit(); assert.equal(ui.backend.categories[2].active, false);
    await ui.action("toggle-food", 1); await ui.submit(); assert.equal(ui.backend.foods[0].active, false);
    assert(ui.calls.some(c => c.method === "PATCH" && c.path === "/admin/foods/1/deactivate"));
  });
  await test("user cannot trigger admin operations", async () => {
    const ui = environment(); await settle(); await ui.action("new-food"); await ui.tab("manage");
    assert(!ui.calls.some(c => c.path.startsWith("/admin/")));
  });
  await test("missing CSRF prevents writes", async () => {
    const ui = environment({ csrf: false }); await settle(); await ui.action("add-food", 1); await ui.submit();
    assert(ui.calls.every(c => c.method === "GET")); assert.match(ui.nodes.get("nutrition-dialog-error").textContent, /bảo vệ phiên/);
  });
  await test("duplicate clicks/submits send one POST while network is slow", async () => {
    const ui = environment(); await settle(); await ui.action("add-food", 1);
    const release = ui.hold((p, o) => o.method === "POST");
    await ui.submit(); await ui.submit(); assert.equal(ui.calls.filter(c => c.method === "POST").length, 1);
    release(); await settle(); assert.equal(ui.backend.details.length, 1);
  });
  await test("uncertain write is never retried automatically", async () => {
    const ui = environment(); await settle(); await ui.action("add-food", 1);
    const release = ui.hold((p, o) => o.method === "POST", true);
    await ui.submit(); release(); await settle();
    await ui.submit(); assert.equal(ui.calls.filter(c => c.method === "POST").length, 1);
    assert.equal(ui.form().querySelector('[type="submit"]').disabled, true);
    await ui.action("close-dialog"); await ui.action("refresh");
    await ui.action("add-food", 1); await ui.submit(); assert.equal(ui.backend.details.length, 1);
  });
  await test("late diary response cannot overwrite newer selected date", async () => {
    const ui = environment(); await settle();
    const release = ui.hold(p => p.includes("date=2026-11-06"));
    await ui.date("2026-11-06"); await ui.date("2026-11-05"); release(); await settle();
    assert.match(ui.nodes.get("diary-caption").textContent, /05\/11\/2026/);
  });
  await test("malformed success response cannot cause duplicate POST", async () => {
    const ui = environment({ malformedWrite: true }); await settle(); await ui.action("add-food", 1); await ui.submit();
    assert.equal(ui.backend.details.length, 1);
    await ui.submit(); assert.equal(ui.calls.filter(c => c.method === "POST").length, 1);
    assert.equal(ui.form().querySelector('[type="submit"]').disabled, true);
    assert.match(ui.nodes.get("nutrition-dialog-error").textContent, /làm mới/);
  });
  await test("untrusted food names/notes are escaped", async () => {
    const ui = environment(); await settle(); ui.backend.foods[0].foodName = '<img src=x onerror="alert(1)">';
    await ui.action("add-food", 1); assert(!ui.nodes.get("nutrition-dialog-content").innerHTML.includes('<img src=x'));
    await ui.submit({ note: "<script>bad()</script>" });
    assert(ui.nodes.get("diary-meals").innerHTML.includes("&lt;script&gt;"));
  });
  await test("GitHub blob images render as raw in catalog/detail/snapshot without changing saved URLs", async () => {
    const blob = "https://github.com/namrooney10/Fitness_AI_Assets/blob/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Ca-chua.jpg";
    const raw = "https://raw.githubusercontent.com/namrooney10/Fitness_AI_Assets/a0a90534873c6b0c6c2ba42d4996c3ce10b73a6c/foods/Ca-chua.jpg";
    const ui = environment(); await settle(); ui.backend.foods[0].imageUrl = blob;
    await ui.tab("foods");
    assert(ui.nodes.get("food-grid").innerHTML.includes(`src="${raw}"`));
    await ui.action("food-info", 1);
    assert(ui.nodes.get("nutrition-dialog-content").innerHTML.includes(`src="${raw}"`));
    await ui.action("add-food", 1); await ui.submit();
    assert(ui.nodes.get("diary-meals").innerHTML.includes(`src="${raw}"`));
    assert.equal(ui.backend.foods[0].imageUrl, blob);
    assert.equal(ui.backend.details[0].imageUrlSnapshot, blob);
  });
  await test("admin normalizes GitHub image URLs on save and rejects non-image/credential URLs", async () => {
    const ui = environment({ admin: true, manage: true }); await settle();
    await ui.action("edit-food", 1);
    await ui.submit({ imageUrl: "javascript:alert(1)" });
    assert(!ui.calls.some(c => c.method === "PUT"));
    await ui.submit({ imageUrl: "https://user:secret@example.com/food.jpg" });
    assert(!ui.calls.some(c => c.method === "PUT"));
    await ui.submit({ imageUrl: "https://github.com/owner/assets/blob/main/foods/food.svg" });
    assert(!ui.calls.some(c => c.method === "PUT"));
    await ui.submit({ imageUrl: "https://github.com/owner/assets/raw/main/foods/food.jpg?raw=true" });
    assert.equal(ui.backend.foods[0].imageUrl, "https://raw.githubusercontent.com/owner/assets/main/foods/food.jpg?raw=true");
  });
  await test("expired login response disables mutations", async () => {
    const ui = environment({ redirected: true }); await settle();
    await ui.action("add-food", 1); assert(ui.calls.every(c => c.method === "GET"));
    assert.match(ui.nodes.get("nutrition-notice").textContent, /đăng nhập/);
  });
  console.log(`Nutrition UI: ${passed} scenarios passed (mock DOM/HTTP, no real database).`);
})().catch(error => { console.error(error); process.exitCode = 1; });
