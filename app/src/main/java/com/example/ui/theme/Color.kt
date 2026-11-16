package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Screenshot-matched Material You Olive-Sage + Slate IDE Tokens
val OliveBg = Color(0xFF1B1C17)
val OliveSurfaceLow = Color(0xFF21231D)
val OliveSurface = Color(0xFF262821)
val OliveSurfaceHigh = Color(0xFF30332A)
val OliveSelectedRow = Color(0xFF3F4334)
val OliveActivePill = Color(0xFF4F6223)
val OliveLimeText = Color(0xFFD6F58D)
val OliveBorder = Color(0xFF33362C)

val SlateEditorBg = Color(0xFF1E2028)
val SlateEditorGutter = Color(0xFF1E2028)
val SlateActiveLine = Color(0xFF2A2D37)
val SlateGutterDivider = Color(0xFF343846)

val BadgeJs = Color(0xFFD99B38)
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
  val activePillBg: Color,
  val activePillFg: Color,
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
    activePillBg = OliveActivePill,
    activePillFg = OliveLimeText,
    selectedRowBg = OliveSelectedRow,
    editorBg = SlateEditorBg,
    editorGutterBg = SlateEditorGutter,
    editorActiveLineBg = SlateActiveLine,
    tabBarBg = OliveBg,
    tabActiveBg = OliveSurfaceHigh,
    border = SlateGutterDivider,
    treeLine = Color(0xFF34372E),
    textPrimary = Color(0xFFE7E9E2),
    textSecondary = Color(0xFFA7ACA0),
    textMuted = Color(0xFF72776B),
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
    chromeBg = Color(0xFFF3F4EE),
    chromeSurface = Color(0xFFECEDE5),
    chromePill = Color(0xFFE3E6DA),
    activePillBg = Color(0xFFD5E8A8),
    activePillFg = Color(0xFF283808),
    selectedRowBg = Color(0xFFE0E5D3),
    editorBg = Color(0xFFFCFCFA),
    editorGutterBg = Color(0xFFF6F7F2),
    editorActiveLineBg = Color(0xFFEDF1E4),
    tabBarBg = Color(0xFFF3F4EE),
    tabActiveBg = Color(0xFFFFFFFF),
    border = Color(0xFFD9DDD0),
    treeLine = Color(0xFFD0D5C5),
    textPrimary = Color(0xFF1C1E18),
    textSecondary = Color(0xFF4F5447),
    textMuted = Color(0xFF858B7B),
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
