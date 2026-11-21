import type {
  BubblePalette,
  CaAccent,
  CodeStylePreset,
  EditorBackgroundPreset,
  EditorSettings,
  EditorThemePreset,
} from '../types/ide';

export interface ThemePalette {
  id: EditorThemePreset;
  name: string;
  subtitle: string;
  bg: string;
  surface: string;
  surface2: string;
  surfaceElevated: string;
  outline: string;
  text: string;
  textMuted: string;
  accent: string;
  cyan: string;
  green: string;
  red: string;
  amber: string;
}

export const THEME_PRESETS: Record<EditorThemePreset, ThemePalette> = {
  OLIVE_OBSIDIAN: {
    id: 'OLIVE_OBSIDIAN',
    name: 'Olive Obsidian',
    subtitle: 'Studio dark slate with warm flare orange & sage contrast',
    bg: '#0B0D10',
    surface: '#12161C',
    surface2: '#181E26',
    surfaceElevated: '#1E2530',
    outline: '#26303D',
    text: '#E6EDF3',
    textMuted: '#93A1B1',
    accent: '#F38020',
    cyan: '#58A6FF',
    green: '#2EE59D',
    red: '#FF5D6C',
    amber: '#FBBF24',
  },
  SLATE_CARBON: {
    id: 'SLATE_CARBON',
    name: 'Slate Carbon',
    subtitle: 'Crisp steel-blue carbon workspace with electric cyan accents',
    bg: '#0D1117',
    surface: '#161B22',
    surface2: '#1C222B',
    surfaceElevated: '#232A35',
    outline: '#30363D',
    text: '#F0F6FC',
    textMuted: '#8B949E',
    accent: '#FF7315',
    cyan: '#79C0FF',
    green: '#3FB950',
    red: '#F85149',
    amber: '#D29922',
  },
  MIDNIGHT_NOIR: {
    id: 'MIDNIGHT_NOIR',
    name: 'Midnight Noir',
    subtitle: 'True OLED pure-black depth with high-contrast neon ink',
    bg: '#040507',
    surface: '#0B0D12',
    surface2: '#11151D',
    surfaceElevated: '#171C27',
    outline: '#222938',
    text: '#F3F6FA',
    textMuted: '#8692A6',
    accent: '#FF6B2B',
    cyan: '#38BDF8',
    green: '#34D399',
    red: '#FB7185',
    amber: '#FCD34D',
  },
  AMBER_DUSK: {
    id: 'AMBER_DUSK',
    name: 'Amber Dusk',
    subtitle: 'Warm espresso obsidian with golden ember highlights',
    bg: '#100D0B',
    surface: '#181310',
    surface2: '#201A15',
    surfaceElevated: '#29211B',
    outline: '#3A2E25',
    text: '#F7EFE6',
    textMuted: '#B29F8F',
    accent: '#F59E0B',
    cyan: '#60A5FA',
    green: '#4ADE80',
    red: '#F87171',
    amber: '#FBBF24',
  },
};

export const CA_ACCENTS: Record<
  CaAccent,
  { name: string; primary: string; soft: string; glow: string }
> = {
  Lime: {
    name: 'Lime',
    primary: '#B7F34D',
    soft: 'rgba(183, 243, 77, 0.16)',
    glow: 'rgba(183, 243, 77, 0.35)',
  },
  Violet: {
    name: 'Violet',
    primary: '#A78BFA',
    soft: 'rgba(167, 139, 250, 0.16)',
    glow: 'rgba(167, 139, 250, 0.35)',
  },
  Teal: {
    name: 'Teal',
    primary: '#2DD4BF',
    soft: 'rgba(45, 212, 191, 0.16)',
    glow: 'rgba(45, 212, 191, 0.35)',
  },
  Orange: {
    name: 'Orange',
    primary: '#F38020',
    soft: 'rgba(243, 128, 32, 0.16)',
    glow: 'rgba(243, 128, 32, 0.38)',
  },
  Pumpkin: {
    name: 'Pumpkin',
    primary: '#FF5E1A',
    soft: 'rgba(255, 94, 26, 0.18)',
    glow: 'rgba(255, 94, 26, 0.42)',
  },
};

export interface SyntaxColorScheme {
  id: CodeStylePreset;
  name: string;
  keyword: string;
  string: string;
  number: string;
  comment: string;
  type: string;
  func: string;
  operator: string;
  punctuation: string;
  tag: string;
  attr: string;
  plain: string;
}

export const CODE_STYLE_PRESETS: Record<CodeStylePreset, SyntaxColorScheme> = {
  THEME_DEFAULT: {
    id: 'THEME_DEFAULT',
    name: 'CodeFlare Default',
    keyword: '#FF7B72',
    string: '#A5D6FF',
    number: '#79C0FF',
    comment: '#7D8590',
    type: '#FFA657',
    func: '#D2A8FF',
    operator: '#F38020',
    punctuation: '#93A1B1',
    tag: '#7EE787',
    attr: '#79C0FF',
    plain: '#E6EDF3',
  },
  NEON_QUERY: {
    id: 'NEON_QUERY',
    name: 'Neon Query',
    keyword: '#FF4FD8',
    string: '#34F5C5',
    number: '#FFD166',
    comment: '#687692',
    type: '#4CC9F0',
    func: '#9BF6FF',
    operator: '#FF8552',
    punctuation: '#8D99AE',
    tag: '#FF4FD8',
    attr: '#4CC9F0',
    plain: '#EEF4FF',
  },
  STUDIO_MONOKAI: {
    id: 'STUDIO_MONOKAI',
    name: 'Studio Monokai',
    keyword: '#F92672',
    string: '#E6DB74',
    number: '#AE81FF',
    comment: '#75715E',
    type: '#66D9EF',
    func: '#A6E22E',
    operator: '#F92672',
    punctuation: '#A8A89D',
    tag: '#F92672',
    attr: '#A6E22E',
    plain: '#F8F8F2',
  },
  TOKYO_NIGHT: {
    id: 'TOKYO_NIGHT',
    name: 'Tokyo Night',
    keyword: '#BB9AF7',
    string: '#9ECE6A',
    number: '#FF9E64',
    comment: '#565F89',
    type: '#2AC3DE',
    func: '#7AA2F7',
    operator: '#89DDFF',
    punctuation: '#9ABDF5',
    tag: '#F7768E',
    attr: '#73DACA',
    plain: '#C0CAF5',
  },
  DRACULA_VIVID: {
    id: 'DRACULA_VIVID',
    name: 'Dracula Vivid',
    keyword: '#FF79C6',
    string: '#F1FA8C',
    number: '#BD93F9',
    comment: '#6272A4',
    type: '#8BE9FD',
    func: '#50FA7B',
    operator: '#FF79C6',
    punctuation: '#ABB2BF',
    tag: '#FF79C6',
    attr: '#50FA7B',
    plain: '#F8F8F2',
  },
};

export const BACKGROUND_PRESETS: Record<
  EditorBackgroundPreset,
  { name: string; description: string }
> = {
  NONE: { name: 'Solid Surface', description: 'Clean distraction-free solid canvas' },
  AURORA_MIST: { name: 'Aurora Mist', description: 'Soft teal & amber atmospheric glow' },
  DEEP_OCEAN: { name: 'Deep Ocean', description: 'Midnight cyan radial vignette' },
  CARBON_GRID: { name: 'Carbon Grid', description: 'Precision architectural blueprint grid' },
  DUSK_HORIZON: { name: 'Dusk Horizon', description: 'Warm ember sunset edge horizon' },
  CUSTOM: { name: 'Custom Tint', description: 'Custom color wash & opacity' },
};

export const BUBBLE_PALETTES: Record<
  BubblePalette,
  { name: string; colors: string[] }
> = {
  FLARE_HEAT: {
    name: 'Flare Heat',
    colors: ['#F38020', '#FF5E1A', '#FBBF24', '#FF7B72'],
  },
  THEME_ACCENT: {
    name: 'Theme Accent',
    colors: ['#F38020', '#58A6FF', '#2EE59D', '#A78BFA'],
  },
  AURORA: {
    name: 'Aurora',
    colors: ['#2EE59D', '#38BDF8', '#A78BFA', '#2DD4BF'],
  },
  MONO_GLASS: {
    name: 'Mono Glass',
    colors: ['#E2E8F0', '#94A3B8', '#CBD5E1', '#64748B'],
  },
  PASTEL: {
    name: 'Pastel',
    colors: ['#FDA4AF', '#93C5FD', '#86EFAC', '#FDE047'],
  },
};

export const DEFAULT_EDITOR_SETTINGS: EditorSettings = {
  themePreset: 'OLIVE_OBSIDIAN',
  uiStyleVersion: 'V1_DEFAULT',
  accent: 'Orange',
  codeStylePreset: 'THEME_DEFAULT',
  backgroundPreset: 'AURORA_MIST',
  customBgColorHex: '#17202E',
  customBgOpacity: 0.35,
  bubblesEnabled: true,
  bubblePalette: 'FLARE_HEAT',
  bubbleDensity: 0.75,
  bubbleSpeed: 0.9,
  bubbleOpacity: 0.32,
  barOpacity: 0.94,
  fontSizePx: 14,
  lineHeightMultiplier: 1.55,
  tabSize: 2,
  useSpacesForTabs: true,
  wordWrap: false,
  showLineNumbers: true,
  showIndentGuides: true,
  showCodeFolding: true,
  showGutterDiffMarkers: true,
  highlightActiveLine: true,
  highlightMatchingBrackets: true,
  fontLigatures: true,
  caretStyle: 'line',
  autoCloseBrackets: true,
  autoIndent: true,
  emmetEnabled: true,
  autocompleteEnabled: true,
  autoTriggerCompletion: true,
  diagnosticsEnabled: true,
  confirmUnsavedClose: true,
  autoSaveEnabled: true,
  autoSaveIntervalSec: 12,
  defaultDeployFormat: 'esm',
  confirmBeforeDeploy: true,
  apiCacheTtlSec: 45,
  smartDragEnabled: true,
  showHardwareSpeed: false,
  defaultWorkerTemplateId: 'hello-worker',
};

export function applyThemeCssVariables(settings: EditorSettings): void {
  if (typeof document === 'undefined') return;
  const palette = THEME_PRESETS[settings.themePreset] || THEME_PRESETS.OLIVE_OBSIDIAN;
  const accent = CA_ACCENTS[settings.accent] || CA_ACCENTS.Orange;
  const isV2 = settings.uiStyleVersion === 'V2_MINIMAL_OLED';

  const root = document.documentElement;
  root.style.setProperty('--cf-bg', isV2 ? '#000000' : palette.bg);
  root.style.setProperty('--cf-surface', isV2 ? '#080A0D' : palette.surface);
  root.style.setProperty('--cf-surface-2', isV2 ? '#0E1217' : palette.surface2);
  root.style.setProperty('--cf-surface-elevated', isV2 ? '#141921' : palette.surfaceElevated);
  root.style.setProperty('--cf-outline', isV2 ? '#1D2430' : palette.outline);
  root.style.setProperty('--cf-text', palette.text);
  root.style.setProperty('--cf-text-muted', palette.textMuted);
  root.style.setProperty('--cf-accent', accent.primary);
  root.style.setProperty('--cf-accent-soft', accent.soft);
  root.style.setProperty('--cf-cyan', palette.cyan);
  root.style.setProperty('--cf-green', palette.green);
  root.style.setProperty('--cf-red', palette.red);
  root.style.setProperty('--cf-amber', palette.amber);
  root.style.setProperty('--cf-bar-opacity', String(settings.barOpacity));
}
