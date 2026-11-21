// Core domain models for CodeFlare IDE

export type AppScreen = 'auth' | 'home' | 'workspace' | 'settings';

export type WorkspaceTab = 'CODE' | 'TEST' | 'CONFIG' | 'CONSOLE';

export type HomeTab = 'PROJECTS' | 'PAGES' | 'INFRA';

export type ConfigSection =
  | 'BINDINGS'
  | 'KV'
  | 'D1'
  | 'R2'
  | 'DO_QUEUES'
  | 'DOMAINS_CRON'
  | 'VERSIONS'
  | 'GITHUB';

export type TestSubMode = 'HTTP' | 'LOCAL' | 'LIVE';

export type SidebarSection = 'EXPLORER' | 'SEARCH' | 'BINDINGS' | 'SNIPPETS';

export type BottomPanelTab = 'PROBLEMS' | 'LOGS' | 'CONSOLE';

export type EditorThemePreset =
  | 'OLIVE_OBSIDIAN'
  | 'SLATE_CARBON'
  | 'MIDNIGHT_NOIR'
  | 'AMBER_DUSK';

export type UiStyleVersion = 'V1_DEFAULT' | 'V2_MINIMAL_OLED';

export type CaAccent = 'Lime' | 'Violet' | 'Teal' | 'Orange' | 'Pumpkin';

export type CodeStylePreset =
  | 'THEME_DEFAULT'
  | 'NEON_QUERY'
  | 'STUDIO_MONOKAI'
  | 'TOKYO_NIGHT'
  | 'DRACULA_VIVID';

export type EditorBackgroundPreset =
  | 'NONE'
  | 'AURORA_MIST'
  | 'DEEP_OCEAN'
  | 'CARBON_GRID'
  | 'DUSK_HORIZON'
  | 'CUSTOM';

export type BubblePalette =
  | 'FLARE_HEAT'
  | 'THEME_ACCENT'
  | 'AURORA'
  | 'MONO_GLASS'
  | 'PASTEL';

export type CaretStyle = 'line' | 'block' | 'underline' | 'candle';

export type ProjectSortMode = 'MODIFIED' | 'NAME' | 'CREATED';

export type ProjectFilterMode = 'ALL' | 'CLOUD' | 'LOCAL';

export type ZenModeLevel = 0 | 1 | 2 | 3 | 4;

export interface EditorSettings {
  themePreset: EditorThemePreset;
  uiStyleVersion: UiStyleVersion;
  accent: CaAccent;
  codeStylePreset: CodeStylePreset;
  backgroundPreset: EditorBackgroundPreset;
  customBgColorHex: string;
  customBgOpacity: number;
  bubblesEnabled: boolean;
  bubblePalette: BubblePalette;
  bubbleDensity: number; // 0.3..1.5
  bubbleSpeed: number; // 0.3..1.8
  bubbleOpacity: number; // 0.1..0.7
  barOpacity: number; // 0.5..1.0
  fontSizePx: number; // 11..24
  lineHeightMultiplier: number; // 1.2..2.0
  tabSize: number; // 2 or 4
  useSpacesForTabs: boolean;
  wordWrap: boolean;
  showLineNumbers: boolean;
  showIndentGuides: boolean;
  showCodeFolding: boolean;
  showGutterDiffMarkers: boolean;
  highlightActiveLine: boolean;
  highlightMatchingBrackets: boolean;
  fontLigatures: boolean;
  caretStyle: CaretStyle;
  autoCloseBrackets: boolean;
  autoIndent: boolean;
  emmetEnabled: boolean;
  autocompleteEnabled: boolean;
  autoTriggerCompletion: boolean;
  diagnosticsEnabled: boolean;
  confirmUnsavedClose: boolean;
  autoSaveEnabled: boolean;
  autoSaveIntervalSec: number;
  defaultDeployFormat: 'esm' | 'sw';
  confirmBeforeDeploy: boolean;
  apiCacheTtlSec: number;
  smartDragEnabled: boolean;
  showHardwareSpeed: boolean;
  defaultWorkerTemplateId: string;
}

export interface CloudflareUser {
  id: string;
  email: string;
  username?: string;
  first_name?: string;
  last_name?: string;
}

export interface CloudflareAccount {
  id: string;
  name: string;
  type?: string;
}

export interface WorkerScriptSummary {
  id: string;
  etag?: string;
  created_on?: string;
  modified_on?: string;
  usage_model?: string;
  handlers?: string[];
  has_assets?: boolean;
  compatibility_date?: string;
  compatibility_flags?: string[];
  logpush?: boolean;
  tail_consumers?: Array<{ service: string; environment?: string }>;
}

export interface WorkerBinding {
  name: string;
  type:
    | 'plain_text'
    | 'secret_text'
    | 'json'
    | 'kv_namespace'
    | 'r2_bucket'
    | 'd1'
    | 'durable_object_namespace'
    | 'queue'
    | 'service'
    | 'ai'
    | string;
  text?: string;
  json?: unknown;
  namespace_id?: string;
  bucket_name?: string;
  id?: string; // D1 database ID
  database_id?: string;
  class_name?: string;
  script_name?: string;
  queue_name?: string;
  service?: string;
  environment?: string;
}

export interface WorkerSettingsResult {
  bindings?: WorkerBinding[];
  compatibility_date?: string;
  compatibility_flags?: string[];
  usage_model?: string;
  logpush?: boolean;
  observability?: { enabled?: boolean };
}

export interface WorkerSubdomainInfo {
  enabled: boolean;
  previews_enabled?: boolean;
}

export interface WorkerRoute {
  id: string;
  pattern: string;
  script?: string;
  zone_id?: string;
  zone_name?: string;
}

export interface WorkerCustomDomain {
  id: string;
  hostname: string;
  service: string;
  environment?: string;
  zone_id?: string;
  zone_name?: string;
}

export interface CronTrigger {
  cron: string;
  created_on?: string;
  modified_on?: string;
}

export interface WorkerVersion {
  id: string;
  number?: number;
  metadata?: {
    created_on?: string;
    author_email?: string;
    source?: string;
  };
  annotations?: {
    'workers/message'?: string;
    'workers/tag'?: string;
  };
}

export interface WorkerDeployment {
  id: string;
  created_on?: string;
  source?: string;
  strategy?: string;
  versions?: Array<{
    version_id: string;
    percentage: number;
  }>;
}

export interface CloudflareZone {
  id: string;
  name: string;
  status?: string;
}

export interface KvNamespace {
  id: string;
  title: string;
  supports_url_encoding?: boolean;
}

export interface KvKeyEntry {
  name: string;
  expiration?: number;
  metadata?: unknown;
}

export interface D1Database {
  uuid: string;
  name: string;
  created_at?: string;
  version?: string;
  num_tables?: number;
  file_size?: number;
}

export interface R2Bucket {
  name: string;
  creation_date?: string;
  location?: string;
}

export interface CloudflareQueue {
  queue_id: string;
  queue_name: string;
  created_on?: string;
  producers_total_count?: number;
  consumers_total_count?: number;
}

export interface PagesProject {
  id?: string;
  name: string;
  subdomain?: string;
  domains?: string[];
  created_on?: string;
  production_branch?: string;
  latest_deployment?: PagesDeployment;
  source?: {
    type?: string;
    config?: {
      owner?: string;
      repo_name?: string;
      production_branch?: string;
    };
  };
  build_config?: {
    build_command?: string;
    destination_dir?: string;
    root_dir?: string;
  };
  deployment_configs?: {
    production?: {
      env_vars?: Record<string, { value?: string; type?: string }>;
    };
    preview?: {
      env_vars?: Record<string, { value?: string; type?: string }>;
    };
  };
}

export interface PagesDeployment {
  id: string;
  short_id?: string;
  project_name?: string;
  environment?: string;
  url?: string;
  created_on?: string;
  latest_stage?: {
    name?: string;
    status?: string;
    started_on?: string;
    ended_on?: string;
  };
  aliases?: string[];
  deployment_trigger?: {
    type?: string;
    metadata?: {
      branch?: string;
      commit_hash?: string;
      commit_message?: string;
    };
  };
}

export interface PagesDomain {
  id?: string;
  name: string;
  status?: string;
  validation_data?: {
    status?: string;
  };
}

export interface LocalWorkspace {
  id: string;
  name: string;
  description?: string;
  templateId?: string;
  entryFile: string;
  files: Record<string, string>;
  bindings: WorkerBinding[];
  compatibilityDate: string;
  createdAt: number;
  updatedAt: number;
}

export interface WorkerTemplate {
  id: string;
  name: string;
  subtitle: string;
  category: 'Starter' | 'API' | 'Storage' | 'Static' | 'Edge';
  entryFile: string;
  files: Record<string, string>;
  bindings?: WorkerBinding[];
}

export type AuthMode = 'NONE' | 'OAUTH' | 'API_TOKEN' | 'GLOBAL_KEY' | 'LOCAL_ONLY';

export interface CloudflareCredentials {
  mode: AuthMode;
  apiToken?: string;
  email?: string;
  globalKey?: string;
  oauthAccessToken?: string;
  oauthRefreshToken?: string;
  oauthExpiresAt?: number;
  selectedAccountId?: string;
  customClientId?: string;
}

export interface GitHubCredentials {
  token: string;
  username?: string;
  avatarUrl?: string;
  authType?: 'PAT' | 'DEVICE_FLOW';
}

export interface GitHubRepo {
  id: number;
  name: string;
  full_name: string;
  private: boolean;
  default_branch: string;
  description?: string;
  html_url: string;
  updated_at?: string;
  owner: {
    login: string;
    avatar_url?: string;
  };
}

export interface GitHubBranch {
  name: string;
  commit: {
    sha: string;
  };
  protected?: boolean;
}

export interface GitHubTreeEntry {
  path: string;
  mode: string;
  type: 'blob' | 'tree';
  sha: string;
  size?: number;
}

export interface GitHubPullRequest {
  number: number;
  title: string;
  state: string;
  html_url: string;
  user?: { login: string };
  head?: { ref: string };
  base?: { ref: string };
  created_at?: string;
}

export interface GitHubIssue {
  number: number;
  title: string;
  state: string;
  html_url: string;
  user?: { login: string };
  created_at?: string;
  labels?: Array<{ name: string; color?: string }>;
}

export interface GitHubWorkflowRun {
  id: number;
  name?: string;
  status?: string;
  conclusion?: string;
  html_url: string;
  head_branch?: string;
  created_at?: string;
}

export interface DiagnosticItem {
  filePath: string;
  line: number;
  column: number;
  severity: 'error' | 'warning' | 'info';
  message: string;
  code?: string;
}

export interface TailLogEntry {
  id: string;
  timestamp: string;
  level: 'log' | 'info' | 'warn' | 'error' | 'debug' | 'outcome';
  message: string;
  source?: string;
}

export interface LocalScriptLog {
  level: string;
  message: string;
  timestamp: string;
}

export interface LocalScriptRunResult {
  mode: 'WORKER_FETCH' | 'SERVICE_WORKER' | 'SCRIPT' | 'EVAL' | 'STATIC_HTML' | 'STATIC_MARKDOWN' | 'STATIC_SVG' | 'STATIC_JSON' | 'ERROR';
  ok: boolean;
  statusCode: number;
  statusText: string;
  responseHeaders: Record<string, string>;
  responseBody: string;
  contentType: string;
  logs: LocalScriptLog[];
  durationMs: number;
  exportsDetected: string[];
  errorMessage?: string;
}

export interface FileHistorySnapshot {
  id: string;
  workspaceKey: string;
  filePath: string;
  content: string;
  timestamp: number;
  label: string;
}

// API Tester Pro models
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';

export type ApiAuthType = 'NONE' | 'BEARER' | 'BASIC' | 'API_KEY';

export type ApiBodyType = 'NONE' | 'JSON' | 'TEXT' | 'FORM_URLENCODED';

export interface KeyValuePair {
  id: string;
  key: string;
  value: string;
  enabled: boolean;
}

export type AssertionTarget = 'STATUS' | 'LATENCY_MS' | 'BODY_CONTAINS' | 'JSON_PATH' | 'HEADER_EQUALS';

export type AssertionOp = 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'LT' | 'LTE' | 'GT' | 'EXISTS';

export interface ApiAssertion {
  id: string;
  enabled: boolean;
  target: AssertionTarget;
  property?: string; // JSONPath or header name
  op: AssertionOp;
  expected: string;
}

export interface AssertionResult {
  assertionId: string;
  passed: boolean;
  actual: string;
  summary: string;
}

export interface ApiRequestSpec {
  id: string;
  name: string;
  method: HttpMethod;
  url: string;
  queryParams: KeyValuePair[];
  headers: KeyValuePair[];
  authType: ApiAuthType;
  authToken?: string;
  authUsername?: string;
  authPassword?: string;
  authApiKeyName?: string;
  authApiKeyValue?: string;
  authApiKeyIn?: 'HEADER' | 'QUERY';
  bodyType: ApiBodyType;
  bodyText: string;
  assertions: ApiAssertion[];
}

export interface ApiResponseSnapshot {
  statusCode: number;
  statusText: string;
  headers: Record<string, string>;
  body: string;
  durationMs: number;
  finalUrl?: string;
  error?: string;
  timestamp: number;
  assertionResults: AssertionResult[];
}

export interface ApiEnvironmentProfile {
  id: string;
  name: string;
  variables: KeyValuePair[];
}

export interface ApiHistoryItem {
  id: string;
  request: ApiRequestSpec;
  statusCode: number;
  durationMs: number;
  timestamp: number;
}

// D1 Relational Studio models
export interface D1ColumnSchema {
  cid: number;
  name: string;
  type: string;
  notnull: boolean;
  dflt_value: string | null;
  pk: number;
}

export interface D1ForeignKey {
  id: number;
  seq: number;
  table: string;
  from: string;
  to: string;
  on_update?: string;
  on_delete?: string;
}

export interface D1IndexInfo {
  name: string;
  unique: boolean;
  columns: string[];
}

export interface D1TableSchema {
  name: string;
  sql?: string;
  columns: D1ColumnSchema[];
  foreignKeys: D1ForeignKey[];
  indexes: D1IndexInfo[];
  rowCount?: number;
}

export interface ErLayoutNode {
  tableName: string;
  x: number;
  y: number;
  width: number;
  height: number;
  layer: number;
}

export interface ErLayoutEdge {
  fromTable: string;
  fromCol: string;
  toTable: string;
  toCol: string;
}
