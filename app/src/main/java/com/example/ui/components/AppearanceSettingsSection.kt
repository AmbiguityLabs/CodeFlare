package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Dvr
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.screens.settings.AppearanceSubPage
import com.example.ui.screens.settings.SettingsCard
import com.example.ui.screens.settings.SettingsChoiceRow
import com.example.ui.screens.settings.SettingsColorRow
import com.example.ui.screens.settings.SettingsDivider
import com.example.ui.screens.settings.SettingsFoldableSection
import com.example.ui.screens.settings.SettingsHubRow
import com.example.ui.screens.settings.SettingsHubSectionHeader
import com.example.ui.screens.settings.SettingsPillChip
import com.example.ui.screens.settings.SettingsToggleRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.WorkerLanguage
import com.example.data.security.EditorBubbleColorStyle
import com.example.data.security.EditorBubbleConfig
import com.example.data.security.EditorSettings
import com.example.data.security.FloatingBarStyleConfig
import com.example.data.security.IconLabelBehavior
import com.example.data.security.PopupMenuStyleConfig
import com.example.editor.SyntaxHighlighter
import com.example.ui.theme.Ca
import com.example.ui.theme.CaAccent
import com.example.ui.theme.CaMotion
import com.example.ui.theme.CodeStylePreset
import com.example.ui.theme.EditorBackgroundPreset
import com.example.ui.theme.EditorThemePreset
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIdePalette
import com.example.ui.theme.UiStyleVersion
import com.example.ui.theme.applyCodeStylePreset
import com.example.ui.theme.paletteFor
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppearanceHubContent(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onOpenSubPage: (AppearanceSubPage) -> Unit,
) {
  val palette = LocalIdePalette.current
  val scheme = MaterialTheme.colorScheme
  val effectiveDark = settings.resolveDarkTheme(isSystemInDarkTheme())
  val activeMode = settings.normalizedThemeMode()
  val activeAccent = settings.normalizedAccent()
  val activeStyle = UiStyleVersion.fromKey(settings.uiStyleVersion)
  val activePreset = EditorThemePreset.fromKey(settings.themePreset)

  val modeDescription =
    when (activeMode) {
      EditorSettings.THEME_LIGHT -> "Bright daylight surfaces and high-contrast ink"
      EditorSettings.THEME_SYSTEM -> "Automatically follow device light/dark mode"
      else -> "Low-glare dark surfaces tuned for long coding sessions"
    }

  val accentDescription =
    when (activeAccent) {
      EditorSettings.ACCENT_DYNAMIC -> "Adapts to wallpaper colors on Android 12+ devices"
      EditorSettings.ACCENT_CUSTOM -> "Using custom Expressive seed color"
      else -> "Expressive Material 3 tonal palette"
    }

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    SettingsCard(title = "Appearance") {
      SettingsChoiceRow(
        title = "Theme",
        description = modeDescription,
        selected = activeMode,
        options =
          listOf(
            EditorSettings.THEME_LIGHT to "Light",
            EditorSettings.THEME_DARK to "Dark",
            EditorSettings.THEME_SYSTEM to "System",
          ),
        chipTestTagPrefix = "settings_mode",
        onSelect = { mode ->
          onUpdateSettings { cur -> cur.copy(themeMode = mode) }
        },
      )

      SettingsDivider()

      SettingsChoiceRow(
        title = "Accent",
        description = accentDescription,
        selected = activeAccent,
        options =
          listOf(
            EditorSettings.ACCENT_DYNAMIC to "Dynamic",
            CaAccent.Lime.key to CaAccent.Lime.label,
            CaAccent.Violet.key to CaAccent.Violet.label,
            CaAccent.Teal.key to CaAccent.Teal.label,
            CaAccent.Orange.key to CaAccent.Orange.label,
            CaAccent.Pumpkin.key to CaAccent.Pumpkin.label,
            EditorSettings.ACCENT_CUSTOM to "Custom",
          ),
        chipTestTagPrefix = "settings_accent",
        chipDotColor = { key ->
          when (key) {
            EditorSettings.ACCENT_DYNAMIC -> scheme.primary
            EditorSettings.ACCENT_CUSTOM -> Color(settings.accentColor or 0xFF000000L)
            else -> CaAccent.entries.firstOrNull { it.key == key }?.swatchColor
          }
        },
        onSelect = { choice ->
          onUpdateSettings { cur ->
            cur.copy(
              accent = choice,
              dynamicColor = choice == EditorSettings.ACCENT_DYNAMIC,
            )
          }
        },
      )

      SettingsDivider()

      SettingsColorRow(
        title = "Custom color",
        description =
          if (activeAccent == EditorSettings.ACCENT_CUSTOM) {
            "Active Expressive seed color — tap swatch to edit"
          } else {
            "Pick any seed color to generate a custom Expressive scheme"
          },
        value = settings.accentColor,
        swatchTag = "settings_custom_accent_color",
        onChange = { picked ->
          onUpdateSettings { cur ->
            cur.copy(
              accentColor = picked,
              accent = EditorSettings.ACCENT_CUSTOM,
              dynamicColor = false,
            )
          }
        },
      )
    }

    SettingsCard(title = "Interface") {
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "Interface Style",
              color = scheme.onSurface,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text =
                if (activeStyle == UiStyleVersion.V2_MINIMAL_OLED) {
                  "High-contrast minimal OLED chrome with pill controls"
                } else {
                  "Expressive Material 3 surfaces matched to active accent"
                },
              color = scheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
            )
          }
          Box(
            modifier =
              Modifier
                .background(scheme.primaryContainer, RoundedCornerShape(Ca.radius.pill))
                .padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Text(
              text = activeStyle.label,
              fontFamily = JetBrainsMono,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = scheme.primary,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          UiStyleVersion.entries.forEach { styleChoice ->
            val selectedChoice = activeStyle == styleChoice
            val pillBg by animateColorAsState(
              targetValue = if (selectedChoice) scheme.primary else scheme.surfaceContainerHigh,
              animationSpec = tween(CaMotion.FAST),
              label = "style_pill_${styleChoice.key}",
            )
            val pillFg = if (selectedChoice) scheme.onPrimary else scheme.onSurfaceVariant
            Row(
              modifier =
                Modifier
                  .weight(1f)
                  .height(38.dp)
                  .clip(RoundedCornerShape(Ca.radius.pill))
                  .background(pillBg)
                  .clickable { onUpdateSettings { it.copy(uiStyleVersion = styleChoice.key) } }
                  .testTag("settings_style_${styleChoice.key.lowercase()}")
                  .padding(horizontal = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              EdgeLoadingIndicator(
                size = 16.dp,
                color = pillFg,
                styleOverride = styleChoice,
              )
              Spacer(Modifier.width(7.dp))
              Text(
                text = styleChoice.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = pillFg,
                maxLines = 1,
              )
            }
          }
        }
      }

      SettingsDivider()

      val activeBehavior = settings.iconLabelBehavior
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "Button Labels",
              color = scheme.onSurface,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = activeBehavior.description,
              color = scheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.width(8.dp))
          IconRevealButton(
            icon =
              when (activeBehavior) {
                IconLabelBehavior.REVEAL_ON_TAP -> Icons.Outlined.TouchApp
                IconLabelBehavior.ICON_ONLY -> Icons.Outlined.VisibilityOff
                IconLabelBehavior.ALWAYS_SHOW -> Icons.Outlined.Visibility
              },
            revealedLabel = "Tap Test",
            onClick = {},
            active = true,
            height = 30.dp,
            iconSize = 15.dp,
            tag = "settings_label_behavior_sample",
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          IconLabelBehavior.entries.forEach { behaviorChoice ->
            SettingsPillChip(
              label = behaviorChoice.shortLabel,
              selected = activeBehavior == behaviorChoice,
              tag = "settings_label_behavior_${behaviorChoice.name.lowercase()}",
              onClick = { onUpdateSettings { it.copy(iconLabelBehavior = behaviorChoice) } },
            )
          }
        }
      }
    }

    SettingsFoldableSection(
      title = "Editor Color Palette",
      icon = Icons.Outlined.Palette,
      summary = activePreset.label,
      initiallyExpanded = false,
      contentPadding = 14.dp,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val presets = EditorThemePreset.entries
        presets.chunked(2).forEach { rowPresets ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            rowPresets.forEach { preset ->
              val selected = preset == activePreset
              val previewPalette = remember(effectiveDark, preset) { paletteFor(effectiveDark, preset) }
              Surface(
                onClick = {
                  onUpdateSettings { cur ->
                    cur.copy(
                      themePreset = preset.name,
                      accent = EditorSettings.accentForPreset(preset.name),
                      dynamicColor = false,
                    )
                  }
                },
                shape = RoundedCornerShape(Ca.radius.control),
                color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
                border =
                  BorderStroke(
                    width = if (selected) 1.dp else 0.75.dp,
                    color = if (selected) scheme.primary.copy(alpha = 0.65f) else scheme.outlineVariant.copy(alpha = 0.6f),
                  ),
                modifier = Modifier.weight(1f).testTag("settings_theme_preset_${preset.name.lowercase()}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier =
                        Modifier
                          .size(11.dp)
                          .clip(CircleShape)
                          .background(previewPalette.editorBg)
                          .border(0.75.dp, previewPalette.border, CircleShape),
                    )
                    Box(
                      modifier =
                        Modifier
                          .size(11.dp)
                          .clip(CircleShape)
                          .background(previewPalette.chromeSurface)
                          .border(0.75.dp, previewPalette.border, CircleShape),
                    )
                    Box(
                      modifier =
                        Modifier
                          .size(11.dp)
                          .clip(CircleShape)
                          .background(previewPalette.activePillFg),
                    )
                  }
                  Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  if (selected) {
                    Icon(
                      imageVector = Icons.Outlined.Check,
                      contentDescription = "Selected theme",
                      tint = scheme.primary,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SettingsHubSectionHeader(
        title = "Screen Customization",
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          SettingsHubRow(
            title = AppearanceSubPage.HOME_PAGE.title,
            subtitle = AppearanceSubPage.HOME_PAGE.subtitle,
            icon = AppearanceSubPage.HOME_PAGE.icon,
            tag = "settings_jump_${AppearanceSubPage.HOME_PAGE.tag}",
            onClick = { onOpenSubPage(AppearanceSubPage.HOME_PAGE) },
          )
          HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            color = scheme.outlineVariant.copy(alpha = 0.65f),
          )
          SettingsHubRow(
            title = AppearanceSubPage.EDITOR.title,
            subtitle = AppearanceSubPage.EDITOR.subtitle,
            icon = AppearanceSubPage.EDITOR.icon,
            tag = "settings_jump_${AppearanceSubPage.EDITOR.tag}",
            onClick = { onOpenSubPage(AppearanceSubPage.EDITOR) },
          )
        }
      }
    }

    SettingsCard(title = "Startup") {
      SettingsToggleRow(
        title = "Reopen last project",
        description = "Reopen the project you were editing when the IDE starts",
        checked = settings.reopenLastProject,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_reopen_last_project"),
        onCheckedChange = { enabled ->
          onUpdateSettings { it.copy(reopenLastProject = enabled) }
        },
      )
    }
  }
}

@Composable
fun HomeAppearanceStudioContent(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    FloatingBarCustomizerSection(
      title = "Home Bottom Bar",
      tagPrefix = "home_bar",
      config = settings.homeBarStyle,
      defaultConfig = FloatingBarStyleConfig.HOME_DEFAULT,
      previewItems =
        listOf(
          Triple("Projects", Icons.Outlined.Code, "4"),
          Triple("Pages", Icons.Outlined.Web, "2"),
          Triple("Infra", Icons.Outlined.Storage, "7"),
        ),
      onChange = { transform ->
        onUpdateSettings { cur -> cur.copy(homeBarStyle = transform(cur.homeBarStyle)) }
      },
      onReset = {
        onUpdateSettings { it.copy(homeBarStyle = FloatingBarStyleConfig.HOME_DEFAULT) }
      },
      initiallyExpanded = true,
    )

    PopupMenuCustomizerSection(
      title = "Home Menus",
      tagPrefix = "home_menu",
      config = settings.homeMenuStyle,
      defaultConfig = PopupMenuStyleConfig.DEFAULT,
      previewHeader = "Home Menu",
      previewRows =
        listOf(
          Triple("Import .zip Archive", Icons.Outlined.Archive, ".zip"),
          Triple("Import .json Bundle", Icons.Outlined.CloudDownload, ".json"),
          Triple("Cloudflare Account", Icons.Outlined.CloudQueue, null),
        ),
      onChange = { transform ->
        onUpdateSettings { cur -> cur.copy(homeMenuStyle = transform(cur.homeMenuStyle)) }
      },
      onReset = {
        onUpdateSettings { it.copy(homeMenuStyle = PopupMenuStyleConfig.DEFAULT) }
      },
      initiallyExpanded = false,
    )
  }
}

@Composable
fun EditorAppearanceStudioContent(
  settings: EditorSettings,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
  onPickEditorBackgroundImage: (Uri) -> Unit,
  onClearEditorBackgroundImage: () -> Unit,
) {
  val palette = LocalIdePalette.current
  val activeBgPreset = EditorBackgroundPreset.fromKey(settings.editorBgPreset)
  val hasCustomPhotoFile =
    remember(settings.editorBgImagePath) {
      settings.editorBgImagePath.isNotBlank() && File(settings.editorBgImagePath).exists()
    }
  val hasActiveBg = rememberHasActiveEditorBackground(settings)

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        onPickEditorBackgroundImage(uri)
      }
    }

  val activeCodeStyle = CodeStylePreset.fromKey(settings.codeStylePreset)

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    CodeStyleReferencesSection(
      activeCodeStyle = activeCodeStyle,
      overrideCanvas = settings.codeStyleOverrideCanvas,
      onSelectStyle = { picked ->
        onUpdateSettings { it.copy(codeStylePreset = picked.key) }
      },
      onToggleOverrideCanvas = { enabled ->
        onUpdateSettings { it.copy(codeStyleOverrideCanvas = enabled) }
      },
    )

    FloatingBarCustomizerSection(
      title = "Editor Bottom Bar",
      tagPrefix = "editor_bar",
      config = settings.editorBarStyle,
      defaultConfig = FloatingBarStyleConfig.EDITOR_DEFAULT,
      previewItems =
        listOf(
          Triple("Code", Icons.Outlined.Code, null),
          Triple("Test", Icons.Outlined.Science, null),
          Triple("Config", Icons.Outlined.Tune, null),
          Triple("Console", Icons.AutoMirrored.Outlined.Dvr, "3"),
        ),
      onChange = { transform ->
        onUpdateSettings { cur -> cur.copy(editorBarStyle = transform(cur.editorBarStyle)) }
      },
      onReset = {
        onUpdateSettings { it.copy(editorBarStyle = FloatingBarStyleConfig.EDITOR_DEFAULT) }
      },
      initiallyExpanded = false,
    )

    PopupMenuCustomizerSection(
      title = "Editor Menus",
      tagPrefix = "editor_menu",
      config = settings.editorMenuStyle,
      defaultConfig = PopupMenuStyleConfig.DEFAULT,
      previewHeader = "Project Actions",
      previewRows =
        listOf(
          Triple("Run in CLI", Icons.Outlined.RocketLaunch, "CLI"),
          Triple("Format Code", Icons.Outlined.Code, null),
          Triple("Git Sync", Icons.Outlined.AccountTree, "GIT"),
        ),
      onChange = { transform ->
        onUpdateSettings { cur -> cur.copy(editorMenuStyle = transform(cur.editorMenuStyle)) }
      },
      onReset = {
        onUpdateSettings { it.copy(editorMenuStyle = PopupMenuStyleConfig.DEFAULT) }
      },
      initiallyExpanded = false,
    )

    val previewBubbleController = remember { EditorBubbleController() }
    val floatingController = LocalFloatingPreviewController.current
    val bgPreviewId = "editor_bg_live_preview"
    val bgSummary =
      if (activeBgPreset == EditorBackgroundPreset.CUSTOM && hasCustomPhotoFile) "Custom"
      else activeBgPreset.label
    SettingsFoldableSection(
      title = "Editor Background",
      icon = Icons.Outlined.Wallpaper,
      summary = bgSummary,
      initiallyExpanded = false,
      onExpandedChange = { expanded -> if (expanded) floatingController?.select(bgPreviewId) },
      contentPadding = 12.dp,
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.activatePreviewOnTouch(bgPreviewId),
      ) {
        SettingsPreviewSlot(
          id = bgPreviewId,
          title = "Editor Background",
          shortLabel = "Background",
          order = 4,
        ) {
          InteractiveEditorBackgroundPreview(
            settings = settings,
            hasActiveBg = hasActiveBg,
            bubbleController = previewBubbleController,
            onPanDelta = { dxNorm, dyNorm ->
              onUpdateSettings { cur ->
                val maxShift = ((cur.editorBgZoom - 1f) * 0.45f).coerceIn(0f, 0.5f)
                cur.copy(
                  editorBgOffsetX = (cur.editorBgOffsetX + dxNorm).coerceIn(-maxShift, maxShift),
                  editorBgOffsetY = (cur.editorBgOffsetY + dyNorm).coerceIn(-maxShift, maxShift),
                )
              }
            },
          )
        }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
          },
          shape = RoundedCornerShape(10.dp),
          color = palette.activePillBg,
          border = BorderStroke(0.75.dp, palette.activePillFg.copy(alpha = 0.4f)),
          modifier = Modifier.weight(1f).height(40.dp).testTag("settings_pick_editor_bg_button"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.AddPhotoAlternate,
              contentDescription = "Choose custom background photo",
              tint = palette.activePillFg,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
              text = if (hasCustomPhotoFile) "Change Photo" else "Choose Photo",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = palette.activePillFg,
            )
          }
        }

        if (hasActiveBg || hasCustomPhotoFile) {
          OutlinedButton(
            onClick = onClearEditorBackgroundImage,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.7f)),
            modifier = Modifier.height(40.dp).testTag("settings_clear_editor_bg_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteOutline,
              contentDescription = "Remove editor background",
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text("Clear", fontSize = 12.sp)
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val visiblePresets =
          buildList {
            add(EditorBackgroundPreset.NONE)
            if (hasCustomPhotoFile) add(EditorBackgroundPreset.CUSTOM)
            add(EditorBackgroundPreset.AURORA_MIST)
            add(EditorBackgroundPreset.DEEP_OCEAN)
            add(EditorBackgroundPreset.CARBON_GRID)
            add(EditorBackgroundPreset.DUSK_HORIZON)
          }
        visiblePresets.forEach { bg ->
          val selected = bg == activeBgPreset
          val dotColor =
            when (bg) {
              EditorBackgroundPreset.NONE -> palette.editorBg
              EditorBackgroundPreset.CUSTOM -> palette.activePillFg
              EditorBackgroundPreset.AURORA_MIST -> Color(0xFF2DD4BF)
              EditorBackgroundPreset.DEEP_OCEAN -> Color(0xFF38BDF8)
              EditorBackgroundPreset.CARBON_GRID -> Color(0xFF94A3B8)
              EditorBackgroundPreset.DUSK_HORIZON -> Color(0xFFF97316)
            }
          Surface(
            onClick = { onUpdateSettings { it.copy(editorBgPreset = bg.key) } },
            shape = RoundedCornerShape(8.dp),
            color = if (selected) palette.activePillBg else palette.chromeSurface,
            border =
              BorderStroke(
                0.75.dp,
                if (selected) palette.activePillFg.copy(alpha = 0.55f) else palette.border.copy(alpha = 0.55f),
              ),
            modifier = Modifier.testTag("settings_bg_preset_${bg.key.lowercase()}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(0.75.dp, palette.border, CircleShape),
              )
              Text(
                text = bg.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) palette.activePillFg else palette.textSecondary,
              )
            }
          }
        }
      }

      if (hasActiveBg) {
        Surface(
          color = palette.chromeSurface,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val opacityPct = (settings.editorBgOpacity.coerceIn(0.05f, 1f) * 100f).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Background Opacity", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = "$opacityPct%",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = settings.editorBgOpacity.coerceIn(0.05f, 1f),
                onValueChange = { v -> onUpdateSettings { it.copy(editorBgOpacity = v.coerceIn(0.05f, 1f)) } },
                valueRange = 0.05f..1.0f,
                modifier = Modifier.testTag("settings_bg_opacity_slider"),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.18f to "18%", 0.35f to "35%", 0.60f to "60%", 0.85f to "85%").forEach { (valF, lbl) ->
                  val close = kotlin.math.abs(settings.editorBgOpacity - valF) < 0.04f
                  QuickPresetChip(
                    label = lbl,
                    selected = close,
                    onClick = { onUpdateSettings { it.copy(editorBgOpacity = valF) } },
                  )
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            val zoomVal = settings.editorBgZoom.coerceIn(1f, 3f)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Background Zoom", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = String.format(Locale.US, "%.2f×", zoomVal),
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = zoomVal,
                onValueChange = { z ->
                  onUpdateSettings { cur ->
                    val clampedZ = z.coerceIn(1f, 3f)
                    val maxShift = ((clampedZ - 1f) * 0.45f).coerceIn(0f, 0.5f)
                    cur.copy(
                      editorBgZoom = clampedZ,
                      editorBgOffsetX = cur.editorBgOffsetX.coerceIn(-maxShift, maxShift),
                      editorBgOffsetY = cur.editorBgOffsetY.coerceIn(-maxShift, maxShift),
                    )
                  }
                },
                valueRange = 1.0f..3.0f,
                modifier = Modifier.testTag("settings_bg_zoom_slider"),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                listOf(1.0f to "1.0×", 1.35f to "1.35×", 1.8f to "1.8×", 2.4f to "2.4×").forEach { (zF, lbl) ->
                  val close = kotlin.math.abs(zoomVal - zF) < 0.08f
                  QuickPresetChip(
                    label = lbl,
                    selected = close,
                    onClick = {
                      onUpdateSettings { cur ->
                        val maxShift = ((zF - 1f) * 0.45f).coerceIn(0f, 0.5f)
                        cur.copy(
                          editorBgZoom = zF,
                          editorBgOffsetX = cur.editorBgOffsetX.coerceIn(-maxShift, maxShift),
                          editorBgOffsetY = cur.editorBgOffsetY.coerceIn(-maxShift, maxShift),
                        )
                      }
                    },
                  )
                }
                Spacer(Modifier.weight(1f))
                if (zoomVal > 1.01f || settings.editorBgOffsetX != 0f || settings.editorBgOffsetY != 0f) {
                  TextButton(
                    onClick = {
                      onUpdateSettings {
                        it.copy(editorBgZoom = 1f, editorBgOffsetX = 0f, editorBgOffsetY = 0f)
                      }
                    },
                    modifier = Modifier.height(28.dp),
                  ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Reset", fontSize = 10.5.sp)
                  }
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            val scrimPct = (settings.editorBgScrim.coerceIn(0f, 0.85f) * 100f).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Code Contrast Scrim", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = "$scrimPct%",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = settings.editorBgScrim.coerceIn(0f, 0.85f),
                onValueChange = { s -> onUpdateSettings { it.copy(editorBgScrim = s.coerceIn(0f, 0.85f)) } },
                valueRange = 0.0f..0.85f,
                modifier = Modifier.testTag("settings_bg_scrim_slider"),
              )
            }
          }
        }
      }
    }
    }

    EditorBubblesCustomizationSection(
      settings = settings,
      hasActiveBg = hasActiveBg,
      previewBubbleController = previewBubbleController,
      onUpdateSettings = onUpdateSettings,
    )
  }
}

@Composable
private fun CodeStyleReferencesSection(
  activeCodeStyle: CodeStylePreset,
  overrideCanvas: Boolean,
  onSelectStyle: (CodeStylePreset) -> Unit,
  onToggleOverrideCanvas: (Boolean) -> Unit,
) {
  val palette = LocalIdePalette.current
  val scheme = MaterialTheme.colorScheme
  var previewHtml by remember { mutableStateOf(false) }

  val previewPalette =
    remember(palette, activeCodeStyle, overrideCanvas) {
      applyCodeStylePreset(
        palette = paletteFor(palette.isDark, palette.preset, palette.uiStyle),
        codeStyle = activeCodeStyle,
        overrideCanvas = overrideCanvas,
      )
    }

  val jsSnippet =
    remember {
      """let avg = 0;
query("SELECT * FROM marks", function (sets) {
  if (sets.length > 0) {
    sets.forEach((d) => { avg += parseFloat(d.val); });
  }
  return avg;
});"""
    }
  val htmlSnippet =
    remember {
      """<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width" />
  <div id="page-header" class="hfeed site">
    <link rel="profile" href="https://edge.dev" />
  </div>
</head>"""
    }

  val activeSample = if (previewHtml) htmlSnippet else jsSnippet
  val activeLang = if (previewHtml) WorkerLanguage.HTML else WorkerLanguage.JAVASCRIPT
  val highlightedSample =
    remember(activeSample, activeLang, previewPalette) {
      SyntaxHighlighter.highlight(activeSample, activeLang, previewPalette)
    }

  val floatingController = LocalFloatingPreviewController.current
  val codeStylePreviewId = "code_style_live_preview"

  SettingsFoldableSection(
    title = "Code Style References",
    icon = Icons.Outlined.Palette,
    summary = activeCodeStyle.label,
    initiallyExpanded = true,
    onExpandedChange = { expanded -> if (expanded) floatingController?.select(codeStylePreviewId) },
    contentPadding = 14.dp,
    modifier = Modifier.testTag("settings_code_style_section"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.activatePreviewOnTouch(codeStylePreviewId),
    ) {
      SettingsPreviewSlot(
        id = codeStylePreviewId,
        title = "Code Style References",
        shortLabel = "Code Style",
        order = 1,
      ) {
        // Live Code Style Preview
        Surface(
          color = previewPalette.editorBg,
          shape = RoundedCornerShape(Ca.radius.control),
          border = BorderStroke(0.75.dp, scheme.outlineVariant.copy(alpha = 0.7f)),
          modifier = Modifier.fillMaxWidth().testTag("settings_code_style_live_preview"),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(previewPalette.editorGutterBg)
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "${activeCodeStyle.label} · ${if (previewHtml) "index.html" else "query.js"}",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = previewPalette.editorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickPresetChip(
                  label = "JS",
                  selected = !previewHtml,
                  onClick = { previewHtml = false },
                )
                QuickPresetChip(
                  label = "HTML",
                  selected = previewHtml,
                  onClick = { previewHtml = true },
                )
              }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.75.dp)
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
              Text(
                text = highlightedSample,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = previewPalette.editorTextPrimary,
              )
            }
          }
        }
      }

      // Reference Gallery Cards
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CodeStylePreset.entries.forEach { preset ->
          val selected = preset == activeCodeStyle
          Surface(
            onClick = { onSelectStyle(preset) },
            shape = RoundedCornerShape(12.dp),
            color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
            border =
              BorderStroke(
                width = if (selected) 1.5.dp else 0.75.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.65f),
              ),
            modifier =
              Modifier
                .width(156.dp)
                .testTag("settings_code_style_${preset.key.lowercase()}"),
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Box(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .background(preset.canvasBg),
              ) {
                val refRes = preset.referenceImageRes
                if (refRes != null) {
                  Image(
                    painter = painterResource(id = refRes),
                    contentDescription = preset.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                  Box(
                    modifier =
                      Modifier
                        .fillMaxSize()
                        .background(
                          Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.58f)),
                          ),
                        ),
                  )
                } else {
                  Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                  ) {
                    Text(
                      text = if (preset == CodeStylePreset.THEME_DEFAULT) "auto theme()" else "const fn = () =>",
                      fontFamily = JetBrainsMono,
                      fontSize = 9.5.sp,
                      color = if (preset == CodeStylePreset.THEME_DEFAULT) palette.syntaxKeyword else preset.keyword,
                      maxLines = 1,
                    )
                    Text(
                      text = "  \"syntax_ref\"",
                      fontFamily = JetBrainsMono,
                      fontSize = 9.5.sp,
                      color = if (preset == CodeStylePreset.THEME_DEFAULT) palette.syntaxString else preset.string,
                      maxLines = 1,
                    )
                    Text(
                      text = "  return true;",
                      fontFamily = JetBrainsMono,
                      fontSize = 9.5.sp,
                      color = if (preset == CodeStylePreset.THEME_DEFAULT) palette.syntaxFunction else preset.function,
                      maxLines = 1,
                    )
                  }
                }

                // Token swatch bar in bottom-left
                Row(
                  modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  val dots =
                    if (preset == CodeStylePreset.THEME_DEFAULT) {
                      listOf(palette.syntaxKeyword, palette.syntaxFunction, palette.syntaxString, palette.syntaxProperty)
                    } else {
                      preset.swatchTokens()
                    }
                  dots.forEach { dot ->
                    Box(
                      modifier =
                        Modifier
                          .size(9.dp)
                          .clip(CircleShape)
                          .background(dot)
                          .border(0.75.dp, Color.Black.copy(alpha = 0.45f), CircleShape),
                    )
                  }
                }

                if (selected) {
                  Box(
                    modifier =
                      Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Check,
                      contentDescription = "Active code style",
                      tint = scheme.onPrimary,
                      modifier = Modifier.size(13.dp),
                    )
                  }
                }
              }

              Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = preset.label,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = preset.subtitle,
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 10.sp,
                  color =
                    if (selected) scheme.onPrimaryContainer.copy(alpha = 0.78f)
                    else scheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      if (activeCodeStyle != CodeStylePreset.THEME_DEFAULT) {
        SettingsDivider()
        SettingsToggleRow(
          title = "Match reference background",
          description = "Apply reference editor background & gutter tones alongside syntax colors",
          checked = overrideCanvas,
          horizontalPadding = 0.dp,
          modifier = Modifier.testTag("settings_code_style_override_canvas"),
          onCheckedChange = onToggleOverrideCanvas,
        )
      }
    }
  }
}

@Composable
private fun InteractiveEditorBackgroundPreview(
  settings: EditorSettings,
  hasActiveBg: Boolean,
  bubbleController: EditorBubbleController? = null,
  onPanDelta: (dxNorm: Float, dyNorm: Float) -> Unit,
) {
  val palette = LocalIdePalette.current
  val canPan = hasActiveBg && settings.editorBgZoom > 1.01f
  val bubblesEnabled = settings.editorBubbles.enabled
  val bubblesTapToPop = bubblesEnabled && settings.editorBubbles.tapToPop
  val latestOnPanDelta by rememberUpdatedState(onPanDelta)
  var previewSize by remember { mutableStateOf(IntSize(1, 1)) }

  val indent = " ".repeat(settings.tabSize.coerceIn(2, 4))
  val sampleCode =
    remember(indent) {
      """export default {
${indent}async fetch(req, env) {
${indent}${indent}const colo = req.cf?.colo ?? "EDGE";
${indent}${indent}const data = await env.KV.get("status");
${indent}${indent}return Response.json({ ok: true, colo, data });
${indent}},
};"""
    }
  val highlighted =
    remember(sampleCode, palette) {
      SyntaxHighlighter.highlight(sampleCode, WorkerLanguage.JAVASCRIPT, palette)
    }

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(168.dp)
        .clip(RoundedCornerShape(12.dp))
        .border(0.75.dp, palette.border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        .onSizeChanged { previewSize = it }
        .then(
          if (bubblesTapToPop && bubbleController != null) {
            Modifier.pointerInput(bubblesTapToPop, bubbleController) {
              detectTapGestures { pos ->
                bubbleController.tryPopAt(pos.x, pos.y)
              }
            }
          } else {
            Modifier
          },
        )
        .then(
          if (canPan) {
            Modifier.pointerInput(canPan) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                val w = previewSize.width.coerceAtLeast(1).toFloat()
                val h = previewSize.height.coerceAtLeast(1).toFloat()
                latestOnPanDelta(dragAmount.x / w, dragAmount.y / h)
              }
            }
          } else {
            Modifier
          },
        )
        .testTag("settings_editor_bg_preview"),
  ) {
    EditorBackgroundLayer(
      settings = settings,
      bubbleController = bubbleController,
      modifier = Modifier.fillMaxSize(),
    )

    Row(modifier = Modifier.fillMaxSize()) {
      if (settings.showLineNumbers) {
        Column(
          modifier =
            Modifier
              .background(
                if (hasActiveBg || bubblesEnabled) palette.editorGutterBg.copy(alpha = 0.62f)
                else palette.editorGutterBg,
              )
              .padding(horizontal = 8.dp, vertical = 10.dp),
          horizontalAlignment = Alignment.End,
        ) {
          for (ln in 1..7) {
            Text(
              text = "$ln",
              fontFamily = JetBrainsMono,
              fontSize = settings.fontSizeSp.coerceIn(10, 15).sp,
              lineHeight = (settings.fontSizeSp.coerceIn(10, 15) * 1.48f).sp,
              color = palette.textMuted,
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 10.dp),
      ) {
        Text(
          text = highlighted,
          style =
            TextStyle(
              fontFamily = JetBrainsMono,
              fontSize = settings.fontSizeSp.coerceIn(10, 15).sp,
              lineHeight = (settings.fontSizeSp.coerceIn(10, 15) * 1.48f).sp,
            ),
          maxLines = 7,
          overflow = TextOverflow.Clip,
        )
      }
    }

    if (canPan) {
      Surface(
        color = palette.chromeSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.65f)),
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
      ) {
        Text(
          text = String.format(Locale.US, "%.2f×", settings.editorBgZoom),
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          fontWeight = FontWeight.SemiBold,
          color = palette.activePillFg,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun QuickPresetChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val palette = LocalIdePalette.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(6.dp),
    color = if (selected) palette.activePillBg else palette.chromeSurface,
    border =
      BorderStroke(
        0.75.dp,
        if (selected) palette.activePillFg.copy(alpha = 0.4f) else palette.border.copy(alpha = 0.45f),
      ),
  ) {
    Text(
      text = label,
      fontFamily = JetBrainsMono,
      fontSize = 10.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (selected) palette.activePillFg else palette.textSecondary,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
    )
  }
}

@Composable
private fun EditorBubblesCustomizationSection(
  settings: EditorSettings,
  hasActiveBg: Boolean,
  previewBubbleController: EditorBubbleController,
  onUpdateSettings: ((EditorSettings) -> EditorSettings) -> Unit,
) {
  val palette = LocalIdePalette.current
  val floatingController = LocalFloatingPreviewController.current
  val cfg = settings.editorBubbles.normalized()
  val bubblesPreviewId = "editor_bubbles_live_preview"
  val summaryText =
    if (cfg.enabled) "${cfg.count} · ${cfg.colorStyle.label}"
    else "Off"

  SettingsFoldableSection(
    title = "Floating Bubbles",
    icon = Icons.Outlined.BubbleChart,
    summary = summaryText,
    initiallyExpanded = false,
    onExpandedChange = { expanded -> if (expanded) floatingController?.select(bubblesPreviewId) },
    contentPadding = 12.dp,
    modifier = Modifier.testTag("settings_editor_bubbles_section"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.activatePreviewOnTouch(bubblesPreviewId),
    ) {
      SettingsPreviewSlot(
        id = bubblesPreviewId,
        title = "Floating Bubbles",
        shortLabel = "Bubbles",
        order = 5,
      ) {
        InteractiveEditorBackgroundPreview(
          settings = settings,
          hasActiveBg = hasActiveBg,
          bubbleController = previewBubbleController,
          onPanDelta = { _, _ -> },
        )
      }

      if (cfg.enabled && cfg.tapToPop) {
        Text(
          text = "Tap any rising bubble in the preview above to pop it live.",
          style = MaterialTheme.typography.labelSmall,
          color = palette.textSecondary,
        )
      }

      SettingsToggleRow(
        title = "Enable Editor Bubbles",
        description = "Animated bottom-to-top bubbles with random size, duration, and tap-to-pop",
        checked = cfg.enabled,
        horizontalPadding = 0.dp,
        modifier = Modifier.testTag("settings_bubbles_enabled_switch"),
        onCheckedChange = { enabled ->
          onUpdateSettings { it.copy(editorBubbles = it.editorBubbles.copy(enabled = enabled).normalized()) }
        },
      )

      if (cfg.enabled) {
        Surface(
          color = palette.chromeSurface,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(0.75.dp, palette.border.copy(alpha = 0.55f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Bubble Style", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                EditorBubbleColorStyle.entries.forEach { style ->
                  val selected = cfg.colorStyle == style
                  SettingsPillChip(
                    label = style.label,
                    selected = selected,
                    tag = "settings_bubble_style_${style.key.lowercase()}",
                    onClick = {
                      onUpdateSettings {
                        it.copy(editorBubbles = it.editorBubbles.copy(colorStyle = style).normalized())
                      }
                    },
                  )
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Bubble Count", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = "${cfg.count}",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = cfg.count.toFloat(),
                onValueChange = { v ->
                  onUpdateSettings {
                    it.copy(editorBubbles = it.editorBubbles.copy(count = v.roundToInt()).normalized())
                  }
                },
                valueRange = 4f..32f,
                modifier = Modifier.testTag("settings_bubbles_count_slider"),
              )
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Random Size Range", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = "${cfg.minSizeDp}–${cfg.maxSizeDp} dp",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                listOf(
                  Triple("Tiny", 8, 20),
                  Triple("Classic", 12, 34),
                  Triple("Mixed", 10, 48),
                  Triple("Large", 20, 58),
                ).forEach { (label, minD, maxD) ->
                  val close = cfg.minSizeDp == minD && cfg.maxSizeDp == maxD
                  QuickPresetChip(
                    label = label,
                    selected = close,
                    onClick = {
                      onUpdateSettings {
                        it.copy(
                          editorBubbles = it.editorBubbles.copy(minSizeDp = minD, maxSizeDp = maxD).normalized(),
                        )
                      }
                    },
                  )
                }
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Min (${cfg.minSizeDp}dp)", fontSize = 10.sp, color = palette.textSecondary)
                  Slider(
                    value = cfg.minSizeDp.toFloat(),
                    onValueChange = { v ->
                      val newMin = v.roundToInt()
                      onUpdateSettings {
                        val nextMax = maxOf(it.editorBubbles.maxSizeDp, newMin + 4)
                        it.copy(
                          editorBubbles = it.editorBubbles.copy(minSizeDp = newMin, maxSizeDp = nextMax).normalized(),
                        )
                      }
                    },
                    valueRange = 6f..36f,
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text("Max (${cfg.maxSizeDp}dp)", fontSize = 10.sp, color = palette.textSecondary)
                  Slider(
                    value = cfg.maxSizeDp.toFloat(),
                    onValueChange = { v ->
                      val newMax = v.roundToInt()
                      onUpdateSettings {
                        val nextMin = minOf(it.editorBubbles.minSizeDp, newMax - 4)
                        it.copy(
                          editorBubbles = it.editorBubbles.copy(minSizeDp = nextMin, maxSizeDp = newMax).normalized(),
                        )
                      }
                    },
                    valueRange = 14f..64f,
                  )
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Rise Duration Range", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = String.format(Locale.US, "%.1f–%.1fs", cfg.minDurationSec, cfg.maxDurationSec),
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                listOf(
                  Triple("Swift", 3.5f, 7.5f),
                  Triple("Balanced", 6.0f, 13.0f),
                  Triple("Dreamy", 9.0f, 20.0f),
                ).forEach { (label, minS, maxS) ->
                  val close =
                    kotlin.math.abs(cfg.minDurationSec - minS) < 0.3f &&
                      kotlin.math.abs(cfg.maxDurationSec - maxS) < 0.3f
                  QuickPresetChip(
                    label = label,
                    selected = close,
                    onClick = {
                      onUpdateSettings {
                        it.copy(
                          editorBubbles =
                            it.editorBubbles.copy(minDurationSec = minS, maxDurationSec = maxS).normalized(),
                        )
                      }
                    },
                  )
                }
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = String.format(Locale.US, "Fastest (%.1fs)", cfg.minDurationSec),
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                  )
                  Slider(
                    value = cfg.minDurationSec,
                    onValueChange = { v ->
                      onUpdateSettings {
                        val nextMax = maxOf(it.editorBubbles.maxDurationSec, v + 1f)
                        it.copy(
                          editorBubbles =
                            it.editorBubbles.copy(minDurationSec = v, maxDurationSec = nextMax).normalized(),
                        )
                      }
                    },
                    valueRange = 2.5f..16.0f,
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = String.format(Locale.US, "Slowest (%.1fs)", cfg.maxDurationSec),
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                  )
                  Slider(
                    value = cfg.maxDurationSec,
                    onValueChange = { v ->
                      onUpdateSettings {
                        val nextMin = minOf(it.editorBubbles.minDurationSec, v - 1f)
                        it.copy(
                          editorBubbles =
                            it.editorBubbles.copy(minDurationSec = nextMin, maxDurationSec = v).normalized(),
                        )
                      }
                    },
                    valueRange = 4.5f..26.0f,
                  )
                }
              }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            val bubbleOpacityPct = (cfg.opacity * 100f).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Bubble Opacity", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                Text(
                  text = "$bubbleOpacityPct%",
                  fontFamily = JetBrainsMono,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = palette.activePillFg,
                )
              }
              Slider(
                value = cfg.opacity,
                onValueChange = { o ->
                  onUpdateSettings {
                    it.copy(editorBubbles = it.editorBubbles.copy(opacity = o).normalized())
                  }
                },
                valueRange = 0.08f..0.80f,
                modifier = Modifier.testTag("settings_bubbles_opacity_slider"),
              )
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.35f), thickness = 0.75.dp)

            SettingsToggleRow(
              title = "Tap to Pop Right Away",
              description = "Clicking any bubble inside the editor pops it instantly",
              checked = cfg.tapToPop,
              horizontalPadding = 0.dp,
              modifier = Modifier.testTag("settings_bubbles_tap_pop_switch"),
              onCheckedChange = { enabled ->
                onUpdateSettings {
                  it.copy(editorBubbles = it.editorBubbles.copy(tapToPop = enabled).normalized())
                }
              },
            )

            SettingsToggleRow(
              title = "Pop Burst Ripples",
              description = "Show a crisp shockwave ring & droplets when a bubble pops",
              checked = cfg.popBurstEnabled,
              horizontalPadding = 0.dp,
              modifier = Modifier.testTag("settings_bubbles_burst_switch"),
              onCheckedChange = { enabled ->
                onUpdateSettings {
                  it.copy(editorBubbles = it.editorBubbles.copy(popBurstEnabled = enabled).normalized())
                }
              },
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              TextButton(
                onClick = {
                  onUpdateSettings {
                    it.copy(editorBubbles = EditorBubbleConfig.DEFAULT.copy(enabled = true))
                  }
                },
              ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Bubbles", fontSize = 11.5.sp)
              }
            }
          }
        }
      }
    }
  }
}
