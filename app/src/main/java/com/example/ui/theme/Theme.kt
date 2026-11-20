package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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

  val codeStyle =
    remember(editorSettings.codeStylePreset) {
      CodeStylePreset.fromKey(editorSettings.codeStylePreset)
    }
  val codeStyleOverrideCanvas = editorSettings.codeStyleOverrideCanvas
  val basePalette =
    remember(darkTheme, themePreset, uiStyle, codeStyle, codeStyleOverrideCanvas) {
      paletteFor(
        darkTheme = darkTheme,
        preset = themePreset,
        uiStyle = uiStyle,
        codeStyle = codeStyle,
        codeStyleOverrideCanvas = codeStyleOverrideCanvas,
      )
    }
  val palette =
    remember(basePalette, resolvedExpressiveScheme) {
      basePalette.bridgedTo(resolvedExpressiveScheme)
    }
  val colorScheme = resolvedExpressiveScheme

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
