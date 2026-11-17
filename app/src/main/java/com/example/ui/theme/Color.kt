package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Reference-matched Material 3 Expressive Olive-Obsidian + Chartreuse IDE Tokens
val OliveBg = Color(0xFF12140E)
val OliveSurfaceLow = Color(0xFF1B1E17)
val OliveSurface = Color(0xFF23271E)
val OliveSurfaceHigh = Color(0xFF2D3226)
val OliveSelectedRow = Color(0xFF343B29)
val OliveActivePill = Color(0xFF47571C)
val OliveLimeText = Color(0xFFD6F462)
val OliveCtaBg = Color(0xFFD2F258)
val OliveCtaFg = Color(0xFF1A2302)
val OliveBorder = Color(0xFF2C3125)

val FeaturedResumeDarkBg = Color(0xFF51346E)
val FeaturedResumeDarkFg = Color(0xFFF6EEFF)
val FeaturedResumeDarkMuted = Color(0xFFD8C6EB)

val SlateEditorBg = Color(0xFF171920)
val SlateEditorGutter = Color(0xFF171920)
val SlateActiveLine = Color(0xFF22252F)
val SlateGutterDivider = Color(0xFF2B2F3C)

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
  val chromeBg: Color,
  val chromeSurface: Color,
  val chromePill: Color,
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
)

val DarkIdePalette =
  IdePalette(
    isDark = true,
    chromeBg = OliveBg,
    chromeSurface = OliveSurfaceLow,
    chromePill = OliveSurface,
    glassSurface = Color(0xCC1E2219),
    glassElevated = Color(0xD9272B21),
    glassDockBg = Color(0xD9161912),
    glassBorderHighlight = Color(0x28FFFFFF),
    glassBorderSubtle = Color(0x4D3A4131),
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
    treeLine = Color(0xFF31362A),
    textPrimary = Color(0xFFECEEE6),
    textSecondary = Color(0xFFA7ACA0),
    textMuted = Color(0xFF757B6D),
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

val LightIdePalette =
  IdePalette(
    isDark = false,
    chromeBg = Color(0xFFF4F6EE),
    chromeSurface = Color(0xFFECEFE4),
    chromePill = Color(0xFFE2E6D7),
    glassSurface = Color(0xE0F0F3E8),
    glassElevated = Color(0xEBFAFCF5),
    glassDockBg = Color(0xE6EFF2E7),
    glassBorderHighlight = Color(0xB8FFFFFF),
    glassBorderSubtle = Color(0x66BFC6B3),
    activePillBg = Color(0xFFD7EC9F),
    activePillFg = Color(0xFF253402),
    ctaBg = Color(0xFF4F6B11),
    ctaFg = Color(0xFFFFFFFF),
    featuredCardBg = Color(0xFFEDE2F8),
    featuredCardFg = Color(0xFF2D1647),
    featuredCardMuted = Color(0xFF5D4678),
    selectedRowBg = Color(0xFFDCE4C9),
    editorBg = Color(0xFFFCFCFA),
    editorGutterBg = Color(0xFFF6F7F2),
    editorActiveLineBg = Color(0xFFEDF1E4),
    tabBarBg = Color(0xFFF4F6EE),
    tabActiveBg = Color(0xFFFCFCFA),
    border = Color(0xFFD4D9C9),
    treeLine = Color(0xFFCDD3C2),
    textPrimary = Color(0xFF1A1C15),
    textSecondary = Color(0xFF4E5445),
    textMuted = Color(0xFF818876),
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
