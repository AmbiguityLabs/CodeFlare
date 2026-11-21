import express from 'express';
import type { Request as ExpressRequest, Response as ExpressResponse } from 'express';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import * as esbuild from 'esbuild';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PORT = Number(process.env.PORT || 3000);

const app = express();

// Health check
app.get('/health', (_req: ExpressRequest, res: ExpressResponse) => {
  res.json({ status: 'ok', app: 'CodeFlare', version: '1.1.0' });
});

// Cloudflare OAuth token exchange / refresh proxy
app.post('/api/cf/oauth/token', express.json({ limit: '1mb' }), async (req: ExpressRequest, res: ExpressResponse) => {
  try {
    const params = new URLSearchParams();
    const body = req.body || {};
    for (const [k, v] of Object.entries(body)) {
      if (v !== undefined && v !== null) {
        params.set(k, String(v));
      }
    }
    const upstream = await fetch('https://dash.cloudflare.com/oauth2/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: params.toString(),
    });
    const text = await upstream.text();
    res.status(upstream.status);
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/json');
    res.send(text);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Cloudflare OAuth proxy error';
    res.status(502).json({ error: 'oauth_proxy_failed', error_description: message });
  }
});

// GitHub Device Flow OAuth proxies (CORS bypass)
app.post('/api/github/oauth/device-code', express.json({ limit: '1mb' }), async (req: ExpressRequest, res: ExpressResponse) => {
  try {
    const { client_id, scope } = req.body || {};
    const params = new URLSearchParams({
      client_id: String(client_id || ''),
      scope: String(scope || 'repo workflow read:user'),
    });
    const upstream = await fetch('https://github.com/login/device/code', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: params.toString(),
    });
    const text = await upstream.text();
    res.status(upstream.status).setHeader('Content-Type', 'application/json').send(text);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'GitHub device code proxy failed';
    res.status(502).json({ error: 'github_proxy_failed', error_description: message });
  }
});

app.post('/api/github/oauth/token', express.json({ limit: '1mb' }), async (req: ExpressRequest, res: ExpressResponse) => {
  try {
    const { client_id, device_code, grant_type } = req.body || {};
    const params = new URLSearchParams({
      client_id: String(client_id || ''),
      device_code: String(device_code || ''),
      grant_type: String(grant_type || 'urn:ietf:params:oauth:grant-type:device_code'),
    });
    const upstream = await fetch('https://github.com/login/oauth/access_token', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: params.toString(),
    });
    const text = await upstream.text();
    res.status(upstream.status).setHeader('Content-Type', 'application/json').send(text);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'GitHub token proxy failed';
    res.status(502).json({ error: 'github_token_proxy_failed', error_description: message });
  }
});

// Cloudflare v4 API proxy (preserves multipart boundaries & binary responses)
app.all(
  '/api/cf/*',
  express.raw({ type: '*/*', limit: '50mb' }),
  async (req: ExpressRequest, res: ExpressResponse) => {
    try {
      const wildcard = (req.params as Record<string, string>)[0] || '';
      const queryIndex = req.originalUrl.indexOf('?');
      const queryString = queryIndex >= 0 ? req.originalUrl.slice(queryIndex) : '';
      const targetUrl = `https://api.cloudflare.com/client/v4/${wildcard}${queryString}`;

      const forwardHeaders: Record<string, string> = {};
      const allowedHeaders = [
        'authorization',
        'x-auth-email',
        'x-auth-key',
        'content-type',
        'accept',
      ];
      for (const h of allowedHeaders) {
        const val = req.headers[h];
        if (typeof val === 'string' && val.length > 0) {
          forwardHeaders[h] = val;
        }
      }

      const hasBody = !['GET', 'HEAD'].includes(req.method.toUpperCase());
      const bodyBuffer =
        hasBody && Buffer.isBuffer(req.body) && req.body.length > 0 ? new Uint8Array(req.body) : undefined;

      const upstream = await fetch(targetUrl, {
        method: req.method,
        headers: forwardHeaders,
        body: bodyBuffer,
      });

      const arrayBuf = await upstream.arrayBuffer();
      const outBuf = Buffer.from(arrayBuf);
      const contentType = upstream.headers.get('content-type');
      if (contentType) {
        res.setHeader('Content-Type', contentType);
      }
      res.status(upstream.status).send(outBuf);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Cloudflare API proxy error';
      res.status(502).json({
        success: false,
        errors: [{ code: 502, message }],
        messages: [],
        result: null,
      });
    }
  },
);

// Server-side HTTP Executor for API Tester Pro & Quick Worker Tester
app.post('/api/http-test', express.json({ limit: '15mb' }), async (req: ExpressRequest, res: ExpressResponse) => {
  const startedAt = Date.now();
  try {
    const { method = 'GET', url, headers = {}, body } = req.body || {};
    if (!url || typeof url !== 'string') {
      res.status(400).json({ error: 'Missing request URL' });
      return;
    }

    const parsedUrl = new URL(url);
    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      res.status(400).json({ error: 'Only http:// and https:// URLs are supported' });
      return;
    }

    const cleanHeaders: Record<string, string> = {};
    if (headers && typeof headers === 'object') {
      for (const [k, v] of Object.entries(headers)) {
        if (k.trim() && v !== undefined && v !== null) {
          cleanHeaders[k.trim()] = String(v);
        }
      }
    }

    const upperMethod = String(method).toUpperCase();
    const canHaveBody = !['GET', 'HEAD'].includes(upperMethod);
    const upstream = await fetch(parsedUrl.toString(), {
      method: upperMethod,
      headers: cleanHeaders,
      body: canHaveBody && body !== undefined && body !== null && body !== '' ? String(body) : undefined,
      redirect: 'follow',
    });

    const responseText = await upstream.text();
    const durationMs = Date.now() - startedAt;
    const responseHeaders: Record<string, string> = {};
    upstream.headers.forEach((value, key) => {
      responseHeaders[key] = value;
    });

    res.json({
      statusCode: upstream.status,
      statusText: upstream.statusText || '',
      headers: responseHeaders,
      body: responseText,
      durationMs,
      finalUrl: upstream.url || parsedUrl.toString(),
    });
  } catch (err: unknown) {
    const durationMs = Date.now() - startedAt;
    const message = err instanceof Error ? err.message : 'HTTP execution error';
    res.json({
      statusCode: 0,
      statusText: 'Network Error',
      headers: {},
      body: '',
      durationMs,
      error: message,
    });
  }
});

// Helper: normalize virtual workspace file path
function normalizePath(p: string): string {
  return p.replace(/\\/g, '/').replace(/^\/+/, '').replace(/\/+/g, '/');
}

function resolveRelativePath(fromFile: string, specifier: string): string {
  const fromDir = path.posix.dirname('/' + normalizePath(fromFile));
  const resolved = path.posix.resolve(fromDir, specifier);
  return normalizePath(resolved);
}

// Local Worker & Script Runner using esbuild + Node VM
app.post('/api/local-run', express.json({ limit: '15mb' }), async (req: ExpressRequest, res: ExpressResponse) => {
  const startedAt = Date.now();
  const logs: Array<{ level: string; message: string; timestamp: string }> = [];
  const pushLog = (level: string, ...args: unknown[]) => {
    const text = args
      .map((a) => {
        if (typeof a === 'string') return a;
        try {
          return JSON.stringify(a, null, 2);
        } catch {
          return String(a);
        }
      })
      .join(' ');
    logs.push({
      level,
      message: text,
      timestamp: new Date().toISOString().slice(11, 23),
    });
  };

  try {
    const {
      workspaceFiles = {},
      entryPath = 'src/index.ts',
      bindings = [],
      method = 'GET',
      requestPath = '/',
      headers = {},
      body = '',
      evalSnippet,
    } = req.body || {};

    const normalizedFiles: Record<string, string> = {};
    for (const [k, v] of Object.entries(workspaceFiles)) {
      normalizedFiles[normalizePath(k)] = String(v ?? '');
    }

    const entry = normalizePath(String(entryPath));
    const entryLower = entry.toLowerCase();

    // Handle static non-JS previews unless evalSnippet is specified
    if (!evalSnippet && (entryLower.endsWith('.html') || entryLower.endsWith('.htm'))) {
      const content = normalizedFiles[entry] || '';
      res.json({
        mode: 'STATIC_HTML',
        ok: true,
        statusCode: 200,
        statusText: 'OK',
        responseHeaders: { 'content-type': 'text/html; charset=utf-8' },
        responseBody: content,
        contentType: 'text/html',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected: [],
      });
      return;
    }

    if (!evalSnippet && entryLower.endsWith('.svg')) {
      const content = normalizedFiles[entry] || '';
      res.json({
        mode: 'STATIC_SVG',
        ok: true,
        statusCode: 200,
        statusText: 'OK',
        responseHeaders: { 'content-type': 'image/svg+xml' },
        responseBody: content,
        contentType: 'image/svg+xml',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected: [],
      });
      return;
    }

    if (!evalSnippet && (entryLower.endsWith('.md') || entryLower.endsWith('.markdown'))) {
      const content = normalizedFiles[entry] || '';
      res.json({
        mode: 'STATIC_MARKDOWN',
        ok: true,
        statusCode: 200,
        statusText: 'OK',
        responseHeaders: { 'content-type': 'text/markdown; charset=utf-8' },
        responseBody: content,
        contentType: 'text/markdown',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected: [],
      });
      return;
    }

    if (!evalSnippet && entryLower.endsWith('.json')) {
      const content = normalizedFiles[entry] || '{}';
      res.json({
        mode: 'STATIC_JSON',
        ok: true,
        statusCode: 200,
        statusText: 'OK',
        responseHeaders: { 'content-type': 'application/json' },
        responseBody: content,
        contentType: 'application/json',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected: [],
      });
      return;
    }

    // Build virtual workspace via esbuild
    const sourceCode = evalSnippet ? String(evalSnippet) : normalizedFiles[entry] ?? '';
    const virtualPlugin: esbuild.Plugin = {
      name: 'codeflare-virtual-fs',
      setup(build) {
        build.onResolve({ filter: /.*/ }, (args) => {
          if (args.kind === 'entry-point') {
            return { path: entry, namespace: 'cf-virtual' };
          }
          if (args.path.startsWith('.')) {
            const importer = args.importer || entry;
            const base = resolveRelativePath(importer, args.path);
            const candidates = [
              base,
              `${base}.ts`,
              `${base}.tsx`,
              `${base}.js`,
              `${base}.jsx`,
              `${base}.mjs`,
              `${base}.json`,
              `${base}/index.ts`,
              `${base}/index.js`,
            ];
            for (const c of candidates) {
              if (c in normalizedFiles) {
                return { path: c, namespace: 'cf-virtual' };
              }
            }
          }
          return { path: args.path, external: true };
        });

        build.onLoad({ filter: /.*/, namespace: 'cf-virtual' }, (args) => {
          const contents =
            args.path === entry && evalSnippet
              ? String(evalSnippet)
              : normalizedFiles[args.path] ?? '';
          const ext = path.posix.extname(args.path).toLowerCase();
          let loader: esbuild.Loader = 'ts';
          if (ext === '.js' || ext === '.mjs' || ext === '.cjs') loader = 'js';
          else if (ext === '.jsx') loader = 'jsx';
          else if (ext === '.tsx') loader = 'tsx';
          else if (ext === '.json') loader = 'json';
          else if (ext === '.css') loader = 'text';
          return { contents, loader };
        });
      },
    };

    const bundled = await esbuild.build({
      entryPoints: [entry],
      bundle: true,
      write: false,
      format: 'cjs',
      platform: 'neutral',
      target: 'es2022',
      mainFields: ['module', 'main'],
      plugins: [virtualPlugin],
    });

    const compiledJs = bundled.outputFiles?.[0]?.text || '';
    if (!compiledJs && !sourceCode.trim()) {
      res.json({
        mode: 'SCRIPT',
        ok: true,
        statusCode: 200,
        statusText: 'OK',
        responseHeaders: {},
        responseBody: '',
        contentType: 'text/plain',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected: [],
      });
      return;
    }

    // Build mock Cloudflare bindings environment
    const mockEnv: Record<string, unknown> = {};
    const kvBacking = new Map<string, Map<string, { value: string; metadata?: unknown }>>();
    const r2Backing = new Map<string, Map<string, { body: Uint8Array; metadata?: unknown }>>();

    if (Array.isArray(bindings)) {
      for (const b of bindings) {
        if (!b || !b.name) continue;
        const name = String(b.name);
        const type = String(b.type || 'plain_text');
        if (type === 'plain_text' || type === 'secret_text') {
          mockEnv[name] = b.text ?? (type === 'secret_text' ? '••••••••' : '');
        } else if (type === 'json') {
          try {
            mockEnv[name] = b.json ?? JSON.parse(b.text || '{}');
          } catch {
            mockEnv[name] = b.text || {};
          }
        } else if (type === 'kv_namespace') {
          const store = new Map<string, { value: string; metadata?: unknown }>();
          kvBacking.set(name, store);
          mockEnv[name] = {
            get: async (key: string, opts?: string | { type?: string }) => {
              const entryVal = store.get(key);
              if (!entryVal) return null;
              const targetType = typeof opts === 'string' ? opts : opts?.type;
              if (targetType === 'json') return JSON.parse(entryVal.value);
              return entryVal.value;
            },
            put: async (key: string, value: string, opts?: { metadata?: unknown }) => {
              store.set(key, { value: String(value), metadata: opts?.metadata });
            },
            delete: async (key: string) => {
              store.delete(key);
            },
            list: async (opts?: { prefix?: string; limit?: number }) => {
              const prefix = opts?.prefix || '';
              const keys = Array.from(store.entries())
                .filter(([k]) => k.startsWith(prefix))
                .slice(0, opts?.limit || 1000)
                .map(([k, v]) => ({ name: k, metadata: v.metadata }));
              return { keys, list_complete: true };
            },
          };
        } else if (type === 'r2_bucket') {
          const bucket = new Map<string, { body: Uint8Array; metadata?: unknown }>();
          r2Backing.set(name, bucket);
          mockEnv[name] = {
            get: async (key: string) => {
              const obj = bucket.get(key);
              if (!obj) return null;
              return {
                key,
                size: obj.body.byteLength,
                text: async () => new TextDecoder().decode(obj.body),
                json: async () => JSON.parse(new TextDecoder().decode(obj.body)),
                arrayBuffer: async () => obj.body.buffer,
              };
            },
            put: async (key: string, value: string | Uint8Array) => {
              const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : value;
              bucket.set(key, { body: bytes });
              return { key, size: bytes.byteLength };
            },
            delete: async (key: string) => {
              bucket.delete(key);
            },
            list: async () => ({
              objects: Array.from(bucket.entries()).map(([k, v]) => ({ key: k, size: v.body.byteLength })),
              truncated: false,
            }),
          };
        } else if (type === 'd1') {
          mockEnv[name] = {
            prepare: (sql: string) => {
              let bound: unknown[] = [];
              const stmt = {
                bind: (...params: unknown[]) => {
                  bound = params;
                  return stmt;
                },
                all: async () => {
                  pushLog('info', `[D1 ${name}] all(): ${sql}`, bound);
                  return { results: [], success: true, meta: { duration: 1 } };
                },
                first: async () => {
                  pushLog('info', `[D1 ${name}] first(): ${sql}`, bound);
                  return null;
                },
                run: async () => {
                  pushLog('info', `[D1 ${name}] run(): ${sql}`, bound);
                  return { results: [], success: true, meta: { changes: 0, duration: 1 } };
                },
              };
              return stmt;
            },
          };
        } else {
          mockEnv[name] = { bindingName: name, bindingType: type };
        }
      }
    }

    // In-memory Cache API mock
    const defaultCacheMap = new Map<string, Response>();
    const mockCaches = {
      default: {
        match: async (reqOrUrl: string | Request) => {
          const key = typeof reqOrUrl === 'string' ? reqOrUrl : reqOrUrl.url;
          const hit = defaultCacheMap.get(key);
          return hit ? hit.clone() : undefined;
        },
        put: async (reqOrUrl: string | Request, response: Response) => {
          const key = typeof reqOrUrl === 'string' ? reqOrUrl : reqOrUrl.url;
          defaultCacheMap.set(key, response.clone());
        },
        delete: async (reqOrUrl: string | Request) => {
          const key = typeof reqOrUrl === 'string' ? reqOrUrl : reqOrUrl.url;
          return defaultCacheMap.delete(key);
        },
      },
    };

    const fetchListeners: Array<(event: { request: Request; respondWith: (p: Promise<Response> | Response) => void; waitUntil: (p: Promise<unknown>) => void }) => void> = [];
    const waitUntilPromises: Promise<unknown>[] = [];

    const moduleObj: { exports: Record<string, unknown> } = { exports: {} };
    const sandboxConsole = {
      log: (...args: unknown[]) => pushLog('log', ...args),
      info: (...args: unknown[]) => pushLog('info', ...args),
      warn: (...args: unknown[]) => pushLog('warn', ...args),
      error: (...args: unknown[]) => pushLog('error', ...args),
      debug: (...args: unknown[]) => pushLog('debug', ...args),
    };

    const sandboxGlobals: Record<string, unknown> = {
      module: moduleObj,
      exports: moduleObj.exports,
      require: (id: string) => {
        if (id === 'node:crypto' || id === 'crypto') return globalThis.crypto;
        throw new Error(`Module "${id}" is not available in the local Workers sandbox`);
      },
      console: sandboxConsole,
      Request,
      Response,
      Headers,
      URL,
      URLSearchParams,
      TextEncoder,
      TextDecoder,
      ReadableStream,
      TransformStream,
      WritableStream,
      Blob,
      File,
      FormData,
      fetch: globalThis.fetch,
      crypto: globalThis.crypto,
      atob: globalThis.atob,
      btoa: globalThis.btoa,
      setTimeout: globalThis.setTimeout,
      clearTimeout: globalThis.clearTimeout,
      caches: mockCaches,
      env: mockEnv,
      addEventListener: (type: string, listener: typeof fetchListeners[number]) => {
        if (type === 'fetch') {
          fetchListeners.push(listener);
        }
      },
    };
    sandboxGlobals.globalThis = sandboxGlobals;
    sandboxGlobals.self = sandboxGlobals;

    const context = vm.createContext(sandboxGlobals);

    // If evalSnippet is a bare expression (like `1 + 2` or `env`), evaluate directly first if valid expression
    if (evalSnippet) {
      let snippetResult: unknown;
      try {
        const exprScript = new vm.Script(`(${evalSnippet})`, { filename: 'repl.js' });
        snippetResult = exprScript.runInContext(context, { timeout: 3000 });
      } catch {
        const stmtScript = new vm.Script(compiledJs, { filename: 'repl.js' });
        snippetResult = stmtScript.runInContext(context, { timeout: 3000 });
      }
      const awaited = snippetResult instanceof Promise ? await snippetResult : snippetResult;
      const bodyStr =
        awaited === undefined
          ? 'undefined'
          : typeof awaited === 'string'
            ? awaited
            : JSON.stringify(awaited, null, 2);
      res.json({
        mode: 'EVAL',
        ok: true,
        statusCode: 200,
        statusText: 'OK',
        responseHeaders: { 'content-type': 'text/plain; charset=utf-8' },
        responseBody: bodyStr,
        contentType: 'text/plain',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected: [],
      });
      return;
    }

    const script = new vm.Script(compiledJs, { filename: entry });
    const scriptReturn = script.runInContext(context, { timeout: 5000 });

    const modExports = (moduleObj.exports || {}) as Record<string, unknown>;
    const defaultExport = (modExports.default ?? modExports) as Record<string, unknown> | undefined;
    const exportsDetected = Object.keys(modExports);

    const ctx = {
      waitUntil: (p: Promise<unknown>) => {
        waitUntilPromises.push(Promise.resolve(p).catch((e) => pushLog('error', 'waitUntil error:', e)));
      },
      passThroughOnException: () => {},
    };

    const cleanPath = String(requestPath).startsWith('/') ? String(requestPath) : `/${requestPath}`;
    const workerUrl = `https://local.codeflare.dev${cleanPath}`;
    const upperMethod = String(method).toUpperCase();
    const canHaveBody = !['GET', 'HEAD'].includes(upperMethod);
    const incomingRequest = new Request(workerUrl, {
      method: upperMethod,
      headers: headers && typeof headers === 'object' ? headers : {},
      body: canHaveBody && body ? String(body) : undefined,
    });

    // Mode 1: ESM Worker with default.fetch
    if (defaultExport && typeof defaultExport.fetch === 'function') {
      const fn = defaultExport.fetch as (req: Request, env: unknown, ctx: unknown) => Promise<Response> | Response;
      const workerResponse = await fn(incomingRequest, mockEnv, ctx);
      await Promise.allSettled(waitUntilPromises);

      if (workerResponse instanceof Response) {
        const resBody = await workerResponse.text();
        const resHeaders: Record<string, string> = {};
        workerResponse.headers.forEach((v, k) => {
          resHeaders[k] = v;
        });
        res.json({
          mode: 'WORKER_FETCH',
          ok: workerResponse.ok,
          statusCode: workerResponse.status,
          statusText: workerResponse.statusText || 'OK',
          responseHeaders: resHeaders,
          responseBody: resBody,
          contentType: workerResponse.headers.get('content-type') || 'text/plain',
          logs,
          durationMs: Date.now() - startedAt,
          exportsDetected,
        });
        return;
      }

      res.json({
        mode: 'WORKER_FETCH',
        ok: true,
        statusCode: 200,
        statusText: 'OK',
        responseHeaders: { 'content-type': 'application/json' },
        responseBody: typeof workerResponse === 'string' ? workerResponse : JSON.stringify(workerResponse, null, 2),
        contentType: 'application/json',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected,
      });
      return;
    }

    // Mode 2: Service Worker syntax addEventListener('fetch', ...)
    if (fetchListeners.length > 0) {
      let capturedPromise: Promise<Response> | Response | null = null;
      const event = {
        request: incomingRequest,
        respondWith: (resp: Promise<Response> | Response) => {
          capturedPromise = resp;
        },
        waitUntil: ctx.waitUntil,
      };
      fetchListeners[0](event);
      const workerResponse = capturedPromise ? await capturedPromise : new Response('No response from respondWith()', { status: 500 });
      await Promise.allSettled(waitUntilPromises);
      const resBody = await workerResponse.text();
      const resHeaders: Record<string, string> = {};
      workerResponse.headers.forEach((v, k) => {
        resHeaders[k] = v;
      });
      res.json({
        mode: 'SERVICE_WORKER',
        ok: workerResponse.ok,
        statusCode: workerResponse.status,
        statusText: workerResponse.statusText || 'OK',
        responseHeaders: resHeaders,
        responseBody: resBody,
        contentType: workerResponse.headers.get('content-type') || 'text/plain',
        logs,
        durationMs: Date.now() - startedAt,
        exportsDetected,
      });
      return;
    }

    // Mode 3: Plain JS/TS script execution
    const awaitedReturn = scriptReturn instanceof Promise ? await scriptReturn : scriptReturn;
    res.json({
      mode: 'SCRIPT',
      ok: true,
      statusCode: 200,
      statusText: 'OK',
      responseHeaders: { 'content-type': 'text/plain; charset=utf-8' },
      responseBody:
        awaitedReturn !== undefined
          ? typeof awaitedReturn === 'string'
            ? awaitedReturn
            : JSON.stringify(awaitedReturn, null, 2)
          : logs.map((l) => l.message).join('\n'),
      contentType: 'text/plain',
      logs,
      durationMs: Date.now() - startedAt,
      exportsDetected,
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.stack || err.message : String(err);
    pushLog('error', message);
    res.json({
      mode: 'ERROR',
      ok: false,
      statusCode: 500,
      statusText: 'Runtime Error',
      responseHeaders: {},
      responseBody: message,
      contentType: 'text/plain',
      logs,
      durationMs: Date.now() - startedAt,
      exportsDetected: [],
      errorMessage: err instanceof Error ? err.message : String(err),
    });
  }
});

async function startServer() {
  const isProd = process.env.NODE_ENV === 'production';
  if (isProd) {
    const distDir = path.resolve(__dirname, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req: ExpressRequest, res: ExpressResponse) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true, hmr: false },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`CodeFlare IDE server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start CodeFlare server:', err);
  process.exit(1);
});
