package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalIdePalette = staticCompositionLocalOf { DarkIdePalette }

private val EdgeDarkColorScheme =
  darkColorScheme(
    primary = OliveCtaBg,
    onPrimary = OliveCtaFg,
    primaryContainer = OliveActivePill,
    onPrimaryContainer = OliveLimeText,
    secondary = Color(0xFFC2CBAF),
    onSecondary = Color(0xFF2B331F),
    secondaryContainer = OliveSelectedRow,
    onSecondaryContainer = Color(0xFFE6ECD8),
    tertiary = Color(0xFFA0D0CB),
    onTertiary = Color(0xFF003734),
    background = OliveBg,
    onBackground = Color(0xFFECEEE6),
    surface = OliveBg,
    onSurface = Color(0xFFECEEE6),
    surfaceContainerLowest = SlateEditorBg,
    surfaceContainerLow = OliveSurfaceLow,
    surfaceContainer = OliveSurface,
    surfaceContainerHigh = OliveSurfaceHigh,
    surfaceContainerHighest = OliveSelectedRow,
    outline = OliveBorder,
    outlineVariant = Color(0xFF2B3024),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
  )

private val EdgeLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF4D6618),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E8A8),
    onPrimaryContainer = Color(0xFF172200),
    secondary = Color(0xFF5A624B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFE6CA),
    onSecondaryContainer = Color(0xFF181E0C),
    tertiary = Color(0xFF396662),
    onTertiary = Color.White,
    background = Color(0xFFF6F7F2),
    onBackground = Color(0xFF1B1C17),
    surface = Color(0xFFF6F7F2),
    onSurface = Color(0xFF1B1C17),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2EA),
    surfaceContainer = Color(0xFFE9ECE2),
    surfaceContainerHigh = Color(0xFFE2E6DA),
    surfaceContainerHighest = Color(0xFFD9DDD0),
    outline = Color(0xFF75796C),
    outlineVariant = Color(0xFFC5C9BA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
  )

@Composable
fun EdgeCodeTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> EdgeDarkColorScheme
      else -> EdgeLightColorScheme
    }

  val palette = if (darkTheme) DarkIdePalette else LightIdePalette

  CompositionLocalProvider(LocalIdePalette provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = EdgeCodeTypography,
      content = content,
    )
  }
}
