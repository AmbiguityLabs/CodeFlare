import http from "node:http";
import fs from "node:fs";
import path from "node:path";

const cwd = process.cwd();
const port = Number(process.env.DEFAULT_APP_PORT || process.env.PORT || 3000);

function findApkPath() {
  const candidates = [
    path.join(cwd, ".build-outputs", "app-debug.apk"),
    path.join(cwd, "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

function renderHtml(apkPath) {
  const hasApk = Boolean(apkPath);
  const sizeMb = hasApk ? (fs.statSync(apkPath).size / (1024 * 1024)).toFixed(1) : null;
  const statusLabel = hasApk ? `Native Android APK Ready (${sizeMb} MB)` : "Native Kotlin Android Project";
  const ctaHtml = hasApk
    ? `<a class="btn" href="/app-debug.apk" download="EdgeCode-debug.apk">Download Installable APK (${sizeMb} MB)</a>`
    : `<div class="note">Open this project in Android Studio or build via Gradle (<code>gradle assembleDebug</code>) to generate the installable APK.</div>`;

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>EdgeCode — Cloudflare Workers Mobile IDE</title>
  <meta name="description" content="Cloudflare Workers mobile IDE featuring native Cloudflare authorization, multi-file code editor, bindings manager, and live HTTP worker tester." />
  <style>
    :root {
      color-scheme: dark;
      --bg: #1B1C17;
      --surface: #21231D;
      --pill: #262821;
      --border: #343846;
      --accent-bg: #4F6223;
      --accent-fg: #D6F58D;
      --text: #E7E9E2;
      --muted: #A7ACA0;
      --emerald: #34D399;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .card {
      width: 100%;
      max-width: 480px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 24px;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: rgba(52, 211, 153, 0.14);
      color: var(--emerald);
      font-size: 12px;
      font-weight: 600;
      padding: 5px 10px;
      border-radius: 8px;
      margin-bottom: 14px;
    }
    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--emerald);
    }
    h1 {
      margin: 0 0 6px;
      font-size: 24px;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .sub {
      margin: 0 0 18px;
      color: var(--muted);
      font-size: 14px;
      line-height: 1.5;
    }
    .meta {
      background: var(--pill);
      border: 1px solid rgba(52, 56, 70, 0.65);
      border-radius: 12px;
      padding: 12px 14px;
      margin-bottom: 18px;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 12px;
      color: var(--muted);
      display: grid;
      gap: 6px;
    }
    .meta strong { color: var(--text); font-weight: 600; }
    .btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      background: var(--accent-bg);
      color: var(--accent-fg);
      text-decoration: none;
      font-weight: 600;
      font-size: 15px;
      padding: 14px 18px;
      border-radius: 12px;
    }
    .note {
      background: var(--pill);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 12px 14px;
      font-size: 13px;
      color: var(--muted);
      line-height: 1.45;
    }
    .steps {
      margin-top: 18px;
      padding-top: 14px;
      border-top: 1px solid rgba(52, 56, 70, 0.6);
      font-size: 13px;
      color: var(--muted);
      line-height: 1.5;
    }
    .steps ul { margin: 8px 0 0; padding-left: 18px; }
    .steps li { margin-bottom: 4px; }
  </style>
</head>
<body>
  <main class="card">
    <div class="badge"><span class="dot"></span> ${statusLabel}</div>
    <h1>EdgeCode</h1>
    <p class="sub">Cloudflare Workers mobile IDE built natively with Kotlin &amp; Jetpack Compose.</p>
    <div class="meta">
      <div>Package: <strong>com.aistudio.edgecode.wqznxm</strong></div>
      <div>Architecture: <strong>Home &rarr; Project (Code / Test / Config / Console)</strong></div>
      <div>Integrations: <strong>Cloudflare Workers, Pages, Infra &amp; Optional GitHub</strong></div>
    </div>
    ${ctaHtml}
    <div class="steps">
      <strong>Workspace Structure:</strong>
      <ul>
        <li>Home Hub for Projects, Pages, Infra, and optional GitHub</li>
        <li>Single-project workspace with Code, Test, Config, and Console modes</li>
        <li>Project-scoped Files, Search, and Snippets drawer</li>
      </ul>
    </div>
  </main>
</body>
</html>`;
}

const server = http.createServer((req, res) => {
  const urlPath = (req.url || "/").split("?")[0];

  if (urlPath === "/health") {
    const payload = JSON.stringify({ status: "ok", app: "EdgeCode" });
    res.writeHead(200, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(payload),
      "Cache-Control": "no-store",
    });
    if (req.method !== "HEAD") res.end(payload);
    else res.end();
    return;
  }

  if (urlPath === "/app-debug.apk") {
    const apkPath = findApkPath();
    if (!apkPath) {
      const errBody = JSON.stringify({ error: "APK artifact not present in this container" });
      res.writeHead(404, {
        "Content-Type": "application/json; charset=utf-8",
        "Content-Length": Buffer.byteLength(errBody),
      });
      if (req.method !== "HEAD") res.end(errBody);
      else res.end();
      return;
    }
    const stat = fs.statSync(apkPath);
    res.writeHead(200, {
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Disposition": 'attachment; filename="EdgeCode-debug.apk"',
      "Content-Length": stat.size,
      "Cache-Control": "no-store",
    });
    if (req.method === "HEAD") {
      res.end();
      return;
    }
    const stream = fs.createReadStream(apkPath);
    stream.on("error", () => {
      if (!res.headersSent) res.writeHead(500);
      res.end();
    });
    stream.pipe(res);
    return;
  }

  const apkPath = findApkPath();
  const html = renderHtml(apkPath);
  res.writeHead(200, {
    "Content-Type": "text/html; charset=utf-8",
    "Content-Length": Buffer.byteLength(html),
    "Cache-Control": "no-store",
  });
  if (req.method !== "HEAD") res.end(html);
  else res.end();
});

server.listen(port, "0.0.0.0", () => {
  console.log(`EdgeCode server listening on http://0.0.0.0:${port}`);
});
