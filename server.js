import http from "node:http";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const cwd = process.cwd();
const pidFilePath = path.join(os.tmpdir(), "edgecode-dev-server.pid");

function resolvePort() {
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i += 1) {
    if (args[i] === "--port" || args[i] === "-p") {
      const parsed = Number(args[i + 1]);
      if (Number.isInteger(parsed) && parsed > 0 && parsed < 65536) {
        return parsed;
      }
    } else if (args[i].startsWith("--port=")) {
      const parsed = Number(args[i].slice("--port=".length));
      if (Number.isInteger(parsed) && parsed > 0 && parsed < 65536) {
        return parsed;
      }
    }
  }
  const nginxPort = Number(process.env.NGINX_PORT || 8080);
  const cpPort = Number(process.env.CONTROL_PLANE_PORT || 8000);
  const envPort = Number(process.env.PORT);
  if (Number.isInteger(envPort) && envPort > 0 && envPort < 65536 && envPort !== nginxPort && envPort !== cpPort) {
    return envPort;
  }
  const defaultAppPort = Number(process.env.DEFAULT_APP_PORT);
  if (Number.isInteger(defaultAppPort) && defaultAppPort > 0 && defaultAppPort < 65536) {
    return defaultAppPort;
  }
  return 3000;
}

const port = resolvePort();

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
  const statusLabel = hasApk ? `APK Ready (${sizeMb} MB)` : "Android Kotlin Project";
  const ctaHtml = hasApk
    ? `<a class="btn" href="/app-debug.apk" download="EdgeCode-debug.apk">Download APK (${sizeMb} MB)</a>`
    : `<div class="note">Build via Gradle (<code>gradle assembleDebug</code>) to generate the APK.</div>`;

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>EdgeCode</title>
  <meta name="description" content="Cloudflare Workers mobile IDE featuring native Cloudflare authorization, multi-file VS Code-style code editor, bindings manager, and live HTTP worker tester." />
  <meta property="og:title" content="EdgeCode" />
  <meta property="og:description" content="Cloudflare Workers mobile IDE featuring native Cloudflare authorization, multi-file VS Code-style code editor, bindings manager, and live HTTP worker tester." />
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
      max-width: 440px;
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
      margin: 0 0 14px;
      font-size: 24px;
      font-weight: 700;
      letter-spacing: -0.02em;
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
  </style>
</head>
<body>
  <main class="card">
    <div class="badge"><span class="dot"></span> ${statusLabel}</div>
    <h1>EdgeCode</h1>
    <div class="meta">
      <div>Package: <strong>com.aistudio.edgecode.wqznxm</strong></div>
      <div>Runtime: <strong>Android Kotlin &amp; Jetpack Compose</strong></div>
    </div>
    ${ctaHtml}
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

  if (urlPath === "/favicon.ico") {
    res.writeHead(204, { "Cache-Control": "no-store" });
    res.end();
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

function writePidFile() {
  try {
    fs.writeFileSync(pidFilePath, String(process.pid), "utf8");
  } catch {
  }
}

function removePidFile() {
  try {
    if (fs.existsSync(pidFilePath) && fs.readFileSync(pidFilePath, "utf8").trim() === String(process.pid)) {
      fs.unlinkSync(pidFilePath);
    }
  } catch {
  }
}

function terminatePreviousServer() {
  try {
    if (!fs.existsSync(pidFilePath)) return false;
    const prevPid = Number(fs.readFileSync(pidFilePath, "utf8").trim());
    if (!Number.isInteger(prevPid) || prevPid <= 1 || prevPid === process.pid) return false;
    process.kill(prevPid, "SIGTERM");
    return true;
  } catch {
    return false;
  }
}

let listenRetries = 0;
let terminatedPrevious = false;
const maxListenRetries = 20;

server.on("error", (err) => {
  if (err && err.code === "EADDRINUSE" && listenRetries < maxListenRetries) {
    if (!terminatedPrevious) {
      terminatedPrevious = true;
      terminatePreviousServer();
    }
    listenRetries += 1;
    setTimeout(() => {
      server.listen(port, "0.0.0.0");
    }, 150);
    return;
  }
  console.error("Server error:", err);
  process.exit(1);
});

for (const sig of ["SIGINT", "SIGTERM"]) {
  process.on(sig, () => {
    removePidFile();
    server.close(() => process.exit(0));
    if (typeof server.closeAllConnections === "function") {
      server.closeAllConnections();
    }
  });
}

process.on("exit", removePidFile);

server.listen(port, "0.0.0.0", () => {
  writePidFile();
  console.log(`EdgeCode server listening on http://0.0.0.0:${port}`);
});
