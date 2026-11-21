package com.example.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.R

// Curated Editor Theme Presets for the Appearance Studio
enum class EditorThemePreset(
  val label: String,
  val subtitle: String,
  val swatchBg: Color,
  val swatchAccent: Color,
) {
  OLIVE_OBSIDIAN(
    label = "Obsidian",
    subtitle = "Warm olive-charcoal",
    swatchBg = Color(0xFF141711),
    swatchAccent = Color(0xFFC5E855),
  ),
  SLATE_CARBON(
    label = "Nordic Slate",
    subtitle = "Cool steel-cyan",
    swatchBg = Color(0xFF11151C),
    swatchAccent = Color(0xFF60A5FA),
  ),
  MIDNIGHT_NOIR(
    label = "Noir OLED",
    subtitle = "Pure deep graphite",
    swatchBg = Color(0xFF0A0B0E),
    swatchAccent = Color(0xFFA78BFA),
  ),
  AMBER_DUSK(
    label = "Cloudflare Dusk",
    subtitle = "Warm ember-ink",
    swatchBg = Color(0xFF161311),
    swatchAccent = Color(0xFFF59E42),
  ),
  ;

  companion object {
    fun fromKey(raw: String?): EditorThemePreset =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OLIVE_OBSIDIAN
  }
}

enum class UiStyleVersion(
  val key: String,
  val label: String,
) {
  V1_DEFAULT("V1", "v1"),
  V2_MINIMAL_OLED("V2", "v2"),
  ;

  companion object {
    fun fromKey(raw: String?): UiStyleVersion =
      entries.firstOrNull { it.key.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
        ?: V1_DEFAULT
  }
}

// Built-in Editor Backdrop Presets (used alongside Custom Photo Picker background)
enum class EditorBackgroundPreset(
  val key: String,
  val label: String,
) {
  NONE("NONE", "Solid"),
  AURORA_MIST("AURORA_MIST", "Aurora"),
  DEEP_OCEAN("DEEP_OCEAN", "Deep Sea"),
  CARBON_GRID("CARBON_GRID", "Blueprint"),
  DUSK_HORIZON("DUSK_HORIZON", "Sunset"),
  CUSTOM("CUSTOM", "Photo"),
  ;

  companion object {
    fun fromKey(raw: String?): EditorBackgroundPreset =
      entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: NONE
  }
}

/**
 * Curated editor syntax & code-style reference themes configurable in Appearance -> Editor.
 */
enum class CodeStylePreset(
  val key: String,
  val label: String,
  val subtitle: String,
  @DrawableRes val referenceImageRes: Int? = null,
  val canvasBg: Color,
  val canvasGutterBg: Color,
  val canvasActiveLineBg: Color,
  val canvasTextPrimary: Color,
  val keyword: Color,
  val control: Color,
  val string: Color,
  val number: Color,
  val comment: Color,
  val function: Color,
  val type: Color,
  val property: Color,
  val operator: Color,
) {
  THEME_DEFAULT(
    key = "DEFAULT",
    label = "Theme Auto",
    subtitle = "Matches your active IDE theme",
    referenceImageRes = null,
    canvasBg = SlateEditorBg,
    canvasGutterBg = SlateEditorGutter,
    canvasActiveLineBg = SlateActiveLine,
    canvasTextPrimary = Color(0xFFECEEE6),
    keyword = Color(0xFFC084FC),
    control = Color(0xFFD8B4FE),
    string = Color(0xFFFBBF24),
    number = Color(0xFF86EFAC),
    comment = Color(0xFF6B7280),
    function = Color(0xFF93C5FD),
    type = Color(0xFF5EEAD4),
    property = Color(0xFFA5B4FC),
    operator = Color(0xFFD1D5DB),
  ),
  NEON_QUERY(
    key = "NEON_QUERY",
    label = "Neon Query",
    subtitle = "OLED black · amber, cyan & rose",
    referenceImageRes = R.drawable.code_style_ref_neon_query,
    canvasBg = Color(0xFF000000),
    canvasGutterBg = Color(0xFF06070A),
    canvasActiveLineBg = Color(0xFF10131A),
    canvasTextPrimary = Color(0xFFE5E7EB),
    keyword = Color(0xFFF59E0B),
    control = Color(0xFFFACC15),
    string = Color(0xFF7DD3FC),
    number = Color(0xFF38BDF8),
    comment = Color(0xFF64748B),
    function = Color(0xFF38BDF8),
    type = Color(0xFFC084FC),
    property = Color(0xFFFB923C),
    operator = Color(0xFFFB7185),
  ),
  STUDIO_MONOKAI(
    key = "STUDIO_MONOKAI",
    label = "Monokai Web",
    subtitle = "Charcoal · crimson, lime & gold",
    referenceImageRes = R.drawable.code_style_ref_monokai_web,
    canvasBg = Color(0xFF1E2227),
    canvasGutterBg = Color(0xFF191D22),
    canvasActiveLineBg = Color(0xFF282D34),
    canvasTextPrimary = Color(0xFFF3F4F6),
    keyword = Color(0xFFFF4F6D),
    control = Color(0xFFFF5E7E),
    string = Color(0xFFFDE047),
    number = Color(0xFFC084FC),
    comment = Color(0xFF6B7280),
    function = Color(0xFF38BDF8),
    type = Color(0xFF38BDF8),
    property = Color(0xFFA3E635),
    operator = Color(0xFFFF6B81),
  ),
  TOKYO_NIGHT(
    key = "TOKYO_NIGHT",
    label = "Tokyo Night",
    subtitle = "Deep indigo · violet, sky & mint",
    referenceImageRes = null,
    canvasBg = Color(0xFF16161E),
    canvasGutterBg = Color(0xFF13131A),
    canvasActiveLineBg = Color(0xFF1F2335),
    canvasTextPrimary = Color(0xFFC0CAF5),
    keyword = Color(0xFFBB9AF7),
    control = Color(0xFFFF7A93),
    string = Color(0xFF9ECE6A),
    number = Color(0xFFFF9E64),
    comment = Color(0xFF565F89),
    function = Color(0xFF7AA2F7),
    type = Color(0xFF2AC3DE),
    property = Color(0xFF73DACA),
    operator = Color(0xFF89DDFF),
  ),
  DRACULA_VIVID(
    key = "DRACULA_VIVID",
    label = "Dracula Vivid",
    subtitle = "Plum ink · pink, green & cyan",
    referenceImageRes = null,
    canvasBg = Color(0xFF1B1C26),
    canvasGutterBg = Color(0xFF171821),
    canvasActiveLineBg = Color(0xFF272938),
    canvasTextPrimary = Color(0xFFF8F8F2),
    keyword = Color(0xFFFF79C6),
    control = Color(0xFFFF79C6),
    string = Color(0xFFF1FA8C),
    number = Color(0xFFBD93F9),
    comment = Color(0xFF6272A4),
    function = Color(0xFF50FA7B),
    type = Color(0xFF8BE9FD),
    property = Color(0xFFFFB86C),
    operator = Color(0xFFFF79C6),
  ),
  ;

  fun swatchTokens(): List<Color> = listOf(keyword, function, string, property)

  companion object {
    fun fromKey(raw: String?): CodeStylePreset =
      entries.firstOrNull { it.key.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
        ?: THEME_DEFAULT
  }
}

// Core Olive-Obsidian tokens (modern ultra-thin flat tones)
val OliveBg = Color(0xFF11130E)
val OliveSurfaceLow = Color(0xFF161913)
val OliveSurface = Color(0xFF1D2119)
val OliveSurfaceHigh = Color(0xFF252A20)
val OliveSelectedRow = Color(0xFF293022)
val OliveActivePill = Color(0xFF333F16)
val OliveLimeText = Color(0xFFD2F063)
val OliveCtaBg = Color(0xFFCDE959)
val OliveCtaFg = Color(0xFF161E04)
val OliveBorder = Color(0xFF262B20)

val FeaturedResumeDarkBg = Color(0xFF271F33)
val FeaturedResumeDarkFg = Color(0xFFF3EBFF)
val FeaturedResumeDarkMuted = Color(0xFFCBBADF)

val SlateEditorBg = Color(0xFF14171D)
val SlateEditorGutter = Color(0xFF14171D)
val SlateActiveLine = Color(0xFF1E222B)
val SlateGutterDivider = Color(0xFF252934)

val BadgeJs = Color(0xFFE0A43E)
val BadgeTs = Color(0xFF38BDF8)
val BadgeJson = Color(0xFF34D399)
val BadgeWrangler = Color(0xFFF6821F)
val FolderYellow = Color(0xFFE5C05C)
val FolderGreen = Color(0xFF4ADE80)
val FolderTeal = Color(0xFF34D399)

val EmeraldLive = Color(0xFF34D399)
val AmberWarn = Color(0xFFFBBF24)
val RoseError = Color(0xFFFB7185)

@Immutable
data class IdePalette(
  val isDark: Boolean,
  val preset: EditorThemePreset = EditorThemePreset.OLIVE_OBSIDIAN,
  val uiStyle: UiStyleVersion = UiStyleVersion.V1_DEFAULT,
  val chromeBg: Color,
  val chromeSurface: Color,
  val chromePill: Color,
  // Flat ultra-thin modern surfaces (kept under existing token names for clean codebase-wide migration)
  val glassSurface: Color,
  val glassElevated: Color,
  val glassDockBg: Color,
  val glassBorderHighlight: Color,
  val glassBorderSubtle: Color,
  val activePillBg: Color,
  val activePillFg: Color,
  val ctaBg: Color,
  val ctaFg: Color,
  val featuredCardBg: Color,
  val featuredCardFg: Color,
  val featuredCardMuted: Color,
  val selectedRowBg: Color,
  val editorBg: Color,
  val editorGutterBg: Color,
  val editorActiveLineBg: Color,
  val tabBarBg: Color,
  val tabActiveBg: Color,
  val border: Color,
  val treeLine: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  // Search match highlights
  val searchMatchBg: Color,
  val searchActiveMatchBg: Color,
  // Syntax highlighting tokens
  val syntaxKeyword: Color,
  val syntaxControl: Color,
  val syntaxString: Color,
  val syntaxNumber: Color,
  val syntaxComment: Color,
  val syntaxFunction: Color,
  val syntaxType: Color,
  val syntaxProperty: Color,
  val syntaxOperator: Color,
) {
  val editorTextPrimary: Color
    get() =
      if (editorBg.luminance() < 0.40f) {
        if (textPrimary.luminance() > 0.45f) textPrimary else Color(0xFFECEEE6)
      } else {
        if (textPrimary.luminance() < 0.45f) textPrimary else Color(0xFF181A14)
      }

  val editorTextMuted: Color
    get() =
      if (editorBg.luminance() < 0.40f) {
        if (!isDark) Color(0xFF8E95A5) else textMuted
      } else {
        if (isDark) Color(0xFF6B7280) else textMuted
      }

  fun canvasAccent(): Color =
    if (editorBg.luminance() < 0.40f && ctaBg.luminance() < 0.28f) activePillFg else ctaBg
}

val DarkIdePalette =
  IdePalette(
    isDark = true,
    preset = EditorThemePreset.OLIVE_OBSIDIAN,
    chromeBg = OliveBg,
    chromeSurface = OliveSurfaceLow,
    chromePill = OliveSurface,
    glassSurface = Color(0xFF161913),
    glassElevated = Color(0xFF1C2018),
    glassDockBg = Color(0xFF131610),
    glassBorderHighlight = Color(0xFF2D3326),
    glassBorderSubtle = Color(0xFF23281E),
    activePillBg = OliveActivePill,
    activePillFg = OliveLimeText,
    ctaBg = OliveCtaBg,
    ctaFg = OliveCtaFg,
    featuredCardBg = FeaturedResumeDarkBg,
    featuredCardFg = FeaturedResumeDarkFg,
    featuredCardMuted = FeaturedResumeDarkMuted,
    selectedRowBg = OliveSelectedRow,
    editorBg = SlateEditorBg,
    editorGutterBg = SlateEditorGutter,
    editorActiveLineBg = SlateActiveLine,
    tabBarBg = OliveBg,
    tabActiveBg = SlateEditorBg,
    border = OliveBorder,
    treeLine = Color(0xFF282D22),
    textPrimary = Color(0xFFECEEE6),
    textSecondary = Color(0xFFA3A89C),
    textMuted = Color(0xFF6F7567),
    searchMatchBg = Color(0x40FBBF24),
    searchActiveMatchBg = Color(0x8CF59E0B),
    syntaxKeyword = Color(0xFFC084FC),
    syntaxControl = Color(0xFFD8B4FE),
    syntaxString = Color(0xFFFBBF24),
    syntaxNumber = Color(0xFF86EFAC),
    syntaxComment = Color(0xFF6B7280),
    syntaxFunction = Color(0xFF93C5FD),
    syntaxType = Color(0xFF5EEAD4),
    syntaxProperty = Color(0xFFA5B4FC),
    syntaxOperator = Color(0xFFD1D5DB),
  )

val SlateDarkIdePalette =
  DarkIdePalette.copy(
    preset = EditorThemePreset.SLATE_CARBON,
    chromeBg = Color(0xFF0E1117),
    chromeSurface = Color(0xFF131720),
    chromePill = Color(0xFF1A202C),
    glassSurface = Color(0xFF131720),
    glassElevated = Color(0xFF191F2B),
    glassDockBg = Color(0xFF10141C),
    glassBorderHighlight = Color(0xFF2A3346),
    glassBorderSubtle = Color(0xFF202736),
    activePillBg = Color(0xFF1B3352),
    activePillFg = Color(0xFF7DD3FC),
    ctaBg = Color(0xFF60A5FA),
    ctaFg = Color(0xFF071324),
    featuredCardBg = Color(0xFF172235),
    featuredCardFg = Color(0xFFE8F1FF),
    featuredCardMuted = Color(0xFF9DB6D9),
    selectedRowBg = Color(0xFF1E293B),
    editorBg = Color(0xFF0F131A),
    editorGutterBg = Color(0xFF0F131A),
    editorActiveLineBg = Color(0xFF181F2C),
    tabBarBg = Color(0xFF0E1117),
    tabActiveBg = Color(0xFF0F131A),
    border = Color(0xFF222A3A),
    treeLine = Color(0xFF232C3D),
    textPrimary = Color(0xFFE6ECF5),
    textSecondary = Color(0xFF98A4B8),
    textMuted = Color(0xFF647085),
    syntaxKeyword = Color(0xFF7DD3FC),
    syntaxControl = Color(0xFFC4B5FD),
    syntaxString = Color(0xFF86EFAC),
    syntaxNumber = Color(0xFFFCD34D),
    syntaxFunction = Color(0xFF93C5FD),
    syntaxType = Color(0xFF5EEAD4),
    syntaxProperty = Color(0xFFA5B4FC),
  )

val NoirDarkIdePalette =
  DarkIdePalette.copy(
    preset = EditorThemePreset.MIDNIGHT_NOIR,
    chromeBg = Color(0xFF090A0D),
    chromeSurface = Color(0xFF101217),
    chromePill = Color(0xFF171A21),
    glassSurface = Color(0xFF101217),
    glassElevated = Color(0xFF161920),
    glassDockBg = Color(0xFF0C0E12),
    glassBorderHighlight = Color(0xFF272B36),
    glassBorderSubtle = Color(0xFF1E212B),
    activePillBg = Color(0xFF2B2147),
    activePillFg = Color(0xFFD8B4FE),
    ctaBg = Color(0xFFC084FC),
    ctaFg = Color(0xFF180A2B),
    featuredCardBg = Color(0xFF1B1629),
    featuredCardFg = Color(0xFFF5ECFF),
    featuredCardMuted = Color(0xFFC2B0D9),
    selectedRowBg = Color(0xFF1E1B2E),
    editorBg = Color(0xFF0B0C10),
    editorGutterBg = Color(0xFF0B0C10),
    editorActiveLineBg = Color(0xFF151821),
    tabBarBg = Color(0xFF090A0D),
    tabActiveBg = Color(0xFF0B0C10),
    border = Color(0xFF1E212B),
    treeLine = Color(0xFF222633),
    textPrimary = Color(0xFFF0F1F5),
    textSecondary = Color(0xFF9CA0B0),
    textMuted = Color(0xFF666B7D),
    syntaxKeyword = Color(0xFFD8B4FE),
    syntaxControl = Color(0xFFF0ABFC),
    syntaxString = Color(0xFFFDE68A),
    syntaxNumber = Color(0xFF6EE7B7),
    syntaxFunction = Color(0xFF93C5FD),
    syntaxType = Color(0xFF67E8F9),
    syntaxProperty = Color(0xFFC4B5FD),
  )

val DuskDarkIdePalette =
  DarkIdePalette.copy(
    preset = EditorThemePreset.AMBER_DUSK,
    chromeBg = Color(0xFF13110F),
    chromeSurface = Color(0xFF1A1714),
    chromePill = Color(0xFF231F1B),
    glassSurface = Color(0xFF1A1714),
    glassElevated = Color(0xFF221E1A),
    glassDockBg = Color(0xFF161310),
    glassBorderHighlight = Color(0xFF352E27),
    glassBorderSubtle = Color(0xFF2B251F),
    activePillBg = Color(0xFF472A12),
    activePillFg = Color(0xFFFDBA74),
    ctaBg = Color(0xFFF59E42),
    ctaFg = Color(0xFF241103),
    featuredCardBg = Color(0xFF2B1D14),
    featuredCardFg = Color(0xFFFFF3E6),
    featuredCardMuted = Color(0xFFDCC0A6),
    selectedRowBg = Color(0xFF2D231A),
    editorBg = Color(0xFF151311),
    editorGutterBg = Color(0xFF151311),
    editorActiveLineBg = Color(0xFF211D18),
    tabBarBg = Color(0xFF13110F),
    tabActiveBg = Color(0xFF151311),
    border = Color(0xFF2C2620),
    treeLine = Color(0xFF302922),
    textPrimary = Color(0xFFF2ECE4),
    textSecondary = Color(0xFFB0A496),
    textMuted = Color(0xFF7B7064),
    syntaxKeyword = Color(0xFFFDBA74),
    syntaxControl = Color(0xFFFCA5A5),
    syntaxString = Color(0xFFA7F3D0),
    syntaxNumber = Color(0xFFFDE047),
    syntaxFunction = Color(0xFF93C5FD),
    syntaxType = Color(0xFF5EEAD4),
    syntaxProperty = Color(0xFFFED7AA),
  )

val LightIdePalette =
  IdePalette(
    isDark = false,
    preset = EditorThemePreset.OLIVE_OBSIDIAN,
    chromeBg = Color(0xFFF6F7F3),
    chromeSurface = Color(0xFFFFFFFF),
    chromePill = Color(0xFFECEFE6),
    glassSurface = Color(0xFFFFFFFF),
    glassElevated = Color(0xFFFAFBF8),
    glassDockBg = Color(0xFFFFFFFF),
    glassBorderHighlight = Color(0xFFD8DDD0),
    glassBorderSubtle = Color(0xFFE3E7DC),
    activePillBg = Color(0xFFDFEEB3),
    activePillFg = Color(0xFF253402),
    ctaBg = Color(0xFF4F6B11),
    ctaFg = Color(0xFFFFFFFF),
    featuredCardBg = Color(0xFFF2ECFA),
    featuredCardFg = Color(0xFF2D1647),
    featuredCardMuted = Color(0xFF5D4678),
    selectedRowBg = Color(0xFFE5ECDA),
    editorBg = Color(0xFFFFFFFF),
    editorGutterBg = Color(0xFFF8F9F6),
    editorActiveLineBg = Color(0xFFF0F3E8),
    tabBarBg = Color(0xFFF6F7F3),
    tabActiveBg = Color(0xFFFFFFFF),
    border = Color(0xFFDFE3D8),
    treeLine = Color(0xFFD8DDD0),
    textPrimary = Color(0xFF181A14),
    textSecondary = Color(0xFF4F5546),
    textMuted = Color(0xFF838A78),
    searchMatchBg = Color(0x4DF59E0B),
    searchActiveMatchBg = Color(0x99F59E0B),
    syntaxKeyword = Color(0xFF7C3AED),
    syntaxControl = Color(0xFF9333EA),
    syntaxString = Color(0xFFB45309),
    syntaxNumber = Color(0xFF047857),
    syntaxComment = Color(0xFF6B7280),
    syntaxFunction = Color(0xFF1D4ED8),
    syntaxType = Color(0xFF0F766E),
    syntaxProperty = Color(0xFF4338CA),
    syntaxOperator = Color(0xFF334155),
  )

fun paletteFor(
  darkTheme: Boolean,
  preset: EditorThemePreset,
  uiStyle: UiStyleVersion = UiStyleVersion.V1_DEFAULT,
  codeStyle: CodeStylePreset = CodeStylePreset.THEME_DEFAULT,
  codeStyleOverrideCanvas: Boolean = true,
): IdePalette {
  val basePalette =
    if (!darkTheme) {
      when (preset) {
        EditorThemePreset.OLIVE_OBSIDIAN -> LightIdePalette
        EditorThemePreset.SLATE_CARBON ->
          LightIdePalette.copy(
            preset = preset,
            chromeBg = Color(0xFFF5F7FA),
            chromePill = Color(0xFFE8EDF5),
            activePillBg = Color(0xFFDBEAFE),
            activePillFg = Color(0xFF1E40AF),
            ctaBg = Color(0xFF2563EB),
            border = Color(0xFFDCE3EE),
            glassBorderHighlight = Color(0xFFD3DCEB),
            glassBorderSubtle = Color(0xFFE2E8F0),
            editorActiveLineBg = Color(0xFFEFF4FB),
          )
        EditorThemePreset.MIDNIGHT_NOIR ->
          LightIdePalette.copy(
            preset = preset,
            chromeBg = Color(0xFFF7F6FA),
            chromePill = Color(0xFFEDEBF4),
            activePillBg = Color(0xFFEDE9FE),
            activePillFg = Color(0xFF5B21B6),
            ctaBg = Color(0xFF6D28D9),
            border = Color(0xFFE2DFEE),
            glassBorderHighlight = Color(0xFFD8D3EA),
            glassBorderSubtle = Color(0xFFE8E5F2),
            editorActiveLineBg = Color(0xFFF3F0FA),
          )
        EditorThemePreset.AMBER_DUSK ->
          LightIdePalette.copy(
            preset = preset,
            chromeBg = Color(0xFFFAF7F4),
            chromePill = Color(0xFFF2EAE1),
            activePillBg = Color(0xFFFFEDD5),
            activePillFg = Color(0xFF9A3412),
            ctaBg = Color(0xFFD96B16),
            border = Color(0xFFE8DED3),
            glassBorderHighlight = Color(0xFFDFD2C4),
            glassBorderSubtle = Color(0xFFECE3D9),
            editorActiveLineBg = Color(0xFFFBF2E9),
          )
      }
    } else {
      when (preset) {
        EditorThemePreset.OLIVE_OBSIDIAN -> DarkIdePalette
        EditorThemePreset.SLATE_CARBON -> SlateDarkIdePalette
        EditorThemePreset.MIDNIGHT_NOIR -> NoirDarkIdePalette
        EditorThemePreset.AMBER_DUSK -> DuskDarkIdePalette
      }
    }

  val styledPalette =
    if (uiStyle == UiStyleVersion.V1_DEFAULT) {
      basePalette.copy(uiStyle = UiStyleVersion.V1_DEFAULT)
    } else if (darkTheme) {
      basePalette.copy(
        uiStyle = UiStyleVersion.V2_MINIMAL_OLED,
        chromeBg = Color(0xFF000000),
        chromeSurface = Color(0xFF141416),
        chromePill = Color(0xFF1C1C1F),
        glassSurface = Color(0xFF141416),
        glassElevated = Color(0xFF1B1B1E),
        glassDockBg = Color(0xFF070708),
        glassBorderHighlight = Color(0xFF2B2B30),
        glassBorderSubtle = Color(0xFF1F1F23),
        activePillBg = Color(0xFF242428),
        activePillFg = Color(0xFFF4F4F5),
        ctaBg = Color(0xFFF4F4F5),
        ctaFg = Color(0xFF09090B),
        featuredCardBg = Color(0xFF141416),
        featuredCardFg = Color(0xFFF4F4F5),
        featuredCardMuted = Color(0xFFA1A1AA),
        selectedRowBg = Color(0xFF1D1D21),
        editorBg = Color(0xFF000000),
        editorGutterBg = Color(0xFF040405),
        editorActiveLineBg = Color(0xFF141416),
        tabBarBg = Color(0xFF000000),
        tabActiveBg = Color(0xFF141416),
        border = Color(0xFF242428),
        treeLine = Color(0xFF222226),
        textPrimary = Color(0xFFF4F4F5),
        textSecondary = Color(0xFFA1A1AA),
        textMuted = Color(0xFF71717A),
      )
    } else {
      basePalette.copy(
        uiStyle = UiStyleVersion.V2_MINIMAL_OLED,
        chromeBg = Color(0xFFFFFFFF),
        chromeSurface = Color(0xFFF4F4F6),
        chromePill = Color(0xFFEBEBEF),
        glassSurface = Color(0xFFF4F4F6),
        glassElevated = Color(0xFFEEEEF1),
        glassDockBg = Color(0xFFFAFAFC),
        glassBorderHighlight = Color(0xFFDADAE0),
        glassBorderSubtle = Color(0xFFE5E5EA),
        activePillBg = Color(0xFFE5E5EA),
        activePillFg = Color(0xFF111113),
        ctaBg = Color(0xFF18181B),
        ctaFg = Color(0xFFFFFFFF),
        featuredCardBg = Color(0xFFF4F4F6),
        featuredCardFg = Color(0xFF111113),
        featuredCardMuted = Color(0xFF6B6B74),
        selectedRowBg = Color(0xFFE9E9EE),
        editorBg = Color(0xFFFFFFFF),
        editorGutterBg = Color(0xFFFAFAFC),
        editorActiveLineBg = Color(0xFFF4F4F6),
        tabBarBg = Color(0xFFFFFFFF),
        tabActiveBg = Color(0xFFF4F4F6),
        border = Color(0xFFE4E4E9),
        treeLine = Color(0xFFDCDCE2),
        textPrimary = Color(0xFF111113),
        textSecondary = Color(0xFF52525B),
        textMuted = Color(0xFF8E8E93),
      )
    }

  return applyCodeStylePreset(styledPalette, codeStyle, codeStyleOverrideCanvas)
}

private fun Color.forLightEditorSurface(): Color {
  // Deepen luminous neon tokens slightly when rendered over light paper without dark canvas override
  val lum = luminance()
  return if (lum > 0.35f) {
    Color(
      red = (red * 0.56f).coerceIn(0f, 1f),
      green = (green * 0.56f).coerceIn(0f, 1f),
      blue = (blue * 0.58f).coerceIn(0f, 1f),
      alpha = alpha,
    )
  } else {
    this
  }
}

fun applyCodeStylePreset(
  palette: IdePalette,
  codeStyle: CodeStylePreset,
  overrideCanvas: Boolean = true,
): IdePalette {
  if (codeStyle == CodeStylePreset.THEME_DEFAULT) return palette
  val useLightSyntaxTones = !palette.isDark && !overrideCanvas
  fun token(color: Color): Color = if (useLightSyntaxTones) color.forLightEditorSurface() else color

  return palette.copy(
    editorBg = if (overrideCanvas) codeStyle.canvasBg else palette.editorBg,
    editorGutterBg = if (overrideCanvas) codeStyle.canvasGutterBg else palette.editorGutterBg,
    editorActiveLineBg = if (overrideCanvas) codeStyle.canvasActiveLineBg else palette.editorActiveLineBg,
    textPrimary = if (overrideCanvas) codeStyle.canvasTextPrimary else palette.textPrimary,
    syntaxKeyword = token(codeStyle.keyword),
    syntaxControl = token(codeStyle.control),
    syntaxString = token(codeStyle.string),
    syntaxNumber = token(codeStyle.number),
    syntaxComment = codeStyle.comment,
    syntaxFunction = token(codeStyle.function),
    syntaxType = token(codeStyle.type),
    syntaxProperty = token(codeStyle.property),
    syntaxOperator = token(codeStyle.operator),
  )
}

fun IdePalette.bridgedTo(
  scheme: androidx.compose.material3.ColorScheme,
  preserveEditorCanvas: Boolean = false,
): IdePalette {
  val isV2 = uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val resolvedChromeBg = if (isV2) chromeBg else scheme.background
  val resolvedChromeSurface =
    if (isV2) lerp(chromeSurface, scheme.surfaceContainerLow, 0.42f) else scheme.surfaceContainerLow
  val resolvedChromePill =
    if (isV2) lerp(chromePill, scheme.surfaceContainer, 0.48f) else scheme.surfaceContainer
  val resolvedGlassSurface =
    if (isV2) lerp(glassSurface, scheme.surfaceContainer, 0.42f) else scheme.surfaceContainer
  val resolvedGlassElevated =
    if (isV2) lerp(glassElevated, scheme.surfaceContainerHigh, 0.48f) else scheme.surfaceContainerHigh
  val resolvedDockBg =
    if (isV2) lerp(glassDockBg, scheme.surfaceContainerLow, 0.40f) else scheme.surfaceContainerLow
  val resolvedEditorBg =
    when {
      preserveEditorCanvas -> editorBg
      isV2 -> editorBg
      else -> scheme.surfaceContainerLowest
    }
  val resolvedGutterBg =
    when {
      preserveEditorCanvas -> editorGutterBg
      isV2 -> lerp(editorGutterBg, scheme.surfaceContainerLow, 0.35f)
      else -> scheme.surfaceContainerLow
    }
  val resolvedActiveLineBg =
    when {
      preserveEditorCanvas -> editorActiveLineBg
      else -> scheme.primary.copy(alpha = if (isDark) 0.11f else 0.08f).compositeOver(resolvedEditorBg)
    }

  return copy(
    chromeBg = resolvedChromeBg,
    chromeSurface = resolvedChromeSurface,
    chromePill = resolvedChromePill,
    glassSurface = resolvedGlassSurface,
    glassElevated = resolvedGlassElevated,
    glassDockBg = resolvedDockBg,
    glassBorderHighlight = scheme.outline,
    glassBorderSubtle = scheme.outlineVariant,
    activePillBg = scheme.primaryContainer,
    activePillFg = scheme.onPrimaryContainer,
    ctaBg = scheme.primary,
    ctaFg = scheme.onPrimary,
    featuredCardBg = scheme.tertiaryContainer,
    featuredCardFg = scheme.onTertiaryContainer,
    featuredCardMuted = scheme.onTertiaryContainer.copy(alpha = 0.74f),
    selectedRowBg = if (isV2) lerp(selectedRowBg, scheme.surfaceContainerHighest, 0.55f) else scheme.surfaceContainerHighest,
    editorBg = resolvedEditorBg,
    editorGutterBg = resolvedGutterBg,
    editorActiveLineBg = resolvedActiveLineBg,
    tabBarBg = resolvedChromeBg,
    tabActiveBg = if (isV2) lerp(tabActiveBg, scheme.surfaceContainer, 0.55f) else scheme.surfaceContainer,
    border = scheme.outlineVariant,
    treeLine = scheme.outlineVariant.copy(alpha = 0.7f),
    textPrimary = if (preserveEditorCanvas) textPrimary else scheme.onSurface,
    textSecondary = scheme.onSurfaceVariant,
    textMuted = scheme.outline,
  )
}

@Immutable
data class IdeSelectionColors(
  val canvasFill: Color,
  val canvasMoveFill: Color,
  val canvasOutline: Color,
  val handleFill: Color,
  val spanBg: Color,
  val spanFg: Color,
)

fun selectionColorsFor(palette: IdePalette): IdeSelectionColors {
  val isDarkCanvas = palette.editorBg.luminance() < 0.35f
  val rail = palette.ctaBg
  return if (isDarkCanvas) {
    IdeSelectionColors(
      canvasFill = rail.copy(alpha = 0.30f),
      canvasMoveFill = rail.copy(alpha = 0.15f),
      canvasOutline = rail,
      handleFill = rail,
      spanBg = rail,
      spanFg = palette.ctaFg,
    )
  } else {
    IdeSelectionColors(
      canvasFill = rail.copy(alpha = 0.22f),
      canvasMoveFill = rail.copy(alpha = 0.12f),
      canvasOutline = rail,
      handleFill = rail,
      spanBg = rail,
      spanFg = palette.ctaFg,
    )
  }
}
