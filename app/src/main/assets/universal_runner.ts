// EdgeCode Universal Deno Runner
// Executes ES Module Cloudflare Workers ({ fetch, scheduled }), Service Workers (addEventListener('fetch')),
// and standalone JS/TS scripts via Deno with local persistent KV, R2, D1 SQL, Cache API, HTMLRewriter, and request.cf.

interface BindingSpec {
  name: string;
  type: string;
  text?: string;
  className?: string;
  scriptName?: string;
  queueName?: string;
  service?: string;
  environment?: string;
}

type FetchListener = (event: {
  type: string;
  request: Request;
  respondWith: (r: Response | Promise<Response>) => void;
  waitUntil: (p: Promise<unknown>) => void;
}) => void;

type ScheduledController = {
  cron: string;
  scheduledTime: number;
  noRetry: () => void;
};

type ScheduledListener = (event: {
  type: string;
  cron: string;
  scheduledTime: number;
  waitUntil: (p: Promise<unknown>) => void;
  noRetry: () => void;
}) => void;

const entryFile = Deno.env.get("EDGECODE_ENTRY_FILE") ?? "";
const evalSnippet = Deno.env.get("EDGECODE_EVAL_SNIPPET") ?? "";
const bindingsRaw = Deno.env.get("EDGECODE_BINDINGS_JSON") ?? "[]";
const port = Number(Deno.env.get("EDGECODE_PORT") ?? "0");
const hostname = Deno.env.get("EDGECODE_HOST") ?? "127.0.0.1";
const stateDir = (Deno.env.get("EDGECODE_STATE_DIR") ?? "").trim();

function toFileUrl(fsPath: string): string {
  const normalized = fsPath.replace(/\\/g, "/");
  const prefixed = normalized.startsWith("/") ? normalized : `/${normalized}`;
  return `file://${encodeURI(prefixed).replace(/#/g, "%23").replace(/\?/g, "%3F")}`;
}

function safeId(input: string): string {
  const clean = String(input || "default").replace(/[^A-Za-z0-9._-]/g, "_");
  return clean.length > 0 ? clean.slice(0, 80) : "default";
}

function hashKeyHex(input: string): string {
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < input.length; i++) {
    const c = input.charCodeAt(i);
    h1 ^= c;
    h1 = Math.imul(h1, 0x01000193);
    h2 ^= c + i;
    h2 = Math.imul(h2, 0x85ebca6b);
  }
  return `${(h1 >>> 0).toString(16).padStart(8, "0")}${(h2 >>> 0).toString(16).padStart(8, "0")}`;
}

function ensureDir(dir: string) {
  if (!dir) return;
  try {
    Deno.mkdirSync(dir, { recursive: true });
  } catch {
    // Directory may already exist
  }
}

function readJsonFile<T>(filePath: string, fallback: T): T {
  if (!filePath) return fallback;
  try {
    const raw = Deno.readTextFileSync(filePath);
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function writeJsonFile(filePath: string, data: unknown) {
  if (!filePath) return;
  try {
    const lastSlash = filePath.lastIndexOf("/");
    if (lastSlash > 0) ensureDir(filePath.slice(0, lastSlash));
    Deno.writeTextFileSync(filePath, JSON.stringify(data));
  } catch {
    // Ignore persistence failure if read-only
  }
}

function createPersistentKv(bindingName: string) {
  type KvRecord = { value: string; metadata?: unknown; expiration?: number };
  const storePath = stateDir ? `${stateDir}/kv/${safeId(bindingName)}.json` : "";
  const store = new Map<string, KvRecord>();

  if (storePath) {
    const saved = readJsonFile<Record<string, KvRecord>>(storePath, {});
    const nowSec = Math.floor(Date.now() / 1000);
    for (const [k, v] of Object.entries(saved)) {
      if (v && (v.expiration === undefined || v.expiration > nowSec)) {
        store.set(k, v);
      }
    }
  }

  const flush = () => {
    if (!storePath) return;
    const obj: Record<string, KvRecord> = {};
    for (const [k, v] of store.entries()) obj[k] = v;
    writeJsonFile(storePath, obj);
  };

  const getActive = (key: string): KvRecord | null => {
    const entry = store.get(key);
    if (!entry) return null;
    if (entry.expiration !== undefined && entry.expiration <= Math.floor(Date.now() / 1000)) {
      store.delete(key);
      flush();
      return null;
    }
    return entry;
  };

  const decodeValue = (raw: string, type?: string) => {
    if (type === "json") return JSON.parse(raw);
    if (type === "arrayBuffer") return new TextEncoder().encode(raw).buffer;
    if (type === "stream") {
      return new ReadableStream({
        start(controller) {
          controller.enqueue(new TextEncoder().encode(raw));
          controller.close();
        },
      });
    }
    return raw;
  };

  return {
    get: async (key: string, opts?: string | { type?: string }) => {
      const entry = getActive(String(key));
      if (!entry) return null;
      const kind = typeof opts === "string" ? opts : opts?.type;
      return decodeValue(entry.value, kind);
    },
    getWithMetadata: async (key: string, opts?: string | { type?: string }) => {
      const entry = getActive(String(key));
      if (!entry) return { value: null, metadata: null, cacheStatus: null };
      const kind = typeof opts === "string" ? opts : opts?.type;
      return {
        value: decodeValue(entry.value, kind),
        metadata: entry.metadata ?? null,
        cacheStatus: null,
      };
    },
    put: async (
      key: string,
      value: unknown,
      opts?: { metadata?: unknown; expiration?: number; expirationTtl?: number },
    ) => {
      let str: string;
      if (typeof value === "string") {
        str = value;
      } else if (value instanceof ArrayBuffer) {
        str = new TextDecoder().decode(new Uint8Array(value));
      } else if (ArrayBuffer.isView(value)) {
        str = new TextDecoder().decode(new Uint8Array(value.buffer, value.byteOffset, value.byteLength));
      } else if (value instanceof ReadableStream) {
        str = await new Response(value).text();
      } else {
        str = JSON.stringify(value);
      }
      const nowSec = Math.floor(Date.now() / 1000);
      let expiration = opts?.expiration;
      if (typeof opts?.expirationTtl === "number" && opts.expirationTtl > 0) {
        expiration = nowSec + Math.floor(opts.expirationTtl);
      }
      store.set(String(key), { value: str, metadata: opts?.metadata, expiration });
      flush();
    },
    delete: async (key: string) => {
      store.delete(String(key));
      flush();
    },
    list: async (opts?: { prefix?: string; limit?: number; cursor?: string }) => {
      const nowSec = Math.floor(Date.now() / 1000);
      let mutated = false;
      for (const [k, v] of store.entries()) {
        if (v.expiration !== undefined && v.expiration <= nowSec) {
          store.delete(k);
          mutated = true;
        }
      }
      if (mutated) flush();

      let keys = Array.from(store.keys()).sort();
      if (opts?.prefix) keys = keys.filter((k) => k.startsWith(opts.prefix!));
      const offset = opts?.cursor ? Math.max(0, Number(opts.cursor) || 0) : 0;
      const limit = opts?.limit && opts.limit > 0 ? opts.limit : 1000;
      const page = keys.slice(offset, offset + limit);
      const nextOffset = offset + page.length;
      const listComplete = nextOffset >= keys.length;
      return {
        keys: page.map((name) => {
          const rec = store.get(name);
          return {
            name,
            expiration: rec?.expiration,
            metadata: rec?.metadata ?? null,
          };
        }),
        list_complete: listComplete,
        cursor: listComplete ? "" : String(nextOffset),
      };
    },
  };
}

function createPersistentR2(bindingName: string) {
  type R2Meta = {
    key: string;
    size: number;
    etag: string;
    uploaded: string;
    blobFile: string;
    httpMetadata?: Record<string, string>;
    customMetadata?: Record<string, string>;
  };

  const bucketDir = stateDir ? `${stateDir}/r2/${safeId(bindingName)}` : "";
  const indexPath = bucketDir ? `${bucketDir}/index.json` : "";
  const memBlobs = new Map<string, Uint8Array>();
  const metaMap = new Map<string, R2Meta>();
  const enc = new TextEncoder();
  const dec = new TextDecoder();

  if (indexPath) {
    ensureDir(bucketDir);
    const saved = readJsonFile<Record<string, R2Meta>>(indexPath, {});
    for (const [k, m] of Object.entries(saved)) {
      if (m && m.key) metaMap.set(k, m);
    }
  }

  const flushIndex = () => {
    if (!indexPath) return;
    const obj: Record<string, R2Meta> = {};
    for (const [k, m] of metaMap.entries()) obj[k] = m;
    writeJsonFile(indexPath, obj);
  };

  const readBytes = (meta: R2Meta): Uint8Array | null => {
    if (bucketDir && meta.blobFile) {
      try {
        return Deno.readFileSync(`${bucketDir}/${meta.blobFile}`);
      } catch {
        return null;
      }
    }
    return memBlobs.get(meta.key) ?? null;
  };

  const writeBytes = (meta: R2Meta, bytes: Uint8Array) => {
    if (bucketDir) {
      ensureDir(bucketDir);
      try {
        Deno.writeFileSync(`${bucketDir}/${meta.blobFile}`, bytes);
      } catch {
        memBlobs.set(meta.key, bytes);
      }
    } else {
      memBlobs.set(meta.key, bytes);
    }
  };

  const removeBlob = (meta: R2Meta) => {
    memBlobs.delete(meta.key);
    if (bucketDir && meta.blobFile) {
      try {
        Deno.removeSync(`${bucketDir}/${meta.blobFile}`);
      } catch {
        // Ignore if already absent
      }
    }
  };

  const toHeadObject = (meta: R2Meta) => ({
    key: meta.key,
    version: meta.etag,
    size: meta.size,
    etag: meta.etag,
    httpEtag: `"${meta.etag}"`,
    uploaded: new Date(meta.uploaded),
    httpMetadata: meta.httpMetadata ?? {},
    customMetadata: meta.customMetadata ?? {},
    writeHttpMetadata(headers: Headers) {
      const h = meta.httpMetadata ?? {};
      if (h.contentType) headers.set("content-type", h.contentType);
      if (h.contentLanguage) headers.set("content-language", h.contentLanguage);
      if (h.contentDisposition) headers.set("content-disposition", h.contentDisposition);
      if (h.contentEncoding) headers.set("content-encoding", h.contentEncoding);
      if (h.cacheControl) headers.set("cache-control", h.cacheControl);
    },
  });

  return {
    get: async (key: string) => {
      const meta = metaMap.get(String(key));
      if (!meta) return null;
      const bytes = readBytes(meta);
      if (!bytes) return null;
      const head = toHeadObject(meta);
      return {
        ...head,
        body: new ReadableStream({
          start(c) {
            c.enqueue(bytes);
            c.close();
          },
        }),
        bodyUsed: false,
        text: async () => dec.decode(bytes),
        json: async () => JSON.parse(dec.decode(bytes)),
        arrayBuffer: async () => bytes.slice().buffer,
        blob: async () => new Blob([bytes.slice().buffer], { type: meta.httpMetadata?.contentType || "application/octet-stream" }),
      };
    },
    head: async (key: string) => {
      const meta = metaMap.get(String(key));
      return meta ? toHeadObject(meta) : null;
    },
    put: async (
      key: string,
      value: unknown,
      opts?: { httpMetadata?: Record<string, string> | Headers; customMetadata?: Record<string, string> },
    ) => {
      const strKey = String(key);
      let bytes: Uint8Array;
      if (value === null || value === undefined) bytes = new Uint8Array(0);
      else if (value instanceof Uint8Array) bytes = value;
      else if (value instanceof ArrayBuffer) bytes = new Uint8Array(value);
      else if (ArrayBuffer.isView(value)) bytes = new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
      else if (typeof value === "string") bytes = enc.encode(value);
      else if (value instanceof Blob) bytes = new Uint8Array(await value.arrayBuffer());
      else if (value instanceof ReadableStream) bytes = new Uint8Array(await new Response(value).arrayBuffer());
      else bytes = enc.encode(JSON.stringify(value));

      const httpMeta: Record<string, string> = {};
      if (opts?.httpMetadata instanceof Headers) {
        opts.httpMetadata.forEach((v, k) => {
          httpMeta[k] = v;
        });
      } else if (opts?.httpMetadata && typeof opts.httpMetadata === "object") {
        Object.assign(httpMeta, opts.httpMetadata);
      }

      const meta: R2Meta = {
        key: strKey,
        size: bytes.byteLength,
        etag: `${hashKeyHex(strKey)}-${bytes.byteLength.toString(16)}`,
        uploaded: new Date().toISOString(),
        blobFile: `${hashKeyHex(strKey)}.bin`,
        httpMetadata: httpMeta,
        customMetadata: opts?.customMetadata ?? {},
      };
      writeBytes(meta, bytes);
      metaMap.set(strKey, meta);
      flushIndex();
      return toHeadObject(meta);
    },
    delete: async (keys: string | string[]) => {
      for (const k of Array.isArray(keys) ? keys : [keys]) {
        const meta = metaMap.get(String(k));
        if (meta) {
          removeBlob(meta);
          metaMap.delete(String(k));
        }
      }
      flushIndex();
    },
    list: async (opts?: { prefix?: string; limit?: number; cursor?: string; delimiter?: string }) => {
      let keys = Array.from(metaMap.keys()).sort();
      const prefix = opts?.prefix ?? "";
      if (prefix) keys = keys.filter((k) => k.startsWith(prefix));

      const delimitedPrefixes = new Set<string>();
      const matchedKeys: string[] = [];
      if (opts?.delimiter) {
        const delim = opts.delimiter;
        for (const k of keys) {
          const rest = k.slice(prefix.length);
          const idx = rest.indexOf(delim);
          if (idx >= 0) {
            delimitedPrefixes.add(prefix + rest.slice(0, idx + delim.length));
          } else {
            matchedKeys.push(k);
          }
        }
      } else {
        matchedKeys.push(...keys);
      }

      const offset = opts?.cursor ? Math.max(0, Number(opts.cursor) || 0) : 0;
      const limit = opts?.limit && opts.limit > 0 ? opts.limit : 1000;
      const slice = matchedKeys.slice(offset, offset + limit);
      const truncated = offset + slice.length < matchedKeys.length;
      return {
        objects: slice.map((k) => toHeadObject(metaMap.get(k)!)),
        truncated,
        cursor: truncated ? String(offset + slice.length) : undefined,
        delimitedPrefixes: Array.from(delimitedPrefixes).sort(),
      };
    },
  };
}

// Persistent zero-dependency SQLite-subset D1 engine for local Worker simulation
function createPersistentD1(bindingName: string) {
  type ColumnDef = {
    name: string;
    type: string;
    primaryKey?: boolean;
    autoIncrement?: boolean;
    notNull?: boolean;
    unique?: boolean;
    defaultValue?: unknown;
  };
  type TableData = {
    name: string;
    sql: string;
    columns: ColumnDef[];
    nextRowId: number;
    rows: Array<Record<string, unknown>>;
  };
  type DbSchema = { tables: Record<string, TableData> };

  const dbPath = stateDir ? `${stateDir}/d1/${safeId(bindingName)}.json` : "";
  const db: DbSchema = dbPath ? readJsonFile<DbSchema>(dbPath, { tables: {} }) : { tables: {} };
  if (!db.tables || typeof db.tables !== "object") db.tables = {};

  const flushDb = () => {
    if (dbPath) writeJsonFile(dbPath, db);
  };

  const unquoteIdent = (raw: string): string => {
    const t = raw.trim();
    if ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith("`") && t.endsWith("`")) || (t.startsWith("[") && t.endsWith("]"))) {
      return t.slice(1, -1).replace(/""/g, '"');
    }
    return t;
  };

  const splitSqlList = (raw: string): string[] => {
    const out: string[] = [];
    let cur = "";
    let depth = 0;
    let inSingle = false;
    let inDouble = false;
    for (let i = 0; i < raw.length; i++) {
      const ch = raw[i];
      if (ch === "'" && !inDouble) {
        cur += ch;
        if (inSingle && raw[i + 1] === "'") {
          cur += raw[++i];
        } else {
          inSingle = !inSingle;
        }
        continue;
      }
      if (ch === '"' && !inSingle) {
        inDouble = !inDouble;
        cur += ch;
        continue;
      }
      if (!inSingle && !inDouble) {
        if (ch === "(") depth++;
        else if (ch === ")") depth = Math.max(0, depth - 1);
        else if (ch === "," && depth === 0) {
          if (cur.trim()) out.push(cur.trim());
          cur = "";
          continue;
        }
      }
      cur += ch;
    }
    if (cur.trim()) out.push(cur.trim());
    return out;
  };

  const splitStatements = (sql: string): string[] => {
    const stmts: string[] = [];
    let cur = "";
    let inSingle = false;
    let inDouble = false;
    for (let i = 0; i < sql.length; i++) {
      const ch = sql[i];
      if (!inSingle && !inDouble && ch === "-" && sql[i + 1] === "-") {
        while (i < sql.length && sql[i] !== "\n") i++;
        continue;
      }
      if (ch === "'" && !inDouble) {
        cur += ch;
        if (inSingle && sql[i + 1] === "'") cur += sql[++i];
        else inSingle = !inSingle;
        continue;
      }
      if (ch === '"' && !inSingle) {
        inDouble = !inDouble;
        cur += ch;
        continue;
      }
      if (ch === ";" && !inSingle && !inDouble) {
        if (cur.trim()) stmts.push(cur.trim());
        cur = "";
        continue;
      }
      cur += ch;
    }
    if (cur.trim()) stmts.push(cur.trim());
    return stmts;
  };

  type ExprCursor = { params: unknown[]; nextParam: number };

  const parseValueToken = (rawToken: string, cursor: ExprCursor): unknown => {
    const t = rawToken.trim();
    if (t === "?") {
      return cursor.params[cursor.nextParam++];
    }
    const numParam = /^\?(\d+)$/.exec(t);
    if (numParam) {
      return cursor.params[Number(numParam[1]) - 1];
    }
    if (/^null$/i.test(t)) return null;
    if (/^true$/i.test(t)) return 1;
    if (/^false$/i.test(t)) return 0;
    if (/^current_timestamp$/i.test(t)) return new Date().toISOString().replace("T", " ").slice(0, 19);
    if (t.startsWith("'") && t.endsWith("'") && t.length >= 2) {
      return t.slice(1, -1).replace(/''/g, "'");
    }
    if (/^-?\d+(\.\d+)?$/.test(t)) return Number(t);
    return t;
  };

  const compareVals = (a: unknown, b: unknown): number => {
    if (a === b) return 0;
    if (a === null || a === undefined) return -1;
    if (b === null || b === undefined) return 1;
    if (typeof a === "number" && typeof b === "number") return a - b;
    const na = Number(a);
    const nb = Number(b);
    if (typeof a !== "boolean" && typeof b !== "boolean" && !Number.isNaN(na) && !Number.isNaN(nb) && String(a).trim() !== "" && String(b).trim() !== "") {
      return na - nb;
    }
    return String(a).localeCompare(String(b));
  };

  const evalWhere = (whereRaw: string, cursor: ExprCursor): ((row: Record<string, unknown>) => boolean) => {
    const trimmed = whereRaw.trim();
    if (!trimmed) return () => true;

    // Bind parameter offsets sequentially once up front for deterministic evaluation
    const boundValues = new Map<number, unknown>();
    let scan = 0;
    let inSingle = false;
    while (scan < trimmed.length) {
      const ch = trimmed[scan];
      if (ch === "'") {
        if (inSingle && trimmed[scan + 1] === "'") {
          scan += 2;
          continue;
        }
        inSingle = !inSingle;
      } else if (!inSingle && ch === "?") {
        const m = /^\?(\d+)/.exec(trimmed.slice(scan));
        if (m) {
          boundValues.set(scan, cursor.params[Number(m[1]) - 1]);
          scan += m[0].length;
          continue;
        } else {
          boundValues.set(scan, cursor.params[cursor.nextParam++]);
        }
      }
      scan++;
    }

    const resolveOperand = (tok: string, row: Record<string, unknown>, baseOffset: number): unknown => {
      const t = tok.trim();
      if (t.startsWith("?")) {
        const idx = trimmed.indexOf(t, Math.max(0, baseOffset));
        if (idx >= 0 && boundValues.has(idx)) return boundValues.get(idx);
      }
      if (/^null$/i.test(t)) return null;
      if (/^true$/i.test(t)) return 1;
      if (/^false$/i.test(t)) return 0;
      if (t.startsWith("'") && t.endsWith("'") && t.length >= 2) {
        return t.slice(1, -1).replace(/''/g, "'");
      }
      if (/^-?\d+(\.\d+)?$/.test(t)) return Number(t);
      const colName = unquoteIdent(t.includes(".") ? t.split(".").pop()! : t);
      return row[colName];
    };

    const splitTopKeyword = (expr: string, kw: string): string[] | null => {
      const parts: string[] = [];
      let cur = "";
      let depth = 0;
      let inStr = false;
      const re = new RegExp(`^\\b${kw}\\b`, "i");
      for (let i = 0; i < expr.length; i++) {
        const ch = expr[i];
        if (ch === "'") {
          cur += ch;
          if (inStr && expr[i + 1] === "'") cur += expr[++i];
          else inStr = !inStr;
          continue;
        }
        if (!inStr) {
          if (ch === "(") depth++;
          else if (ch === ")") depth = Math.max(0, depth - 1);
          else if (depth === 0 && (i === 0 || /\s|\)/.test(expr[i - 1])) && re.test(expr.slice(i))) {
            parts.push(cur.trim());
            cur = "";
            i += kw.length - 1;
            continue;
          }
        }
        cur += ch;
      }
      if (parts.length === 0) return null;
      parts.push(cur.trim());
      return parts;
    };

    const compile = (expr: string, offsetHint: number): ((row: Record<string, unknown>) => boolean) => {
      let s = expr.trim();
      while (s.startsWith("(") && s.endsWith(")")) {
        let depth = 0;
        let wrapsAll = true;
        let inStr = false;
        for (let i = 0; i < s.length; i++) {
          if (s[i] === "'") inStr = !inStr;
          if (!inStr) {
            if (s[i] === "(") depth++;
            else if (s[i] === ")") depth--;
            if (depth === 0 && i < s.length - 1) {
              wrapsAll = false;
              break;
            }
          }
        }
        if (wrapsAll) s = s.slice(1, -1).trim();
        else break;
      }

      const orParts = splitTopKeyword(s, "OR");
      if (orParts) {
        const fns = orParts.map((p) => compile(p, trimmed.indexOf(p, offsetHint)));
        return (row) => fns.some((fn) => fn(row));
      }

      const andParts = splitTopKeyword(s, "AND");
      if (andParts) {
        const fns = andParts.map((p) => compile(p, trimmed.indexOf(p, offsetHint)));
        return (row) => fns.every((fn) => fn(row));
      }

      const isNullMatch = /^(.+?)\s+IS\s+(NOT\s+)?NULL$/i.exec(s);
      if (isNullMatch) {
        const isNot = Boolean(isNullMatch[2]);
        const col = isNullMatch[1];
        return (row) => {
          const v = resolveOperand(col, row, offsetHint);
          return isNot ? v !== null && v !== undefined : v === null || v === undefined;
        };
      }

      const inMatch = /^(.+?)\s+(NOT\s+)?IN\s*\((.+)\)$/i.exec(s);
      if (inMatch) {
        const isNot = Boolean(inMatch[2]);
        const lhs = inMatch[1];
        const items = splitSqlList(inMatch[3]);
        return (row) => {
          const lv = resolveOperand(lhs, row, offsetHint);
          const hit = items.some((it) => compareVals(lv, resolveOperand(it, row, offsetHint)) === 0);
          return isNot ? !hit : hit;
        };
      }

      const likeMatch = /^(.+?)\s+(NOT\s+)?LIKE\s+(.+)$/i.exec(s);
      if (likeMatch) {
        const isNot = Boolean(likeMatch[2]);
        const lhs = likeMatch[1];
        const rhs = likeMatch[3];
        return (row) => {
          const lv = String(resolveOperand(lhs, row, offsetHint) ?? "");
          const pat = String(resolveOperand(rhs, row, offsetHint) ?? "");
          const regexStr =
            "^" +
            pat.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/%/g, ".*").replace(/_/g, ".") +
            "$";
          const hit = new RegExp(regexStr, "is").test(lv);
          return isNot ? !hit : hit;
        };
      }

      const opMatch = /^(.+?)\s*(<=|>=|<>|!=|==|=|<|>)\s*(.+)$/.exec(s);
      if (opMatch) {
        const lhs = opMatch[1];
        const op = opMatch[2];
        const rhs = opMatch[3];
        const rhsOff = Math.max(offsetHint, trimmed.indexOf(rhs, Math.max(0, offsetHint)));
        return (row) => {
          const lv = resolveOperand(lhs, row, offsetHint);
          const rv = resolveOperand(rhs, row, rhsOff);
          if (lv === null || lv === undefined || rv === null || rv === undefined) return false;
          const cmp = compareVals(lv, rv);
          switch (op) {
            case "=":
            case "==":
              return cmp === 0;
            case "!=":
            case "<>":
              return cmp !== 0;
            case "<":
              return cmp < 0;
            case "<=":
              return cmp <= 0;
            case ">":
              return cmp > 0;
            case ">=":
              return cmp >= 0;
            default:
              return false;
          }
        };
      }

      return () => true;
    };

    return compile(trimmed, 0);
  };

  const findTable = (name: string): TableData | undefined => {
    const clean = unquoteIdent(name).toLowerCase();
    return Object.values(db.tables).find((t) => t.name.toLowerCase() === clean);
  };

  const executeSingle = (
    rawSql: string,
    params: unknown[],
    cteTables: Map<string, Array<Record<string, unknown>>> = new Map(),
  ): { results: Array<Record<string, unknown>>; success: boolean; meta: Record<string, unknown> } => {
    const started = performance.now();
    let sql = rawSql.trim().replace(/;+\s*$/, "");
    if (!sql) {
      return { results: [] as Record<string, unknown>[], success: true, meta: { duration: 0, changes: 0, last_row_id: 0 } };
    }

    // Materialize CTEs: WITH cte1 [(cols)] AS (...), cte2 AS (...) SELECT ...
    if (/^WITH\s+/i.test(sql)) {
      const localCtes = new Map(cteTables);
      let idx = sql.replace(/^WITH\s+(?:RECURSIVE\s+)?/i, "");
      while (idx.length > 0) {
        const hdr = /^([A-Za-z_][A-Za-z0-9_]*|"[^"]+"|`[^`]+`)(?:\s*\(([^)]*)\))?\s+AS\s*\(/i.exec(idx);
        if (!hdr) break;
        const cteName = unquoteIdent(hdr[1]).toLowerCase();
        const colAliases = hdr[2] ? splitSqlList(hdr[2]).map((c) => unquoteIdent(c)) : null;
        let pos = hdr[0].length;
        let depth = 1;
        let inSingle = false;
        while (pos < idx.length && depth > 0) {
          const ch = idx[pos];
          if (ch === "'") {
            if (inSingle && idx[pos + 1] === "'") pos += 2;
            else { inSingle = !inSingle; pos++; }
            continue;
          }
          if (!inSingle) {
            if (ch === "(") depth++;
            else if (ch === ")") depth--;
          }
          pos++;
        }
        if (depth !== 0) break;
        const innerSql = idx.slice(hdr[0].length, pos - 1).trim();
        const cteRes = executeSingle(innerSql, params, localCtes);
        const materialized = colAliases
          ? cteRes.results.map((row) => {
              const vals = Object.values(row);
              const mapped: Record<string, unknown> = {};
              colAliases.forEach((alias, i) => { mapped[alias] = vals[i] ?? null; });
              return mapped;
            })
          : cteRes.results;
        localCtes.set(cteName, materialized);
        const rest = idx.slice(pos).trimStart();
        if (rest.startsWith(",")) {
          idx = rest.slice(1).trimStart();
        } else {
          sql = rest;
          break;
        }
      }
      if (sql !== rawSql.trim().replace(/;+\s*$/, "")) {
        return executeSingle(sql, params, localCtes);
      }
    }

    // PRAGMA table_info('tbl')
    const pragmaMatch = /^PRAGMA\s+table_info\s*\(\s*(.+?)\s*\)$/i.exec(sql);
    if (pragmaMatch) {
      const tName = pragmaMatch[1].replace(/^['"]|['"]$/g, "");
      const tbl = findTable(tName);
      const results = (tbl?.columns ?? []).map((c, idx) => ({
        cid: idx,
        name: c.name,
        type: c.type || "TEXT",
        notnull: c.notNull ? 1 : 0,
        dflt_value: c.defaultValue ?? null,
        pk: c.primaryKey ? 1 : 0,
      }));
      return { results, success: true, meta: { duration: performance.now() - started, changes: 0 } };
    }

    // CREATE TABLE [IF NOT EXISTS]
    const createMatch = /^CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([^\s(]+)\s*\(([\s\S]+)\)$/i.exec(sql);
    if (createMatch) {
      const tName = unquoteIdent(createMatch[1]);
      if (findTable(tName)) {
        return { results: [], success: true, meta: { duration: performance.now() - started, changes: 0 } };
      }
      const defs = splitSqlList(createMatch[2]);
      const cols: ColumnDef[] = [];
      for (const def of defs) {
        if (/^(PRIMARY\s+KEY|UNIQUE|FOREIGN\s+KEY|CONSTRAINT|CHECK)\b/i.test(def)) continue;
        const tok = def.match(/^("[^"]+"|`[^`]+`|\[[^\]]+\]|[^\s]+)\s*([\s\S]*)$/);
        if (!tok) continue;
        const cName = unquoteIdent(tok[1]);
        const rest = tok[2] ?? "";
        const cType = (rest.match(/^([A-Za-z]+)/)?.[1] ?? "TEXT").toUpperCase();
        const defaultMatch = /\bDEFAULT\s+('(?:''|[^'])*'|-?\d+(?:\.\d+)?|NULL|CURRENT_TIMESTAMP|TRUE|FALSE)/i.exec(rest);
        cols.push({
          name: cName,
          type: cType,
          primaryKey: /\bPRIMARY\s+KEY\b/i.test(rest),
          autoIncrement: /\bAUTOINCREMENT\b/i.test(rest),
          notNull: /\bNOT\s+NULL\b/i.test(rest),
          unique: /\bUNIQUE\b/i.test(rest),
          defaultValue: defaultMatch ? parseValueToken(defaultMatch[1], { params: [], nextParam: 0 }) : undefined,
        });
      }
      db.tables[tName] = { name: tName, sql, columns: cols, nextRowId: 1, rows: [] };
      flushDb();
      return { results: [], success: true, meta: { duration: performance.now() - started, changes: 0 } };
    }

    // DROP TABLE [IF EXISTS]
    const dropMatch = /^DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?([^\s;]+)$/i.exec(sql);
    if (dropMatch) {
      const t = findTable(dropMatch[1]);
      if (t) {
        delete db.tables[t.name];
        flushDb();
      }
      return { results: [], success: true, meta: { duration: performance.now() - started, changes: 0 } };
    }

    // ALTER TABLE tbl ADD [COLUMN] colDef
    const alterAddMatch = /^ALTER\s+TABLE\s+([^\s]+)\s+ADD\s+(?:COLUMN\s+)?(.+)$/i.exec(sql);
    if (alterAddMatch) {
      const tbl = findTable(alterAddMatch[1]);
      if (!tbl) throw new Error(`D1_ERROR: no such table: ${alterAddMatch[1]}`);
      const tok = alterAddMatch[2].trim().match(/^("[^"]+"|`[^`]+`|[^\s]+)\s*(.*)$/);
      if (tok) {
        const cName = unquoteIdent(tok[1]);
        const cType = (tok[2]?.match(/^([A-Za-z]+)/)?.[1] ?? "TEXT").toUpperCase();
        if (!tbl.columns.some((c) => c.name === cName)) {
          tbl.columns.push({ name: cName, type: cType });
          for (const r of tbl.rows) r[cName] = null;
          flushDb();
        }
      }
      return { results: [], success: true, meta: { duration: performance.now() - started, changes: 0 } };
    }

    // INSERT [OR REPLACE | OR IGNORE] INTO tbl [(cols)] VALUES (...), (...) [RETURNING ...]
    const insertMatch =
      /^INSERT\s+(?:OR\s+(REPLACE|IGNORE)\s+)?INTO\s+([^\s(]+)\s*(?:\(([^)]+)\)\s*)?VALUES\s*([\s\S]+?)(?:\s+RETURNING\s+([\s\S]+))?$/i.exec(
        sql,
      );
    if (insertMatch) {
      const conflictMode = insertMatch[1]?.toUpperCase();
      const tbl = findTable(insertMatch[2]);
      if (!tbl) throw new Error(`D1_ERROR: no such table: ${insertMatch[2]}`);
      const colNames = insertMatch[3]
        ? splitSqlList(insertMatch[3]).map(unquoteIdent)
        : tbl.columns.map((c) => c.name);
      const returningRaw = insertMatch[5]?.trim();

      // Split top-level (...) tuples in VALUES
      const tuplesRaw = insertMatch[4].trim();
      const tuples: string[] = [];
      let depth = 0;
      let startIdx = -1;
      let inStr = false;
      for (let i = 0; i < tuplesRaw.length; i++) {
        const ch = tuplesRaw[i];
        if (ch === "'") {
          if (inStr && tuplesRaw[i + 1] === "'") {
            i++;
            continue;
          }
          inStr = !inStr;
        } else if (!inStr) {
          if (ch === "(") {
            if (depth === 0) startIdx = i + 1;
            depth++;
          } else if (ch === ")") {
            depth--;
            if (depth === 0 && startIdx >= 0) {
              tuples.push(tuplesRaw.slice(startIdx, i));
              startIdx = -1;
            }
          }
        }
      }

      const cursor: ExprCursor = { params, nextParam: 0 };
      const insertedRows: Array<Record<string, unknown>> = [];
      let lastRowId = 0;
      let changes = 0;

      for (const tup of tuples) {
        const valTokens = splitSqlList(tup);
        const row: Record<string, unknown> = {};
        for (const c of tbl.columns) {
          if (c.defaultValue !== undefined) row[c.name] = c.defaultValue;
          else row[c.name] = null;
        }
        colNames.forEach((cn, idx) => {
          row[cn] = idx < valTokens.length ? parseValueToken(valTokens[idx], cursor) : null;
        });

        const pkCol = tbl.columns.find((c) => c.primaryKey);
        if (pkCol && (row[pkCol.name] === null || row[pkCol.name] === undefined)) {
          row[pkCol.name] = tbl.nextRowId++;
        } else if (pkCol && typeof row[pkCol.name] === "number") {
          tbl.nextRowId = Math.max(tbl.nextRowId, (row[pkCol.name] as number) + 1);
        }

        const existingIdx = pkCol
          ? tbl.rows.findIndex((r) => r[pkCol.name] === row[pkCol.name])
          : -1;
        if (existingIdx >= 0) {
          if (conflictMode === "IGNORE") continue;
          if (conflictMode === "REPLACE") {
            tbl.rows[existingIdx] = row;
          } else {
            throw new Error(`D1_ERROR: UNIQUE constraint failed: ${tbl.name}.${pkCol!.name}`);
          }
        } else {
          tbl.rows.push(row);
        }
        lastRowId = pkCol && typeof row[pkCol.name] === "number" ? (row[pkCol.name] as number) : tbl.rows.length;
        changes++;
        insertedRows.push({ ...row });
      }

      flushDb();
      const results = returningRaw
        ? insertedRows.map((r) => (returningRaw === "*" ? r : Object.fromEntries(splitSqlList(returningRaw).map((c) => { const k = unquoteIdent(c); return [k, r[k]]; }))))
        : [];
      return {
        results,
        success: true,
        meta: { duration: performance.now() - started, changes, last_row_id: lastRowId },
      };
    }

    // UPDATE tbl SET ... [WHERE ...] [RETURNING ...]
    const updateMatch =
      /^UPDATE\s+([^\s]+)\s+SET\s+([\s\S]+?)(?:\s+WHERE\s+([\s\S]+?))?(?:\s+RETURNING\s+([\s\S]+))?$/i.exec(sql);
    if (updateMatch) {
      const tbl = findTable(updateMatch[1]);
      if (!tbl) throw new Error(`D1_ERROR: no such table: ${updateMatch[1]}`);
      const setList = splitSqlList(updateMatch[2]);
      const cursor: ExprCursor = { params, nextParam: 0 };
      const assignments = setList.map((assign) => {
        const eqIdx = assign.indexOf("=");
        const col = unquoteIdent(assign.slice(0, eqIdx));
        const val = parseValueToken(assign.slice(eqIdx + 1), cursor);
        return { col, val };
      });
      const predicate = updateMatch[3] ? evalWhere(updateMatch[3], cursor) : () => true;
      const returningRaw = updateMatch[4]?.trim();
      const updatedRows: Array<Record<string, unknown>> = [];
      let changes = 0;

      for (const r of tbl.rows) {
        if (predicate(r)) {
          for (const a of assignments) r[a.col] = a.val;
          changes++;
          updatedRows.push({ ...r });
        }
      }
      flushDb();
      const results = returningRaw
        ? updatedRows.map((r) => (returningRaw === "*" ? r : Object.fromEntries(splitSqlList(returningRaw).map((c) => { const k = unquoteIdent(c); return [k, r[k]]; }))))
        : [];
      return { results, success: true, meta: { duration: performance.now() - started, changes } };
    }

    // DELETE FROM tbl [WHERE ...] [RETURNING ...]
    const deleteMatch =
      /^DELETE\s+FROM\s+([^\s]+)(?:\s+WHERE\s+([\s\S]+?))?(?:\s+RETURNING\s+([\s\S]+))?$/i.exec(sql);
    if (deleteMatch) {
      const tbl = findTable(deleteMatch[1]);
      if (!tbl) throw new Error(`D1_ERROR: no such table: ${deleteMatch[1]}`);
      const cursor: ExprCursor = { params, nextParam: 0 };
      const predicate = deleteMatch[2] ? evalWhere(deleteMatch[2], cursor) : () => true;
      const returningRaw = deleteMatch[3]?.trim();
      const kept: Array<Record<string, unknown>> = [];
      const removed: Array<Record<string, unknown>> = [];
      for (const r of tbl.rows) {
        if (predicate(r)) removed.push(r);
        else kept.push(r);
      }
      tbl.rows = kept;
      flushDb();
      const results = returningRaw
        ? removed.map((r) => (returningRaw === "*" ? { ...r } : Object.fromEntries(splitSqlList(returningRaw).map((c) => { const k = unquoteIdent(c); return [k, r[k]]; }))))
        : [];
      return { results, success: true, meta: { duration: performance.now() - started, changes: removed.length } };
    }

    // SELECT [DISTINCT] cols FROM tbl [WHERE ...] [GROUP BY ...] [ORDER BY ...] [LIMIT ...] [OFFSET ...]
    const selectMatch =
      /^SELECT\s+(?:(DISTINCT)\s+)?([\s\S]+?)\s+FROM\s+([^\s;]+)(?:\s+WHERE\s+([\s\S]+?))?(?:\s+GROUP\s+BY\s+([\s\S]+?))?(?:\s+ORDER\s+BY\s+([\s\S]+?))?(?:\s+LIMIT\s+([^\s]+))?(?:\s+OFFSET\s+([^\s]+))?$/i.exec(
        sql,
      );
    if (selectMatch) {
      const isDistinct = Boolean(selectMatch[1]);
      const colsExpr = selectMatch[2].trim();
      const tableName = unquoteIdent(selectMatch[3]);
      const cursor: ExprCursor = { params, nextParam: 0 };
      const predicate = selectMatch[4] ? evalWhere(selectMatch[4], cursor) : () => true;
      const groupByRaw = selectMatch[5]?.trim();
      const orderRaw = selectMatch[6]?.trim();
      const limitVal = selectMatch[7] ? Number(parseValueToken(selectMatch[7], cursor)) : undefined;
      const offsetVal = selectMatch[8] ? Number(parseValueToken(selectMatch[8], cursor)) : 0;

      let sourceRows: Array<Record<string, unknown>>;
      const cteHit = cteTables.get(tableName.toLowerCase());
      if (cteHit) {
        sourceRows = cteHit;
      } else if (tableName.toLowerCase() === "sqlite_master" || tableName.toLowerCase() === "sqlite_schema") {
        sourceRows = Object.values(db.tables).map((t) => ({
          type: "table",
          name: t.name,
          tbl_name: t.name,
          rootpage: 1,
          sql: t.sql,
        }));
      } else {
        const tbl = findTable(tableName);
        if (!tbl) throw new Error(`D1_ERROR: no such table: ${tableName}`);
        sourceRows = tbl.rows;
      }

      let rows = sourceRows.filter(predicate).map((r) => ({ ...r }));
      const selectParts = splitSqlList(colsExpr);

      const evalAggFn = (fn: string, arg: string, groupRows: Array<Record<string, unknown>>): unknown => {
        if (fn === "COUNT") {
          return arg === "*"
            ? groupRows.length
            : groupRows.filter((r) => r[unquoteIdent(arg)] !== null && r[unquoteIdent(arg)] !== undefined).length;
        }
        const vals = groupRows.map((r) => Number(r[unquoteIdent(arg)])).filter((n) => !Number.isNaN(n));
        if (vals.length === 0) return null;
        if (fn === "SUM") return vals.reduce((acc, x) => acc + x, 0);
        if (fn === "AVG") return vals.reduce((acc, x) => acc + x, 0) / vals.length;
        if (fn === "MIN") return Math.min(...vals);
        if (fn === "MAX") return Math.max(...vals);
        return null;
      };

      let alreadyProjected = false;
      if (groupByRaw) {
        const groupCols = splitSqlList(groupByRaw).map((g) => unquoteIdent(g));
        const buckets = new Map<string, Array<Record<string, unknown>>>();
        for (const r of rows) {
          const key = JSON.stringify(groupCols.map((c) => r[c] ?? null));
          const bucket = buckets.get(key);
          if (bucket) bucket.push(r);
          else buckets.set(key, [r]);
        }
        const aggRe = /^(COUNT|SUM|AVG|MIN|MAX)\s*\(\s*([^)]+)\s*\)(?:\s+AS\s+(.+))?$/i;
        rows = Array.from(buckets.values()).map((bucket) => {
          const first = bucket[0] ?? {};
          if (colsExpr === "*") return { ...first };
          const out: Record<string, unknown> = {};
          for (const part of selectParts) {
            const am = aggRe.exec(part);
            if (am) {
              const alias = am[3] ? unquoteIdent(am[3]) : part.replace(/\s+/g, "");
              out[alias] = evalAggFn(am[1].toUpperCase(), am[2].trim(), bucket);
            } else {
              const asMatch = /^(.+?)\s+AS\s+(.+)$/i.exec(part);
              const src = unquoteIdent(asMatch ? asMatch[1] : part);
              const alias = unquoteIdent(asMatch ? asMatch[2] : part);
              out[alias] = first[src] ?? null;
            }
          }
          return out;
        });
        alreadyProjected = true;
      }

      if (orderRaw) {
        const orderSpecs = splitSqlList(orderRaw).map((part) => {
          const m = /^(.+?)(?:\s+(ASC|DESC))?$/i.exec(part.trim())!;
          return { col: unquoteIdent(m[1]), desc: m[2]?.toUpperCase() === "DESC" };
        });
        rows.sort((a, b) => {
          for (const spec of orderSpecs) {
            const cmp = compareVals(a[spec.col], b[spec.col]);
            if (cmp !== 0) return spec.desc ? -cmp : cmp;
          }
          return 0;
        });
      }

      // Check aggregate functions (ungrouped single-row aggregate)
      const aggMatch =
        !groupByRaw && selectParts.length === 1
          ? /^(COUNT|SUM|AVG|MIN|MAX)\s*\(\s*([^)]+)\s*\)(?:\s+AS\s+(.+))?$/i.exec(selectParts[0])
          : null;
      if (aggMatch) {
        const fn = aggMatch[1].toUpperCase();
        const arg = aggMatch[2].trim();
        const alias = aggMatch[3] ? unquoteIdent(aggMatch[3]) : selectParts[0].replace(/\s+/g, "");
        return {
          results: [{ [alias]: evalAggFn(fn, arg, rows) }],
          success: true,
          meta: { duration: performance.now() - started, changes: 0 },
        };
      }

      if (offsetVal > 0 || limitVal !== undefined) {
        rows = rows.slice(offsetVal, limitVal !== undefined ? offsetVal + limitVal : undefined);
      }

      if (!alreadyProjected && colsExpr !== "*") {
        const projections = selectParts.map((part) => {
          const asMatch = /^(.+?)\s+AS\s+(.+)$/i.exec(part);
          if (asMatch) return { src: unquoteIdent(asMatch[1]), out: unquoteIdent(asMatch[2]) };
          const ident = unquoteIdent(part);
          return { src: ident, out: ident };
        });
        rows = rows.map((r) => {
          const out: Record<string, unknown> = {};
          for (const p of projections) out[p.out] = r[p.src] ?? null;
          return out;
        });
      }

      if (isDistinct) {
        const seen = new Set<string>();
        rows = rows.filter((r) => {
          const k = JSON.stringify(r);
          if (seen.has(k)) return false;
          seen.add(k);
          return true;
        });
      }

      return { results: rows, success: true, meta: { duration: performance.now() - started, changes: 0 } };
    }

    // Fallback for unsupported SQL (e.g. transaction BEGIN/COMMIT/PRAGMA)
    return { results: [], success: true, meta: { duration: performance.now() - started, changes: 0 } };
  };

  type PreparedStmt = {
    bind: (...params: unknown[]) => PreparedStmt;
    all: () => Promise<Record<string, unknown>>;
    first: (colName?: string) => Promise<unknown>;
    run: () => Promise<Record<string, unknown>>;
    raw: (opts?: { columnNames?: boolean }) => Promise<unknown[]>;
  };

  const makeStmt = (sql: string, bound: unknown[] = []): PreparedStmt => ({
    bind: (...params: unknown[]) => makeStmt(sql, params),
    all: async () => executeSingle(sql, bound),
    first: async (colName?: string) => {
      const res = executeSingle(sql, bound);
      const row = res.results[0] ?? null;
      if (!row) return null;
      return colName ? (row[colName] ?? null) : row;
    },
    run: async () => executeSingle(sql, bound),
    raw: async (opts?: { columnNames?: boolean }) => {
      const res = executeSingle(sql, bound);
      if (res.results.length === 0) return [];
      const cols = Object.keys(res.results[0]);
      const data = res.results.map((r) => cols.map((c) => r[c]));
      return opts?.columnNames ? [cols, ...data] : data;
    },
  });

  return {
    prepare: (sql: string) => makeStmt(sql),
    batch: async (stmts: PreparedStmt[]) => Promise.all(stmts.map((s) => s.all())),
    exec: async (multiSql: string) => {
      const started = performance.now();
      const parts = splitStatements(multiSql);
      for (const part of parts) executeSingle(part, []);
      return { count: parts.length, duration: performance.now() - started };
    },
  };
}

type LoadedWorkerModuleRef = {
  mod: Record<string, unknown> | null;
  env: Record<string, unknown>;
};

function createPersistentQueue(spec: BindingSpec, modRef: LoadedWorkerModuleRef) {
  const queueName = spec.queueName || spec.name;
  const qDir = stateDir ? `${stateDir}/queues` : "";
  const qFile = qDir ? `${qDir}/${safeId(queueName)}.json` : "";
  if (qDir) ensureDir(qDir);

  type QueuedMsg = { id: string; timestamp: string; body: unknown; contentType: string; attempts: number };

  const readMessages = (): QueuedMsg[] => {
    if (!qFile) return [];
    const parsed = readJsonFile<unknown>(qFile, []);
    return Array.isArray(parsed) ? (parsed as QueuedMsg[]) : [];
  };

  const appendMessages = async (items: Array<{ body: unknown; contentType?: string }>) => {
    const nowIso = new Date().toISOString();
    const added: QueuedMsg[] = items.map((it) => ({
      id: typeof crypto?.randomUUID === "function" ? crypto.randomUUID() : hashKeyHex(`${Date.now()}-${Math.random()}`),
      timestamp: nowIso,
      body: it.body,
      contentType: it.contentType || (typeof it.body === "string" ? "text" : "json"),
      attempts: 1,
    }));
    if (qFile) {
      const existing = readMessages();
      writeJsonFile(qFile, [...existing.slice(-200), ...added]);
    }

    const handler =
      (modRef.mod?.default as Record<string, unknown> | undefined)?.queue ??
      modRef.mod?.queue;
    if (typeof handler === "function") {
      const batch = {
        queue: queueName,
        messages: added.map((m) => ({
          id: m.id,
          timestamp: new Date(m.timestamp),
          body: m.body,
          attempts: m.attempts,
          ack: () => {},
          retry: () => {},
        })),
        ackAll: () => {},
        retryAll: () => {},
      };
      const ctx = makeExecutionContext();
      await (handler as (b: unknown, e: unknown, c: unknown) => Promise<unknown>)(batch, modRef.env, ctx);
      await ctx.flush();
    }
    return added;
  };

  return {
    send: async (body: unknown, opts?: { contentType?: string }) => {
      await appendMessages([{ body, contentType: opts?.contentType }]);
    },
    sendBatch: async (messages: Iterable<{ body: unknown; contentType?: string }>) => {
      await appendMessages(Array.from(messages));
    },
  };
}

function createPersistentDurableObjectNamespace(spec: BindingSpec, modRef: LoadedWorkerModuleRef) {
  const nsDir = stateDir ? `${stateDir}/do/${safeId(spec.className || spec.name)}` : "";
  if (nsDir) ensureDir(nsDir);

  const createDoStorage = (idStr: string) => {
    const file = nsDir ? `${nsDir}/${safeId(idStr)}.json` : "";
    let data: Record<string, unknown> = {};
    let alarmAtMs: number | null = null;
    if (file) {
      const parsed = readJsonFile<{ data?: Record<string, unknown>; alarmAtMs?: number } | null>(file, null);
      if (parsed && typeof parsed === "object") {
        data = parsed.data && typeof parsed.data === "object" ? parsed.data : {};
        alarmAtMs = typeof parsed.alarmAtMs === "number" ? parsed.alarmAtMs : null;
      }
    }
    const flush = () => {
      if (file) writeJsonFile(file, { data, alarmAtMs });
    };

    const storage = {
      get: async (keyOrKeys: string | string[]) => {
        if (Array.isArray(keyOrKeys)) {
          const out = new Map<string, unknown>();
          for (const k of keyOrKeys) {
            if (k in data) out.set(k, data[k]);
          }
          return out;
        }
        return keyOrKeys in data ? data[keyOrKeys] : undefined;
      },
      put: async (keyOrEntries: string | Record<string, unknown>, value?: unknown) => {
        if (typeof keyOrEntries === "string") {
          data[keyOrEntries] = value;
        } else if (keyOrEntries && typeof keyOrEntries === "object") {
          Object.assign(data, keyOrEntries);
        }
        flush();
      },
      delete: async (keyOrKeys: string | string[]) => {
        if (Array.isArray(keyOrKeys)) {
          let count = 0;
          for (const k of keyOrKeys) {
            if (k in data) { delete data[k]; count++; }
          }
          if (count > 0) flush();
          return count;
        }
        const existed = keyOrKeys in data;
        if (existed) {
          delete data[keyOrKeys];
          flush();
        }
        return existed;
      },
      deleteAll: async () => {
        data = {};
        alarmAtMs = null;
        flush();
      },
      list: async (opts?: { prefix?: string; limit?: number; reverse?: boolean }) => {
        const prefix = opts?.prefix ?? "";
        let keys = Object.keys(data).filter((k) => k.startsWith(prefix)).sort();
        if (opts?.reverse) keys.reverse();
        if (opts?.limit !== undefined) keys = keys.slice(0, Math.max(0, opts.limit));
        const map = new Map<string, unknown>();
        for (const k of keys) map.set(k, data[k]);
        return map;
      },
      getAlarm: async () => alarmAtMs,
      setAlarm: async (scheduledTime: number | Date) => {
        alarmAtMs = scheduledTime instanceof Date ? scheduledTime.getTime() : Number(scheduledTime);
        flush();
      },
      deleteAlarm: async () => {
        alarmAtMs = null;
        flush();
      },
      transaction: async <T>(closure: (txn: unknown) => Promise<T>) => closure(storage),
      sync: async () => { flush(); },
    };
    return storage;
  };

  const instances = new Map<string, unknown>();
  const makeIdObj = (hex: string, name?: string) => ({
    toString: () => hex,
    equals: (other: { toString(): string }) => other?.toString() === hex,
    name,
  });

  return {
    idFromName: (name: string) => makeIdObj(hashKeyHex(`${spec.name}:${name}`), name),
    idFromString: (id: string) => makeIdObj(String(id)),
    newUniqueId: () => makeIdObj(hashKeyHex(`${spec.name}:${Date.now()}:${Math.random()}`)),
    get: (id: { toString(): string; name?: string } | string) => {
      const idStr = typeof id === "string" ? id : id.toString();
      const idObj = typeof id === "string" ? makeIdObj(idStr) : id;
      return {
        id: idObj,
        name: typeof id === "string" ? undefined : id.name,
        fetch: async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
          const req = input instanceof Request ? input : new Request(typeof input === "string" ? input : input.toString(), init);
          const className = spec.className;
          const DoCtor = className ? modRef.mod?.[className] : undefined;
          if (typeof DoCtor === "function") {
            let inst = instances.get(idStr) as { fetch?: (r: Request) => Promise<Response> | Response } | undefined;
            if (!inst) {
              const state = {
                id: idObj,
                storage: createDoStorage(idStr),
                waitUntil: (p: Promise<unknown>) => { void Promise.resolve(p); },
                blockConcurrencyWhile: async <T>(fn: () => Promise<T>) => fn(),
                acceptWebSocket: () => {},
                getWebSockets: () => [],
              };
              inst = new (DoCtor as new (s: unknown, e: unknown) => { fetch?: (r: Request) => Promise<Response> | Response })(state, modRef.env);
              instances.set(idStr, inst);
            }
            if (typeof inst.fetch === "function") {
              return await inst.fetch(req);
            }
          }
          return Response.json(
            {
              localDurableObject: spec.name,
              className: className ?? null,
              id: idStr,
              note: className
                ? `Exported Durable Object class '${className}' was not found on module; using fallback stub.`
                : "No class_name configured for Durable Object binding.",
            },
            { status: 501 },
          );
        },
      };
    },
  };
}

function createServiceBindingStub(spec: BindingSpec, modRef: LoadedWorkerModuleRef) {
  return {
    fetch: async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const req = input instanceof Request ? input : new Request(typeof input === "string" ? input : input.toString(), init);
      const localFetch = (modRef.mod?.default as Record<string, unknown> | undefined)?.fetch ?? modRef.mod?.fetch;
      if (spec.service && spec.service === modRef.env.__EDGECODE_SCRIPT_NAME && typeof localFetch === "function") {
        const ctx = makeExecutionContext();
        const res = await (localFetch as (r: Request, e: unknown, c: unknown) => Promise<Response>)(req, modRef.env, ctx);
        await ctx.flush();
        return res;
      }
      return Response.json(
        {
          error: "SERVICE_BINDING_UNAVAILABLE_LOCALLY",
          binding: spec.name,
          targetService: spec.service ?? null,
          environment: spec.environment ?? null,
          url: req.url,
        },
        { status: 501 },
      );
    },
  };
}

function createEnv(bindings: BindingSpec[], modRef: LoadedWorkerModuleRef): Record<string, unknown> {
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
      env[b.name] = createPersistentKv(b.name);
    } else if (b.type === "r2_bucket") {
      env[b.name] = createPersistentR2(b.name);
    } else if (b.type === "d1") {
      env[b.name] = createPersistentD1(b.name);
    } else if (b.type === "queue") {
      env[b.name] = createPersistentQueue(b, modRef);
    } else if (b.type === "durable_object_namespace") {
      env[b.name] = createPersistentDurableObjectNamespace(b, modRef);
    } else if (b.type === "service") {
      env[b.name] = createServiceBindingStub(b, modRef);
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

function attachCloudflareProperties(request: Request): Request {
  if (!("cf" in request)) {
    const cfMeta = Object.freeze({
      colo: "SIN",
      country: "BD",
      city: "Dhaka",
      continent: "AS",
      timezone: "Asia/Dhaka",
      asn: 13335,
      asOrganization: "Cloudflare, Inc.",
      httpProtocol: "HTTP/2",
      tlsVersion: "TLSv1.3",
      tlsCipher: "AEAD-AES128-GCM-SHA256",
      clientTcpRtt: 12,
      botManagement: { score: 99, verifiedBot: false },
    });
    try {
      Object.defineProperty(request, "cf", {
        value: cfMeta,
        enumerable: true,
        configurable: true,
      });
    } catch {
      // Ignore if Request instance is sealed
    }
  }
  return request;
}

function ensureCachesApi() {
  type CacheEntry = { response: Response; expiresAtMs: number | null };
  const storeMap = new Map<string, Map<string, CacheEntry>>();

  const makeCacheBucket = (bucketName: string) => {
    let entries = storeMap.get(bucketName);
    if (!entries) {
      entries = new Map();
      storeMap.set(bucketName, entries);
    }
    const resolveKey = (req: Request | string): string => {
      const rawUrl = typeof req === "string" ? req : req.url;
      const hashIdx = rawUrl.indexOf("#");
      return hashIdx >= 0 ? rawUrl.slice(0, hashIdx) : rawUrl;
    };
    return {
      match: async (req: Request | string, opts?: { ignoreMethod?: boolean }) => {
        if (typeof req !== "string" && !opts?.ignoreMethod && req.method !== "GET" && req.method !== "HEAD") {
          return undefined;
        }
        const key = resolveKey(req);
        const rec = entries!.get(key);
        if (!rec) return undefined;
        if (rec.expiresAtMs !== null && Date.now() >= rec.expiresAtMs) {
          entries!.delete(key);
          return undefined;
        }
        return rec.response.clone();
      },
      put: async (req: Request | string, res: Response) => {
        const cc = (res.headers.get("cache-control") ?? "").toLowerCase();
        if (cc.includes("no-store")) return;
        let expiresAtMs: number | null = null;
        const maxAge = /(?:s-maxage|max-age)=(\d+)/.exec(cc);
        if (maxAge) {
          const sec = Number(maxAge[1]);
          if (sec <= 0) return;
          expiresAtMs = Date.now() + sec * 1000;
        }
        entries!.set(resolveKey(req), { response: res.clone(), expiresAtMs });
      },
      delete: async (req: Request | string, opts?: { ignoreMethod?: boolean }) => {
        if (typeof req !== "string" && !opts?.ignoreMethod && req.method !== "GET" && req.method !== "HEAD") {
          return false;
        }
        return entries!.delete(resolveKey(req));
      },
    };
  };

  const defaultBucket = makeCacheBucket("default");
  const g = globalThis as Record<string, unknown>;
  const existing = g.caches as Record<string, unknown> | undefined;

  if (!existing) {
    g.caches = {
      default: defaultBucket,
      open: async (name: string) => makeCacheBucket(String(name || "default")),
    };
    return;
  }

  if (typeof existing.default === "undefined") {
    try {
      Object.defineProperty(existing, "default", {
        value: defaultBucket,
        configurable: true,
        writable: true,
      });
    } catch {
      const wrapped = Object.assign(Object.create(Object.getPrototypeOf(existing) || Object.prototype), {
        default: defaultBucket,
        open: typeof existing.open === "function"
          ? existing.open.bind(existing)
          : async (name: string) => makeCacheBucket(String(name || "default")),
        match: typeof existing.match === "function" ? existing.match.bind(existing) : defaultBucket.match,
        delete: typeof existing.delete === "function" ? existing.delete.bind(existing) : defaultBucket.delete,
      });
      try {
        Object.defineProperty(g, "caches", { value: wrapped, configurable: true, writable: true });
      } catch {
        g.caches = wrapped;
      }
    }
  }
}

// Lightweight Cloudflare HTMLRewriter shim for local Worker execution
function installHtmlRewriterIfMissing() {
  const g = globalThis as Record<string, unknown>;
  if (typeof g.HTMLRewriter !== "undefined") return;

  type ElementHandler = {
    element?: (el: unknown) => void | Promise<void>;
    text?: (chunk: { text: string; lastInTextNode: boolean }) => void | Promise<void>;
  };

  const parseAttrs = (attrText: string): Array<[string, string | null]> => {
    const out: Array<[string, string | null]> = [];
    const re = /([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(attrText)) !== null) {
      out.push([m[1], m[2] ?? m[3] ?? m[4] ?? null]);
    }
    return out;
  };

  const matchesSelector = (selector: string, tag: string, attrs: Array<[string, string | null]>): boolean => {
    return selector.split(",").some((part) => {
      const s = part.trim();
      if (!s) return false;
      const simple = s.split(/\s+/).pop()!;
      const tagMatch = /^([a-zA-Z0-9_-]+)/.exec(simple);
      if (tagMatch && tagMatch[1] !== "*" && tagMatch[1].toLowerCase() !== tag.toLowerCase()) return false;
      const idMatch = /#([a-zA-Z0-9_-]+)/.exec(simple);
      if (idMatch) {
        const idAttr = attrs.find(([k]) => k.toLowerCase() === "id")?.[1];
        if (idAttr !== idMatch[1]) return false;
      }
      const classMatches = [...simple.matchAll(/\.([a-zA-Z0-9_-]+)/g)];
      if (classMatches.length > 0) {
        const clsList = (attrs.find(([k]) => k.toLowerCase() === "class")?.[1] ?? "").split(/\s+/);
        for (const cm of classMatches) {
          if (!clsList.includes(cm[1])) return false;
        }
      }
      const attrMatches = [...simple.matchAll(/\[([^\]=]+)(?:=(?:"([^"]*)"|'([^']*)'|([^\]]*)))?\]/g)];
      for (const am of attrMatches) {
        const found = attrs.find(([k]) => k.toLowerCase() === am[1].toLowerCase());
        if (!found) return false;
        const expected = am[2] ?? am[3] ?? am[4];
        if (expected !== undefined && found[1] !== expected) return false;
      }
      return true;
    });
  };

  g.HTMLRewriter = class HTMLRewriter {
    private handlers: Array<{ selector: string; handler: ElementHandler }> = [];

    on(selector: string, handler: ElementHandler) {
      this.handlers.push({ selector, handler });
      return this;
    }

    onDocument(_handler: unknown) {
      return this;
    }

    transform(response: Response): Response {
      const handlers = this.handlers.slice();
      const headers = new Headers(response.headers);
      headers.delete("content-length");
      const transformedPromise = response.text().then(async (html) => {
        if (handlers.length === 0) return html;
        let result = html;
        for (const { selector, handler } of handlers) {
          if (!handler.element) continue;
          const tagRe = /<([a-zA-Z][a-zA-Z0-9:-]*)(\s+[^<>]*?)?(\/?)>/g;
          let rebuilt = "";
          let lastIdx = 0;
          let m: RegExpExecArray | null;
          while ((m = tagRe.exec(result)) !== null) {
            const tagName = m[1];
            const attrs = parseAttrs(m[2] ?? "");
            if (!matchesSelector(selector, tagName, attrs)) continue;
            rebuilt += result.slice(lastIdx, m.index);
            let beforeHtml = "";
            let afterHtml = "";
            let prependHtml = "";
            let appendHtml = "";
            let innerReplacement: string | null = null;
            let removed = false;
            const attrMap = new Map<string, string | null>(attrs);
            const el = {
              tagName: tagName.toLowerCase(),
              removed: false,
              getAttribute: (name: string) => attrMap.get(name) ?? null,
              hasAttribute: (name: string) => attrMap.has(name),
              setAttribute: (name: string, val: string) => {
                attrMap.set(name, String(val));
                return el;
              },
              removeAttribute: (name: string) => {
                attrMap.delete(name);
                return el;
              },
              before: (c: string) => {
                beforeHtml += c;
                return el;
              },
              after: (c: string) => {
                afterHtml += c;
                return el;
              },
              prepend: (c: string) => {
                prependHtml += c;
                return el;
              },
              append: (c: string) => {
                appendHtml += c;
                return el;
              },
              setInnerContent: (c: string) => {
                innerReplacement = c;
                return el;
              },
              remove: () => {
                removed = true;
                el.removed = true;
                return el;
              },
              onEndTag: (_cb: unknown) => {},
            };
            await handler.element(el);
            const selfClosing = m[3] === "/" || /^(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)$/i.test(tagName);
            const attrStr = Array.from(attrMap.entries())
              .map(([k, v]) => (v === null ? k : `${k}="${String(v).replace(/"/g, "&quot;")}"`))
              .join(" ");
            const openTag = `<${tagName}${attrStr ? " " + attrStr : ""}${selfClosing ? " /" : ""}>`;
            if (removed) {
              if (!selfClosing) {
                const closeTag = `</${tagName}>`;
                const closeIdx = result.toLowerCase().indexOf(closeTag.toLowerCase(), tagRe.lastIndex);
                if (closeIdx >= 0) tagRe.lastIndex = closeIdx + closeTag.length;
              }
              rebuilt += beforeHtml + afterHtml;
            } else if (!selfClosing && innerReplacement !== null) {
              const closeTag = `</${tagName}>`;
              const closeIdx = result.toLowerCase().indexOf(closeTag.toLowerCase(), tagRe.lastIndex);
              if (closeIdx >= 0) {
                tagRe.lastIndex = closeIdx + closeTag.length;
                rebuilt += `${beforeHtml}${openTag}${prependHtml}${innerReplacement}${appendHtml}${closeTag}${afterHtml}`;
              } else {
                rebuilt += `${beforeHtml}${openTag}${prependHtml}${innerReplacement}${appendHtml}${afterHtml}`;
              }
            } else {
              rebuilt += `${beforeHtml}${openTag}${prependHtml}${appendHtml}${afterHtml}`;
            }
            lastIdx = tagRe.lastIndex;
          }
          rebuilt += result.slice(lastIdx);
          result = rebuilt;
        }
        return result;
      });
      return new Response(
        new ReadableStream({
          async start(controller) {
            try {
              const htmlOut = await transformedPromise;
              controller.enqueue(new TextEncoder().encode(htmlOut));
              controller.close();
            } catch (err) {
              controller.error(err);
            }
          },
        }),
        { status: response.status, statusText: response.statusText, headers },
      );
    }
  };
}

function installWebSocketPairIfMissing() {
  const g = globalThis as Record<string, unknown>;
  if (typeof g.WebSocketPair !== "undefined") return;
  g.WebSocketPair = class WebSocketPair {
    0: Record<string, unknown>;
    1: Record<string, unknown>;
    constructor() {
      const makeHalf = () => {
        const listeners = new Map<string, Array<(ev: unknown) => void>>();
        return {
          accept() {},
          close() {},
          send(_msg: unknown) {},
          addEventListener(type: string, cb: (ev: unknown) => void) {
            const list = listeners.get(type) ?? [];
            list.push(cb);
            listeners.set(type, list);
          },
        };
      };
      this[0] = makeHalf();
      this[1] = makeHalf();
    }
  };
}

let fetchListener: FetchListener | null = null;
let scheduledListener: ScheduledListener | null = null;
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
  if (type === "scheduled" && typeof listener === "function") {
    scheduledListener = listener as unknown as ScheduledListener;
    return;
  }
  if (origAddEventListener) {
    origAddEventListener(type, listener, options);
  }
};

ensureCachesApi();
installHtmlRewriterIfMissing();
installWebSocketPairIfMissing();

async function main() {
  let parsedBindings: BindingSpec[] = [];
  try {
    parsedBindings = JSON.parse(bindingsRaw);
  } catch {
    parsedBindings = [];
  }
  const modRef: LoadedWorkerModuleRef = { mod: null, env: {} };
  const env = createEnv(parsedBindings, modRef);
  modRef.env = env;

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
  modRef.mod = (mod && typeof mod === "object" ? mod : null) as Record<string, unknown> | null;

  let workerFetch:
    | ((req: Request, env: Record<string, unknown>, ctx: ReturnType<typeof makeExecutionContext>) => Response | Promise<Response>)
    | null = null;
  let workerScheduled:
    | ((ctrl: ScheduledController, env: Record<string, unknown>, ctx: ReturnType<typeof makeExecutionContext>) => unknown)
    | null = null;
  let workerQueue:
    | ((batch: unknown, env: Record<string, unknown>, ctx: ReturnType<typeof makeExecutionContext>) => unknown)
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

  if (mod?.default && typeof mod.default.scheduled === "function") {
    workerScheduled = mod.default.scheduled.bind(mod.default);
  } else if (typeof mod?.scheduled === "function") {
    workerScheduled = mod.scheduled.bind(mod);
  }

  if (mod?.default && typeof mod.default.queue === "function") {
    workerQueue = mod.default.queue.bind(mod.default);
  } else if (typeof mod?.queue === "function") {
    workerQueue = mod.queue.bind(mod);
  }

  const handleInternalTrigger = async (request: Request): Promise<Response | null> => {
    const url = new URL(request.url);
    if (url.pathname === "/__edgecode/queue") {
      if (!workerQueue) {
        return Response.json({ ok: false, error: "Worker does not export a queue() consumer handler" }, { status: 404 });
      }
      const ctx = makeExecutionContext();
      try {
        let payload: { queue?: string; messages?: Array<{ id?: string; body?: unknown; attempts?: number }> } = {};
        if (request.method === "POST") {
          const text = await request.text();
          if (text.trim().length > 0) {
            try {
              payload = JSON.parse(text);
            } catch {
              payload = { messages: [{ body: text }] };
            }
          }
        }
        const queueName = payload.queue || url.searchParams.get("queue") || "default";
        const rawMessages =
          Array.isArray(payload.messages) && payload.messages.length > 0
            ? payload.messages
            : [{ body: url.searchParams.get("body") ?? { triggeredAt: new Date().toISOString() } }];
        const batch = {
          queue: queueName,
          messages: rawMessages.map((m, idx) => ({
            id: m.id ?? `msg-${Date.now()}-${idx}`,
            timestamp: new Date(),
            body: m.body,
            attempts: m.attempts ?? 1,
            ack: () => {},
            retry: () => {},
          })),
          ackAll: () => {},
          retryAll: () => {},
        };
        await workerQueue(batch, env, ctx);
        await ctx.flush();
        return Response.json({ ok: true, queue: queueName, processed: batch.messages.length });
      } catch (err) {
        return Response.json(
          { ok: false, error: err instanceof Error ? (err.stack ?? err.message) : String(err) },
          { status: 500 },
        );
      }
    }

    if (url.pathname !== "/__edgecode/cron") return null;
    const cronExpr = url.searchParams.get("cron") ?? "* * * * *";
    const scheduledTime = Date.now();
    const ctx = makeExecutionContext();
    try {
      if (workerScheduled) {
        await workerScheduled({ cron: cronExpr, scheduledTime, noRetry() {} }, env, ctx);
        await ctx.flush();
        return Response.json({ ok: true, cron: cronExpr, scheduledTime });
      }
      if (scheduledListener) {
        scheduledListener({ type: "scheduled", cron: cronExpr, scheduledTime, waitUntil: ctx.waitUntil, noRetry() {} });
        await ctx.flush();
        return Response.json({ ok: true, cron: cronExpr, scheduledTime });
      }
      return Response.json({ ok: false, error: "Worker does not export a scheduled() handler" }, { status: 404 });
    } catch (err) {
      return Response.json(
        { ok: false, error: err instanceof Error ? (err.stack ?? err.message) : String(err) },
        { status: 500 },
      );
    }
  };

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
        const triggerResp = await handleInternalTrigger(request);
        if (triggerResp) return triggerResp;
        const ctx = makeExecutionContext();
        try {
          const cfRequest = attachCloudflareProperties(request);
          const response = await handler(cfRequest, env, ctx);
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
        const triggerResp = await handleInternalTrigger(request);
        if (triggerResp) return triggerResp;
        const ctx = makeExecutionContext();
        let responded: Response | Promise<Response> | null = null;
        try {
          listener({
            type: "fetch",
            request: attachCloudflareProperties(request),
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
