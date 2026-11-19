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
  const nginxPort = process.env.NGINX_PORT ? Number(process.env.NGINX_PORT) : null;
  const cpPort = process.env.CONTROL_PLANE_PORT ? Number(process.env.CONTROL_PLANE_PORT) : null;
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
    path.join(cwd, "EdgeCode.apk"),
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
  const apkChip = hasApk
    ? `<a class="btn primary" href="/app-debug.apk" download="EdgeCode.apk">Download APK (${sizeMb} MB)</a>`
    : `<a class="btn ghost" href="https://github.com/AmbiguityLabs/CodeFlare/releases" target="_blank" rel="noreferrer">GitHub Releases</a>`;

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
      --bg: #000000;
      --card: #141416;
      --card-elev: #1C1C1F;
      --divider: #242428;
      --text: #F2F2F7;
      --muted: #8E8E93;
      --accent: #FFFFFF;
      --accent-fg: #000000;
      --rose: #F87171;
      --emerald: #34D399;
      --amber: #FBBF24;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", Roboto, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .shell {
      width: 100%;
      max-width: 460px;
      display: grid;
      gap: 14px;
    }
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 4px 6px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 20px;
      font-weight: 600;
      letter-spacing: -0.02em;
    }
    .status {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 999px;
      background: var(--card);
      font-size: 12px;
      font-weight: 500;
      color: var(--muted);
    }
    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--amber);
    }
    .dot.stopped { background: var(--rose); }
    .card {
      background: var(--card);
      border-radius: 24px;
      overflow: hidden;
    }
    .row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 18px;
      font-size: 15px;
      border-bottom: 0.5px solid var(--divider);
      cursor: pointer;
      user-select: none;
      transition: background 140ms ease;
    }
    .row:last-child { border-bottom: 0; }
    .row:hover { background: var(--card-elev); }
    .row-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .row-val {
      color: var(--muted);
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 12px;
    }
    .row-detail {
      display: none;
      padding: 0 18px 14px 48px;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 12px;
      color: var(--muted);
      border-bottom: 0.5px solid var(--divider);
    }
    .row-detail.open { display: block; }
    .tabs {
      display: flex;
      gap: 6px;
      padding: 12px 14px 0;
    }
    .tab {
      flex: 1;
      border: 0;
      border-radius: 999px;
      background: var(--card-elev);
      color: var(--muted);
      padding: 8px 10px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
    }
    .tab.active {
      background: var(--accent);
      color: var(--accent-fg);
    }
    .preview-box {
      padding: 16px;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 12px;
      line-height: 1.55;
      color: var(--text);
      min-height: 112px;
    }
    .actions {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
    }
    .btn {
      appearance: none;
      border: 0;
      border-radius: 999px;
      height: 44px;
      padding: 0 16px;
      font-size: 13.5px;
      font-weight: 600;
      text-decoration: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .btn.primary {
      background: var(--accent);
      color: var(--accent-fg);
    }
    .btn.ghost {
      background: var(--card);
      color: var(--text);
    }
    .btn.danger {
      background: rgba(248, 113, 113, 0.14);
      color: var(--rose);
    }
    .btn:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .svg {
      width: 18px;
      height: 18px;
      stroke: currentColor;
      fill: none;
      stroke-width: 1.75;
      stroke-linecap: round;
      stroke-linejoin: round;
      color: var(--muted);
      flex-shrink: 0;
    }
  </style>
</head>
<body>
  <main class="shell">
    <div class="header">
      <div class="brand">
        <svg class="svg" style="color:#fff" viewBox="0 0 24 24"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
        <span>EdgeCode</span>
      </div>
      <div class="status" id="envStatus">
        <span class="dot" id="envDot"></span>
        <span id="envLabel">Demo Only</span>
      </div>
    </div>

    <section class="card">
      <div class="row" data-detail="d-pkg">
        <div class="row-left">
          <svg class="svg" viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="4"/></svg>
          <span>Runtime</span>
        </div>
        <span class="row-val">Android arm64</span>
      </div>
      <div class="row-detail" id="d-pkg">com.aistudio.edgecode.wqznxm · Native APK</div>

      <div class="row" data-detail="d-deno">
        <div class="row-left">
          <svg class="svg" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          <span>Local Script Engine</span>
        </div>
        <span class="row-val">libdeno.so</span>
      </div>
      <div class="row-detail" id="d-deno">jniLibs/arm64-v8a/libdeno.so + universal_runner.ts</div>

      <div class="row" data-detail="d-ais">
        <div class="row-left">
          <svg class="svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
          <span>AI Studio Host</span>
        </div>
        <span class="row-val">Idle</span>
      </div>
      <div class="row-detail" id="d-ais">App execution disabled inside AI Studio preview</div>
    </section>

    <section class="card">
      <div class="tabs">
        <button class="tab active" data-tab="worker">Worker</button>
        <button class="tab" data-tab="sw">ServiceWorker</button>
        <button class="tab" data-tab="script">TS/JS</button>
      </div>
      <pre class="preview-box" id="demoPreview">export default {
  async fetch(request, env, ctx) {
    return Response.json({ edge: "deno-local", ok: true });
  }
};</pre>
    </section>

    <div class="actions">
      ${apkChip}
      <button class="btn danger" id="stopEnvBtn" type="button">Stop Environment</button>
    </div>
  </main>

  <script>
    const snippets = {
      worker: 'export default {\\n  async fetch(request, env, ctx) {\\n    return Response.json({ edge: "deno-local", ok: true });\\n  }\\n};',
      sw: 'addEventListener("fetch", (event) => {\\n  event.respondWith(new Response("ServiceWorker OK"));\\n});',
      script: 'import { Readable } from "node:stream";\\nconsole.log("Deno + Node compat ready:", typeof Readable);'
    };

    document.querySelectorAll(".row[data-detail]").forEach((el) => {
      el.addEventListener("click", () => {
        const panel = document.getElementById(el.dataset.detail);
        if (panel) panel.classList.toggle("open");
      });
    });

    document.querySelectorAll(".tab").forEach((btn) => {
      btn.addEventListener("click", () => {
        document.querySelectorAll(".tab").forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");
        document.getElementById("demoPreview").textContent = snippets[btn.dataset.tab] || "";
      });
    });

    const stopBtn = document.getElementById("stopEnvBtn");
    let isStopped = false;
    stopBtn.addEventListener("click", async () => {
      isStopped = !isStopped;
      stopBtn.disabled = true;
      try {
        await fetch(isStopped ? "/api/stop" : "/api/resume", { method: "POST" });
      } catch {}
      stopBtn.disabled = false;
      const dot = document.getElementById("envDot");
      const label = document.getElementById("envLabel");
      if (isStopped) {
        dot.classList.add("stopped");
        label.textContent = "Environment Stopped";
        stopBtn.textContent = "Resume Demo";
        stopBtn.className = "btn ghost";
      } else {
        dot.classList.remove("stopped");
        label.textContent = "Demo Only";
        stopBtn.textContent = "Stop Environment";
        stopBtn.className = "btn danger";
      }
    });
  </script>
</body>
</html>`;
}

let isEnvironmentStopped = false;

const server = http.createServer((req, res) => {
  const urlPath = (req.url || "/").split("?")[0];

  if (urlPath === "/health") {
    const payload = JSON.stringify({
      status: "ok",
      mode: isEnvironmentStopped ? "stopped-idle" : "static-demo",
      app: "EdgeCode",
    });
    res.writeHead(200, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(payload),
      "Cache-Control": "no-store",
    });
    if (req.method !== "HEAD") res.end(payload);
    else res.end();
    return;
  }

  if (urlPath === "/api/stop" && req.method === "POST") {
    isEnvironmentStopped = true;
    const payload = JSON.stringify({ stopped: true, portAlive: true });
    res.writeHead(200, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(payload),
      "Cache-Control": "no-store",
    });
    res.end(payload);
    return;
  }

  if (urlPath === "/api/resume" && req.method === "POST") {
    isEnvironmentStopped = false;
    const payload = JSON.stringify({ stopped: false, portAlive: true });
    res.writeHead(200, {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Length": Buffer.byteLength(payload),
      "Cache-Control": "no-store",
    });
    res.end(payload);
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
      "Content-Disposition": 'attachment; filename="EdgeCode.apk"',
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
const maxListenRetries = 60;

server.on("error", (err) => {
  if (err && err.code === "EADDRINUSE" && listenRetries < maxListenRetries) {
    if (!terminatedPrevious) {
      terminatedPrevious = true;
      terminatePreviousServer();
    }
    listenRetries += 1;
    setTimeout(() => {
      server.listen(port, "0.0.0.0");
    }, 250);
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
  console.log(`EdgeCode static demo server listening on http://0.0.0.0:${port}`);
});
