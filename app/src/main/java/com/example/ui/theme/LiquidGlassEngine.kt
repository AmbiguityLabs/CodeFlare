package com.example.ui.theme

import android.content.Context
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Snapshot describing the underlying content sliding beneath Liquid Glass chrome:
 * - [luminance]: 0f = deep dark / night, 1f = bright daylight / white page
 * - [contentDensity]: 0f = clean blank canvas, 1f = dense text / cards / code
 * - [scrollOffsetPx]: live scroll offset so lensing caustics shift continuously as content scrolls
 */
@Immutable
data class LiquidGlassBackdropState(
  val luminance: Float = 0.12f,
  val contentDensity: Float = 0.5f,
  val scrollOffsetPx: Float = 0f,
  val accentHint: Color? = null,
)

val LocalLiquidGlassBackdrop = compositionLocalOf { LiquidGlassBackdropState() }

@Immutable
data class LiquidGlassAdaptiveColors(
  val effectiveDarkGlass: Boolean,
  val bodyTopTint: Color,
  val bodyBottomTint: Color,
  val foregroundPrimary: Color,
  val foregroundSecondary: Color,
  val activePillBg: Color,
  val activePillFg: Color,
  val specularHighlight: Color,
  val rimSubtle: Color,
  val chromaticCoolRim: Color,
  val chromaticWarmRim: Color,
  val shadowColor: Color,
  val shadowElevationDp: Dp,
  val lensStrength: Float,
)

private const val DEFAULT_LIGHT_X = -0.24f
private const val DEFAULT_LIGHT_Y = -0.88f

private const val LIQUID_GLASS_LENS_AGSL = """
uniform shader composable;
uniform float2 size;
uniform float radius;
uniform float lensStrength;
uniform float thickness;
uniform float scrollShift;
uniform float2 lightDir;

float sdRoundRect(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
  if (size.x <= 1.0 || size.y <= 1.0 || lensStrength <= 0.001) {
    return composable.eval(coord);
  }
  float2 halfSize = size * 0.5;
  float safeRadius = min(radius, min(halfSize.x, halfSize.y));
  float2 rel = coord - halfSize;
  float sd = sdRoundRect(rel, halfSize, safeRadius);
  if (sd > 0.0) {
    return composable.eval(coord);
  }

  float rimBand = min(min(halfSize.x, halfSize.y) * 0.62, 34.0 * thickness);
  float distIn = clamp(-sd, 0.0, rimBand);
  float edgeRatio = 1.0 - (distIn / max(rimBand, 1.0));
  float bend = edgeRatio * edgeRatio * lensStrength * thickness;

  float2 gradDir = normalize(rel + float2(0.0001, 0.0001));
  float causticWave = sin((coord.y + scrollShift) * 0.08 + lightDir.x * 2.2) * 0.25 + 0.75;
  float2 warp = gradDir * (bend * 13.0 * causticWave) - lightDir * (bend * 3.5);

  float disp = bend * 2.6;
  float2 uvR = clamp(coord - warp - gradDir * disp, float2(0.0), size - float2(1.0));
  float2 uvG = clamp(coord - warp, float2(0.0), size - float2(1.0));
  float2 uvB = clamp(coord - warp + gradDir * disp, float2(0.0), size - float2(1.0));

  half4 sR = composable.eval(uvR);
  half4 sG = composable.eval(uvG);
  half4 sB = composable.eval(uvB);
  return half4(sR.r, sG.g, sB.b, sG.a);
}
"""

private fun isRunningInRobolectricOrPreview(): Boolean =
  Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

/**
 * Pure optical parameter solver (unit-testable cleanly without Compose runtime).
 * Computes clear <-> tinted body alpha, content-density shadow boost, lensing bend strength,
 * and automatic light/dark glass polarity flip when bright vs dark backdrops slide underneath.
 */
internal fun computeLiquidGlassParams(
  tintFraction: Float,
  thickness: Float,
  backdropLuminance: Float,
  contentDensity: Float,
  paletteIsDark: Boolean,
  emergenceProgress: Float,
): Pair<Float, Boolean> {
  val tint = tintFraction.coerceIn(0f, 1f)
  val density = contentDensity.coerceIn(0f, 1f)
  val lum = backdropLuminance.coerceIn(0f, 1f)
  val effectiveDark =
    when {
      lum >= 0.64f -> false
      lum <= 0.34f -> true
      else -> paletteIsDark
    }
  // Clear glass bends light strongly (high lensing); sliding toward Tinted calms warp for solid contrast
  val baseLens = ((1f - tint * 0.72f) * thickness.coerceIn(0.6f, 2.2f) * (0.65f + 0.35f * (1f - density * 0.3f)))
  val emergedLens = (baseLens * emergenceProgress.coerceIn(0f, 1f)).coerceIn(0f, 2.0f)
  return emergedLens to effectiveDark
}

@Composable
fun rememberLiquidGlassLightDirection(enabled: Boolean): Offset {
  val context = LocalContext.current
  val inspection = LocalInspectionMode.current
  var lightX by remember { mutableFloatStateOf(DEFAULT_LIGHT_X) }
  var lightY by remember { mutableFloatStateOf(DEFAULT_LIGHT_Y) }

  DisposableEffect(enabled, context, inspection) {
    if (!enabled || inspection || isRunningInRobolectricOrPreview()) {
      lightX = DEFAULT_LIGHT_X
      lightY = DEFAULT_LIGHT_Y
      onDispose {}
    } else {
      val sm = runCatching { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }.getOrNull()
      val sensor =
        sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
          ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
      if (sm == null || sensor == null) {
        lightX = DEFAULT_LIGHT_X
        lightY = DEFAULT_LIGHT_Y
        onDispose {}
      } else {
        val listener =
          object : SensorEventListener {
            private var fx = DEFAULT_LIGHT_X
            private var fy = DEFAULT_LIGHT_Y

            override fun onSensorChanged(event: SensorEvent) {
              if (event.values.size < 2) return
              // Invert X tilt so tilting right sweeps reflection across glass opposite to gravity
              val nx = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
              val ny = (-event.values[1] / 9.81f).coerceIn(-1f, 1f)
              val mag = hypot(nx.toDouble(), ny.toDouble()).toFloat().coerceAtLeast(0.05f)
              val targetX = (nx / mag).coerceIn(-1f, 1f)
              val targetY = (ny / mag).coerceIn(-1f, 1f)
              fx += (targetX - fx) * 0.16f
              fy += (targetY - fy) * 0.16f
              lightX = fx
              lightY = fy
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
          }
        val registered = runCatching { sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }.getOrDefault(false)
        onDispose {
          if (registered) {
            runCatching { sm.unregisterListener(listener) }
          }
        }
      }
    }
  }

  return Offset(lightX, lightY)
}

@Composable
fun rememberLiquidGlassAdaptiveColors(
  palette: IdePalette,
  tintFraction: Float,
  thickness: Float = 1.0f,
  backdropOverride: LiquidGlassBackdropState? = null,
  emergenceProgress: Float = 1f,
  surfaceOpacityMultiplier: Float = 1f,
): LiquidGlassAdaptiveColors {
  val backdrop = backdropOverride ?: LocalLiquidGlassBackdrop.current
  val tint = tintFraction.coerceIn(0f, 1f)
  val density = backdrop.contentDensity.coerceIn(0f, 1f)
  val (lensStrength, effectiveDark) =
    computeLiquidGlassParams(
      tintFraction = tint,
      thickness = thickness,
      backdropLuminance = backdrop.luminance,
      contentDensity = density,
      paletteIsDark = palette.isDark,
      emergenceProgress = emergenceProgress,
    )

  // Auto dark/light polarity flip + tint opacity boost as Clear -> Tinted or text density rises
  val clearMinAlpha = if (effectiveDark) 0.24f else 0.30f
  val tintedMaxAlpha = if (effectiveDark) 0.90f else 0.92f
  val densityContrastBoost = density * (1f - tint) * 0.16f
  val thicknessContrastBoost = ((thickness - 1f).coerceAtLeast(0f)) * 0.10f
  val resolvedBodyAlpha =
    ((clearMinAlpha + (tintedMaxAlpha - clearMinAlpha) * tint + densityContrastBoost + thicknessContrastBoost) * surfaceOpacityMultiplier)
      .coerceIn(0.14f, 0.98f)

  val accentBase = backdrop.accentHint ?: palette.activePillBg
  val darkTop = lerp(Color(0xFF282B36), lerp(palette.chromeSurface, accentBase, 0.24f), 0.35f + 0.45f * tint)
  val darkBottom = lerp(Color(0xFF101218), palette.chromeBg, 0.30f + 0.50f * tint)
  val lightTop = lerp(Color(0xFFFCFDFF), lerp(palette.chromeSurface, accentBase, 0.18f), 0.25f + 0.45f * tint)
  val lightBottom = lerp(Color(0xFFEAECEF), palette.chromeSurface, 0.30f + 0.50f * tint)

  val bodyTop by animateColorAsState(
    targetValue = (if (effectiveDark) darkTop else lightTop).copy(alpha = (resolvedBodyAlpha * 0.96f).coerceIn(0.12f, 0.98f)),
    animationSpec = tween(220),
    label = "lg_body_top",
  )
  val bodyBottom by animateColorAsState(
    targetValue = (if (effectiveDark) darkBottom else lightBottom).copy(alpha = (resolvedBodyAlpha * 1.04f).coerceIn(0.16f, 0.99f)),
    animationSpec = tween(220),
    label = "lg_body_bottom",
  )
  val fgPrimary by animateColorAsState(
    targetValue = if (effectiveDark) Color(0xFFF6F8FC) else Color(0xFF11131A),
    animationSpec = tween(210),
    label = "lg_fg_primary",
  )
  val fgSecondary by animateColorAsState(
    targetValue = if (effectiveDark) Color(0xFFD0D6E2) else Color(0xFF3E4454),
    animationSpec = tween(210),
    label = "lg_fg_secondary",
  )
  val pillBg by animateColorAsState(
    targetValue = palette.activePillBg.copy(alpha = (0.48f + 0.42f * tint).coerceIn(0.35f, 0.94f)),
    animationSpec = tween(210),
    label = "lg_pill_bg",
  )
  val pillFg by animateColorAsState(
    targetValue = palette.activePillFg,
    animationSpec = tween(210),
    label = "lg_pill_fg",
  )

  // Darker shadow under dense code/text or thicker menus; lighter shadow over plain bright pages
  val baseShadowAlpha =
    ((0.14f + density * 0.34f + (thickness - 1f).coerceAtLeast(0f) * 0.18f) *
      (if (effectiveDark) 1.35f else 0.72f))
      .coerceIn(0.08f, 0.80f)
  val elevationDp =
    ((4.5f + density * 4.5f + (thickness - 1f).coerceAtLeast(0f) * 8.5f) *
      emergenceProgress.coerceIn(0f, 1f))
      .coerceIn(0f, 20f)
      .dp

  return LiquidGlassAdaptiveColors(
    effectiveDarkGlass = effectiveDark,
    bodyTopTint = bodyTop,
    bodyBottomTint = bodyBottom,
    foregroundPrimary = fgPrimary,
    foregroundSecondary = fgSecondary,
    activePillBg = pillBg,
    activePillFg = pillFg,
    specularHighlight = Color.White.copy(alpha = if (effectiveDark) 0.76f else 0.94f),
    rimSubtle =
      if (effectiveDark) Color.White.copy(alpha = 0.22f)
      else Color(0xFF1E293B).copy(alpha = 0.16f),
    chromaticCoolRim = lerp(palette.ctaBg, Color(0xFF60A5FA), 0.55f).copy(alpha = (0.28f * lensStrength).coerceIn(0f, 0.52f)),
    chromaticWarmRim = lerp(palette.featuredCardFg, Color(0xFFF59E0B), 0.45f).copy(alpha = (0.20f * lensStrength).coerceIn(0f, 0.38f)),
    shadowColor = Color.Black.copy(alpha = baseShadowAlpha),
    shadowElevationDp = elevationDp,
    lensStrength = lensStrength,
  )
}

/**
 * Real-time Liquid Glass surface modifier implementing:
 * 1. Optical AGSL lensing warp on API 33+ + chromatic Fresnel edge refraction across all APIs (24+)
 * 2. Stacked virtual directional lighting responsive to phone tilt
 * 3. Content-aware shadow depth & automatic light/dark polarity flip
 * 4. Clear <-> Tinted control + thickness multiplier (buttons 1.0x -> dock 1.25x -> open menus 1.65x)
 */
fun Modifier.liquidGlassSurface(
  shape: Shape = RoundedCornerShape(20.dp),
  cornerRadius: Dp = 20.dp,
  thickness: Float = 1.0f,
  tintFraction: Float? = null,
  enabled: Boolean? = null,
  backdropOverride: LiquidGlassBackdropState? = null,
  emergenceProgress: Float = 1f,
  surfaceOpacityMultiplier: Float = 1f,
  borderOpacityMultiplier: Float = 1f,
  drawShadow: Boolean = true,
): Modifier = composed {
  val palette = LocalIdePalette.current
  val settings = LocalEditorSettings.current
  val isEnabled = enabled ?: settings.liquidGlassEnabled
  val tint = (tintFraction ?: settings.liquidGlassTint).coerceIn(0f, 1f)
  val backdrop = backdropOverride ?: LocalLiquidGlassBackdrop.current

  if (!isEnabled) {
    return@composed this
      .clip(shape)
      .drawBehind {
        drawRect(palette.chromeSurface.copy(alpha = surfaceOpacityMultiplier.coerceIn(0.2f, 1f)))
      }
  }

  val adaptive =
    rememberLiquidGlassAdaptiveColors(
      palette = palette,
      tintFraction = tint,
      thickness = thickness,
      backdropOverride = backdrop,
      emergenceProgress = emergenceProgress,
      surfaceOpacityMultiplier = surfaceOpacityMultiplier,
    )
  val lightDir = rememberLiquidGlassLightDirection(settings.liquidGlassMotionHighlights)
  val density = LocalDensity.current
  val cornerPx = with(density) { cornerRadius.toPx() }
  var boxSize by remember { mutableStateOf(IntSize.Zero) }

  val runtimeShader =
    remember {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isRunningInRobolectricOrPreview()) {
        runCatching { RuntimeShader(LIQUID_GLASS_LENS_AGSL) }.getOrNull()
      } else {
        null
      }
    }

  val lensRenderEffect =
    remember(
      runtimeShader,
      boxSize,
      cornerPx,
      adaptive.lensStrength,
      thickness,
      backdrop.scrollOffsetPx,
      lightDir,
    ) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        runtimeShader != null &&
        boxSize.width > 4 &&
        boxSize.height > 4 &&
        adaptive.lensStrength > 0.04f
      ) {
        runCatching {
          runtimeShader.setFloatUniform("size", boxSize.width.toFloat(), boxSize.height.toFloat())
          runtimeShader.setFloatUniform("radius", cornerPx)
          runtimeShader.setFloatUniform("lensStrength", adaptive.lensStrength)
          runtimeShader.setFloatUniform("thickness", thickness)
          runtimeShader.setFloatUniform("scrollShift", backdrop.scrollOffsetPx)
          runtimeShader.setFloatUniform("lightDir", lightDir.x, lightDir.y)
          AndroidRenderEffect.createRuntimeShaderEffect(runtimeShader, "composable").asComposeRenderEffect()
        }.getOrNull()
      } else {
        null
      }
    }

  val borderScale = borderOpacityMultiplier.coerceIn(0f, 1.4f)
  val emerge = emergenceProgress.coerceIn(0f, 1f)
  val emergeFlash = sin(emerge * PI).toFloat().coerceIn(0f, 1f) * 0.22f

  this
    .onSizeChanged { boxSize = it }
    .then(
      if (drawShadow && adaptive.shadowElevationDp > 0.5.dp) {
        Modifier.shadow(
          elevation = adaptive.shadowElevationDp,
          shape = shape,
          ambientColor = adaptive.shadowColor,
          spotColor = adaptive.shadowColor,
        )
      } else {
        Modifier
      },
    )
    .clip(shape)
    .graphicsLayer {
      if (lensRenderEffect != null) {
        renderEffect = lensRenderEffect
      }
    }
    .drawWithContent {
      val w = size.width
      val h = size.height
      if (w <= 0f || h <= 0f) {
        drawContent()
        return@drawWithContent
      }
      val radPx = cornerPx.coerceAtMost(minOf(w, h) * 0.5f)
      val cr = CornerRadius(radPx, radPx)

      // 1. Glass tinted / clear core body
      drawRoundRect(
        brush = Brush.verticalGradient(listOf(adaptive.bodyTopTint, adaptive.bodyBottomTint)),
        cornerRadius = cr,
      )

      // 2. Stacked internal directional light reflection that moves with phone tilt
      val lightCenter =
        Offset(
          x = w * (0.5f + lightDir.x * 0.42f),
          y = h * (0.18f + lightDir.y * 0.26f),
        )
      val sheenAlpha = ((0.14f + 0.16f * adaptive.lensStrength) * emerge + emergeFlash).coerceIn(0f, 0.45f)
      if (sheenAlpha > 0.005f) {
        drawRoundRect(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  adaptive.specularHighlight.copy(alpha = sheenAlpha),
                  adaptive.specularHighlight.copy(alpha = sheenAlpha * 0.28f),
                  Color.Transparent,
                ),
              center = lightCenter,
              radius = maxOf(w, h) * 0.85f,
            ),
          cornerRadius = cr,
        )
      }

      // 3. Draw child icons/labels crisp above glass fill
      drawContent()

      // 4. Chromatic edge lensing rim + tilt-angled Fresnel specular stroke (gives clear shape w/o solid box)
      if (borderScale > 0.01f && adaptive.lensStrength > 0.02f) {
        val causticStrokePx = (1.6f * thickness).coerceIn(1.0f, 3.2f)
        drawRoundRect(
          brush =
            Brush.linearGradient(
              colors =
                listOf(
                  adaptive.chromaticCoolRim.copy(alpha = (adaptive.chromaticCoolRim.alpha * borderScale).coerceIn(0f, 0.65f)),
                  Color.Transparent,
                  adaptive.chromaticWarmRim.copy(alpha = (adaptive.chromaticWarmRim.alpha * borderScale).coerceIn(0f, 0.55f)),
                ),
              start = Offset(w * (0.2f + lightDir.x * 0.25f), 0f),
              end = Offset(w * (0.8f - lightDir.x * 0.25f), h),
            ),
          cornerRadius = cr,
          style = Stroke(width = causticStrokePx),
        )
      }

      if (borderScale > 0.01f) {
        val topSpecAlpha = ((0.58f + 0.24f * adaptive.lensStrength) * borderScale * emerge + emergeFlash).coerceIn(0f, 0.95f)
        val bottomEdgeAlpha = ((0.20f + 0.18f * adaptive.lensStrength) * borderScale * emerge).coerceIn(0f, 0.55f)
        drawRoundRect(
          brush =
            Brush.linearGradient(
              colors =
                listOf(
                  adaptive.specularHighlight.copy(alpha = topSpecAlpha),
                  adaptive.rimSubtle.copy(alpha = (adaptive.rimSubtle.alpha * borderScale).coerceIn(0f, 0.45f)),
                  adaptive.specularHighlight.copy(alpha = bottomEdgeAlpha),
                ),
              start = Offset(w * (0.5f + lightDir.x * 0.5f), 0f),
              end = Offset(w * (0.5f - lightDir.x * 0.5f), h),
            ),
          cornerRadius = cr,
          style = Stroke(width = 1.dp.toPx()),
        )
      }
    }
}

/**
 * Internal touch-origin illumination: pressing a Liquid Glass control illuminates it
 * *from inside* starting directly beneath the user's finger coordinate.
 * Uses PointerEventPass.Initial so normal clickable / Surface onClick handlers are unaffected.
 */
fun Modifier.liquidGlassTouchGlow(
  glowColor: Color? = null,
  enabled: Boolean = true,
): Modifier = composed {
  val palette = LocalIdePalette.current
  val settings = LocalEditorSettings.current
  if (!enabled || !settings.liquidGlassEnabled) return@composed this

  val scope = rememberCoroutineScope()
  var touchOrigin by remember { mutableStateOf(Offset.Unspecified) }
  val glowAnim = remember { Animatable(0f) }
  val resolvedColor = glowColor ?: if (palette.isDark) Color.White else palette.ctaBg

  this
    .pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val firstChange = event.changes.firstOrNull() ?: continue
          if (firstChange.pressed && glowAnim.targetValue < 0.5f) {
            touchOrigin = firstChange.position
            scope.launch {
              glowAnim.snapTo(0.28f)
              glowAnim.animateTo(1f, tween(130))
            }
          } else if (firstChange.pressed) {
            touchOrigin = firstChange.position
          } else if (event.changes.none { it.pressed } && glowAnim.targetValue > 0f) {
            scope.launch {
              glowAnim.animateTo(0f, tween(340))
            }
          }
        }
      }
    }
    .drawWithContent {
      val level = glowAnim.value
      val origin = touchOrigin
      if (level > 0.01f && origin != Offset.Unspecified) {
        val radius = (maxOf(size.width, size.height) * (0.52f + 0.42f * level)).coerceAtLeast(24f)
        drawRect(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  resolvedColor.copy(alpha = 0.46f * level),
                  resolvedColor.copy(alpha = 0.16f * level),
                  Color.Transparent,
                ),
              center = origin,
              radius = radius,
            ),
        )
      }
      drawContent()
    }
}

private val LiquidGlassEmergeEasing = CubicBezierEasing(0.18f, 0.92f, 0.26f, 1.0f)

/**
 * Optical bend-in emergence wrapper: instead of a flat fade, Liquid Glass surfaces form out of
 * the screen by ramping optical bending and gentle lens expansion (0 -> 1).
 */
@Composable
fun LiquidGlassEmergeVisibility(
  visible: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable (emergenceProgress: Float) -> Unit,
) {
  val settings = LocalEditorSettings.current
  val emergence by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(durationMillis = if (settings.liquidGlassEnabled) 250 else 150, easing = LiquidGlassEmergeEasing),
    label = "lg_emergence",
  )

  AnimatedVisibility(
    visible = visible,
    enter =
      if (settings.liquidGlassEnabled) {
        scaleIn(tween(240, easing = LiquidGlassEmergeEasing), initialScale = 0.91f) +
          fadeIn(tween(210))
      } else {
        fadeIn(tween(150))
      },
    exit =
      scaleOut(tween(150), targetScale = 0.94f) + fadeOut(tween(140)),
    modifier = modifier,
  ) {
    Box(
      modifier =
        Modifier.graphicsLayer {
          val e = emergence.coerceIn(0f, 1f)
          val s = 0.94f + 0.06f * e
          scaleX = s
          scaleY = s
        },
    ) {
      content(emergence)
    }
  }
}

/**
 * Pure hit-test solver for hold-and-slide icon scrubbing across a horizontal strip.
 * Keeps adjacent items seamless across spacing gaps while cancelling cleanly if the finger
 * drifts far outside the bar vertically.
 */
internal fun findScrubItemIndexAtOffset(
  itemBounds: Map<Int, Rect>,
  pointer: Offset,
  verticalCancelSlopPx: Float,
  horizontalGapSlopPx: Float = 28f,
): Int {
  if (itemBounds.isEmpty() || pointer == Offset.Unspecified) return -1
  val minTop = itemBounds.values.minOf { it.top }
  val maxBottom = itemBounds.values.maxOf { it.bottom }
  if (pointer.y < minTop - verticalCancelSlopPx || pointer.y > maxBottom + verticalCancelSlopPx) {
    return -1
  }
  val directHit =
    itemBounds.entries.firstOrNull { (_, rect) ->
      pointer.x in rect.left..rect.right
    }
  if (directHit != null) return directHit.key

  val minLeft = itemBounds.values.minOf { it.left } - horizontalGapSlopPx
  val maxRight = itemBounds.values.maxOf { it.right } + horizontalGapSlopPx
  if (pointer.x !in minLeft..maxRight) return -1

  return itemBounds.entries
    .minByOrNull { (_, rect) -> abs(pointer.x - rect.center.x) }
    ?.key
    ?: -1
}

@Stable
class LiquidGlassScrubState {
  internal val rootItemBounds = mutableStateMapOf<Int, Rect>()
  internal var hostOriginInRoot by mutableStateOf(Offset.Zero)
  var isScrubbing by mutableStateOf(false)
    internal set
  var hoveredIndex by mutableIntStateOf(-1)
    internal set
  var pointerOffset by mutableStateOf(Offset.Unspecified)
    internal set

  internal val lensXAnim = Animatable(0f)
  internal val lensYAnim = Animatable(0f)
  internal val lensWAnim = Animatable(0f)
  internal val lensHAnim = Animatable(0f)
  internal val lensIntensityAnim = Animatable(0f)

  internal fun updateHostCoords(coords: LayoutCoordinates) {
    if (coords.isAttached) {
      hostOriginInRoot = coords.positionInRoot()
    }
  }

  internal fun registerItem(index: Int, coords: LayoutCoordinates) {
    if (coords.isAttached) {
      rootItemBounds[index] = coords.boundsInRoot()
    }
  }

  internal fun localBounds(): Map<Int, Rect> =
    rootItemBounds.mapValues { (_, rect) ->
      rect.translate(-hostOriginInRoot.x, -hostOriginInRoot.y)
    }

  fun isItemScrubHovered(index: Int): Boolean = isScrubbing && hoveredIndex == index
}

@Composable
fun rememberLiquidGlassScrubState(): LiquidGlassScrubState = remember { LiquidGlassScrubState() }

fun Modifier.liquidGlassScrubItem(
  state: LiquidGlassScrubState,
  index: Int,
): Modifier =
  onGloballyPositioned { coords ->
    state.registerItem(index, coords)
  }

/**
 * Enables Apple-style hold-and-slide icon scrubbing across any icon strip:
 * quick taps and scroll swipes remain untouched; holding >= 185ms illuminates and detaches
 * a gliding Liquid Glass lens that slides between icons with haptic ticks and commits on release.
 */
fun Modifier.liquidGlassScrubHost(
  state: LiquidGlassScrubState,
  itemCount: Int,
  enabled: Boolean = true,
  holdActivationMs: Long = 185L,
  onCommitItem: (Int) -> Unit,
): Modifier = composed {
  if (!enabled || itemCount <= 0) return@composed this
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current
  val latestItemCount by rememberUpdatedState(itemCount)
  val latestOnCommit by rememberUpdatedState(onCommitItem)
  val verticalCancelSlopPx = with(density) { 46.dp.toPx() }
  val horizontalGapSlopPx = with(density) { 22.dp.toPx() }

  fun animateLensTo(rect: Rect, snap: Boolean) {
    scope.launch {
      if (snap) {
        state.lensXAnim.snapTo(rect.left)
        state.lensYAnim.snapTo(rect.top)
        state.lensWAnim.snapTo(rect.width)
        state.lensHAnim.snapTo(rect.height)
      } else {
        launch { state.lensXAnim.animateTo(rect.left, spring(stiffness = 520f, dampingRatio = 0.78f)) }
        launch { state.lensYAnim.animateTo(rect.top, spring(stiffness = 520f, dampingRatio = 0.82f)) }
        launch { state.lensWAnim.animateTo(rect.width, spring(stiffness = 480f, dampingRatio = 0.78f)) }
        launch { state.lensHAnim.animateTo(rect.height, spring(stiffness = 480f, dampingRatio = 0.82f)) }
      }
    }
  }

  this
    .onGloballyPositioned { state.updateHostCoords(it) }
    .pointerInput(enabled) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val downPos = down.position
        var abortedBeforeHold = false

        withTimeoutOrNull(holdActivationMs) {
          while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
            if (ch == null || !ch.pressed) {
              abortedBeforeHold = true
              break
            }
            if ((ch.position - downPos).getDistance() > viewConfiguration.touchSlop) {
              abortedBeforeHold = true
              break
            }
          }
        }

        if (abortedBeforeHold) {
          return@awaitEachGesture
        }

        val boundsMap = state.localBounds()
        val startIdx = findScrubItemIndexAtOffset(boundsMap, downPos, verticalCancelSlopPx, horizontalGapSlopPx)
        if (startIdx !in 0 until latestItemCount) {
          return@awaitEachGesture
        }

        state.isScrubbing = true
        state.hoveredIndex = startIdx
        state.pointerOffset = downPos
        boundsMap[startIdx]?.let { animateLensTo(it, snap = true) }
        scope.launch {
          state.lensIntensityAnim.snapTo(0.25f)
          state.lensIntensityAnim.animateTo(1f, tween(150))
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        var lastPosition = downPos
        try {
          while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
            if (ch == null || !ch.pressed) {
              ch?.consume()
              break
            }
            ch.consume()
            lastPosition = ch.position
            state.pointerOffset = lastPosition
            val currentBounds = state.localBounds()
            val nextIdx = findScrubItemIndexAtOffset(currentBounds, lastPosition, verticalCancelSlopPx, horizontalGapSlopPx)
            val validIdx = if (nextIdx in 0 until latestItemCount) nextIdx else -1
            if (validIdx != state.hoveredIndex) {
              state.hoveredIndex = validIdx
              if (validIdx >= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentBounds[validIdx]?.let { animateLensTo(it, snap = false) }
              }
            }
          }
        } finally {
          val committedIdx = state.hoveredIndex
          state.isScrubbing = false
          state.hoveredIndex = -1
          state.pointerOffset = Offset.Unspecified
          scope.launch {
            state.lensIntensityAnim.animateTo(0f, tween(180))
          }
          if (committedIdx in 0 until latestItemCount) {
            latestOnCommit(committedIdx)
          }
        }
      }
    }
}

/**
 * Floating Liquid Glass lens overlay rendered above or around scrubbed icons so holding
 * and sliding visibly moves the same chromatic-edge, tilt-lit, finger-glowing glass lens.
 */
@Composable
fun LiquidGlassScrubLensOverlay(
  state: LiquidGlassScrubState,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 18.dp,
  thickness: Float = 1.4f,
) {
  val intensity = state.lensIntensityAnim.value
  val lensW = state.lensWAnim.value
  val lensH = state.lensHAnim.value
  if (intensity <= 0.01f || lensW <= 2f || lensH <= 2f) return

  val palette = LocalIdePalette.current
  val settings = LocalEditorSettings.current
  val adaptive =
    rememberLiquidGlassAdaptiveColors(
      palette = palette,
      tintFraction = (settings.liquidGlassTint * 0.85f).coerceIn(0f, 1f),
      thickness = thickness,
      emergenceProgress = intensity,
    )
  val lightDir = rememberLiquidGlassLightDirection(settings.liquidGlassMotionHighlights)
  val density = LocalDensity.current
  val baseCornerPx = with(density) { cornerRadius.toPx() }
  val glowColor = palette.activePillFg

  Canvas(modifier = modifier) {
    val expandScale = 1f + 0.07f * intensity
    val padW = lensW * (expandScale - 1f) * 0.5f + with(density) { 4.dp.toPx() } * intensity
    val padH = lensH * (expandScale - 1f) * 0.5f + with(density) { 2.dp.toPx() } * intensity
    val left = state.lensXAnim.value - padW
    val top = state.lensYAnim.value - padH
    val w = (lensW + padW * 2f).coerceAtLeast(4f)
    val h = (lensH + padH * 2f).coerceAtLeast(4f)
    val rad = baseCornerPx.coerceAtMost(minOf(w, h) * 0.5f)
    val cr = CornerRadius(rad, rad)
    val topLeft = Offset(left, top)
    val rectSize = Size(w, h)

    if (settings.liquidGlassEnabled) {
      // 1. Floating glass lens tint body
      drawRoundRect(
        brush =
          Brush.verticalGradient(
            colors =
              listOf(
                adaptive.bodyTopTint.copy(alpha = (adaptive.bodyTopTint.alpha * 0.58f * intensity).coerceIn(0f, 0.55f)),
                adaptive.bodyBottomTint.copy(alpha = (adaptive.bodyBottomTint.alpha * 0.42f * intensity).coerceIn(0f, 0.48f)),
              ),
            startY = top,
            endY = top + h,
          ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cr,
      )

      // 2. Finger-origin internal caustic glow sliding directly under the finger
      val pointer = state.pointerOffset
      val glowCenter =
        if (pointer != Offset.Unspecified) {
          Offset(
            x = pointer.x.coerceIn(left, left + w),
            y = pointer.y.coerceIn(top, top + h),
          )
        } else {
          Offset(left + w * 0.5f, top + h * 0.5f)
        }
      drawRoundRect(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                glowColor.copy(alpha = 0.42f * intensity),
                glowColor.copy(alpha = 0.14f * intensity),
                Color.Transparent,
              ),
            center = glowCenter,
            radius = maxOf(w, h) * 0.85f,
          ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cr,
      )

      // 3. Chromatic Fresnel dispersion rim
      drawRoundRect(
        brush =
          Brush.linearGradient(
            colors =
              listOf(
                adaptive.chromaticCoolRim.copy(alpha = (0.55f * intensity).coerceIn(0f, 0.72f)),
                Color.Transparent,
                adaptive.chromaticWarmRim.copy(alpha = (0.45f * intensity).coerceIn(0f, 0.62f)),
              ),
            start = Offset(left + w * (0.2f + lightDir.x * 0.25f), top),
            end = Offset(left + w * (0.8f - lightDir.x * 0.25f), top + h),
          ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cr,
        style = Stroke(width = 2.dp.toPx()),
      )

      // 4. Tilt-responsive bright specular highlight border
      drawRoundRect(
        brush =
          Brush.linearGradient(
            colors =
              listOf(
                adaptive.specularHighlight.copy(alpha = (0.86f * intensity).coerceIn(0f, 0.96f)),
                adaptive.rimSubtle.copy(alpha = 0.26f * intensity),
                adaptive.specularHighlight.copy(alpha = 0.48f * intensity),
              ),
            start = Offset(left + w * (0.5f + lightDir.x * 0.5f), top),
            end = Offset(left + w * (0.5f - lightDir.x * 0.5f), top + h),
          ),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cr,
        style = Stroke(width = 1.15.dp.toPx()),
      )
    } else {
      drawRoundRect(
        color = palette.activePillFg.copy(alpha = 0.18f * intensity),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cr,
      )
      drawRoundRect(
        color = palette.activePillFg.copy(alpha = 0.52f * intensity),
        topLeft = topLeft,
        size = rectSize,
        cornerRadius = cr,
        style = Stroke(width = 1.dp.toPx()),
      )
    }
  }
}

