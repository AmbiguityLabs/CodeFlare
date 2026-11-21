import type {
  ApiEnvironmentProfile,
  ApiHistoryItem,
  ApiRequestSpec,
  CloudflareCredentials,
  EditorSettings,
  FileHistorySnapshot,
  GitHubCredentials,
  LocalWorkspace,
} from '../types/ide';
import { DEFAULT_EDITOR_SETTINGS } from './theme';

const KEYS = {
  CF_CREDS: 'codeflare.cf.credentials.v1',
  GH_CREDS: 'codeflare.gh.credentials.v1',
  SETTINGS: 'codeflare.editor.settings.v1',
  LOCAL_WORKSPACES: 'codeflare.local.workspaces.v1',
  RECENT_PROJECTS: 'codeflare.recent.projects.v1',
  PINNED_PROJECTS: 'codeflare.pinned.projects.v1',
  AUTOSAVE_DRAFTS: 'codeflare.autosave.drafts.v1',
  FILE_HISTORY: 'codeflare.file.history.v1',
  API_PROFILES: 'codeflare.api.profiles.v1',
  API_SAVED_REQUESTS: 'codeflare.api.saved_requests.v1',
  API_HISTORY: 'codeflare.api.history.v1',
  GH_REPO_LINKS: 'codeflare.gh.repo_links.v1',
  SNIPPETS: 'codeflare.custom.snippets.v1',
};

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function writeJson(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // ignore storage quota overflow cleanly
  }
}

export function loadCfCredentials(): CloudflareCredentials {
  return readJson<CloudflareCredentials>(KEYS.CF_CREDS, { mode: 'NONE' });
}

export function saveCfCredentials(creds: CloudflareCredentials): void {
  writeJson(KEYS.CF_CREDS, creds);
}

export function clearCfCredentials(): void {
  localStorage.removeItem(KEYS.CF_CREDS);
}

export function loadGhCredentials(): GitHubCredentials | null {
  return readJson<GitHubCredentials | null>(KEYS.GH_CREDS, null);
}

export function saveGhCredentials(creds: GitHubCredentials | null): void {
  if (!creds) {
    localStorage.removeItem(KEYS.GH_CREDS);
  } else {
    writeJson(KEYS.GH_CREDS, creds);
  }
}

export function loadEditorSettings(): EditorSettings {
  const stored = readJson<Partial<EditorSettings>>(KEYS.SETTINGS, {});
  return { ...DEFAULT_EDITOR_SETTINGS, ...stored };
}

export function saveEditorSettings(settings: EditorSettings): void {
  writeJson(KEYS.SETTINGS, settings);
}

export function loadLocalWorkspaces(): LocalWorkspace[] {
  return readJson<LocalWorkspace[]>(KEYS.LOCAL_WORKSPACES, []);
}

export function saveLocalWorkspaces(workspaces: LocalWorkspace[]): void {
  writeJson(KEYS.LOCAL_WORKSPACES, workspaces);
}

export interface RecentProjectItem {
  key: string; // cloud:<scriptId> or local:<workspaceId>
  kind: 'cloud' | 'local';
  id: string;
  name: string;
  openedAt: number;
  lastFile?: string;
}

export function loadRecentProjects(): RecentProjectItem[] {
  return readJson<RecentProjectItem[]>(KEYS.RECENT_PROJECTS, []);
}

export function recordRecentProject(item: RecentProjectItem): RecentProjectItem[] {
  const existing = loadRecentProjects().filter((p) => p.key !== item.key);
  const next = [item, ...existing].slice(0, 12);
  writeJson(KEYS.RECENT_PROJECTS, next);
  return next;
}

export function loadPinnedProjects(): string[] {
  return readJson<string[]>(KEYS.PINNED_PROJECTS, []);
}

export function togglePinnedProject(key: string): string[] {
  const existing = loadPinnedProjects();
  const next = existing.includes(key)
    ? existing.filter((k) => k !== key)
    : [key, ...existing];
  writeJson(KEYS.PINNED_PROJECTS, next);
  return next;
}

export interface AutoSaveDraft {
  workspaceKey: string;
  files: Record<string, string>;
  savedAt: number;
}

export function loadAutoSaveDraft(workspaceKey: string): AutoSaveDraft | null {
  const map = readJson<Record<string, AutoSaveDraft>>(KEYS.AUTOSAVE_DRAFTS, {});
  return map[workspaceKey] || null;
}

export function saveAutoSaveDraft(workspaceKey: string, files: Record<string, string>): void {
  const map = readJson<Record<string, AutoSaveDraft>>(KEYS.AUTOSAVE_DRAFTS, {});
  map[workspaceKey] = { workspaceKey, files, savedAt: Date.now() };
  writeJson(KEYS.AUTOSAVE_DRAFTS, map);
}

export function clearAutoSaveDraft(workspaceKey: string): void {
  const map = readJson<Record<string, AutoSaveDraft>>(KEYS.AUTOSAVE_DRAFTS, {});
  delete map[workspaceKey];
  writeJson(KEYS.AUTOSAVE_DRAFTS, map);
}

export function loadFileHistory(workspaceKey: string, filePath?: string): FileHistorySnapshot[] {
  const all = readJson<FileHistorySnapshot[]>(KEYS.FILE_HISTORY, []);
  return all.filter(
    (s) => s.workspaceKey === workspaceKey && (!filePath || s.filePath === filePath),
  );
}

export function appendFileHistorySnapshot(snapshot: FileHistorySnapshot): void {
  const all = readJson<FileHistorySnapshot[]>(KEYS.FILE_HISTORY, []);
  // Deduplicate identical latest snapshot for same file
  const latest = all.find(
    (s) => s.workspaceKey === snapshot.workspaceKey && s.filePath === snapshot.filePath,
  );
  if (latest && latest.content === snapshot.content) return;
  const next = [snapshot, ...all].slice(0, 200);
  writeJson(KEYS.FILE_HISTORY, next);
}

export function loadApiProfiles(): ApiEnvironmentProfile[] {
  return readJson<ApiEnvironmentProfile[]>(KEYS.API_PROFILES, [
    {
      id: 'dev',
      name: 'Development',
      variables: [
        { id: 'v1', key: 'baseUrl', value: 'https://example.workers.dev', enabled: true },
        { id: 'v2', key: 'apiKey', value: 'dev-secret-key', enabled: true },
      ],
    },
    {
      id: 'staging',
      name: 'Staging',
      variables: [
        { id: 'v3', key: 'baseUrl', value: 'https://staging.example.workers.dev', enabled: true },
      ],
    },
    {
      id: 'prod',
      name: 'Production',
      variables: [
        { id: 'v4', key: 'baseUrl', value: 'https://api.example.com', enabled: true },
      ],
    },
  ]);
}

export function saveApiProfiles(profiles: ApiEnvironmentProfile[]): void {
  writeJson(KEYS.API_PROFILES, profiles);
}

export function loadSavedApiRequests(): ApiRequestSpec[] {
  return readJson<ApiRequestSpec[]>(KEYS.API_SAVED_REQUESTS, []);
}

export function saveSavedApiRequests(list: ApiRequestSpec[]): void {
  writeJson(KEYS.API_SAVED_REQUESTS, list);
}

export function loadApiHistory(): ApiHistoryItem[] {
  return readJson<ApiHistoryItem[]>(KEYS.API_HISTORY, []);
}

export function saveApiHistory(items: ApiHistoryItem[]): void {
  writeJson(KEYS.API_HISTORY, items.slice(0, 50));
}

export interface RepoWorkspaceLink {
  owner: string;
  repo: string;
  branch: string;
}

export function loadRepoLinks(): Record<string, RepoWorkspaceLink> {
  return readJson<Record<string, RepoWorkspaceLink>>(KEYS.GH_REPO_LINKS, {});
}

export function saveRepoLink(workspaceKey: string, link: RepoWorkspaceLink | null): void {
  const all = loadRepoLinks();
  if (!link) delete all[workspaceKey];
  else all[workspaceKey] = link;
  writeJson(KEYS.GH_REPO_LINKS, all);
}

export function computeStorageStats(): { keysCount: number; bytesApprox: number } {
  let bytesApprox = 0;
  let keysCount = 0;
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    if (k && k.startsWith('codeflare.')) {
      keysCount++;
      const v = localStorage.getItem(k) || '';
      bytesApprox += (k.length + v.length) * 2;
    }
  }
  return { keysCount, bytesApprox };
}

export function clearAllCodeflareCaches(): void {
  localStorage.removeItem(KEYS.AUTOSAVE_DRAFTS);
  localStorage.removeItem(KEYS.FILE_HISTORY);
  localStorage.removeItem(KEYS.API_HISTORY);
}
