// EdgeCode Universal Deno Runner
// Executes ES Module Cloudflare Workers ({ fetch }), Service Workers (addEventListener('fetch')),
// and standalone JS/TS scripts directly via Deno.

interface BindingSpec {
  name: string;
  type: string;
  text?: string;
}

type FetchListener = (event: {
  type: string;
  request: Request;
  respondWith: (r: Response | Promise<Response>) => void;
  waitUntil: (p: Promise<unknown>) => void;
}) => void;

const entryFile = Deno.env.get("EDGECODE_ENTRY_FILE") ?? "";
const evalSnippet = Deno.env.get("EDGECODE_EVAL_SNIPPET") ?? "";
const bindingsRaw = Deno.env.get("EDGECODE_BINDINGS_JSON") ?? "[]";
const port = Number(Deno.env.get("EDGECODE_PORT") ?? "8080");
const hostname = Deno.env.get("EDGECODE_HOST") ?? "0.0.0.0";

function toFileUrl(fsPath: string): string {
  const normalized = fsPath.replace(/\\/g, "/");
  const prefixed = normalized.startsWith("/") ? normalized : `/${normalized}`;
  return `file://${encodeURI(prefixed).replace(/#/g, "%23").replace(/\?/g, "%3F")}`;
}

function createEnv(bindings: BindingSpec[]): Record<string, unknown> {
  const env: Record<string, unknown> = {};
  for (const b of bindings) {
    if (!b || !b.name) continue;
    if (b.type === "plain_text" || b.type === "secret_text") {
      const val = b.text ?? "";
      env[b.name] = val;
      try {
        Deno.env.set(b.name, val);
      } catch {
        // Ignore if env mutation is disallowed
      }
    } else if (b.type === "kv_namespace") {
      const store = new Map<string, { value: string; metadata?: unknown }>();
      env[b.name] = {
        get: async (key: string, opts?: string | { type?: string }) => {
          const entry = store.get(String(key));
          if (!entry) return null;
          const kind = typeof opts === "string" ? opts : opts?.type;
          if (kind === "json") return JSON.parse(entry.value);
          if (kind === "arrayBuffer") return new TextEncoder().encode(entry.value).buffer;
          if (kind === "stream") {
            return new ReadableStream({
              start(controller) {
                controller.enqueue(new TextEncoder().encode(entry.value));
                controller.close();
              },
            });
          }
          return entry.value;
        },
        getWithMetadata: async (key: string, opts?: string | { type?: string }) => {
          const entry = store.get(String(key));
          if (!entry) return { value: null, metadata: null };
          const kind = typeof opts === "string" ? opts : opts?.type;
          const value = kind === "json" ? JSON.parse(entry.value) : entry.value;
          return { value, metadata: entry.metadata ?? null };
        },
        put: async (key: string, value: unknown, opts?: { metadata?: unknown }) => {
          const str = typeof value === "string" ? value : JSON.stringify(value);
          store.set(String(key), { value: str, metadata: opts?.metadata });
        },
        delete: async (key: string) => {
          store.delete(String(key));
        },
        list: async (opts?: { prefix?: string; limit?: number }) => {
          let keys = Array.from(store.keys());
          if (opts?.prefix) keys = keys.filter((k) => k.startsWith(opts.prefix!));
          if (opts?.limit) keys = keys.slice(0, opts.limit);
          return {
            keys: keys.map((name) => ({ name, metadata: store.get(name)?.metadata ?? null })),
            list_complete: true,
            cursor: "",
          };
        },
      };
    } else if (b.type === "r2_bucket") {
      const store = new Map<string, Uint8Array>();
      const enc = new TextEncoder();
      const dec = new TextDecoder();
      env[b.name] = {
        get: async (key: string) => {
          const bytes = store.get(key);
          if (!bytes) return null;
          return {
            key,
            size: bytes.byteLength,
            text: async () => dec.decode(bytes),
            json: async () => JSON.parse(dec.decode(bytes)),
            arrayBuffer: async () => bytes.slice().buffer,
            body: new ReadableStream({
              start(c) {
                c.enqueue(bytes);
                c.close();
              },
            }),
          };
        },
        head: async (key: string) => {
          const bytes = store.get(key);
          return bytes ? { key, size: bytes.byteLength } : null;
        },
        put: async (key: string, value: unknown) => {
          let bytes: Uint8Array;
          if (value instanceof Uint8Array) bytes = value;
          else if (value instanceof ArrayBuffer) bytes = new Uint8Array(value);
          else if (typeof value === "string") bytes = enc.encode(value);
          else bytes = enc.encode(JSON.stringify(value));
          store.set(key, bytes);
          return { key, size: bytes.byteLength };
        },
        delete: async (keys: string | string[]) => {
          for (const k of Array.isArray(keys) ? keys : [keys]) store.delete(k);
        },
        list: async (opts?: { prefix?: string; limit?: number }) => {
          let keys = Array.from(store.keys());
          if (opts?.prefix) keys = keys.filter((k) => k.startsWith(opts.prefix!));
          if (opts?.limit) keys = keys.slice(0, opts.limit);
          return {
            objects: keys.map((key) => ({ key, size: store.get(key)?.byteLength ?? 0 })),
            truncated: false,
          };
        },
      };
    } else if (b.type === "d1") {
      const makeStmt = (sql: string) => ({
        bind: (..._params: unknown[]) => makeStmt(sql),
        all: async () => ({ results: [], success: true, meta: { sql } }),
        first: async () => null,
        run: async () => ({ results: [], success: true, meta: { sql, changes: 0 } }),
        raw: async () => [],
      });
      env[b.name] = {
        prepare: (sql: string) => makeStmt(sql),
        batch: async (stmts: Array<{ all: () => Promise<unknown> }>) =>
          Promise.all(stmts.map((s) => s.all())),
        exec: async (_sql: string) => ({ count: 0, duration: 0 }),
      };
    } else {
      env[b.name] = {};
    }
  }
  return env;
}

function makeExecutionContext() {
  const pending: Promise<unknown>[] = [];
  return {
    waitUntil(p: Promise<unknown>) {
      if (p && typeof p.then === "function") {
        pending.push(Promise.resolve(p).catch((err) => console.error("[waitUntil]", err)));
      }
    },
    passThroughOnException() {},
    flush: async () => {
      if (pending.length > 0) {
        await Promise.allSettled(pending.splice(0, pending.length));
      }
    },
  };
}

let fetchListener: FetchListener | null = null;
const origAddEventListener = globalThis.addEventListener?.bind(globalThis);
(globalThis as Record<string, unknown>).addEventListener = function (
  type: string,
  listener: EventListenerOrEventListenerObject,
  options?: boolean | AddEventListenerOptions,
) {
  if (type === "fetch" && typeof listener === "function") {
    fetchListener = listener as unknown as FetchListener;
    return;
  }
  if (origAddEventListener) {
    origAddEventListener(type, listener, options);
  }
};

if (typeof (globalThis as Record<string, unknown>).caches === "undefined") {
  const memCache = new Map<string, Response>();
  const cacheObj = {
    match: async (req: Request | string) => {
      const key = typeof req === "string" ? req : req.url;
      return memCache.get(key)?.clone();
    },
    put: async (req: Request | string, res: Response) => {
      const key = typeof req === "string" ? req : req.url;
      memCache.set(key, res.clone());
    },
    delete: async (req: Request | string) => {
      const key = typeof req === "string" ? req : req.url;
      return memCache.delete(key);
    },
  };
  (globalThis as Record<string, unknown>).caches = {
    default: cacheObj,
    open: async (_name: string) => cacheObj,
  };
}

async function main() {
  let parsedBindings: BindingSpec[] = [];
  try {
    parsedBindings = JSON.parse(bindingsRaw);
  } catch {
    parsedBindings = [];
  }
  const env = createEnv(parsedBindings);

  if (evalSnippet.trim().length > 0) {
    const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
    const trimmed = evalSnippet.trim();
    const fnBody =
      trimmed.includes(";") || trimmed.includes("\n") || /\breturn\b/.test(trimmed)
        ? trimmed
        : `return (${trimmed});`;
    const fn = new AsyncFunction("env", fnBody);
    const result = await fn(env);
    if (result !== undefined) {
      console.log(typeof result === "string" ? result : JSON.stringify(result, null, 2));
    }
    console.log("__EDGECODE_READY__:SCRIPT:0");
    Deno.exit(0);
  }

  if (!entryFile) {
    console.error("Missing EDGECODE_ENTRY_FILE");
    Deno.exit(1);
  }

  const mod = await import(toFileUrl(entryFile));

  let workerFetch:
    | ((req: Request, env: Record<string, unknown>, ctx: ReturnType<typeof makeExecutionContext>) => Response | Promise<Response>)
    | null = null;

  if (mod?.default && typeof mod.default.fetch === "function") {
    workerFetch = mod.default.fetch.bind(mod.default);
  } else if (typeof mod?.fetch === "function") {
    workerFetch = mod.fetch.bind(mod);
  } else if (
    typeof mod?.default === "function" &&
    mod.default.prototype &&
    typeof mod.default.prototype.fetch === "function"
  ) {
    workerFetch = (req, runtimeEnv, ctx) => {
      const instance = new mod.default(ctx, runtimeEnv);
      return instance.fetch(req);
    };
  }

  if (workerFetch) {
    const handler = workerFetch;
    Deno.serve(
      {
        port,
        hostname,
        onListen: ({ port: boundPort }) => {
          console.log(`__EDGECODE_READY__:WORKER:${boundPort}`);
        },
      },
      async (request: Request) => {
        const ctx = makeExecutionContext();
        try {
          const response = await handler(request, env, ctx);
          await ctx.flush();
          return response instanceof Response ? response : new Response(String(response ?? ""));
        } catch (err) {
          console.error(err);
          return new Response(err instanceof Error ? (err.stack ?? err.message) : String(err), {
            status: 500,
            headers: { "content-type": "text/plain; charset=utf-8" },
          });
        }
      },
    );
    return;
  }

  if (fetchListener) {
    const listener = fetchListener;
    Deno.serve(
      {
        port,
        hostname,
        onListen: ({ port: boundPort }) => {
          console.log(`__EDGECODE_READY__:WORKER:${boundPort}`);
        },
      },
      async (request: Request) => {
        const ctx = makeExecutionContext();
        let responded: Response | Promise<Response> | null = null;
        try {
          listener({
            type: "fetch",
            request,
            respondWith: (res) => {
              responded = res;
            },
            waitUntil: ctx.waitUntil,
          });
          if (!responded) {
            return new Response("Service Worker did not call event.respondWith()", { status: 500 });
          }
          const resolved = await responded;
          await ctx.flush();
          return resolved instanceof Response ? resolved : new Response(String(resolved ?? ""));
        } catch (err) {
          console.error(err);
          return new Response(err instanceof Error ? (err.stack ?? err.message) : String(err), {
            status: 500,
            headers: { "content-type": "text/plain; charset=utf-8" },
          });
        }
      },
    );
    return;
  }

  // Standard JS/TS script: side effects/top-level logs have already executed on dynamic import.
  if (typeof mod?.default === "function") {
    const out = await mod.default(env);
    if (out !== undefined) {
      console.log(typeof out === "string" ? out : JSON.stringify(out, null, 2));
    }
  } else if (mod?.default !== undefined) {
    console.log(typeof mod.default === "string" ? mod.default : JSON.stringify(mod.default, null, 2));
  }

  console.log("__EDGECODE_READY__:SCRIPT:0");
  Deno.exit(0);
}

main().catch((err) => {
  console.error(err instanceof Error ? (err.stack ?? err.message) : String(err));
  Deno.exit(1);
});
