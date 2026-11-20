package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.security.EditorSettings
import com.example.data.security.FloatingBarStyleConfig
import com.example.data.security.IconLabelBehavior
import com.example.data.security.PopupMenuStyleConfig
import com.example.data.security.SelectionDragConfig

@Immutable
data class BarStylePreferences(
  val homeBar: FloatingBarStyleConfig = FloatingBarStyleConfig.HOME_DEFAULT,
  val editorBar: FloatingBarStyleConfig = FloatingBarStyleConfig.EDITOR_DEFAULT,
  val homeMenu: PopupMenuStyleConfig = PopupMenuStyleConfig.DEFAULT,
  val editorMenu: PopupMenuStyleConfig = PopupMenuStyleConfig.DEFAULT,
)

val LocalIdePalette = staticCompositionLocalOf { DarkIdePalette }
val LocalBarStylePreferences = staticCompositionLocalOf { BarStylePreferences() }
val LocalPopupMenuStyle = staticCompositionLocalOf { PopupMenuStyleConfig.DEFAULT }
val LocalSelectionDragConfig = staticCompositionLocalOf { SelectionDragConfig.DEFAULT }
val LocalIconLabelBehavior = staticCompositionLocalOf { IconLabelBehavior.REVEAL_ON_TAP }
val LocalEditorSettings = staticCompositionLocalOf { EditorSettings() }

private fun buildDarkColorScheme(palette: IdePalette) =
  darkColorScheme(
    primary = palette.ctaBg,
    onPrimary = palette.ctaFg,
    primaryContainer = palette.activePillBg,
    onPrimaryContainer = palette.activePillFg,
    secondary = palette.textSecondary,
    onSecondary = palette.chromeBg,
    secondaryContainer = palette.selectedRowBg,
    onSecondaryContainer = palette.textPrimary,
    tertiary = palette.syntaxType,
    onTertiary = palette.chromeBg,
    background = palette.chromeBg,
    onBackground = palette.textPrimary,
    surface = palette.chromeBg,
    onSurface = palette.textPrimary,
    surfaceContainerLowest = palette.editorBg,
    surfaceContainerLow = palette.chromeSurface,
    surfaceContainer = palette.chromePill,
    surfaceContainerHigh = palette.glassElevated,
    surfaceContainerHighest = palette.selectedRowBg,
    outline = palette.border,
    outlineVariant = palette.glassBorderSubtle,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
  )

private fun buildLightColorScheme(palette: IdePalette) =
  lightColorScheme(
    primary = palette.ctaBg,
    onPrimary = palette.ctaFg,
    primaryContainer = palette.activePillBg,
    onPrimaryContainer = palette.activePillFg,
    secondary = palette.textSecondary,
    onSecondary = Color.White,
    secondaryContainer = palette.selectedRowBg,
    onSecondaryContainer = palette.textPrimary,
    tertiary = palette.syntaxType,
    onTertiary = Color.White,
    background = palette.chromeBg,
    onBackground = palette.textPrimary,
    surface = palette.chromeBg,
    onSurface = palette.textPrimary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = palette.chromeSurface,
    surfaceContainer = palette.chromePill,
    surfaceContainerHigh = palette.selectedRowBg,
    surfaceContainerHighest = palette.border,
    outline = palette.border,
    outlineVariant = palette.glassBorderSubtle,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
  )

@Composable
fun EdgeCodeTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  accent: String = EditorSettings.ACCENT_LIME,
  accentColor: Long = EditorSettings.DEFAULT_ACCENT_COLOR,
  themePreset: EditorThemePreset = EditorThemePreset.OLIVE_OBSIDIAN,
  uiStyle: UiStyleVersion = UiStyleVersion.V1_DEFAULT,
  barPreferences: BarStylePreferences = BarStylePreferences(),
  selectionDragConfig: SelectionDragConfig = SelectionDragConfig.DEFAULT,
  iconLabelBehavior: IconLabelBehavior = IconLabelBehavior.REVEAL_ON_TAP,
  editorSettings: EditorSettings = EditorSettings(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val normalizedAccent = accent.lowercase()
  val caAccent = remember(normalizedAccent, themePreset) { CaAccent.fromKey(normalizedAccent, themePreset) }
  val useDynamic = normalizedAccent == EditorSettings.ACCENT_DYNAMIC || dynamicColor
  val seedColor =
    if (normalizedAccent == EditorSettings.ACCENT_CUSTOM) Color(accentColor or 0xFF000000L)
    else null

  val wallpaperScheme =
    if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      null
    }
  val resolvedExpressiveScheme =
    when {
      seedColor != null -> remember(seedColor, darkTheme) { expressiveColorSchemeFromSeed(seedColor, darkTheme) }
      wallpaperScheme != null -> wallpaperScheme
      else -> remember(caAccent, darkTheme) { expressiveColorScheme(caAccent, darkTheme) }
    }

  val basePalette = remember(darkTheme, themePreset, uiStyle) { paletteFor(darkTheme, themePreset, uiStyle) }
  val palette =
    remember(basePalette, resolvedExpressiveScheme, uiStyle) {
      if (uiStyle == UiStyleVersion.V2_MINIMAL_OLED) basePalette
      else basePalette.bridgedTo(resolvedExpressiveScheme)
    }
  val colorScheme =
    if (uiStyle == UiStyleVersion.V2_MINIMAL_OLED && seedColor == null && !useDynamic) {
      if (darkTheme) remember(palette) { buildDarkColorScheme(palette) }
      else remember(palette) { buildLightColorScheme(palette) }
    } else {
      resolvedExpressiveScheme
    }

  CompositionLocalProvider(
    LocalIdePalette provides palette,
    LocalBarStylePreferences provides barPreferences,
    LocalPopupMenuStyle provides barPreferences.homeMenu,
    LocalSelectionDragConfig provides selectionDragConfig.normalized(),
    LocalIconLabelBehavior provides iconLabelBehavior,
    LocalEditorSettings provides editorSettings,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = EdgeCodeTypography,
      shapes = ExpressiveShapes,
      content = content,
    )
  }
}
