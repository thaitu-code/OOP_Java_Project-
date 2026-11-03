// Local UI preview with disposable in-memory fixtures. NEVER proxies requests to the real app.
// First render templates: mvnw.cmd -Dtest=WorkoutPageControllerTest test
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { fixture } = require("./workout-ui-fixture.cjs");
const root = path.resolve(__dirname, "..");
const mock = fixture(() => Date.now(), { automation: process.env.WORKOUT_UI_PREVIEW_AUTOMATION === "1" });
if (process.env.WORKOUT_UI_PREVIEW_OPEN_WORKOUT === "1") {
  mock.route("POST", "/workouts");
  mock.route("POST", "/workouts/1/details", { exerciseId: 1, ...(mock.exercises[0].levels?.length ? { levelId: 1 } : {}) });
  mock.route("PATCH", "/workouts/1/details/1/start");
  mock.details[0].status = "FROZEN"; mock.details[0]._token = null;
  mock.details[0]._last = Date.now() - 40000;
}
const port = Number(process.env.WORKOUT_UI_PREVIEW_PORT || 8771);
const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, "http://localhost");
    const p = url.pathname;
    let file;
    if (["/", "/workout", "/admin/workout-library"].includes(p)) file = path.join(root, "target/workout-preview", p.startsWith("/admin") ? "admin.html" : "user.html");
    else if (/^\/(css|js)\/[\w.-]+$/.test(p)) file = path.join(root, "src/main/resources/static", p);
    if (file) {
      let data = fs.readFileSync(file);
      if (file.endsWith(".html")) data = data.toString().replace('<main id="workout-app"', '<div style="background:#fff3d8;padding:8px;text-align:center;font:12px Arial">BẢN KIỂM THỬ GIAO DIỆN · Dữ liệu giả trong bộ nhớ · Không kết nối database</div><main id="workout-app"');
      res.setHeader("Content-Type", file.endsWith(".html") ? "text/html; charset=utf-8" : file.endsWith(".css") ? "text/css; charset=utf-8" : "text/javascript; charset=utf-8");
      res.setHeader("Cache-Control", "no-store"); res.end(data); return;
    }
    let text = ""; for await (const chunk of req) text += chunk;
    const delay = Math.max(0, Number(process.env.WORKOUT_UI_PREVIEW_DELAY_MS || 0));
    if (delay) await new Promise(resolve => setTimeout(resolve, delay));
    const result = mock.route(req.method, req.url, text ? JSON.parse(text) : undefined);
    res.statusCode = result.status; res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.end(result.data == null ? "" : JSON.stringify(result.data));
  } catch (error) { res.statusCode = 500; res.end(JSON.stringify({ message: error.message })); }
});
server.listen(port, "127.0.0.1", () => console.log(`UI fixture only: http://127.0.0.1:${port}/workout`));
