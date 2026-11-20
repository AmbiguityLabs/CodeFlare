package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.Ca
import com.example.ui.theme.CaMotion
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.RoseError
import com.example.ui.theme.UiStyleVersion
import com.example.ui.theme.eyebrow
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SettingsCategory(
  val title: String,
  val subtitle: String,
  val jumpTag: String,
  val icon: ImageVector,
  val keywords: List<String>,
) {
  APPEARANCE(
    title = "Appearance",
    subtitle = "Theme, accent, code style & startup",
    jumpTag = "appearance",
    icon = Icons.Outlined.Palette,
    keywords = listOf("theme", "dark", "light", "system", "accent", "color", "dynamic", "material you", "custom", "lime", "violet", "teal", "orange", "pumpkin", "style", "code style", "syntax", "monokai", "neon", "tokyo", "dracula", "labels", "home", "editor", "background", "bubbles", "floating", "pop", "particles", "reopen", "startup"),
  ),
  EDITOR(
    title = "Editor",
    subtitle = "Font, caret, gestures, tabs, selection",
    jumpTag = "editor",
    icon = Icons.Outlined.Code,
    keywords = listOf("font", "jetbrains", "monospace", "caret", "candle", "underline", "block", "ligatures", "size", "tab", "word wrap", "scrollbar", "line numbers", "folding", "sticky", "indent", "brackets", "pinch", "zoom", "symbol bar", "keyboard", "auto save", "drafts", "unsaved", "warnings", "selection", "drag"),
  ),
  COMPLETION(
    title = "Code Completion",
    subtitle = "Auto-popup, delay & suggestion cap",
    jumpTag = "completion",
    icon = Icons.Outlined.AutoAwesome,
    keywords = listOf("autocomplete", "completion", "popup", "delay", "suggestions", "max items"),
  ),
  ANALYSIS(
    title = "Analysis & Diagnostics",
    subtitle = "On-the-fly linting & reparse delay",
    jumpTag = "analysis",
    icon = Icons.Outlined.Lightbulb,
    keywords = listOf("analysis", "diagnostics", "lint", "inspect", "reparse", "delay", "errors"),
  ),
  CLOUDFLARE(
    title = "Cloudflare",
    subtitle = "API token, Global key, OAuth, accounts",
    jumpTag = "credentials",
    icon = Icons.Outlined.Key,
    keywords = listOf("token", "api", "key", "oauth", "account", "credentials", "session"),
  ),
  DEPLOY(
    title = "Deployment",
    subtitle = "Compatibility date, nodejs_compat, workers.dev",
    jumpTag = "deploy",
    icon = Icons.Outlined.RocketLaunch,
    keywords = listOf("compat", "date", "nodejs", "workers.dev", "subdomain", "preview", "deploy"),
  ),
  STORAGE(
    title = "Storage & Sync",
    subtitle = "Directory, cache TTL, GitHub sync, disconnect",
    jumpTag = "storage",
    icon = Icons.Outlined.Folder,
    keywords = listOf("folder", "storage", "disk", "cache", "github", "sync", "disconnect", "session"),
  ),
}

enum class AppearanceSubPage(
  val title: String,
  val headerTitle: String,
  val subtitle: String,
  val tag: String,
  val icon: ImageVector,
) {
  HOME_PAGE("Home Page", "Appearance · Home Page", "Floating bottom bar & popup menus", "appearance_home", Icons.Outlined.Home),
  EDITOR("Editor", "Appearance · Editor", "Code styles, bottom bar, bubbles & background", "appearance_editor", Icons.Outlined.Code),
}

// ---- CodeAssist Material 3 Expressive Settings Controls ------------------------------------------

/** A grouped M3 Expressive settings card with an optional uppercase eyebrow header (ported from CodeAssist). */
@Composable
fun SettingsCard(
  title: String?,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val isV2 = LocalIdePalette.current.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  val shape = RoundedCornerShape(if (isV2) Ca.radius.xl else Ca.radius.lg)
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer, shape)
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    if (title != null) {
      Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.eyebrow,
        fontWeight = FontWeight.SemiBold,
      )
    }
    content()
  }
}

@Composable
private fun RowScopeLabel(
  title: String,
  description: String?,
  modifier: Modifier = Modifier,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = title,
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
    )
    if (description != null) {
      Text(
        text = description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
fun CaSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
  Switch(checked = on, onCheckedChange = onToggle)
}

/** A one-of-N choice rendered as a horizontally-scrolling row of pill accent chips (CodeAssist style). */
@Composable
fun SettingsChoiceRow(
  title: String,
  description: String?,
  selected: String,
  options: List<Pair<String, String>>,
  modifier: Modifier = Modifier,
  chipTestTagPrefix: String? = null,
  chipDotColor: ((String) -> Color?)? = null,
  onSelect: (String) -> Unit,
) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    RowScopeLabel(title, description)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      options.forEach { (value, label) ->
        SettingsPillChip(
          label = label,
          selected = value == selected,
          dotColor = chipDotColor?.invoke(value),
          tag = chipTestTagPrefix?.let { "${it}_${value.lowercase()}" },
          onClick = { onSelect(value) },
        )
      }
    }
  }
}

@Composable
fun SettingsPillChip(
  label: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  dotColor: Color? = null,
  tag: String? = null,
  onClick: () -> Unit,
) {
  val bg by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
    animationSpec = tween(CaMotion.FAST),
    label = "caChipBg",
  )
  val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier =
      modifier
        .background(bg, RoundedCornerShape(Ca.radius.pill))
        .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = 14.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (dotColor != null) {
      Box(
        modifier =
          Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(dotColor)
            .border(
              0.75.dp,
              if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
              else MaterialTheme.colorScheme.outlineVariant,
              CircleShape,
            ),
      )
    }
    Text(
      text = label,
      color = fg,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
    )
  }
}

@Composable
fun SettingsSliderRow(
  title: String,
  description: String?,
  value: Int,
  min: Int,
  max: Int,
  step: Int,
  unit: String?,
  modifier: Modifier = Modifier,
  onChangeFinished: (() -> Unit)? = null,
  onChange: (Int) -> Unit,
) {
  val safeStep = step.coerceAtLeast(1)
  val steps = (((max - min) / safeStep) - 1).coerceAtLeast(0)
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      RowScopeLabel(title, description, Modifier.weight(1f))
      Box(
        modifier =
          Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Ca.radius.pill))
            .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(
          text = "$value${unit?.let { " $it" } ?: ""}",
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    Slider(
      value = value.toFloat(),
      onValueChange = { raw ->
        val snapped = (((raw - min) / safeStep).roundToInt() * safeStep + min).coerceIn(min, max)
        if (snapped != value) onChange(snapped)
      },
      valueRange = min.toFloat()..max.toFloat(),
      steps = steps,
      onValueChangeFinished = onChangeFinished,
      colors =
        SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary,
          inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          activeTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0f),
          inactiveTickColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f),
        ),
    )
  }
}

@Composable
fun SettingsActionRow(
  title: String,
  description: String?,
  buttonLabel: String,
  destructive: Boolean,
  modifier: Modifier = Modifier,
  buttonTag: String? = null,
  onClick: () -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    RowScopeLabel(title, description, Modifier.weight(1f))
    val fill = if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer
    val fg = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
      modifier =
        Modifier
          .background(fill, RoundedCornerShape(Ca.radius.control))
          .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
          .then(if (buttonTag != null) Modifier.testTag(buttonTag) else Modifier)
          .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
      Text(buttonLabel, color = fg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
fun SettingsDivider() {
  Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)))
}

@Composable
fun AdvancedGroup(content: @Composable () -> Unit) {
  var open by remember { mutableStateOf(false) }
  val chevronRotation by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (open) 180f else 0f,
    animationSpec = tween(CaMotion.FAST),
    label = "advChevron",
  )
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clickable(remember { MutableInteractionSource() }, null) { open = !open },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.KeyboardArrowDown,
        contentDescription = null,
        modifier = Modifier.size(16.dp).rotate(chevronRotation),
        tint = MaterialTheme.colorScheme.outline,
      )
      Text(
        text = "Advanced",
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
      )
    }
    AnimatedVisibility(
      visible = open,
      enter = expandVertically(tween(CaMotion.FAST)) + fadeIn(),
      exit = shrinkVertically(tween(CaMotion.FAST)) + fadeOut(),
    ) {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
  }
}

/** A color setting: label/description on the left, swatch on the right opening CodeAssist's HSL+Hex picker. */
@Composable
fun SettingsColorRow(
  title: String,
  description: String?,
  value: Long,
  modifier: Modifier = Modifier,
  swatchTag: String? = null,
  onChange: (Long) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(remember { MutableInteractionSource() }, null) { open = true }
        .then(if (swatchTag != null) Modifier.testTag(swatchTag) else Modifier),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    RowScopeLabel(title, description, Modifier.weight(1f))
    Box(
      modifier =
        Modifier
          .size(36.dp)
          .background(Color(value or 0xFF000000L), RoundedCornerShape(Ca.radius.control))
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Ca.radius.control)),
    )
  }
  ColorPickerDialog(
    visible = open,
    initial = value,
    onDismiss = { open = false },
  ) { picked ->
    onChange(picked)
    open = false
  }
}

private val PICKER_PRESETS =
  listOf(
    0xFF8B5CF6L, 0xFF6750A4L, 0xFF1C9BBDL, 0xFF00A8A0L, 0xFF3DDC84L, 0xFF2E7D32L,
    0xFFF9A825L, 0xFFC16A1CL, 0xFFE0533DL, 0xFFD81B60L, 0xFF7F52FFL, 0xFF3A6FE0L,
  )

/** Shared HSL + Hex color picker dialog ported directly from CodeAssist SettingsControls.kt. */
@Composable
fun ColorPickerDialog(
  visible: Boolean,
  initial: Long,
  onDismiss: () -> Unit,
  allowAlpha: Boolean = false,
  onPick: (Long) -> Unit,
) {
  if (!visible) return
  Dialog(onDismissRequest = onDismiss) {
    val start = remember(initial) { colorToHsl(Color(initial)) }
    var h by remember(initial) { mutableFloatStateOf(start[0]) }
    var s by remember(initial) { mutableFloatStateOf(start[1]) }
    var l by remember(initial) { mutableFloatStateOf(start[2]) }
    var a by remember(initial) { mutableFloatStateOf(Color(initial).alpha) }
    val current = hslToColor(h, s, l).copy(alpha = if (allowAlpha) a else 1f)
    var hexDraft by remember(initial) { mutableStateOf<String?>(null) }

    Column(
      modifier =
        Modifier
          .widthIn(max = 360.dp)
          .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Ca.radius.xl))
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Ca.radius.xl))
          .padding(20.dp)
          .testTag("settings_color_picker_dialog"),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Custom color",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier
              .size(56.dp)
              .background(current, RoundedCornerShape(Ca.radius.control))
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Ca.radius.control)),
        )
        BasicTextField(
          value = hexDraft ?: hexOf(current),
          onValueChange = { text ->
            hexDraft = text
            parseHexColor(text)?.let { c ->
              val t = colorToHsl(c)
              h = t[0]
              s = t[1]
              l = t[2]
              if (allowAlpha) a = c.alpha
            }
          },
          singleLine = true,
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(
              color = MaterialTheme.colorScheme.onSurface,
              fontFamily = FontFamily.Monospace,
            ),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier =
            Modifier
              .width(128.dp)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Ca.radius.control))
              .padding(horizontal = 10.dp, vertical = 8.dp)
              .testTag("settings_color_picker_hex"),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PICKER_PRESETS.forEach { p ->
          val pc = Color(p)
          Box(
            modifier =
              Modifier
                .size(30.dp)
                .background(pc, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(remember { MutableInteractionSource() }, null) {
                  val t = colorToHsl(pc)
                  h = t[0]
                  s = t[1]
                  l = t[2]
                  hexDraft = null
                },
          )
        }
      }
      PickerSlider("Hue", h, 0f, 360f) { h = it; hexDraft = null }
      PickerSlider("Saturation", s, 0f, 1f) { s = it; hexDraft = null }
      PickerSlider("Lightness", l, 0f, 1f) { l = it; hexDraft = null }
      if (allowAlpha) {
        PickerSlider("Opacity", a, 0f, 1f) { a = it; hexDraft = null }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
          Text("Cancel")
        }
        Button(
          onClick = { onPick(argbLong(current)) },
          modifier = Modifier.weight(1f).testTag("settings_color_picker_save"),
        ) {
          Text("Save")
        }
      }
    }
  }
}

@Composable
private fun PickerSlider(
  label: String,
  value: Float,
  min: Float,
  max: Float,
  onChange: (Float) -> Unit,
) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    Slider(
      value = value,
      onValueChange = onChange,
      valueRange = min..max,
      colors =
        SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary,
          inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
  }
}

private fun colorToHsl(c: Color): FloatArray {
  val r = c.red
  val g = c.green
  val b = c.blue
  val max = maxOf(r, g, b)
  val min = minOf(r, g, b)
  val l = (max + min) / 2f
  val d = max - min
  if (d < 1e-5f) return floatArrayOf(0f, 0f, l)
  val s = d / (1f - abs(2f * l - 1f))
  val h =
    when (max) {
      r -> 60f * (((g - b) / d) % 6f)
      g -> 60f * ((b - r) / d + 2f)
      else -> 60f * ((r - g) / d + 4f)
    }
  return floatArrayOf((h + 360f) % 360f, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
  val c = (1f - abs(2f * l - 1f)) * s
  val hp = (((h % 360f) + 360f) % 360f) / 60f
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

private fun argbLong(c: Color): Long {
  val a = (c.alpha * 255f).roundToInt().coerceIn(0, 255).toLong()
  val r = (c.red * 255f).roundToInt().toLong()
  val g = (c.green * 255f).roundToInt().toLong()
  val b = (c.blue * 255f).roundToInt().toLong()
  return (a shl 24) or (r shl 16) or (g shl 8) or b
}

internal fun parseHexColor(text: String): Color? {
  val digits = text.trim().removePrefix("#")
  if (digits.length != 6 && digits.length != 8) return null
  val value = digits.toLongOrNull(16) ?: return null
  return Color(if (digits.length == 6) value or 0xFF000000L else value)
}

private fun hexOf(c: Color): String {
  val digits = "0123456789ABCDEF"
  fun h2(v: Int) = "${digits[(v shr 4) and 0xF]}${digits[v and 0xF]}"
  val r = (c.red * 255f).roundToInt()
  val g = (c.green * 255f).roundToInt()
  val b = (c.blue * 255f).roundToInt()
  return "#${h2(r)}${h2(g)}${h2(b)}"
}

// ---- CodeAssist Hub Navigation Primitives --------------------------------------------------------

@Composable
fun SettingsHubSectionHeader(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.semantics { heading() },
  )
}

@Composable
fun SettingsHubRow(
  title: String,
  subtitle: String?,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  statusDotColor: Color? = null,
  tag: String? = null,
) {
  val scheme = MaterialTheme.colorScheme
  ListItem(
    modifier =
      modifier
        .clickable(onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    leadingContent = {
      Surface(
        shape = CircleShape,
        color = scheme.primaryContainer,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = scheme.onPrimaryContainer,
          )
        }
      }
    },
    headlineContent = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = scheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    supportingContent =
      subtitle?.let { sub ->
        {
          Text(
            text = sub,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      },
    trailingContent = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (statusDotColor != null) {
          Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusDotColor))
        }
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = scheme.outline,
        )
      }
    },
  )
}

@Composable
fun SettingsHubSearchField(
  query: String,
  onQuery: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = MaterialTheme.colorScheme
  Row(
    modifier =
      modifier
        .height(40.dp)
        .clip(RoundedCornerShape(50))
        .background(c.surfaceContainerHigh)
        .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.onSurfaceVariant)
    BasicTextField(
      value = query,
      onValueChange = onQuery,
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.onSurface),
      cursorBrush = SolidColor(c.primary),
      modifier = Modifier.weight(1f).testTag("settings_search_input"),
      decorationBox = { inner ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (query.isEmpty()) {
            Text(
              text = "Search settings",
              style = MaterialTheme.typography.bodyMedium,
              color = c.outline,
            )
          }
          inner()
        }
      },
    )
    if (query.isNotEmpty()) {
      IconButton(onClick = { onQuery("") }, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Outlined.Close, contentDescription = "Clear search", modifier = Modifier.size(14.dp))
      }
    }
  }
}

@Composable
fun SettingsHubSidebarItem(
  title: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  statusDotColor: Color? = null,
  tag: String? = null,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    selected = selected,
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = if (selected) scheme.secondaryContainer else Color.Transparent,
    contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 42.dp)
        .semantics { role = Role.Tab }
        .then(if (tag != null) Modifier.testTag(tag) else Modifier),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
      Column(Modifier.weight(1f).padding(start = 12.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onSecondaryContainer.copy(alpha = 0.78f) else scheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (statusDotColor != null) {
        Box(modifier = Modifier.size(6.5.dp).clip(CircleShape).background(statusDotColor))
      }
    }
  }
}

// ---- Harmonized CodeFlare Legacy Primitives (used by Editor / Cloudflare / Deploy / Storage) ---

@Composable
fun SettingsGroup(
  modifier: Modifier = Modifier,
  label: String? = null,
  trailingValue: String? = null,
  contentPadding: Dp = 0.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val palette = LocalIdePalette.current
  val isV2 = palette.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (label != null || trailingValue != null) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (label != null) {
          Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.eyebrow,
            color = scheme.outline,
          )
        } else {
          Spacer(Modifier.width(1.dp))
        }
        if (trailingValue != null) {
          Text(
            text = trailingValue,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
          )
        }
      }
    }
    Surface(
      color = scheme.surfaceContainer,
      shape = RoundedCornerShape(if (isV2) Ca.radius.xl else Ca.radius.lg),
      modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(CaMotion.FAST)),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(contentPadding),
        content = content,
      )
    }
  }
}

@Composable
fun SettingsFoldableSection(
  title: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  summary: String? = null,
  initiallyExpanded: Boolean = false,
  contentPadding: Dp = 0.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val isV2 = LocalIdePalette.current.uiStyle == UiStyleVersion.V2_MINIMAL_OLED
  var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
  val chevronRotation by androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(CaMotion.FAST),
    label = "fold_chevron_$title",
  )

  Surface(
    color = scheme.surfaceContainer,
    shape = RoundedCornerShape(if (isV2) Ca.radius.xl else Ca.radius.lg),
    modifier = modifier.fillMaxWidth().animateContentSize(animationSpec = tween(CaMotion.FAST)),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = if (expanded) scheme.primaryContainer else scheme.surfaceContainerHigh,
          modifier = Modifier.size(34.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (expanded) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = scheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (!summary.isNullOrBlank()) {
          Box(
            modifier =
              Modifier
                .background(scheme.primaryContainer, RoundedCornerShape(Ca.radius.pill))
                .padding(horizontal = 9.dp, vertical = 3.dp),
          ) {
            Text(
              text = summary,
              fontFamily = JetBrainsMono,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = scheme.primary,
              maxLines = 1,
            )
          }
        }
        Icon(
          imageVector = Icons.Outlined.KeyboardArrowDown,
          contentDescription = if (expanded) "Collapse" else "Expand",
          tint = scheme.outline,
          modifier = Modifier.size(18.dp).rotate(chevronRotation),
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
          SettingsHairlineDivider()
          Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            content = content,
          )
        }
      }
    }
  }
}

@Composable
fun SettingsMenuRow(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  value: String? = null,
  selected: Boolean = false,
  statusDotColor: Color? = null,
  showChevron: Boolean = true,
  useUnfoldIndicator: Boolean = false,
  tag: String? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val rowBg by animateColorAsState(
    targetValue = if (selected) scheme.secondaryContainer else Color.Transparent,
    animationSpec = tween(CaMotion.FAST),
    label = "menu_row_bg",
  )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .background(rowBg)
        .clickable(onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      shape = CircleShape,
      color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
      modifier = Modifier.size(36.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )

    if (statusDotColor != null) {
      Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusDotColor))
    }

    if (!value.isNullOrBlank()) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (useUnfoldIndicator) {
      Icon(
        imageVector = Icons.Outlined.UnfoldMore,
        contentDescription = null,
        tint = scheme.outline,
        modifier = Modifier.size(18.dp),
      )
    } else if (showChevron) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = scheme.outline,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun SettingsHairlineDivider(
  modifier: Modifier = Modifier,
  startIndent: Dp = 0.dp,
) {
  HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    thickness = 1.dp,
    modifier = modifier.padding(start = startIndent),
  )
}

@Composable
fun SettingsToggleRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  description: String? = null,
  leadingIcon: ImageVector? = null,
  horizontalPadding: Dp = 16.dp,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clickable(role = Role.Switch) { onCheckedChange(!checked) }
        .padding(horizontal = horizontalPadding, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (leadingIcon != null) {
      Icon(
        imageVector = leadingIcon,
        contentDescription = null,
        tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }

    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = scheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!description.isNullOrBlank()) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant,
        )
      }
    }

    CaSwitch(on = checked, onToggle = onCheckedChange)
  }
}

@Composable
fun SettingsToggleSwitch(
  checked: Boolean,
  modifier: Modifier = Modifier,
) {
  Switch(
    checked = checked,
    onCheckedChange = null,
    modifier = modifier,
  )
}

@Composable
fun SettingsSegmentedControl(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    options.forEachIndexed { idx, label ->
      val selected = idx == selectedIndex
      val bg by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        animationSpec = tween(CaMotion.FAST),
        label = "seg_bg_$idx",
      )
      val fg = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
      Box(
        modifier =
          Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(Ca.radius.pill))
            .background(bg)
            .clickable { onSelect(idx) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = fg,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun SettingsChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
  monospace: Boolean = false,
) {
  val scheme = MaterialTheme.colorScheme
  val bg by animateColorAsState(
    targetValue = if (selected) scheme.primary else scheme.surfaceContainerHigh,
    animationSpec = tween(CaMotion.FAST),
    label = "choice_chip_bg",
  )
  val fg = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(Ca.radius.pill),
    color = bg,
    modifier = modifier.heightIn(min = 36.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (leadingIcon != null) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = fg,
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
      }
      Text(
        text = label,
        fontFamily = if (monospace) JetBrainsMono else MaterialTheme.typography.bodySmall.fontFamily,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = fg,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun SettingsMetricTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  accentColor: Color? = null,
) {
  val scheme = MaterialTheme.colorScheme
  Surface(
    color = scheme.surfaceContainerHigh,
    shape = RoundedCornerShape(Ca.radius.control),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = value,
        fontFamily = JetBrainsMono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = accentColor ?: scheme.onSurface,
        maxLines = 1,
      )
      Text(
        text = label.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 8.5.sp,
        letterSpacing = 0.6.sp,
        color = scheme.outline,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun SettingsStatusBanner(
  message: String,
  isError: Boolean,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val tone = if (isError) RoseError else EmeraldLive
  Surface(
    color = tone.copy(alpha = 0.12f),
    shape = RoundedCornerShape(Ca.radius.control),
    border = BorderStroke(0.75.dp, tone.copy(alpha = 0.36f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Icon(
        imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircleOutline,
        contentDescription = null,
        tint = tone,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurface,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
