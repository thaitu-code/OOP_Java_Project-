// Local preview only: disposable RAM data, no calls to the running FitnessAI server or database.
const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const { fixture } = require("./nutrition-ui-fixture.cjs");
const root = path.resolve(__dirname, "..");
const previewCandidates = process.env.NUTRITION_UI_PREVIEW_DIR ? [path.resolve(process.env.NUTRITION_UI_PREVIEW_DIR)] : [
  path.join(root, "target/nutrition-preview"),
  path.join(root, "target/nutrition-ui-verification/target/nutrition-preview")
];
const preview = previewCandidates.filter(dir => fs.existsSync(path.join(dir, "user.html")))
  .sort((a, b) => fs.statSync(path.join(b, "user.html")).mtimeMs - fs.statSync(path.join(a, "user.html")).mtimeMs)[0];
if (!preview) throw new Error("Render templates first: ./mvnw.cmd -Dtest=NutritionPageControllerTest test");
const mock = fixture();
const port = Number(process.env.NUTRITION_UI_PREVIEW_PORT || 8772);
const mediaTypes = { ".css": "text/css", ".js": "text/javascript", ".html": "text/html" };
const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, "http://localhost");
    let file;
    if (["/", "/nutrition", "/admin/nutrition-library"].includes(url.pathname)) file = path.join(preview, url.pathname.startsWith("/admin/") ? "admin.html" : "user.html");
    if (/^\/(css|js)\/[a-z-]+\.(css|js)$/.test(url.pathname)) file = path.join(root, "src/main/resources/static", url.pathname);
    if (file) {
      let content = fs.readFileSync(file, "utf8");
      if (file.endsWith(".html")) content = content.replace('<section class="n-hero">', '<p class="n-notice warning">Bản xem trước · Dữ liệu mô phỏng trong bộ nhớ, không kết nối database thật.</p><section class="n-hero">');
      res.writeHead(200, { "Content-Type": `${mediaTypes[path.extname(file)]}; charset=utf-8`, "Cache-Control": "no-store" });
      res.end(content); return;
    }
    const admin = (req.headers.referer || "").includes("/admin/nutrition-library");
    if (url.pathname.startsWith("/admin/") && !admin) { res.writeHead(403); res.end(JSON.stringify({ message: "Forbidden" })); return; }
    let text = "";
    for await (const chunk of req) text += chunk;
    const result = mock.route(req.method, req.url, text ? JSON.parse(text) : undefined);
    res.writeHead(result.status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
    res.end(result.data === null ? "" : JSON.stringify(result.data));
  } catch (error) { res.writeHead(500); res.end(JSON.stringify({ message: error.message })); }
});
server.listen(port, "127.0.0.1", () => console.log(`Nutrition UI fixture only: http://127.0.0.1:${port}/nutrition`));
