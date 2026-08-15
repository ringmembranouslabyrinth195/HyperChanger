package btm.m.os4.systemuihook

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import org.json.JSONObject

const val REMOTE_PREFERENCE_GROUP = "hyper_system_ui_hook"

data class GlassTuning(
    val blurPercent: Int = 100,
    val opacity: Int = 100,
    val color: Int = 0xFFFFFFFF.toInt(),
    val customColorEnabled: Boolean = false,
)

/**
 * Values map directly to the verified MiBackgroundStyle glass parameter
 * array. Zero deltas and a disabled override leave the platform untouched.
 */
data class MaterialOverride(
    val enabled: Boolean = false,
    val glassRadius: Int = 0,
    val blurPercent: Int = 100,
    val scalePercent: Int = 100,
    val brightness: Int = 0,
    val darker: Int = 0,
    val refraction: Int = 0,
    val burn: Int = 0,
    val saturation: Int = 0,
    val alpha: Int = 0,
    val edgeThickness: Int = 0,
    val reflection: Int = 0,
    val directionalLight: Int = 0,
    val backgroundSaturation: Int = 0,
    val backgroundBrightness: Int = 0,
    val tintEnabled: Boolean = false,
    val tintColor: Int = 0xFFFFFFFF.toInt(),
    val tintStrength: Int = 0,
)

data class ShadePreset(
    val name: String,
    val payload: String,
    val builtIn: Boolean = false,
)

data class HookSettings(
    val notificationElementsMaterial: MaterialOverride = MaterialOverride(),
    val controlCenterElementsMaterial: MaterialOverride = MaterialOverride(),
    val notificationCenterBackgroundMaterial: MaterialOverride = MaterialOverride(),
    val controlCenterBackgroundMaterial: MaterialOverride = MaterialOverride(),
    val notificationOpacity: Int = 100,
    val controlCenterOpacity: Int = 100,
    val blurRadius: Float = 0f,
    val controlButtonOpacity: Int = 100,
    val controlButtonBlurRadius: Float = 0f,
    val controlSliderOpacity: Int = 100,
    val controlSliderBlurRadius: Float = 0f,
    val controlCardOpacity: Int = 100,
    val controlCardBlurRadius: Float = 0f,
    val notificationCardOpacity: Int = 100,
    val notificationCardBlurRadius: Float = 0f,
    val notificationContextUnified: Boolean = true,
    val notificationTypeUnified: Boolean = true,
    val notificationCenterBackground: GlassTuning = GlassTuning(),
    val controlCenterBackground: GlassTuning = GlassTuning(),
    val notificationCenterNormal: GlassTuning = GlassTuning(),
    val lockscreenNormal: GlassTuning = GlassTuning(),
    val notificationCenterMedia: GlassTuning = GlassTuning(),
    val lockscreenMedia: GlassTuning = GlassTuning(),
    val notificationCenterFocus: GlassTuning = GlassTuning(),
    val lockscreenFocus: GlassTuning = GlassTuning(),
    val controlCenterButton: GlassTuning = GlassTuning(),
    val controlCenterSlider: GlassTuning = GlassTuning(),
    val removeFocusAndIslandWhitelistLimit: Boolean = false,
    val islandEnabled: Boolean = false,
    val islandWidth: Int = 108,
    val expandedIslandBackgroundEnabled: Boolean = false,
    val expandedIslandBackgroundOpacity: Int = 97,
    val expandedIslandGlassBlurRadius: Int = 110,
    val expandedIslandGlassLargeBlurRadius: Int = 110,
    val expandedIslandSelfBlurRadius: Int = 0,
    val expandedIslandShowHighlight: Boolean = false,
    val clockEnabled: Boolean = false,
    val clockSize: Float = 14.8f,
    val paddingEndEnabled: Boolean = false,
    val paddingEnd: Float = 6f,
    val paddingStartEnabled: Boolean = false,
    val paddingStart: Float = 12.5f,
    val heightEnabled: Boolean = false,
    val statusBarHeight: Int = 40,
    val paddingTopEnabled: Boolean = false,
    val paddingTop: Float = 15f,
    val topButtonsRadiusEnabled: Boolean = false,
    val topButtonsRadius: Float = 24f,
    val mediaCardRadiusEnabled: Boolean = false,
    val mediaCardRadius: Float = 24f,
    val sliderRadiusEnabled: Boolean = false,
    val sliderRadius: Float = 24f,
    val deviceCenterRadiusEnabled: Boolean = false,
    val deviceCenterRadius: Float = 24f,
    val removeDepthImageLimit: Boolean = false,
    val notificationFodMode: Int = 0,
    val hideLockscreenChargingText: Boolean = false,
    val lockscreenShortcutBackgroundMode: Int = 0,
    val lockscreenShortcutGlassRadius: Float = 48f,
    val shortcutIconColorMode: Int = 0,
    val shortcutPureColor: Int = 0x73FFFFFF,
    val shortcutAdvancedMaterialColor: Int = 0xFFFFFFFF.toInt(),
    val shortcutAdvancedMaterialOpacity: Int = 14,
    val shortcutAdvancedMaterialBlurRadius: Int = 80,
    val shortcutAdvancedMaterialHighlight: Boolean = false,
    val shortcutSoftGlassColor: Int = 0xFFFFFFFF.toInt(),
    val shortcutSoftGlassOpacity: Int = 10,
    val shortcutSoftGlassBackdropBlurRadius: Int = 80,
    val shortcutSoftGlassBlurRadius: Int = 36,
    val shortcutSoftGlassLuminance: Float = 0.14f,
    val themeMode: String = "system",
    val navigationStyle: String = "hyper_os",
    val predictiveBackEnabled: Boolean = true,
    val predictiveBackProgress: Int = 90,
)

class HookSettingsStore(context: Context) {
    private val local = context.getSharedPreferences(REMOTE_PREFERENCE_GROUP, Context.MODE_PRIVATE)
    var settings: HookSettings = local.toSettings()
        private set

    init {
        local.edit().removeLegacyIslandPreferences().removeLegacyShadePreferences().apply()
    }

    fun syncRemote(service: XposedService) {
        val remote = service.getRemotePreferences(REMOTE_PREFERENCE_GROUP)
        remote.edit().removeLegacyIslandPreferences().removeLegacyShadePreferences().apply()
        settings = if (remote.contains(KEY_INITIALIZED)) {
            remote.toSettings()
        } else {
            settings
        }
        remote.write(settings)
        local.write(settings)
    }

    fun update(service: XposedService?, transform: (HookSettings) -> HookSettings) {
        settings = transform(settings)
        local.write(settings)
        service?.getRemotePreferences(REMOTE_PREFERENCE_GROUP)?.write(settings)
    }

    fun userShadePresets(): List<ShadePreset> = runCatching {
        val entries = JSONObject(local.getString(KEY_USER_SHADE_PRESETS, "{}") ?: "{}")
        entries.keys().asSequence().mapNotNull { name ->
            entries.optString(name).takeIf { it.isNotBlank() }?.let { ShadePreset(name, it) }
        }.toList().sortedBy { it.name }
    }.getOrDefault(emptyList())

    fun saveUserShadePreset(name: String, settings: HookSettings) {
        val normalizedName = name.trim().take(MAX_PRESET_NAME_LENGTH)
        require(normalizedName.isNotBlank())
        val entries = JSONObject(local.getString(KEY_USER_SHADE_PRESETS, "{}") ?: "{}")
        entries.put(normalizedName, settings.exportShadePreset(normalizedName))
        local.edit().putString(KEY_USER_SHADE_PRESETS, entries.toString()).apply()
    }

    fun deleteUserShadePreset(name: String) {
        val entries = JSONObject(local.getString(KEY_USER_SHADE_PRESETS, "{}") ?: "{}")
        entries.remove(name)
        local.edit().putString(KEY_USER_SHADE_PRESETS, entries.toString()).apply()
    }
}

private const val KEY_INITIALIZED = "initialized"
private const val KEY_USER_SHADE_PRESETS = "user_shade_presets_v1"
private const val MAX_PRESET_NAME_LENGTH = 40
private const val KEY_NOTIFICATION_ELEMENTS_MATERIAL = "shade_notification_elements_material_v2"
private const val KEY_CONTROL_CENTER_ELEMENTS_MATERIAL = "shade_control_center_elements_material_v2"
private const val KEY_NOTIFICATION_CENTER_BACKGROUND_MATERIAL = "shade_notification_center_background_material_v2"
private const val KEY_CONTROL_CENTER_BACKGROUND_MATERIAL = "shade_control_center_background_material_v2"
private const val LEGACY_EXPANDED_ISLAND_BACKGROUND_COLOR = "expanded_island_background_color"
private const val LEGACY_ISLAND_GLOW_ENABLED = "island_glow_enabled"
private const val LEGACY_ISLAND_PROGRESS_STYLE = "island_progress_style"
private const val LEGACY_ISLAND_MUSIC_SOURCE_ICON_ENABLED = "island_music_source_icon_enabled"
private const val LEGACY_ISLAND_TITLE_TOP_SPACING = "island_title_top_spacing"
private const val KEY_NOTIFICATION_OPACITY = "notification_opacity"
private const val KEY_CONTROL_CENTER_OPACITY = "control_center_opacity"
private const val KEY_BLUR_RADIUS = "blur_radius"
private const val KEY_CONTROL_BUTTON_OPACITY = "control_button_opacity"
private const val KEY_CONTROL_BUTTON_BLUR_RADIUS = "control_button_blur_radius"
private const val KEY_CONTROL_SLIDER_OPACITY = "control_slider_opacity"
private const val KEY_CONTROL_SLIDER_BLUR_RADIUS = "control_slider_blur_radius"
private const val KEY_CONTROL_CARD_OPACITY = "control_card_opacity"
private const val KEY_CONTROL_CARD_BLUR_RADIUS = "control_card_blur_radius"
private const val KEY_NOTIFICATION_CARD_OPACITY = "notification_card_opacity"
private const val KEY_NOTIFICATION_CARD_BLUR_RADIUS = "notification_card_blur_radius"
private const val KEY_NOTIFICATION_CONTEXT_UNIFIED = "notification_context_unified"
private const val KEY_NOTIFICATION_TYPE_UNIFIED = "notification_type_unified"
private const val KEY_NOTIFICATION_CENTER_BACKGROUND = "notification_center_background_glass"
private const val KEY_CONTROL_CENTER_BACKGROUND = "control_center_background_glass"
private const val KEY_NOTIFICATION_CENTER_NORMAL = "notification_center_normal_glass"
private const val KEY_LOCKSCREEN_NORMAL = "lockscreen_normal_glass"
private const val KEY_NOTIFICATION_CENTER_MEDIA = "notification_center_media_glass"
private const val KEY_LOCKSCREEN_MEDIA = "lockscreen_media_glass"
private const val KEY_NOTIFICATION_CENTER_FOCUS = "notification_center_focus_glass"
private const val KEY_LOCKSCREEN_FOCUS = "lockscreen_focus_glass"
private const val KEY_CONTROL_CENTER_BUTTON = "control_center_button_glass"
private const val KEY_CONTROL_CENTER_SLIDER = "control_center_slider_glass"
private const val KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT =
    "remove_focus_and_island_whitelist_limit"
private const val KEY_ISLAND_ENABLED = "island_enabled"
private const val KEY_ISLAND_WIDTH = "island_width"
private const val KEY_EXPANDED_ISLAND_BACKGROUND_ENABLED = "expanded_island_background_enabled"
private const val KEY_EXPANDED_ISLAND_BACKGROUND_OPACITY = "expanded_island_background_opacity"
private const val KEY_EXPANDED_ISLAND_GLASS_BLUR_RADIUS = "expanded_island_glass_blur_radius"
private const val KEY_EXPANDED_ISLAND_GLASS_LARGE_BLUR_RADIUS = "expanded_island_glass_large_blur_radius"
private const val KEY_EXPANDED_ISLAND_SELF_BLUR_RADIUS = "expanded_island_self_blur_radius"
private const val KEY_EXPANDED_ISLAND_SHOW_HIGHLIGHT = "expanded_island_show_highlight"
private const val KEY_CLOCK_ENABLED = "clock_enabled"
private const val KEY_CLOCK_SIZE = "clock_size"
private const val KEY_PADDING_END_ENABLED = "padding_end_enabled"
private const val KEY_PADDING_END = "padding_end"
private const val KEY_PADDING_START_ENABLED = "padding_start_enabled"
private const val KEY_PADDING_START = "padding_start"
private const val KEY_HEIGHT_ENABLED = "height_enabled"
private const val KEY_STATUS_BAR_HEIGHT = "status_bar_height"
private const val KEY_PADDING_TOP_ENABLED = "padding_top_enabled"
private const val KEY_PADDING_TOP = "padding_top"
private const val KEY_TOP_BUTTONS_RADIUS_ENABLED = "top_buttons_radius_enabled"
private const val KEY_TOP_BUTTONS_RADIUS = "top_buttons_radius"
private const val KEY_MEDIA_CARD_RADIUS_ENABLED = "media_card_radius_enabled"
private const val KEY_MEDIA_CARD_RADIUS = "media_card_radius"
private const val KEY_SLIDER_RADIUS_ENABLED = "slider_radius_enabled"
private const val KEY_SLIDER_RADIUS = "slider_radius"
private const val KEY_DEVICE_CENTER_RADIUS_ENABLED = "device_center_radius_enabled"
private const val KEY_DEVICE_CENTER_RADIUS = "device_center_radius"
private const val KEY_REMOVE_DEPTH_IMAGE_LIMIT = "remove_depth_image_limit"
private const val KEY_NOTIFICATION_FOD_MODE = "notification_fod_mode"
private const val KEY_NOTIFICATIONS_IGNORE_FOD = "notifications_ignore_fod"
private const val KEY_HIDE_LOCKSCREEN_CHARGING_TEXT = "hide_lockscreen_charging_text"
private const val KEY_LOCKSCREEN_SHORTCUT_BACKGROUND_MODE = "lockscreen_shortcut_background_mode"
private const val KEY_LOCKSCREEN_SHORTCUT_GLASS_ENABLED = "lockscreen_shortcut_glass_enabled"
private const val KEY_LOCKSCREEN_SHORTCUT_GLASS_RADIUS = "lockscreen_shortcut_glass_radius"
private const val KEY_SHORTCUT_ICON_COLOR_MODE = "shortcut_icon_color_mode"
private const val KEY_SHORTCUT_PURE_COLOR = "shortcut_pure_color"
private const val KEY_SHORTCUT_ADVANCED_MATERIAL_COLOR = "shortcut_advanced_material_color"
private const val KEY_SHORTCUT_ADVANCED_MATERIAL_OPACITY = "shortcut_advanced_material_opacity"
private const val KEY_SHORTCUT_ADVANCED_MATERIAL_BLUR_RADIUS = "shortcut_advanced_material_blur_radius"
private const val KEY_SHORTCUT_ADVANCED_MATERIAL_HIGHLIGHT = "shortcut_advanced_material_highlight"
private const val KEY_SHORTCUT_SOFT_GLASS_COLOR = "shortcut_soft_glass_color"
private const val KEY_SHORTCUT_SOFT_GLASS_OPACITY = "shortcut_soft_glass_opacity"
private const val KEY_SHORTCUT_SOFT_GLASS_BACKDROP_BLUR_RADIUS = "shortcut_soft_glass_backdrop_blur_radius"
private const val KEY_SHORTCUT_SOFT_GLASS_BLUR_RADIUS = "shortcut_soft_glass_blur_radius"
private const val KEY_SHORTCUT_SOFT_GLASS_LUMINANCE = "shortcut_soft_glass_luminance"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_NAVIGATION_STYLE = "navigation_style"
private const val KEY_PREDICTIVE_BACK_ENABLED = "predictive_back_enabled"
private const val KEY_PREDICTIVE_BACK_PROGRESS = "predictive_back_progress"
private fun SharedPreferences.toSettings() = HookSettings(
    notificationElementsMaterial = getMaterialOverride(KEY_NOTIFICATION_ELEMENTS_MATERIAL),
    controlCenterElementsMaterial = getMaterialOverride(KEY_CONTROL_CENTER_ELEMENTS_MATERIAL),
    notificationCenterBackgroundMaterial = getMaterialOverride(KEY_NOTIFICATION_CENTER_BACKGROUND_MATERIAL),
    controlCenterBackgroundMaterial = getMaterialOverride(KEY_CONTROL_CENTER_BACKGROUND_MATERIAL),
    notificationOpacity = getInt(KEY_NOTIFICATION_OPACITY, 100).coerceIn(0, 100),
    controlCenterOpacity = getInt(KEY_CONTROL_CENTER_OPACITY, 100).coerceIn(0, 100),
    blurRadius = getFloat(KEY_BLUR_RADIUS, 0f).coerceIn(0f, 40f),
    controlButtonOpacity = getInt(KEY_CONTROL_BUTTON_OPACITY, 100).coerceIn(0, 100),
    controlButtonBlurRadius = getFloat(KEY_CONTROL_BUTTON_BLUR_RADIUS, 0f).coerceIn(0f, 40f),
    controlSliderOpacity = getInt(KEY_CONTROL_SLIDER_OPACITY, 100).coerceIn(0, 100),
    controlSliderBlurRadius = getFloat(KEY_CONTROL_SLIDER_BLUR_RADIUS, 0f).coerceIn(0f, 40f),
    controlCardOpacity = getInt(KEY_CONTROL_CARD_OPACITY, 100).coerceIn(0, 100),
    controlCardBlurRadius = getFloat(KEY_CONTROL_CARD_BLUR_RADIUS, 0f).coerceIn(0f, 40f),
    notificationCardOpacity = getInt(KEY_NOTIFICATION_CARD_OPACITY, 100).coerceIn(0, 100),
    notificationCardBlurRadius = getFloat(KEY_NOTIFICATION_CARD_BLUR_RADIUS, 0f).coerceIn(0f, 40f),
    notificationContextUnified = getBoolean(KEY_NOTIFICATION_CONTEXT_UNIFIED, true),
    notificationTypeUnified = getBoolean(KEY_NOTIFICATION_TYPE_UNIFIED, true),
    notificationCenterBackground = getGlassTuning(KEY_NOTIFICATION_CENTER_BACKGROUND),
    controlCenterBackground = getGlassTuning(KEY_CONTROL_CENTER_BACKGROUND),
    notificationCenterNormal = getGlassTuning(KEY_NOTIFICATION_CENTER_NORMAL),
    lockscreenNormal = getGlassTuning(KEY_LOCKSCREEN_NORMAL),
    notificationCenterMedia = getGlassTuning(KEY_NOTIFICATION_CENTER_MEDIA),
    lockscreenMedia = getGlassTuning(KEY_LOCKSCREEN_MEDIA),
    notificationCenterFocus = getGlassTuning(KEY_NOTIFICATION_CENTER_FOCUS),
    lockscreenFocus = getGlassTuning(KEY_LOCKSCREEN_FOCUS),
    controlCenterButton = getGlassTuning(KEY_CONTROL_CENTER_BUTTON),
    controlCenterSlider = getGlassTuning(KEY_CONTROL_CENTER_SLIDER),
    removeFocusAndIslandWhitelistLimit =
        getBoolean(KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT, false),
    islandEnabled = getBoolean(KEY_ISLAND_ENABLED, false),
    islandWidth = getInt(KEY_ISLAND_WIDTH, 108).coerceIn(108, 190),
    expandedIslandBackgroundEnabled = getBoolean(KEY_EXPANDED_ISLAND_BACKGROUND_ENABLED, false),
    expandedIslandBackgroundOpacity = getInt(KEY_EXPANDED_ISLAND_BACKGROUND_OPACITY, 35).coerceIn(0, 35),
    expandedIslandGlassBlurRadius = getInt(KEY_EXPANDED_ISLAND_GLASS_BLUR_RADIUS, 10).coerceIn(0, 10),
    expandedIslandGlassLargeBlurRadius = getInt(KEY_EXPANDED_ISLAND_GLASS_LARGE_BLUR_RADIUS, 10).coerceIn(0, 10),
    expandedIslandSelfBlurRadius = getInt(KEY_EXPANDED_ISLAND_SELF_BLUR_RADIUS, 0).coerceIn(0, 10),
    expandedIslandShowHighlight = getBoolean(KEY_EXPANDED_ISLAND_SHOW_HIGHLIGHT, false),
    clockEnabled = getBoolean(KEY_CLOCK_ENABLED, false),
    clockSize = getFloat(KEY_CLOCK_SIZE, 14.8f).coerceIn(10f, 24f),
    paddingEndEnabled = getBoolean(KEY_PADDING_END_ENABLED, false),
    paddingEnd = getFloat(KEY_PADDING_END, 6f).coerceIn(0f, 32f),
    paddingStartEnabled = getBoolean(KEY_PADDING_START_ENABLED, false),
    paddingStart = getFloat(KEY_PADDING_START, 12.5f).coerceIn(0f, 32f),
    heightEnabled = getBoolean(KEY_HEIGHT_ENABLED, false),
    statusBarHeight = getInt(KEY_STATUS_BAR_HEIGHT, 40).coerceIn(24, 72),
    paddingTopEnabled = getBoolean(KEY_PADDING_TOP_ENABLED, false),
    paddingTop = getFloat(KEY_PADDING_TOP, 15f).coerceIn(0f, 32f),
    topButtonsRadiusEnabled = getBoolean(KEY_TOP_BUTTONS_RADIUS_ENABLED, false),
    topButtonsRadius = getFloat(KEY_TOP_BUTTONS_RADIUS, 24f).coerceIn(0f, 60f),
    mediaCardRadiusEnabled = getBoolean(KEY_MEDIA_CARD_RADIUS_ENABLED, false),
    mediaCardRadius = getFloat(KEY_MEDIA_CARD_RADIUS, 24f).coerceIn(0f, 60f),
    sliderRadiusEnabled = getBoolean(KEY_SLIDER_RADIUS_ENABLED, false),
    sliderRadius = getFloat(KEY_SLIDER_RADIUS, 24f).coerceIn(0f, 60f),
    deviceCenterRadiusEnabled = getBoolean(KEY_DEVICE_CENTER_RADIUS_ENABLED, false),
    deviceCenterRadius = getFloat(KEY_DEVICE_CENTER_RADIUS, 24f).coerceIn(0f, 60f),
    removeDepthImageLimit = getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false),
    notificationFodMode = getInt(
        KEY_NOTIFICATION_FOD_MODE,
        if (getBoolean(KEY_NOTIFICATIONS_IGNORE_FOD, false)) 2 else 0,
    ).coerceIn(0, 2),
    hideLockscreenChargingText = getBoolean(KEY_HIDE_LOCKSCREEN_CHARGING_TEXT, false),
    lockscreenShortcutBackgroundMode = getInt(
        KEY_LOCKSCREEN_SHORTCUT_BACKGROUND_MODE,
        if (getBoolean(KEY_LOCKSCREEN_SHORTCUT_GLASS_ENABLED, false)) 3 else 0,
    ).coerceIn(0, 3),
    lockscreenShortcutGlassRadius = getFloat(KEY_LOCKSCREEN_SHORTCUT_GLASS_RADIUS, 48f)
        .coerceIn(28f, 80f),
    shortcutIconColorMode = getInt(KEY_SHORTCUT_ICON_COLOR_MODE, 0).coerceIn(0, 2),
    shortcutPureColor = getInt(KEY_SHORTCUT_PURE_COLOR, 0x73FFFFFF),
    shortcutAdvancedMaterialColor = getInt(KEY_SHORTCUT_ADVANCED_MATERIAL_COLOR, 0xFFFFFFFF.toInt()),
    shortcutAdvancedMaterialOpacity = getInt(KEY_SHORTCUT_ADVANCED_MATERIAL_OPACITY, 14).coerceIn(0, 35),
    shortcutAdvancedMaterialBlurRadius = getInt(KEY_SHORTCUT_ADVANCED_MATERIAL_BLUR_RADIUS, 10).coerceIn(0, 10),
    shortcutAdvancedMaterialHighlight = getBoolean(KEY_SHORTCUT_ADVANCED_MATERIAL_HIGHLIGHT, false),
    shortcutSoftGlassColor = getInt(KEY_SHORTCUT_SOFT_GLASS_COLOR, 0xFFFFFFFF.toInt()),
    shortcutSoftGlassOpacity = getInt(KEY_SHORTCUT_SOFT_GLASS_OPACITY, 10).coerceIn(0, 35),
    shortcutSoftGlassBackdropBlurRadius = getInt(KEY_SHORTCUT_SOFT_GLASS_BACKDROP_BLUR_RADIUS, 10).coerceIn(0, 10),
    shortcutSoftGlassBlurRadius = getInt(KEY_SHORTCUT_SOFT_GLASS_BLUR_RADIUS, 10).coerceIn(0, 10),
    shortcutSoftGlassLuminance = getFloat(KEY_SHORTCUT_SOFT_GLASS_LUMINANCE, 0.14f).coerceIn(0f, 0.4f),
    themeMode = getString(KEY_THEME_MODE, "system").orEmpty().ifBlank { "system" },
    navigationStyle = getString(KEY_NAVIGATION_STYLE, "hyper_os").orEmpty().ifBlank { "hyper_os" },
    predictiveBackEnabled = getBoolean(KEY_PREDICTIVE_BACK_ENABLED, true),
    predictiveBackProgress = getInt(KEY_PREDICTIVE_BACK_PROGRESS, 90).coerceIn(10, 100),
)

private fun SharedPreferences.Editor.removeLegacyIslandPreferences(): SharedPreferences.Editor =
    remove(LEGACY_EXPANDED_ISLAND_BACKGROUND_COLOR)
        .remove(LEGACY_ISLAND_GLOW_ENABLED)
        .remove(LEGACY_ISLAND_PROGRESS_STYLE)
        .remove(LEGACY_ISLAND_MUSIC_SOURCE_ICON_ENABLED)
        .remove(LEGACY_ISLAND_TITLE_TOP_SPACING)

private fun SharedPreferences.Editor.removeLegacyShadePreferences(): SharedPreferences.Editor =
    remove(KEY_NOTIFICATION_OPACITY)
        .remove(KEY_CONTROL_CENTER_OPACITY)
        .remove(KEY_BLUR_RADIUS)
        .remove(KEY_CONTROL_BUTTON_OPACITY)
        .remove(KEY_CONTROL_BUTTON_BLUR_RADIUS)
        .remove(KEY_CONTROL_SLIDER_OPACITY)
        .remove(KEY_CONTROL_SLIDER_BLUR_RADIUS)
        .remove(KEY_CONTROL_CARD_OPACITY)
        .remove(KEY_CONTROL_CARD_BLUR_RADIUS)
        .remove(KEY_NOTIFICATION_CARD_OPACITY)
        .remove(KEY_NOTIFICATION_CARD_BLUR_RADIUS)
        .remove(KEY_NOTIFICATION_CONTEXT_UNIFIED)
        .remove(KEY_NOTIFICATION_TYPE_UNIFIED)
        .remove(KEY_NOTIFICATION_CENTER_BACKGROUND)
        .remove(KEY_CONTROL_CENTER_BACKGROUND)
        .remove(KEY_NOTIFICATION_CENTER_NORMAL)
        .remove(KEY_LOCKSCREEN_NORMAL)
        .remove(KEY_NOTIFICATION_CENTER_MEDIA)
        .remove(KEY_LOCKSCREEN_MEDIA)
        .remove(KEY_NOTIFICATION_CENTER_FOCUS)
        .remove(KEY_LOCKSCREEN_FOCUS)
        .remove(KEY_CONTROL_CENTER_BUTTON)
        .remove(KEY_CONTROL_CENTER_SLIDER)

fun SharedPreferences.getMaterialOverride(key: String): MaterialOverride =
    parseMaterialOverride(getString(key, null)).let { value ->
        if (key == KEY_NOTIFICATION_CENTER_BACKGROUND_MATERIAL || key == KEY_CONTROL_CENTER_BACKGROUND_MATERIAL) {
            value.copy(
                blurPercent = value.blurPercent.coerceIn(0, 100),
                alpha = if (value.enabled) value.alpha.coerceIn(-100, -65) else value.alpha,
            )
        } else {
            value
        }
    }

private fun parseMaterialOverride(encoded: String?): MaterialOverride {
    val parts = encoded?.split('|') ?: return MaterialOverride()
    fun int(index: Int, range: IntRange, fallback: Int) =
        parts.getOrNull(index)?.toIntOrNull()?.coerceIn(range) ?: fallback
    return MaterialOverride(
        enabled = parts.getOrNull(0)?.toBooleanStrictOrNull() ?: false,
        glassRadius = int(1, 0..10, 0),
        blurPercent = int(2, 0..200, 100),
        scalePercent = int(3, 0..200, 100),
        brightness = int(4, -30..30, 0),
        darker = int(5, -50..50, 0),
        refraction = int(6, -100..100, 0),
        burn = int(7, -50..50, 0),
        saturation = int(8, -100..100, 0),
        alpha = int(9, -100..0, 0),
        edgeThickness = int(10, -100..100, 0),
        reflection = int(11, -100..100, 0),
        directionalLight = int(12, -100..100, 0),
        backgroundSaturation = int(13, -100..100, 0),
        backgroundBrightness = int(14, -100..100, 0),
        tintEnabled = parts.getOrNull(15)?.toBooleanStrictOrNull() ?: false,
        tintColor = parts.getOrNull(16)?.toLongOrNull()?.toInt() ?: 0xFFFFFFFF.toInt(),
        tintStrength = int(17, 0..50, 0),
    )
}

private fun MaterialOverride.serialize(): String = listOf(
    enabled, glassRadius, blurPercent, scalePercent, brightness, darker, refraction, burn,
    saturation, alpha, edgeThickness, reflection, directionalLight, backgroundSaturation,
    backgroundBrightness, tintEnabled, tintColor.toLong(), tintStrength,
).joinToString("|")

fun HookSettings.exportShadePreset(name: String? = null): String = JSONObject().apply {
    put("format", "os4changer-shade-preset")
    put("version", 2)
    name?.trim()?.takeIf { it.isNotBlank() }?.let { put("name", it.take(MAX_PRESET_NAME_LENGTH)) }
    put("notificationElements", notificationElementsMaterial.serialize())
    put("controlCenterElements", controlCenterElementsMaterial.serialize())
    put("notificationCenterBackground", notificationCenterBackgroundMaterial.serialize())
    put("controlCenterBackground", controlCenterBackgroundMaterial.serialize())
}.toString(2)

fun HookSettings.importShadePreset(payload: String): HookSettings {
    val imported = parseShadePreset(payload).settings
    return copy(
        notificationElementsMaterial = imported.notificationElementsMaterial,
        controlCenterElementsMaterial = imported.controlCenterElementsMaterial,
        notificationCenterBackgroundMaterial = imported.notificationCenterBackgroundMaterial,
        controlCenterBackgroundMaterial = imported.controlCenterBackgroundMaterial,
    )
}

fun parseShadePreset(payload: String): ShadePresetImport {
    val preset = JSONObject(payload)
    require(preset.optString("format") == "os4changer-shade-preset") { "不支持的预设文件" }
    require(preset.optInt("version", 0) == 2) { "不支持的预设版本" }
    fun value(key: String): String? = if (preset.has(key)) preset.getString(key) else null
    return ShadePresetImport(
        name = preset.optString("name").trim().take(MAX_PRESET_NAME_LENGTH).ifBlank { null },
        settings = HookSettings().copy(
        notificationElementsMaterial = parseMaterialOverride(value("notificationElements")),
        controlCenterElementsMaterial = parseMaterialOverride(value("controlCenterElements")),
        notificationCenterBackgroundMaterial = parseMaterialOverride(value("notificationCenterBackground")),
        controlCenterBackgroundMaterial = parseMaterialOverride(value("controlCenterBackground")),
        ),
    )
}

data class ShadePresetImport(val name: String?, val settings: HookSettings)

fun ShadePreset.applyTo(settings: HookSettings): HookSettings = settings.importShadePreset(payload)

private fun SharedPreferences.getGlassTuning(key: String): GlassTuning {
    val parts = getString(key, null)?.split('|') ?: return GlassTuning()
    return GlassTuning(
        blurPercent = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 200) ?: 100,
        opacity = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 100,
        color = parts.getOrNull(2)?.toLongOrNull()?.toInt() ?: 0xFFFFFFFF.toInt(),
        customColorEnabled = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
    )
}

private fun GlassTuning.serialize(): String =
    "$blurPercent|$opacity|${color.toLong()}|$customColorEnabled"

private fun SharedPreferences.write(value: HookSettings) {
    edit()
        .removeLegacyIslandPreferences()
        .removeLegacyShadePreferences()
        .putBoolean(KEY_INITIALIZED, true)
        .putString(KEY_NOTIFICATION_ELEMENTS_MATERIAL, value.notificationElementsMaterial.serialize())
        .putString(KEY_CONTROL_CENTER_ELEMENTS_MATERIAL, value.controlCenterElementsMaterial.serialize())
        .putString(KEY_NOTIFICATION_CENTER_BACKGROUND_MATERIAL, value.notificationCenterBackgroundMaterial.serialize())
        .putString(KEY_CONTROL_CENTER_BACKGROUND_MATERIAL, value.controlCenterBackgroundMaterial.serialize())
        .putBoolean(
            KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT,
            value.removeFocusAndIslandWhitelistLimit,
        )
        .putBoolean(KEY_ISLAND_ENABLED, value.islandEnabled)
        .putInt(KEY_ISLAND_WIDTH, value.islandWidth)
        .putBoolean(KEY_EXPANDED_ISLAND_BACKGROUND_ENABLED, value.expandedIslandBackgroundEnabled)
        .putInt(KEY_EXPANDED_ISLAND_BACKGROUND_OPACITY, value.expandedIslandBackgroundOpacity)
        .putInt(KEY_EXPANDED_ISLAND_GLASS_BLUR_RADIUS, value.expandedIslandGlassBlurRadius)
        .putInt(KEY_EXPANDED_ISLAND_GLASS_LARGE_BLUR_RADIUS, value.expandedIslandGlassLargeBlurRadius)
        .putInt(KEY_EXPANDED_ISLAND_SELF_BLUR_RADIUS, value.expandedIslandSelfBlurRadius)
        .putBoolean(KEY_EXPANDED_ISLAND_SHOW_HIGHLIGHT, value.expandedIslandShowHighlight)
        .putBoolean(KEY_CLOCK_ENABLED, value.clockEnabled)
        .putFloat(KEY_CLOCK_SIZE, value.clockSize)
        .putBoolean(KEY_PADDING_END_ENABLED, value.paddingEndEnabled)
        .putFloat(KEY_PADDING_END, value.paddingEnd)
        .putBoolean(KEY_PADDING_START_ENABLED, value.paddingStartEnabled)
        .putFloat(KEY_PADDING_START, value.paddingStart)
        .putBoolean(KEY_HEIGHT_ENABLED, value.heightEnabled)
        .putInt(KEY_STATUS_BAR_HEIGHT, value.statusBarHeight)
        .putBoolean(KEY_PADDING_TOP_ENABLED, value.paddingTopEnabled)
        .putFloat(KEY_PADDING_TOP, value.paddingTop)
        .putBoolean(KEY_TOP_BUTTONS_RADIUS_ENABLED, value.topButtonsRadiusEnabled)
        .putFloat(KEY_TOP_BUTTONS_RADIUS, value.topButtonsRadius)
        .putBoolean(KEY_MEDIA_CARD_RADIUS_ENABLED, value.mediaCardRadiusEnabled)
        .putFloat(KEY_MEDIA_CARD_RADIUS, value.mediaCardRadius)
        .putBoolean(KEY_SLIDER_RADIUS_ENABLED, value.sliderRadiusEnabled)
        .putFloat(KEY_SLIDER_RADIUS, value.sliderRadius)
        .putBoolean(KEY_DEVICE_CENTER_RADIUS_ENABLED, value.deviceCenterRadiusEnabled)
        .putFloat(KEY_DEVICE_CENTER_RADIUS, value.deviceCenterRadius)
        .putBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, value.removeDepthImageLimit)
        .putInt(KEY_NOTIFICATION_FOD_MODE, value.notificationFodMode)
        .putBoolean(KEY_HIDE_LOCKSCREEN_CHARGING_TEXT, value.hideLockscreenChargingText)
        .putInt(KEY_LOCKSCREEN_SHORTCUT_BACKGROUND_MODE, value.lockscreenShortcutBackgroundMode)
        .putBoolean(KEY_LOCKSCREEN_SHORTCUT_GLASS_ENABLED, value.lockscreenShortcutBackgroundMode != 0)
        .putFloat(KEY_LOCKSCREEN_SHORTCUT_GLASS_RADIUS, value.lockscreenShortcutGlassRadius)
        .putInt(KEY_SHORTCUT_ICON_COLOR_MODE, value.shortcutIconColorMode)
        .putInt(KEY_SHORTCUT_PURE_COLOR, value.shortcutPureColor)
        .putInt(KEY_SHORTCUT_ADVANCED_MATERIAL_COLOR, value.shortcutAdvancedMaterialColor)
        .putInt(KEY_SHORTCUT_ADVANCED_MATERIAL_OPACITY, value.shortcutAdvancedMaterialOpacity)
        .putInt(KEY_SHORTCUT_ADVANCED_MATERIAL_BLUR_RADIUS, value.shortcutAdvancedMaterialBlurRadius)
        .putBoolean(KEY_SHORTCUT_ADVANCED_MATERIAL_HIGHLIGHT, value.shortcutAdvancedMaterialHighlight)
        .putInt(KEY_SHORTCUT_SOFT_GLASS_COLOR, value.shortcutSoftGlassColor)
        .putInt(KEY_SHORTCUT_SOFT_GLASS_OPACITY, value.shortcutSoftGlassOpacity)
        .putInt(KEY_SHORTCUT_SOFT_GLASS_BACKDROP_BLUR_RADIUS, value.shortcutSoftGlassBackdropBlurRadius)
        .putInt(KEY_SHORTCUT_SOFT_GLASS_BLUR_RADIUS, value.shortcutSoftGlassBlurRadius)
        .putFloat(KEY_SHORTCUT_SOFT_GLASS_LUMINANCE, value.shortcutSoftGlassLuminance)
        .putString(KEY_THEME_MODE, value.themeMode)
        .putString(KEY_NAVIGATION_STYLE, value.navigationStyle)
        .putBoolean(KEY_PREDICTIVE_BACK_ENABLED, value.predictiveBackEnabled)
        .putInt(KEY_PREDICTIVE_BACK_PROGRESS, value.predictiveBackProgress)
        .apply()
}
