package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.security.EditorSettings
import kotlin.math.abs

/**
 * CodeAssist Material 3 Expressive accents, tonal palettes, seed scheme generator,
 * and expressive shape/radius/spacing tokens.
 */
enum class CaAccent(
  val key: String,
  val label: String,
  val swatchColor: Color,
) {
  Lime(EditorSettings.ACCENT_LIME, "Lime", Color(0xFFCDF14A)),
  Violet(EditorSettings.ACCENT_VIOLET, "Violet", Color(0xFF8B5CF6)),
  Teal(EditorSettings.ACCENT_TEAL, "Teal", Color(0xFF55D6F5)),
  Orange(EditorSettings.ACCENT_ORANGE, "Orange", Color(0xFFD98A3D)),
  Pumpkin(EditorSettings.ACCENT_PUMPKIN, "Pumpkin", Color(0xFFFF9A3C)),
  ;

  companion object {
    fun fromKey(raw: String?, fallbackPreset: EditorThemePreset = EditorThemePreset.OLIVE_OBSIDIAN): CaAccent =
      entries.firstOrNull { it.key.equals(raw, ignoreCase = true) }
        ?: when (fallbackPreset) {
          EditorThemePreset.OLIVE_OBSIDIAN -> Lime
          EditorThemePreset.SLATE_CARBON -> Teal
          EditorThemePreset.MIDNIGHT_NOIR -> Violet
          EditorThemePreset.AMBER_DUSK -> Orange
        }
  }
}

@Immutable
object CaSpacing {
  val s1 = 4.dp
  val s2 = 8.dp
  val s3 = 12.dp
  val s4 = 16.dp
  val s5 = 20.dp
  val s6 = 24.dp
  val s7 = 28.dp
  val s8 = 32.dp
  val s10 = 40.dp
  val s12 = 48.dp
}

@Immutable
object CaRadius {
  val xs = 6.dp
  val sm = 9.dp
  val control = 12.dp
  val md = 14.dp
  val lg = 18.dp
  val xl = 24.dp
  val sheet = 26.dp
  val pill = 999.dp
}

object Ca {
  val spacing get() = CaSpacing
  val radius get() = CaRadius
}

object CaMotion {
  const val FAST = 150
  const val MEDIUM = 240
}

val ExpressiveShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(26.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

private class AccentRoles(
  val primary: Color,
  val onPrimary: Color,
  val primaryContainer: Color,
  val onPrimaryContainer: Color,
  val inversePrimary: Color,
  val secondary: Color,
  val onSecondary: Color,
  val secondaryContainer: Color,
  val onSecondaryContainer: Color,
  val tertiary: Color,
  val onTertiary: Color,
  val tertiaryContainer: Color,
  val onTertiaryContainer: Color,
)

private fun ColorScheme.withAccent(a: AccentRoles): ColorScheme = copy(
  primary = a.primary,
  onPrimary = a.onPrimary,
  primaryContainer = a.primaryContainer,
  onPrimaryContainer = a.onPrimaryContainer,
  inversePrimary = a.inversePrimary,
  secondary = a.secondary,
  onSecondary = a.onSecondary,
  secondaryContainer = a.secondaryContainer,
  onSecondaryContainer = a.onSecondaryContainer,
  tertiary = a.tertiary,
  onTertiary = a.onTertiary,
  tertiaryContainer = a.tertiaryContainer,
  onTertiaryContainer = a.onTertiaryContainer,
  surfaceTint = a.primary,
)

private val NeutralLight = lightColorScheme(
  background = Color(0xFFFAF9FD),
  onBackground = Color(0xFF1B1B1F),
  surface = Color(0xFFFAF9FD),
  onSurface = Color(0xFF1B1B1F),
  surfaceVariant = Color(0xFFE3E1EC),
  onSurfaceVariant = Color(0xFF46464F),
  surfaceTint = Color(0xFF6E4CE0),
  inverseSurface = Color(0xFF303034),
  inverseOnSurface = Color(0xFFF2F0F4),
  outline = Color(0xFF767680),
  outlineVariant = Color(0xFFC7C5D0),
  scrim = Color(0xFF000000),
  surfaceBright = Color(0xFFFAF9FD),
  surfaceDim = Color(0xFFDAD9DE),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF4F3F7),
  surfaceContainer = Color(0xFFEEEDF2),
  surfaceContainerHigh = Color(0xFFE9E7EC),
  surfaceContainerHighest = Color(0xFFE3E2E6),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
)

private val NeutralDark = darkColorScheme(
  background = Color(0xFF000000),
  onBackground = Color(0xFFF5F6FA),
  surface = Color(0xFF000000),
  onSurface = Color(0xFFF5F6FA),
  surfaceVariant = Color(0xFF262730),
  onSurfaceVariant = Color(0xFFAEB2C0),
  surfaceTint = Color(0xFFCFBCFF),
  inverseSurface = Color(0xFFF5F6FA),
  inverseOnSurface = Color(0xFF111115),
  outline = Color(0xFF7B8090),
  outlineVariant = Color(0xFF23252E),
  scrim = Color(0xFF000000),
  surfaceBright = Color(0xFF1F2028),
  surfaceDim = Color(0xFF000000),
  surfaceContainerLowest = Color(0xFF000000),
  surfaceContainerLow = Color(0xFF08080A),
  surfaceContainer = Color(0xFF0E0E12),
  surfaceContainerHigh = Color(0xFF16161C),
  surfaceContainerHighest = Color(0xFF1E1E26),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
)

private val VioletLight = AccentRoles(
  primary = Color(0xFF6E4CE0),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFE9DDFF),
  onPrimaryContainer = Color(0xFF22005C),
  inversePrimary = Color(0xFFCFBCFF),
  secondary = Color(0xFF625B71),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFE8DEF8),
  onSecondaryContainer = Color(0xFF1E192B),
  tertiary = Color(0xFFB0468E),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFFFD8EC),
  onTertiaryContainer = Color(0xFF3B0027),
)

private val VioletDark = AccentRoles(
  primary = Color(0xFFCFBCFF),
  onPrimary = Color(0xFF38158A),
  primaryContainer = Color(0xFF5335C0),
  onPrimaryContainer = Color(0xFFE9DDFF),
  inversePrimary = Color(0xFF6E4CE0),
  secondary = Color(0xFFCBC2DB),
  onSecondary = Color(0xFF332D41),
  secondaryContainer = Color(0xFF4A4458),
  onSecondaryContainer = Color(0xFFE8DEF8),
  tertiary = Color(0xFFFFAED6),
  onTertiary = Color(0xFF5E1147),
  tertiaryContainer = Color(0xFF7C2C60),
  onTertiaryContainer = Color(0xFFFFD8EC),
)

private val TealLight = AccentRoles(
  primary = Color(0xFF00687A),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFACEDFF),
  onPrimaryContainer = Color(0xFF001F27),
  inversePrimary = Color(0xFF55D6F5),
  secondary = Color(0xFF4B6269),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFCDE7EF),
  onSecondaryContainer = Color(0xFF061F25),
  tertiary = Color(0xFF5B5C7E),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFE1E0FF),
  onTertiaryContainer = Color(0xFF171937),
)

private val TealDark = AccentRoles(
  primary = Color(0xFF55D6F5),
  onPrimary = Color(0xFF003641),
  primaryContainer = Color(0xFF004E5D),
  onPrimaryContainer = Color(0xFFACEDFF),
  inversePrimary = Color(0xFF00687A),
  secondary = Color(0xFFB1CBD3),
  onSecondary = Color(0xFF1C343A),
  secondaryContainer = Color(0xFF334A51),
  onSecondaryContainer = Color(0xFFCDE7EF),
  tertiary = Color(0xFFC3C3EB),
  onTertiary = Color(0xFF2C2E4D),
  tertiaryContainer = Color(0xFF434465),
  onTertiaryContainer = Color(0xFFE1E0FF),
)

private val OrangeLight = AccentRoles(
  primary = Color(0xFF9A4A00),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFFFDCBE),
  onPrimaryContainer = Color(0xFF2E1500),
  inversePrimary = Color(0xFFFFB77C),
  secondary = Color(0xFF745943),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFFFDCBE),
  onSecondaryContainer = Color(0xFF2A1706),
  tertiary = Color(0xFF5B6236),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFDFE7B0),
  onTertiaryContainer = Color(0xFF191E00),
)

private val OrangeDark = AccentRoles(
  primary = Color(0xFFFFB77C),
  onPrimary = Color(0xFF4D2700),
  primaryContainer = Color(0xFF6D3900),
  onPrimaryContainer = Color(0xFFFFDCBE),
  inversePrimary = Color(0xFF9A4A00),
  secondary = Color(0xFFE3C0A4),
  onSecondary = Color(0xFF422C19),
  secondaryContainer = Color(0xFF5B422D),
  onSecondaryContainer = Color(0xFFFFDCBE),
  tertiary = Color(0xFFC3CB97),
  onTertiary = Color(0xFF2D330D),
  tertiaryContainer = Color(0xFF434A21),
  onTertiaryContainer = Color(0xFFDFE7B0),
)

private val LimeLight = lightColorScheme(
  primary = Color(0xFF465700),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFC7EE45),
  onPrimaryContainer = Color(0xFF131F00),
  inversePrimary = Color(0xFFCDF14A),
  secondary = Color(0xFF5A6146),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFDEE5C3),
  onSecondaryContainer = Color(0xFF181E08),
  tertiary = Color(0xFF6C3E85),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFF2D9FF),
  onTertiaryContainer = Color(0xFF280B3B),
  background = Color(0xFFFBFAED),
  onBackground = Color(0xFF1B1C15),
  surface = Color(0xFFFBFAED),
  onSurface = Color(0xFF1B1C15),
  surfaceVariant = Color(0xFFE4E3D7),
  onSurfaceVariant = Color(0xFF45483A),
  surfaceTint = Color(0xFF465700),
  inverseSurface = Color(0xFF303129),
  inverseOnSurface = Color(0xFFF2F1E4),
  outline = Color(0xFF767966),
  outlineVariant = Color(0xFFC6C9B2),
  scrim = Color(0xFF000000),
  surfaceBright = Color(0xFFFBFAED),
  surfaceDim = Color(0xFFDCDBCE),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF5F4E7),
  surfaceContainer = Color(0xFFEFEEE2),
  surfaceContainerHigh = Color(0xFFEAE9DC),
  surfaceContainerHighest = Color(0xFFE4E3D7),
  error = Color(0xFF8F4C38),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDBD1),
  onErrorContainer = Color(0xFF3A0B01),
)

private val LimeDark = darkColorScheme(
  primary = Color(0xFFCDF14A),
  onPrimary = Color(0xFF232D00),
  primaryContainer = Color(0xFF344200),
  onPrimaryContainer = Color(0xFFE4FF74),
  inversePrimary = Color(0xFF465700),
  secondary = Color(0xFFC2C9A5),
  onSecondary = Color(0xFF2C331B),
  secondaryContainer = Color(0xFF424937),
  onSecondaryContainer = Color(0xFFDEE5C3),
  tertiary = Color(0xFFDEB8F5),
  onTertiary = Color(0xFF3B1E4F),
  tertiaryContainer = Color(0xFF53306B),
  onTertiaryContainer = Color(0xFFF2D9FF),
  background = Color(0xFF12140C),
  onBackground = Color(0xFFE3E3D6),
  surface = Color(0xFF12140C),
  onSurface = Color(0xFFE3E3D6),
  surfaceVariant = Color(0xFF45483A),
  onSurfaceVariant = Color(0xFFC6C9B2),
  surfaceTint = Color(0xFFCDF14A),
  inverseSurface = Color(0xFFE3E3D6),
  inverseOnSurface = Color(0xFF303129),
  outline = Color(0xFF90937E),
  outlineVariant = Color(0xFF45483A),
  scrim = Color(0xFF000000),
  surfaceBright = Color(0xFF383A2F),
  surfaceDim = Color(0xFF12140C),
  surfaceContainerLowest = Color(0xFF0C0F07),
  surfaceContainerLow = Color(0xFF1A1C14),
  surfaceContainer = Color(0xFF1F2118),
  surfaceContainerHigh = Color(0xFF292B22),
  surfaceContainerHighest = Color(0xFF34362C),
  error = Color(0xFFFFB4A2),
  onError = Color(0xFF561F0F),
  errorContainer = Color(0xFF723523),
  onErrorContainer = Color(0xFFFFDBD1),
)

private val PumpkinLight = lightColorScheme(
  primary = Color(0xFF964900),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFFFDCC2),
  onPrimaryContainer = Color(0xFF311300),
  inversePrimary = Color(0xFFFFB77A),
  secondary = Color(0xFF755845),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFFFDCC6),
  onSecondaryContainer = Color(0xFF2B1709),
  tertiary = Color(0xFF704BA2),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFEDDCFF),
  onTertiaryContainer = Color(0xFF290055),
  background = Color(0xFFFFF8F5),
  onBackground = Color(0xFF221A16),
  surface = Color(0xFFFFF8F5),
  onSurface = Color(0xFF221A16),
  surfaceVariant = Color(0xFFF2DFD3),
  onSurfaceVariant = Color(0xFF52443B),
  surfaceTint = Color(0xFF964900),
  inverseSurface = Color(0xFF382E2A),
  inverseOnSurface = Color(0xFFFFEDE4),
  outline = Color(0xFF84746A),
  outlineVariant = Color(0xFFD7C3B7),
  scrim = Color(0xFF000000),
  surfaceBright = Color(0xFFFFF8F5),
  surfaceDim = Color(0xFFE7D7CF),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFFFF1EA),
  surfaceContainer = Color(0xFFFCEBE2),
  surfaceContainerHigh = Color(0xFFF6E5DC),
  surfaceContainerHighest = Color(0xFFF0DFD7),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
)

private val PumpkinDark = darkColorScheme(
  primary = Color(0xFFFFB77A),
  onPrimary = Color(0xFF4F2500),
  primaryContainer = Color(0xFF8A4600),
  onPrimaryContainer = Color(0xFFFFDCC2),
  inversePrimary = Color(0xFF964900),
  secondary = Color(0xFFE5BFA8),
  onSecondary = Color(0xFF432B1C),
  secondaryContainer = Color(0xFF5C4131),
  onSecondaryContainer = Color(0xFFFFDCC6),
  tertiary = Color(0xFFD7BAFF),
  onTertiary = Color(0xFF3F1B6B),
  tertiaryContainer = Color(0xFF573484),
  onTertiaryContainer = Color(0xFFEDDCFF),
  background = Color(0xFF161216),
  onBackground = Color(0xFFEAE0E4),
  surface = Color(0xFF161216),
  onSurface = Color(0xFFEAE0E4),
  surfaceVariant = Color(0xFF4A4045),
  onSurfaceVariant = Color(0xFFCEC2C8),
  surfaceTint = Color(0xFFFFB77A),
  inverseSurface = Color(0xFFEAE0E4),
  inverseOnSurface = Color(0xFF342F33),
  outline = Color(0xFF978C92),
  outlineVariant = Color(0xFF4A4045),
  scrim = Color(0xFF000000),
  surfaceBright = Color(0xFF3D373C),
  surfaceDim = Color(0xFF161216),
  surfaceContainerLowest = Color(0xFF110D11),
  surfaceContainerLow = Color(0xFF1F1A1E),
  surfaceContainer = Color(0xFF231E22),
  surfaceContainerHigh = Color(0xFF2E282C),
  surfaceContainerHighest = Color(0xFF393337),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
)

fun expressiveColorScheme(accent: CaAccent, dark: Boolean): ColorScheme {
  if (accent == CaAccent.Lime) return if (dark) LimeDark else LimeLight
  if (accent == CaAccent.Pumpkin) return if (dark) PumpkinDark else PumpkinLight
  val roles =
    when (accent) {
      CaAccent.Violet -> if (dark) VioletDark else VioletLight
      CaAccent.Teal -> if (dark) TealDark else TealLight
      CaAccent.Orange -> if (dark) OrangeDark else OrangeLight
      CaAccent.Lime, CaAccent.Pumpkin -> error("handled above")
    }
  return (if (dark) NeutralDark else NeutralLight).withAccent(roles)
}

private class Tones(private val hue: Float, private val sat: Float) {
  fun tone(t: Int): Color = hslColor(hue, sat, t / 100f)
}

/**
 * Self-contained Material 3 Expressive tonal palette generator from a custom seed color
 * (ported from CodeAssist's SeedScheme.kt).
 */
fun expressiveColorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
  val (h, s0, _) = seed.toHsl()
  val chroma = if (s0 < 0.05f) 0.10f else s0
  val primary = Tones(h, chroma.coerceIn(0.32f, 0.95f))
  val secondary = Tones(h, (chroma * 0.35f).coerceIn(0.10f, 0.40f))
  val tertiary = Tones((h + 60f) % 360f, (chroma * 0.55f).coerceIn(0.28f, 0.75f))
  val neutral = Tones(h, 0.04f)
  val neutralV = Tones(h, 0.09f)
  val error = Tones(15f, 0.80f)

  return if (dark) {
    darkColorScheme(
      primary = primary.tone(80),
      onPrimary = primary.tone(20),
      primaryContainer = primary.tone(30),
      onPrimaryContainer = primary.tone(90),
      inversePrimary = primary.tone(40),
      secondary = secondary.tone(80),
      onSecondary = secondary.tone(20),
      secondaryContainer = secondary.tone(30),
      onSecondaryContainer = secondary.tone(90),
      tertiary = tertiary.tone(80),
      onTertiary = tertiary.tone(20),
      tertiaryContainer = tertiary.tone(30),
      onTertiaryContainer = tertiary.tone(90),
      background = neutral.tone(6),
      onBackground = neutral.tone(90),
      surface = neutral.tone(6),
      onSurface = neutral.tone(90),
      surfaceVariant = neutralV.tone(30),
      onSurfaceVariant = neutralV.tone(80),
      surfaceTint = primary.tone(80),
      inverseSurface = neutral.tone(90),
      inverseOnSurface = neutral.tone(20),
      outline = neutralV.tone(60),
      outlineVariant = neutralV.tone(30),
      scrim = Color.Black,
      surfaceBright = neutral.tone(24),
      surfaceDim = neutral.tone(6),
      surfaceContainerLowest = neutral.tone(4),
      surfaceContainerLow = neutral.tone(10),
      surfaceContainer = neutral.tone(12),
      surfaceContainerHigh = neutral.tone(17),
      surfaceContainerHighest = neutral.tone(22),
      error = error.tone(80),
      onError = error.tone(20),
      errorContainer = error.tone(30),
      onErrorContainer = error.tone(90),
    )
  } else {
    lightColorScheme(
      primary = primary.tone(40),
      onPrimary = primary.tone(100),
      primaryContainer = primary.tone(90),
      onPrimaryContainer = primary.tone(10),
      inversePrimary = primary.tone(80),
      secondary = secondary.tone(40),
      onSecondary = secondary.tone(100),
      secondaryContainer = secondary.tone(90),
      onSecondaryContainer = secondary.tone(10),
      tertiary = tertiary.tone(40),
      onTertiary = tertiary.tone(100),
      tertiaryContainer = tertiary.tone(90),
      onTertiaryContainer = tertiary.tone(10),
      background = neutral.tone(98),
      onBackground = neutral.tone(10),
      surface = neutral.tone(98),
      onSurface = neutral.tone(10),
      surfaceVariant = neutralV.tone(90),
      onSurfaceVariant = neutralV.tone(30),
      surfaceTint = primary.tone(40),
      inverseSurface = neutral.tone(20),
      inverseOnSurface = neutral.tone(95),
      outline = neutralV.tone(50),
      outlineVariant = neutralV.tone(80),
      scrim = Color.Black,
      surfaceBright = neutral.tone(98),
      surfaceDim = neutral.tone(87),
      surfaceContainerLowest = neutral.tone(100),
      surfaceContainerLow = neutral.tone(96),
      surfaceContainer = neutral.tone(94),
      surfaceContainerHigh = neutral.tone(92),
      surfaceContainerHighest = neutral.tone(90),
      error = error.tone(40),
      onError = error.tone(100),
      errorContainer = error.tone(90),
      onErrorContainer = error.tone(10),
    )
  }
}

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
  val r = red
  val g = green
  val b = blue
  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val l = (max + min) / 2f
  val d = max - min
  if (d < 1e-5f) return Hsl(0f, 0f, l)
  val s = d / (1f - abs(2f * l - 1f))
  val h =
    when (max) {
      r -> 60f * (((g - b) / d) % 6f)
      g -> 60f * ((b - r) / d + 2f)
      else -> 60f * ((r - g) / d + 4f)
    }
  return Hsl((h + 360f) % 360f, s, l)
}

private fun hslColor(h: Float, s: Float, l: Float): Color {
  val c = (1f - abs(2f * l - 1f)) * s
  val hp = ((h % 360f) + 360f) % 360f / 60f
  val x = c * (1f - abs(hp % 2f - 1f))
  val (r1, g1, b1) =
    when {
      hp < 1f -> Triple(c, x, 0f)
      hp < 2f -> Triple(x, c, 0f)
      hp < 3f -> Triple(0f, c, x)
      hp < 4f -> Triple(0f, x, c)
      hp < 5f -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
  val m = l - c / 2f
  return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}
