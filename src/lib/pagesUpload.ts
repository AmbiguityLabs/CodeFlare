import JSZip from 'jszip';
import { blake3Hex } from './blake3';

export interface StagedPageAsset {
  webPath: string; // e.g., "/index.html"
  hash: string; // 32-char BLAKE3 hex
  contentType: string;
  base64: string;
  sizeBytes: number;
}

const MIME_MAP: Record<string, string> = {
  html: 'text/html; charset=utf-8',
  htm: 'text/html; charset=utf-8',
  css: 'text/css; charset=utf-8',
  js: 'application/javascript; charset=utf-8',
  mjs: 'application/javascript; charset=utf-8',
  json: 'application/json; charset=utf-8',
  svg: 'image/svg+xml',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  webp: 'image/webp',
  gif: 'image/gif',
  ico: 'image/x-icon',
  woff: 'font/woff',
  woff2: 'font/woff2',
  ttf: 'font/ttf',
  txt: 'text/plain; charset=utf-8',
  xml: 'application/xml',
  wasm: 'application/wasm',
  map: 'application/json',
};

export function guessMimeType(filePath: string): string {
  const ext = filePath.split('.').pop()?.toLowerCase() || '';
  return MIME_MAP[ext] || 'application/octet-stream';
}

export function normalizePageAssetPath(rawPath: string): string {
  let clean = rawPath.replace(/\\/g, '/').replace(/^\/+/, '');
  if (!clean) clean = 'index.html';
  return '/' + clean;
}

export function uint8ToBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    const slice = bytes.subarray(i, i + chunk);
    binary += String.fromCharCode(...slice);
  }
  return btoa(binary);
}

/**
 * Exact Wrangler asset hash formula:
 * blake3(utf8(base64(bytes) + extensionWithoutDot)).hex().slice(0, 32)
 */
export function computePagesAssetHash(bytes: Uint8Array, filePath: string): { hash: string; base64: string } {
  const base64 = uint8ToBase64(bytes);
  const dotIdx = filePath.lastIndexOf('.');
  const slashIdx = filePath.lastIndexOf('/');
  const ext = dotIdx > slashIdx && dotIdx >= 0 ? filePath.slice(dotIdx + 1).toLowerCase() : '';
  const payload = new TextEncoder().encode(base64 + ext);
  const hash = blake3Hex(payload).slice(0, 32);
  return { hash, base64 };
}

export function stagePageAsset(rawPath: string, bytes: Uint8Array): StagedPageAsset {
  const webPath = normalizePageAssetPath(rawPath);
  const { hash, base64 } = computePagesAssetHash(bytes, webPath);
  return {
    webPath,
    hash,
    contentType: guessMimeType(webPath),
    base64,
    sizeBytes: bytes.byteLength,
  };
}

export async function stageFilesFromFileList(files: FileList | File[]): Promise<StagedPageAsset[]> {
  const list = Array.from(files);
  const rawPaths = list.map((f) => (f as File & { webkitRelativePath?: string }).webkitRelativePath || f.name);
  // Strip common top folder if directory upload has a single root folder
  let stripPrefix = '';
  if (rawPaths.length > 0 && rawPaths[0].includes('/')) {
    const firstRoot = rawPaths[0].split('/')[0] + '/';
    if (rawPaths.every((p) => p.startsWith(firstRoot))) {
      stripPrefix = firstRoot;
    }
  }

  const results: StagedPageAsset[] = [];
  for (let i = 0; i < list.length; i++) {
    const f = list[i];
    let rel = rawPaths[i];
    if (stripPrefix && rel.startsWith(stripPrefix)) {
      rel = rel.slice(stripPrefix.length);
    }
    if (!rel || rel.startsWith('.git/') || rel.includes('/.git/')) continue;
    const buf = new Uint8Array(await f.arrayBuffer());
    results.push(stagePageAsset(rel, buf));
  }
  return results;
}

export async function stageFilesFromZip(zipFile: File): Promise<StagedPageAsset[]> {
  const zip = await JSZip.loadAsync(await zipFile.arrayBuffer());
  const entries = Object.values(zip.files).filter((e) => !e.dir);
  const names = entries.map((e) => e.name.replace(/^\/+/, ''));

  let stripPrefix = '';
  if (names.length > 0 && names[0].includes('/')) {
    const firstRoot = names[0].split('/')[0] + '/';
    if (names.every((n) => n.startsWith(firstRoot))) {
      stripPrefix = firstRoot;
    }
  }

  const staged: StagedPageAsset[] = [];
  for (const entry of entries) {
    let rel = entry.name.replace(/^\/+/, '');
    if (stripPrefix && rel.startsWith(stripPrefix)) {
      rel = rel.slice(stripPrefix.length);
    }
    if (!rel || rel.startsWith('__MACOSX') || rel.startsWith('.git/')) continue;
    const bytes = await entry.async('uint8array');
    staged.push(stagePageAsset(rel, bytes));
  }
  return staged;
}

export function stageFromWorkspaceFiles(files: Record<string, string>): StagedPageAsset[] {
  const enc = new TextEncoder();
  return Object.entries(files)
    .filter(([p]) => !p.endsWith('wrangler.jsonc') && !p.endsWith('wrangler.toml'))
    .map(([p, content]) => stagePageAsset(p, enc.encode(content)));
}
