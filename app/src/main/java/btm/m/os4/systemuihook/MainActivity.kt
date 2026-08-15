package btm.m.os4.systemuihook

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import btm.m.liquidglass.LabelMode
import btm.m.liquidglass.hook.DampedDragAnimation
import btm.m.liquidglass.hook.CustomNavigation
import btm.m.liquidglass.hook.HostTab
import btm.m.liquidglass.hook.InteractiveHighlight
import btm.m.liquidglass.momentumBackTransform
import btm.m.liquidglass.rememberMomentumPredictiveBack
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.window.WindowDialog
import java.time.Year
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val hookStore by lazy { HookSettingsStore(this) }
    private val cameraStore by lazy { CameraSettingsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContent { Root(hookStore, cameraStore) }
    }
}

private enum class Tab(val title: String) { CATEGORY("\u5206\u7c7b"), SETTINGS("\u8bbe\u7f6e") }
private enum class PageId {
    SHADE,
    SHADE_PRESETS,
    SHADE_NOTIFICATION_ELEMENTS,
    SHADE_CONTROL_CENTER_ELEMENTS,
    SHADE_NOTIFICATION_BACKGROUND,
    SHADE_CONTROL_CENTER_BACKGROUND,
    ISLAND, STATUS, CONTROL, LOCK, CAMERA, ABOUT, DONATE, OPEN,
}

private data class ShadePresetActions(
    val userPresets: List<ShadePreset>,
    val saveUserPreset: (String) -> Unit,
    val deleteUserPreset: (ShadePreset) -> Unit,
    val importJson: () -> Unit,
    val importQr: () -> Unit,
    val exportJson: (ShadePreset) -> Unit,
    val exportQr: (ShadePreset) -> Unit,
)

private data class QrShareRequest(val name: String, val payload: String)

@Composable
private fun Root(hooks: HookSettingsStore, cameras: CameraSettingsStore) {
    val service by HookApplication.service.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(hooks.settings) }
    var cameraSettings by remember { mutableStateOf(cameras.settings) }
    var userPresets by remember { mutableStateOf(hooks.userShadePresets()) }
    val context = LocalContext.current
    var pendingJsonExport by remember { mutableStateOf<ShadePreset?>(null) }
    var qrShareRequest by remember { mutableStateOf<QrShareRequest?>(null) }
    var pendingQrSave by remember { mutableStateOf<QrShareRequest?>(null) }
    val exportPreset = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val preset = pendingJsonExport ?: return@rememberLauncherForActivityResult
        pendingJsonExport = null
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(preset.payload)
            } ?: error("无法写入文件")
        }.onSuccess {
            Toast.makeText(context, "预设已导出", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    fun importPresetPayload(payload: String) {
        runCatching { parseShadePreset(payload) }
            .onSuccess { imported ->
                hooks.update(service) { it.importShadePreset(payload) }
                settings = hooks.settings
                imported.name?.let { hooks.saveUserShadePreset(it, settings) }
                userPresets = hooks.userShadePresets()
                Toast.makeText(context, "预设已导入", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show()
            }
    }
    val importPreset = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取文件")
        }.onSuccess(::importPresetPayload).onFailure {
            Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show()
        }
    }
    val importQrPreset = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                ?: error("无法读取图片")
        }.map(::readPresetQrCode).onSuccess(::importPresetPayload).onFailure {
            Toast.makeText(context, "未识别到有效预设二维码", Toast.LENGTH_SHORT).show()
        }
    }
    val saveQrPreset = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val request = pendingQrSave ?: return@rememberLauncherForActivityResult
        pendingQrSave = null
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                createPresetQrCode(request.payload).compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: error("无法写入文件")
        }.onSuccess {
            Toast.makeText(context, "二维码已保存", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }
    val controller = remember(settings.themeMode) {
        ThemeController(
            when (settings.themeMode) {
                "light" -> ColorSchemeMode.Light
                "dark" -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
        )
    }
    LaunchedEffect(service) {
        service?.let {
            hooks.syncRemote(it)
            cameras.syncRemote(it)
            settings = hooks.settings
            cameraSettings = cameras.settings
        }
    }
    MiuixTheme(controller = controller) {
        ApplySystemBarAppearance()
        Shell(
            settings, cameraSettings, service,
            update = { transform -> hooks.update(service, transform); settings = hooks.settings },
            updateCamera = { transform -> cameras.update(service, transform); cameraSettings = cameras.settings },
            presetActions = ShadePresetActions(
                userPresets = userPresets,
                saveUserPreset = { name ->
                    hooks.saveUserShadePreset(name, settings)
                    userPresets = hooks.userShadePresets()
                    Toast.makeText(context, "预设已保存", Toast.LENGTH_SHORT).show()
                },
                deleteUserPreset = { preset ->
                    hooks.deleteUserShadePreset(preset.name)
                    userPresets = hooks.userShadePresets()
                },
                importJson = { importPreset.launch(arrayOf("application/json", "text/plain")) },
                importQr = { importQrPreset.launch("image/*") },
                exportJson = { preset ->
                    pendingJsonExport = preset
                    exportPreset.launch("${preset.name}.json")
                },
                exportQr = { preset -> qrShareRequest = QrShareRequest(preset.name, preset.payload) },
            ),
        )
        qrShareRequest?.let { request ->
            QrShareDialog(
                request = request,
                onDismiss = { qrShareRequest = null },
                onSave = {
                    pendingQrSave = request
                    saveQrPreset.launch("${request.name}.png")
                },
            )
        }
    }
}

@Composable
private fun ApplySystemBarAppearance() {
    val activity = LocalContext.current as? Activity ?: return
    val view = LocalView.current
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    SideEffect {
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

@Composable
private fun Shell(
    settings: HookSettings,
    cameras: CameraSettings,
    service: XposedService?,
    update: ((HookSettings) -> HookSettings) -> Unit,
    updateCamera: ((CameraSettings) -> CameraSettings) -> Unit,
    presetActions: ShadePresetActions,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.CATEGORY) }
    val pageStack = remember { mutableStateListOf<PageId>() }
    val page = pageStack.lastOrNull()
    var retainedPage by remember { mutableStateOf<PageId?>(null) }
    var navigatingForward by remember { mutableStateOf(true) }
    if (page != null) retainedPage = page
    val openRootPage: (PageId) -> Unit = { target ->
        navigatingForward = true
        pageStack.clear()
        pageStack += target
    }
    val openNestedPage: (PageId) -> Unit = { target ->
        if (pageStack.lastOrNull() != target) {
            navigatingForward = true
            pageStack += target
        }
    }
    val dismissPage: () -> Unit = {
        if (pageStack.size > 1) {
            navigatingForward = false
            pageStack.removeAt(pageStack.lastIndex)
        } else {
            pageStack.clear()
        }
        Unit
    }
    val backState = rememberMomentumPredictiveBack(
        enabled = page != null && settings.predictiveBackEnabled,
        maxProgress = settings.predictiveBackProgress.coerceIn(10, 100) / 100f,
        onBack = dismissPage
    )
    LaunchedEffect(pageStack.size) {
        if (pageStack.isNotEmpty()) backState.reset()
    }
    BackHandler(enabled = page != null && !settings.predictiveBackEnabled, onBack = dismissPage)
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // The backdrop must only record page content. Recording the navigation that consumes it
        // creates a RenderNode cycle and crashes HyperOS's RenderThread.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            when (tab) {
                Tab.CATEGORY -> CategoryHome(openRootPage)
                Tab.SETTINGS -> SettingsHome(settings, service != null, update, openRootPage)
            }
        }
        BottomBar(tab, { tab = it }, settings, backdrop, Modifier.align(Alignment.BottomCenter))
        AnimatedVisibility(
            visible = page != null,
            enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(260)) + scaleIn(tween(300), initialScale = .97f),
            exit = slideOutHorizontally(tween(260)) { it / 8 } + fadeOut(tween(220)) + scaleOut(tween(260), targetScale = .985f)
        ) {
            Box(Modifier.fillMaxSize().momentumBackTransform(backState)) {
                AnimatedContent(
                    targetState = page ?: retainedPage,
                    transitionSpec = {
                        if (navigatingForward) {
                            (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 10 } + fadeOut(tween(180)))
                        } else {
                            (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(180)))
                        }
                    },
                    label = "detailPageNavigation",
                ) { detailPage ->
                    detailPage?.let {
                        Detail(
                            it, settings, cameras, update, updateCamera, presetActions,
                            openPage = openNestedPage, back = dismissPage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    tab: Tab,
    select: (Tab) -> Unit,
    settings: HookSettings,
    backdrop: Backdrop,
    modifier: Modifier
) {
    val view = LocalView.current
    val index = remember { mutableIntStateOf(tab.ordinal) }
    LaunchedEffect(tab) { index.intValue = tab.ordinal }
    val tabs = Tab.entries.mapIndexed { i, item ->
        HostTab(item.title, "os4.$i") { index.intValue = i; select(item) }
    }
    Box(
        modifier.fillMaxWidth().height(if (settings.navigationStyle == "hyper_os") 76.dp else 100.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        CustomNavigation(
            sourceView = view,
            tabs = tabs,
            selectedIndex = index,
            blurRadius = if (settings.navigationStyle == "liquid_glass") 3 else 18,
            labelMode = LabelMode.ICON_AND_TEXT.preferenceValue,
            navigationStyle = settings.navigationStyle,
            advancedMaterial = true,
            colorMode = settings.themeMode,
            liquidBottomSpacingDp = 0,
            onHostPreDraw = {},
            backdropOverride = backdrop,
            tabIconContent = { i, color ->
                Image(
                    imageVector = if (i == Tab.CATEGORY.ordinal) Icons.Rounded.Apps else Icons.Rounded.Settings,
                    contentDescription = tabs[i].label,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(color)
                )
            }
        )
    }
}

@Composable
private fun CategoryHome(open: (PageId) -> Unit) = AppPage("OS4 Changer") { padding, scroll ->
    AppList(padding, scroll) {
        item { Entry("\u901a\u77e5\u4e2d\u5fc3\u4e0e\u63a7\u5236\u4e2d\u5fc3") { open(PageId.SHADE) } }
        item { Entry("\u8d85\u7ea7\u5c9b") { open(PageId.ISLAND) } }
        item { Entry("\u72b6\u6001\u680f") { open(PageId.STATUS) } }
        item { Entry("\u9501\u5c4f") { open(PageId.LOCK) } }
        item { Entry("\u76f8\u673a\u4e0e\u76f8\u518c\u7f16\u8f91") { open(PageId.CAMERA) } }
    }
}

@Composable
private fun SettingsHome(
    settings: HookSettings,
    online: Boolean,
    update: ((HookSettings) -> HookSettings) -> Unit,
    open: (PageId) -> Unit
) = AppPage("\u8bbe\u7f6e") { padding, scroll ->
    val context = LocalContext.current
    var predictiveProgress by remember(settings.predictiveBackProgress) { mutableFloatStateOf(settings.predictiveBackProgress.toFloat()) }
    var showScopeRestartDialog by remember { mutableStateOf(false) }
    AppList(padding, scroll) {
        item { ServiceCard(online) }
        item {
            Group("\u5e94\u7528\u8bbe\u7f6e") {
                ArrowPreference(
                    title = "\u91cd\u542f\u4f5c\u7528\u57df\u5e94\u7528",
                    onClick = { showScopeRestartDialog = true },
                )
                OverlayDropdownPreference(
                    title = "\u4e3b\u9898\u6a21\u5f0f",
                    items = listOf("\u8ddf\u968f\u7cfb\u7edf", "\u6d45\u8272\u6a21\u5f0f", "\u6df1\u8272\u6a21\u5f0f"),
                    selectedIndex = listOf("system", "light", "dark").indexOf(settings.themeMode).coerceAtLeast(0),
                    onSelectedIndexChange = { i -> update { it.copy(themeMode = listOf("system", "light", "dark")[i]) } }
                )
                OverlayDropdownPreference(
                    title = "\u5e95\u90e8\u5bfc\u822a\u680f\u6837\u5f0f",
                    items = listOf("HyperOS \u5e95\u680f", "HyperOS \u60ac\u6d6e\u5e95\u680f", "\u6db2\u6001\u73bb\u7483\u5e95\u680f"),
                    selectedIndex = listOf("hyper_os", "hyper_os_floating", "liquid_glass").indexOf(settings.navigationStyle).coerceAtLeast(0),
                    onSelectedIndexChange = { i -> update { it.copy(navigationStyle = listOf("hyper_os", "hyper_os_floating", "liquid_glass")[i]) } }
                )
                SwitchPreference(
                    title = "\u9884\u6d4b\u6027\u8fd4\u56de\u52a8\u753b",
                    checked = settings.predictiveBackEnabled,
                    onCheckedChange = { value -> update { it.copy(predictiveBackEnabled = value) } }
                )
                if (settings.predictiveBackEnabled) {
                    SliderPreference(
                        value = predictiveProgress,
                        onValueChange = { predictiveProgress = it },
                        onValueChangeFinished = { update { it.copy(predictiveBackProgress = predictiveProgress.toInt()) } },
                        title = "\u9884\u6d4b\u6027\u8fd4\u56de\u52a8\u753b\u6700\u5927\u8fdb\u5ea6",
                        valueText = "${predictiveProgress.toInt()}%",
                        valueRange = 10f..100f,
                        steps = 89
                    )
                }
            }
        }
        item {
            Group("\u5e94\u7528\u4fe1\u606f") {
                ArrowPreference(title = "\u5173\u4e8e", onClick = { open(PageId.ABOUT) })
                ArrowPreference(title = "\u6350\u8d60", onClick = { open(PageId.DONATE) })
                ArrowPreference(title = "\u5f00\u6e90\u4ee3\u7801\u58f0\u660e", onClick = { open(PageId.OPEN) })
                ArrowPreference(title = "\u672c\u9879\u76ee\u57fa\u4e8e MIUIX \u6784\u5efa", onClick = { openUrl(context, "https://compose-miuix-ui.github.io/miuix/") })
            }
        }
    }
    RestartScopeDialog(
        show = showScopeRestartDialog,
        onDismiss = { showScopeRestartDialog = false },
        onRestart = { targets ->
            SystemUiRestarter.restart(context, targets)
            showScopeRestartDialog = false
        },
    )
}

@Composable
private fun RestartScopeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onRestart: (Set<ScopeApplication>) -> Unit,
) {
    var selectedTargets by remember(show) {
        mutableStateOf<Set<ScopeApplication>>(setOf(ScopeApplication.SYSTEM_UI))
    }
    WindowDialog(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "\u91cd\u542f\u4f5c\u7528\u57df\u5e94\u7528",
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
            )
            Text(
                "\u9009\u62e9\u9700\u8981\u91cd\u542f\u7684\u5e94\u7528",
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Start,
            )
            ScopeRestartCheckboxes(
                selectedTargets = selectedTargets,
                onSelectedTargetsChange = { selectedTargets = it },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onDismiss, Modifier.weight(1f)) { Text("\u53d6\u6d88") }
                Button(
                    onClick = { onRestart(selectedTargets) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("\u91cd\u542f") }
            }
        }
    }
}

@Composable
private fun ScopeRestartCheckboxes(
    selectedTargets: Set<ScopeApplication>,
    onSelectedTargetsChange: (Set<ScopeApplication>) -> Unit,
) {
    ScopeApplication.entries.forEach { target ->
        val toggle = {
            onSelectedTargetsChange(
                if (target in selectedTargets) selectedTargets - target else selectedTargets + target,
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = if (target in selectedTargets) ToggleableState.On else ToggleableState.Off,
                onClick = toggle,
            )
            Text(
                target.title,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun ServiceCard(online: Boolean) {
    val color = if (online) ComposeColor(0xFF38A169) else ComposeColor(0xFFE05353)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color.copy(alpha = .14f)), insideMargin = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text("LSPosed", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
            Text(if (online) "\u5df2\u8fde\u63a5" else "\u672a\u8fde\u63a5", style = MiuixTheme.textStyles.body2, color = color, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SmallTitle(title, insideMargin = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp))
        Card(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun Entry(title: String, click: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { ArrowPreference(title = title, onClick = click) }
}

@Composable
private fun Detail(
    page: PageId,
    settings: HookSettings,
    cameras: CameraSettings,
    update: ((HookSettings) -> HookSettings) -> Unit,
    updateCamera: ((CameraSettings) -> CameraSettings) -> Unit,
    presetActions: ShadePresetActions,
    openPage: (PageId) -> Unit,
    back: () -> Unit
) {
    when (page) {
        PageId.SHADE -> Shade(settings, update, presetActions, openPage, back)
        PageId.SHADE_PRESETS -> ShadePresets(settings, update, presetActions, back)
        PageId.SHADE_NOTIFICATION_ELEMENTS -> MaterialOverrideAdvancedPage(
            "通知元素", settings.notificationElementsMaterial, false,
            back,
        ) { update { value -> value.copy(notificationElementsMaterial = it) } }
        PageId.SHADE_CONTROL_CENTER_ELEMENTS -> MaterialOverrideAdvancedPage(
            "控制中心元素", settings.controlCenterElementsMaterial, false,
            back,
        ) { update { value -> value.copy(controlCenterElementsMaterial = it) } }
        PageId.SHADE_NOTIFICATION_BACKGROUND -> MaterialOverrideAdvancedPage(
            "通知中心背景", settings.notificationCenterBackgroundMaterial, true,
            back,
        ) { update { value -> value.copy(notificationCenterBackgroundMaterial = it) } }
        PageId.SHADE_CONTROL_CENTER_BACKGROUND -> MaterialOverrideAdvancedPage(
            "控制中心背景", settings.controlCenterBackgroundMaterial, true,
            back,
        ) { update { value -> value.copy(controlCenterBackgroundMaterial = it) } }
        PageId.ISLAND -> Island(settings, update, back)
        PageId.STATUS -> Status(settings, update, back)
        PageId.CONTROL -> Control(settings, update, back)
        PageId.LOCK -> Lock(settings, update, back)
        PageId.CAMERA -> Camera(cameras, updateCamera, back)
        PageId.ABOUT -> About(back)
        PageId.DONATE -> Donate(back)
        PageId.OPEN -> OpenSource(back)
    }
}

@Composable
private fun Shade(
    s: HookSettings,
    update: ((HookSettings) -> HookSettings) -> Unit,
    presetActions: ShadePresetActions,
    openPage: (PageId) -> Unit,
    back: () -> Unit,
) = AppPage("\u901a\u77e5\u4e2d\u5fc3\u4e0e\u63a7\u5236\u4e2d\u5fc3", back) { p, scroll ->
    var showSavePresetDialog by remember { mutableStateOf(false) }
    AppList(p, scroll, 28) {
        item { Group("\u9884\u8bbe") {
            ArrowPreference(title = "\u9884\u8bbe", onClick = { openPage(PageId.SHADE_PRESETS) })
            ArrowPreference(title = "\u4fdd\u5b58\u5f53\u524d\u9884\u8bbe", onClick = { showSavePresetDialog = true })
        } }
        item { MaterialOverrideCard("\u5143\u7d20 - \u901a\u77e5", s.notificationElementsMaterial, false, { openPage(PageId.SHADE_NOTIFICATION_ELEMENTS) }) {
            update { value -> value.copy(notificationElementsMaterial = it) }
        } }
        item { MaterialOverrideCard("\u5143\u7d20 - \u63a7\u5236\u4e2d\u5fc3", s.controlCenterElementsMaterial, false, { openPage(PageId.SHADE_CONTROL_CENTER_ELEMENTS) }) {
            update { value -> value.copy(controlCenterElementsMaterial = it) }
        } }
        item { MaterialOverrideCard("\u80cc\u666f - \u901a\u77e5\u4e2d\u5fc3", s.notificationCenterBackgroundMaterial, true, { openPage(PageId.SHADE_NOTIFICATION_BACKGROUND) }) {
            update { value -> value.copy(notificationCenterBackgroundMaterial = it) }
        } }
        item { MaterialOverrideCard("\u80cc\u666f - \u63a7\u5236\u4e2d\u5fc3", s.controlCenterBackgroundMaterial, true, { openPage(PageId.SHADE_CONTROL_CENTER_BACKGROUND) }) {
            update { value -> value.copy(controlCenterBackgroundMaterial = it) }
        } }
        item {
            Group("\u5706\u89d2") {
                ArrowPreference(title = "\u5706\u89d2 - \u63a7\u5236\u4e2d\u5fc3", onClick = { openPage(PageId.CONTROL) })
            }
        }
    }
    SavePresetDialog(
        show = showSavePresetDialog,
        onDismiss = { showSavePresetDialog = false },
        onSave = { name ->
            presetActions.saveUserPreset(name)
            showSavePresetDialog = false
        },
    )
}

@Composable
private fun MaterialOverrideCard(
    title: String,
    value: MaterialOverride,
    isBackground: Boolean,
    openAdvanced: () -> Unit,
    onChange: (MaterialOverride) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        ArrowPreference(title = title, onClick = { expanded = !expanded })
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .98f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .98f),
        ) {
            Column {
        SwitchPreference(
            title = "\u542f\u7528\u6750\u8d28\u8c03\u6574",
            checked = value.enabled,
            onCheckedChange = { enabled -> onChange(enableMaterialOverride(value, isBackground, enabled)) },
        )
        AnimatedVisibility(
            visible = value.enabled,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .96f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .96f),
        ) {
            Column {
                ParameterIntSlide("\u6a21\u7cca\u6bd4\u4f8b", value.blurPercent.coerceIn(0, if (isBackground) 100 else 200), 0..if (isBackground) 100 else 200, "%") { onChange(value.copy(blurPercent = it)) }
                if (isBackground) ParameterIntSlide("\u80cc\u666f\u7f29\u653e\u6bd4\u4f8b", value.scalePercent, 0..200, "%") { onChange(value.copy(scalePercent = it)) }
                if (isBackground) {
                    ParameterIntSlide("\u80cc\u666f\u4e0d\u900f\u660e\u5ea6", (value.alpha + 100).coerceIn(0, 35), 0..35, "%") { onChange(value.copy(alpha = it - 100)) }
                    ParameterIntSlide("\u6df7\u8272\u5f3a\u5ea6", value.tintStrength, 0..50, " x0.01") { onChange(value.copy(tintEnabled = it > 0, tintStrength = it)) }
                } else {
                    ParameterIntSlide("Glass \u6a21\u7cca\u534a\u5f84", value.glassRadius.coerceIn(0, 10), 0..10, " px") { onChange(value.copy(glassRadius = it)) }
                    ParameterIntSlide("\u73bb\u7483\u5f3a\u5ea6", (value.refraction / 2 + 50).coerceIn(0, 100), 0..100, "%") { onChange(applyCompactGlassStrength(value, it)) }
                    ParameterIntSlide("\u900f\u660e\u5ea6", value.alpha + 50, 0..100, "%") { onChange(value.copy(alpha = it - 50)) }
                    ParameterIntSlide("\u8fb9\u7f18\u4e0e\u53cd\u5c04", value.reflection + 50, 0..100, "%") { onChange(applyCompactReflection(value, it)) }
                    ParameterIntSlide("\u8272\u5f69", value.saturation + 50, 0..100, "%") { onChange(applyCompactColor(value, it)) }
                }
                ArrowPreference(title = "\u9ad8\u7ea7\u6a21\u5f0f", onClick = openAdvanced)
            }
        }
    }
}
}
}

@Composable
private fun MaterialOverrideAdvancedPage(
    title: String,
    value: MaterialOverride,
    isBackground: Boolean,
    back: () -> Unit,
    onChange: (MaterialOverride) -> Unit,
) = AppPage(title, back) { p, scroll ->
    AppList(p, scroll, 28) {
        item { Card(Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "\u542f\u7528\u6750\u8d28\u8c03\u6574",
                checked = value.enabled,
                onCheckedChange = { enabled -> onChange(enableMaterialOverride(value, isBackground, enabled)) },
            )
            AnimatedVisibility(value.enabled) {
                Column {
                    ParameterIntSlide("\u6a21\u7cca\u6bd4\u4f8b", value.blurPercent.coerceIn(0, if (isBackground) 100 else 200), 0..if (isBackground) 100 else 200, "%") { onChange(value.copy(blurPercent = it)) }
                    if (isBackground) {
                        ParameterIntSlide("\u80cc\u666f\u7f29\u653e\u6bd4\u4f8b", value.scalePercent, 0..200, "%") { onChange(value.copy(scalePercent = it)) }
                        ParameterIntSlide("\u80cc\u666f\u4e0d\u900f\u660e\u5ea6", (value.alpha + 100).coerceIn(0, 35), 0..35, "%") { onChange(value.copy(alpha = it - 100)) }
                    } else {
                        ParameterIntSlide("Glass \u6a21\u7cca\u534a\u5f84", value.glassRadius.coerceIn(0, 10), 0..10, " px") { onChange(value.copy(glassRadius = it)) }
                        ParameterIntSlide("\u4eae\u5ea6\u504f\u79fb", value.brightness, -30..30, " x0.01") { onChange(value.copy(brightness = it)) }
                        ParameterIntSlide("\u538b\u6697\u504f\u79fb", value.darker, -50..50, " x0.01") { onChange(value.copy(darker = it)) }
                        ParameterIntSlide("\u6298\u5c04\u504f\u79fb", value.refraction, -100..100, " x0.01") { onChange(value.copy(refraction = it)) }
                        ParameterIntSlide("\u70e7\u707c\u504f\u79fb", value.burn, -50..50, " x0.01") { onChange(value.copy(burn = it)) }
                        ParameterIntSlide("\u9971\u548c\u5ea6\u504f\u79fb", value.saturation, -100..100, " x0.01") { onChange(value.copy(saturation = it)) }
                        ParameterIntSlide("\u900f\u660e\u5ea6\u504f\u79fb", value.alpha, -50..50, " x0.01") { onChange(value.copy(alpha = it)) }
                        ParameterIntSlide("\u8fb9\u7f18\u539a\u5ea6", value.edgeThickness, -100..100, " x0.01") { onChange(value.copy(edgeThickness = it)) }
                        ParameterIntSlide("\u53cd\u5c04\u5f3a\u5ea6", value.reflection, -100..100, " x0.01") { onChange(value.copy(reflection = it)) }
                        ParameterIntSlide("\u65b9\u5411\u5149\u5f3a\u5ea6", value.directionalLight, -100..100, " x0.01") { onChange(value.copy(directionalLight = it)) }
                        ParameterIntSlide("\u80cc\u666f\u9971\u548c\u5ea6", value.backgroundSaturation, -100..100, " x0.01") { onChange(value.copy(backgroundSaturation = it)) }
                        ParameterIntSlide("\u80cc\u666f\u4eae\u5ea6", value.backgroundBrightness, -100..100, " x0.01") { onChange(value.copy(backgroundBrightness = it)) }
                    }
                    SwitchPreference(
                        title = "\u81ea\u5b9a\u4e49\u6df7\u8272",
                        checked = value.tintEnabled,
                        onCheckedChange = { onChange(value.copy(tintEnabled = it)) },
                    )
                    AnimatedVisibility(value.tintEnabled) {
                        Column {
                            ShortcutBackgroundColorPreference("\u6df7\u8272\u989c\u8272", value.tintColor) { onChange(value.copy(tintColor = it)) }
                            ParameterIntSlide("\u6df7\u8272\u5f3a\u5ea6", value.tintStrength, 0..50, " x0.01") { onChange(value.copy(tintStrength = it)) }
                        }
                    }
                }
            }
        } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShadePresets(
    settings: HookSettings,
    update: ((HookSettings) -> HookSettings) -> Unit,
    actions: ShadePresetActions,
    back: () -> Unit,
) = AppPage("\u9884\u8bbe", back) { p, scroll ->
    var selectedPreset by remember { mutableStateOf<ShadePreset?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val presets = builtInShadePresets(settings)
    AppList(p, scroll, 28) {
        item {
            Group("\u5bfc\u5165") {
                ArrowPreference(title = "\u5bfc\u5165\u9884\u8bbe", onClick = { showImportDialog = true })
            }
        }
        item { SmallTitle("\u5185\u7f6e\u9884\u8bbe", insideMargin = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)) }
        item { Card(Modifier.fillMaxWidth()) {
            presets.forEach { preset ->
                PresetRow(preset, onUse = { update { preset.applyTo(it) } }, onLongPress = { selectedPreset = preset })
            }
        } }
        item { SmallTitle("\u7528\u6237\u9884\u8bbe", insideMargin = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)) }
        item { Card(Modifier.fillMaxWidth()) {
            if (actions.userPresets.isEmpty()) {
                Text("\u6682\u65e0\u4fdd\u5b58\u7684\u7528\u6237\u9884\u8bbe", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(16.dp))
            } else {
                actions.userPresets.forEach { preset ->
                    PresetRow(preset, onUse = { update { preset.applyTo(it) } }, onLongPress = { selectedPreset = preset })
                }
            }
        } }
    }
    ImportPresetDialog(
        show = showImportDialog,
        onDismiss = { showImportDialog = false },
        onJson = { showImportDialog = false; actions.importJson() },
        onQr = { showImportDialog = false; actions.importQr() },
    )
    selectedPreset?.let { preset ->
        PresetActionDialog(
            preset = preset,
            onDismiss = { selectedPreset = null },
            onUse = { update { preset.applyTo(it) }; selectedPreset = null },
            onExport = { showExportDialog = true },
            onDelete = {
                actions.deleteUserPreset(preset)
                selectedPreset = null
            },
        )
        ExportPresetDialog(
            show = showExportDialog,
            preset = preset,
            onDismiss = { showExportDialog = false },
            onJson = { showExportDialog = false; selectedPreset = null; actions.exportJson(preset) },
            onQr = { showExportDialog = false; selectedPreset = null; actions.exportQr(preset) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetRow(preset: ShadePreset, onUse: () -> Unit, onLongPress: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onUse, onLongClick = onLongPress).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(preset.name, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
        Image(MiuixIcons.Regular.ChevronForward, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary))
    }
}

private fun builtInShadePresets(settings: HookSettings): List<ShadePreset> {
    val presets: List<Pair<String, (HookSettings) -> HookSettings>> = listOf(
        "\u7cfb\u7edf\u9ed8\u8ba4" to { it.copy(
            notificationElementsMaterial = MaterialOverride(), controlCenterElementsMaterial = MaterialOverride(),
            notificationCenterBackgroundMaterial = MaterialOverride(), controlCenterBackgroundMaterial = MaterialOverride(),
        ) },
        "\u4f4e\u6a21\u7cca\u73bb\u7483" to { it.copy(
            notificationElementsMaterial = referenceElementMaterial(), controlCenterElementsMaterial = referenceElementMaterial(),
            notificationCenterBackgroundMaterial = backgroundMaterial(blurPercent = 12, opacity = 5),
            controlCenterBackgroundMaterial = backgroundMaterial(blurPercent = 12, opacity = 5),
        ) },
        "\u6e05\u900f\u73bb\u7483" to { it.copy(
            notificationElementsMaterial = referenceElementMaterial(brightness = 4, darker = -14, alpha = -12),
            controlCenterElementsMaterial = referenceElementMaterial(brightness = 4, darker = -14, alpha = -12),
            notificationCenterBackgroundMaterial = backgroundMaterial(blurPercent = 15, scalePercent = 80, opacity = 5),
            controlCenterBackgroundMaterial = backgroundMaterial(blurPercent = 15, scalePercent = 80, opacity = 5),
        ) },
        "\u900f\u660e\u73bb\u7483" to { it.copy(
            notificationElementsMaterial = referenceElementMaterial(brightness = 8, saturation = -12, alpha = -5),
            controlCenterElementsMaterial = referenceElementMaterial(brightness = 8, saturation = -12, alpha = -5),
            notificationCenterBackgroundMaterial = backgroundMaterial(blurPercent = 18, opacity = 5),
            controlCenterBackgroundMaterial = backgroundMaterial(blurPercent = 18, opacity = 5),
        ) },
        "\u6df1\u8272\u5bf9\u6bd4" to { it.copy(
            notificationElementsMaterial = referenceElementMaterial(brightness = -15, darker = 18, saturation = -20),
            controlCenterElementsMaterial = referenceElementMaterial(brightness = -15, darker = 18, saturation = -20),
            notificationCenterBackgroundMaterial = backgroundMaterial(blurPercent = 54, opacity = 21),
            controlCenterBackgroundMaterial = backgroundMaterial(blurPercent = 54, opacity = 21),
        ) },
        "\u9ad8\u53cd\u5c04" to { it.copy(
            notificationElementsMaterial = referenceElementMaterial(refraction = 35, reflection = 35, edgeThickness = 18),
            controlCenterElementsMaterial = referenceElementMaterial(refraction = 35, reflection = 35, edgeThickness = 18),
            notificationCenterBackgroundMaterial = backgroundMaterial(blurPercent = 3, opacity = 5),
            controlCenterBackgroundMaterial = backgroundMaterial(blurPercent = 3, opacity = 5),
        ) },
        "\u8272\u5f69\u589e\u5f3a" to { it.copy(
            notificationElementsMaterial = referenceElementMaterial(saturation = 22, backgroundSaturation = 20),
            controlCenterElementsMaterial = referenceElementMaterial(saturation = 22, backgroundSaturation = 20),
            notificationCenterBackgroundMaterial = backgroundMaterial(blurPercent = 3, opacity = 5),
            controlCenterBackgroundMaterial = backgroundMaterial(blurPercent = 3, opacity = 5),
        ) },
        "\u8f7b\u91cf\u6d41\u7545" to { it.copy(
            notificationElementsMaterial = referenceElementMaterial(glassRadius = 10, alpha = -8),
            controlCenterElementsMaterial = referenceElementMaterial(glassRadius = 10, alpha = -8),
            notificationCenterBackgroundMaterial = backgroundMaterial(blurPercent = 18, scalePercent = 60, opacity = 21),
            controlCenterBackgroundMaterial = backgroundMaterial(blurPercent = 18, scalePercent = 60, opacity = 21),
        ) },
    )
    return presets.map { (name, apply) -> ShadePreset(name, apply(settings).exportShadePreset(name), builtIn = true) }
}

@Composable
private fun SavePresetDialog(show: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(show) { mutableStateOf("") }
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("保存当前预设", style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
                decorationBox = { field ->
                    if (name.isBlank()) Text("预设名", style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    field()
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onDismiss, Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onSave(name) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("确定") }
            }
        }
    }
}

@Composable
private fun ImportPresetDialog(show: Boolean, onDismiss: () -> Unit, onJson: () -> Unit, onQr: () -> Unit) {
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("导入预设", style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
            Button(onJson, Modifier.fillMaxWidth()) { Text("JSON") }
            Button(onQr, Modifier.fillMaxWidth()) { Text("二维码") }
            Button(onDismiss, Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

@Composable
private fun PresetActionDialog(
    preset: ShadePreset,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    WindowDialog(show = true, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(preset.name, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
            Button(onUse, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) { Text("使用") }
            Button(onExport, Modifier.fillMaxWidth()) { Text("导出") }
            if (!preset.builtIn) Button(onDelete, Modifier.fillMaxWidth()) { Text("删除") }
            Button(onDismiss, Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

@Composable
private fun ExportPresetDialog(
    show: Boolean,
    preset: ShadePreset,
    onDismiss: () -> Unit,
    onJson: () -> Unit,
    onQr: () -> Unit,
) {
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("导出 ${preset.name}", style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
            Button(onJson, Modifier.fillMaxWidth()) { Text("JSON") }
            Button(onQr, Modifier.fillMaxWidth()) { Text("二维码") }
            Button(onDismiss, Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

@Composable
private fun QrShareDialog(request: QrShareRequest, onDismiss: () -> Unit, onSave: () -> Unit) {
    val bitmap = remember(request.payload) { createPresetQrCode(request.payload) }
    WindowDialog(show = true, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(request.name, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "预设二维码", modifier = Modifier.size(240.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onDismiss, Modifier.weight(1f)) { Text("取消") }
                Button(onSave, Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text("保存") }
            }
        }
    }
}

private fun referenceElementMaterial(
    glassRadius: Int = 10,
    brightness: Int = -5,
    darker: Int = -8,
    refraction: Int = 20,
    burn: Int = -25,
    saturation: Int = 0,
    alpha: Int = 0,
    reflection: Int = 0,
    edgeThickness: Int = 0,
    backgroundSaturation: Int = 0,
): MaterialOverride = MaterialOverride(
    enabled = true, glassRadius = glassRadius, brightness = brightness, darker = darker,
    refraction = refraction, burn = burn, saturation = saturation, alpha = alpha,
    reflection = reflection, edgeThickness = edgeThickness, backgroundSaturation = backgroundSaturation,
)

private fun enableMaterialOverride(
    value: MaterialOverride,
    isBackground: Boolean,
    enabled: Boolean,
): MaterialOverride = if (enabled && isBackground) {
    value.copy(enabled = true, blurPercent = value.blurPercent.coerceIn(0, 100), alpha = value.alpha.coerceIn(-100, -65))
} else {
    value.copy(enabled = enabled)
}

private fun backgroundMaterial(blurPercent: Int, scalePercent: Int = 100, opacity: Int = 35): MaterialOverride = MaterialOverride(
    enabled = true,
    blurPercent = blurPercent.coerceIn(0, 100),
    scalePercent = scalePercent.coerceIn(0, 200),
    alpha = opacity.coerceIn(0, 35) - 100,
)

private fun applyCompactGlassStrength(value: MaterialOverride, percent: Int): MaterialOverride {
    val amount = (percent - 50).coerceIn(-50, 50)
    return value.copy(
        brightness = (amount / 4).coerceIn(-30, 30),
        darker = (-amount / 5).coerceIn(-50, 50),
        refraction = (amount * 2).coerceIn(-100, 100),
        burn = (-amount / 2).coerceIn(-50, 50),
    )
}

private fun applyCompactReflection(value: MaterialOverride, percent: Int): MaterialOverride {
    val amount = (percent - 50).coerceIn(-50, 50)
    return value.copy(reflection = amount, edgeThickness = (amount / 2).coerceIn(-100, 100))
}

private fun applyCompactColor(value: MaterialOverride, percent: Int): MaterialOverride {
    val amount = (percent - 50).coerceIn(-50, 50)
    return value.copy(saturation = amount, backgroundSaturation = amount)
}

@Composable
private fun Island(s: HookSettings, update: ((HookSettings) -> HookSettings) -> Unit, back: () -> Unit) = AppPage("\u8d85\u7ea7\u5c9b", back) { p, scroll ->
    AppList(p, scroll, 28) {
        item {
            Card(Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "\u53bb\u9664\u7126\u70b9\u901a\u77e5\u4e0e\u8d85\u7ea7\u5c9b\u767d\u540d\u5355\u9650\u5236",
                    checked = s.removeFocusAndIslandWhitelistLimit,
                    onCheckedChange = { value ->
                        update { it.copy(removeFocusAndIslandWhitelistLimit = value) }
                    },
                )
                SwitchPreference(title = "\u81ea\u5b9a\u4e49\u8d85\u7ea7\u5c9b\u957f\u5ea6", checked = s.islandEnabled, onCheckedChange = { v -> update { it.copy(islandEnabled = v) } })
                if (s.islandEnabled) IntSlide("\u6700\u5c0f\u5bbd\u5ea6", s.islandWidth, 108..190) { v -> update { it.copy(islandWidth = v) } }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "\u5c55\u5f00\u6001\u4e0b\u7684\u8d85\u7ea7\u5c9b\u80cc\u666f\u8c03\u6574",
                    checked = s.expandedIslandBackgroundEnabled,
                    onCheckedChange = { v -> update { it.copy(expandedIslandBackgroundEnabled = v) } },
                )
                AnimatedVisibility(
                    visible = s.expandedIslandBackgroundEnabled,
                    enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .96f),
                    exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .96f),
                ) {
                    Column {
                        ParameterIntSlide("\u80cc\u666f\u4e0d\u900f\u660e\u5ea6", s.expandedIslandBackgroundOpacity, 0..35, "%") { value ->
                            update { it.copy(expandedIslandBackgroundOpacity = value) }
                        }
                        ParameterIntSlide("Glass \u5c0f\u6a21\u7cca\u534a\u5f84", s.expandedIslandGlassBlurRadius, 0..10, " px") { value ->
                            update { it.copy(expandedIslandGlassBlurRadius = value) }
                        }
                        ParameterIntSlide("Glass \u5927\u6a21\u7cca\u534a\u5f84", s.expandedIslandGlassLargeBlurRadius, 0..10, " px") { value ->
                            update { it.copy(expandedIslandGlassLargeBlurRadius = value) }
                        }
                        ParameterIntSlide("\u81ea\u6a21\u7cca\u5f3a\u5ea6", s.expandedIslandSelfBlurRadius, 0..10, " px") { value ->
                            update { it.copy(expandedIslandSelfBlurRadius = value) }
                        }
                        SwitchPreference(
                            title = "\u663e\u793a\u9ad8\u5149",
                            checked = s.expandedIslandShowHighlight,
                            onCheckedChange = { value -> update { it.copy(expandedIslandShowHighlight = value) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Status(s: HookSettings, update: ((HookSettings) -> HookSettings) -> Unit, back: () -> Unit) = AppPage("\u72b6\u6001\u680f", back) { p, scroll ->
    AppList(p, scroll, 28) { item { Card(Modifier.fillMaxWidth()) {
        Dim("\u65f6\u949f\u5927\u5c0f", s.clockEnabled, { v -> update { it.copy(clockEnabled = v) } }, s.clockSize, 10f..24f) { v -> update { it.copy(clockSize = v) } }
        Dim("\u53f3\u8fb9\u8ddd", s.paddingEndEnabled, { v -> update { it.copy(paddingEndEnabled = v) } }, s.paddingEnd, 0f..32f) { v -> update { it.copy(paddingEnd = v) } }
        Dim("\u5de6\u8fb9\u8ddd", s.paddingStartEnabled, { v -> update { it.copy(paddingStartEnabled = v) } }, s.paddingStart, 0f..32f) { v -> update { it.copy(paddingStart = v) } }
        Dim("\u72b6\u6001\u680f\u9ad8\u5ea6", s.heightEnabled, { v -> update { it.copy(heightEnabled = v) } }, s.statusBarHeight.toFloat(), 24f..72f) { v -> update { it.copy(statusBarHeight = v.toInt()) } }
        Dim("\u4e0a\u8fb9\u8ddd", s.paddingTopEnabled, { v -> update { it.copy(paddingTopEnabled = v) } }, s.paddingTop, 0f..32f) { v -> update { it.copy(paddingTop = v) } }
    } } }
}

@Composable
private fun Control(s: HookSettings, update: ((HookSettings) -> HookSettings) -> Unit, back: () -> Unit) = AppPage("\u63a7\u5236\u4e2d\u5fc3", back) { p, scroll ->
    AppList(p, scroll, 28) { item { Card(Modifier.fillMaxWidth()) {
        Corner("\u9876\u90e8\u64cd\u4f5c\u6309\u94ae", s.topButtonsRadiusEnabled, { v -> update { it.copy(topButtonsRadiusEnabled = v) } }, s.topButtonsRadius) { v -> update { it.copy(topButtonsRadius = v) } }
        Corner("\u5a92\u4f53\u5361\u7247", s.mediaCardRadiusEnabled, { v -> update { it.copy(mediaCardRadiusEnabled = v) } }, s.mediaCardRadius) { v -> update { it.copy(mediaCardRadius = v) } }
        Corner("\u97f3\u91cf / \u4eae\u5ea6\u6761", s.sliderRadiusEnabled, { v -> update { it.copy(sliderRadiusEnabled = v) } }, s.sliderRadius) { v -> update { it.copy(sliderRadius = v) } }
        Corner("\u878d\u5408\u8bbe\u5907\u4e2d\u5fc3", s.deviceCenterRadiusEnabled, { v -> update { it.copy(deviceCenterRadiusEnabled = v) } }, s.deviceCenterRadius) { v -> update { it.copy(deviceCenterRadius = v) } }
    } } }
}

@Composable
private fun Lock(s: HookSettings, update: ((HookSettings) -> HookSettings) -> Unit, back: () -> Unit) = AppPage("\u9501\u5c4f", back) { p, scroll ->
    AppList(p, scroll, 28) { item { Card(Modifier.fillMaxWidth()) {
        SwitchPreference(title = "\u53bb\u9664\u666f\u6df1\u9650\u5236", checked = s.removeDepthImageLimit, onCheckedChange = { v -> update { it.copy(removeDepthImageLimit = v) } })
        WindowDropdownPreference(title = "\u53bb\u9664\u901a\u77e5\u4e0b\u6c89\u4f4d\u7f6e\u9650\u5236", items = listOf("\u7cfb\u7edf\u9ed8\u8ba4", "\u9690\u85cf\u6307\u7eb9\u56fe\u6807", "\u4e0d\u9690\u85cf\u6307\u7eb9\u56fe\u6807"), selectedIndex = s.notificationFodMode, onSelectedIndexChange = { v -> update { it.copy(notificationFodMode = v) } })
        SwitchPreference(title = "\u53bb\u9664\u9501\u5c4f\u5145\u7535\u4e2d\u6587\u672c", checked = s.hideLockscreenChargingText, onCheckedChange = { v -> update { it.copy(hideLockscreenChargingText = v) } })
        OverlayDropdownPreference(
            title = "\u9501\u5c4f\u5feb\u6377\u529f\u80fd\u80cc\u666f",
            items = listOf("\u4e0d\u663e\u793a", "\u7eaf\u8272", "\u9ad8\u7ea7\u6750\u8d28", "\u900f\u660e\u73bb\u7483"),
            selectedIndex = s.lockscreenShortcutBackgroundMode,
            onSelectedIndexChange = { value ->
                update { it.copy(lockscreenShortcutBackgroundMode = value) }
            },
        )
        OverlayDropdownPreference(
            title = "\u5feb\u6377\u6309\u94ae\u56fe\u6807\u989c\u8272",
            items = listOf("\u81ea\u52a8\u6a21\u5f0f", "\u6d45\u8272", "\u6df1\u8272"),
            selectedIndex = s.shortcutIconColorMode,
            onSelectedIndexChange = { value -> update { it.copy(shortcutIconColorMode = value) } },
        )
        AnimatedVisibility(
            visible = s.lockscreenShortcutBackgroundMode != 0,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .96f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .96f),
        ) {
            FloatSlide("\u5706\u5f62\u534a\u5f84", s.lockscreenShortcutGlassRadius, 28f..80f) { v ->
                update { it.copy(lockscreenShortcutGlassRadius = v) }
            }
        }
        AnimatedVisibility(
            visible = s.lockscreenShortcutBackgroundMode == 1,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .96f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .96f),
        ) {
            ShortcutBackgroundColorPreference(
                color = s.shortcutPureColor,
                onColorChange = { value -> update { it.copy(shortcutPureColor = value) } },
            )
        }
        AnimatedVisibility(
            visible = s.lockscreenShortcutBackgroundMode == 2,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .96f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .96f),
        ) {
            Column {
                ShortcutBackgroundColorPreference(
                    color = s.shortcutAdvancedMaterialColor,
                    onColorChange = { value ->
                        update { it.copy(shortcutAdvancedMaterialColor = value) }
                    },
                )
                ParameterIntSlide(
                    title = "\u4e0d\u900f\u660e\u5ea6",
                    value = s.shortcutAdvancedMaterialOpacity,
                    range = 0..35,
                    suffix = "%",
                ) { value -> update { it.copy(shortcutAdvancedMaterialOpacity = value) } }
                ParameterIntSlide(
                    title = "\u80cc\u666f\u6a21\u7cca\u5ea6",
                    value = s.shortcutAdvancedMaterialBlurRadius,
                    range = 0..10,
                ) { value -> update { it.copy(shortcutAdvancedMaterialBlurRadius = value) } }
                SwitchPreference(
                    title = "\u663e\u793a\u9ad8\u5149",
                    checked = s.shortcutAdvancedMaterialHighlight,
                    onCheckedChange = { value ->
                        update { it.copy(shortcutAdvancedMaterialHighlight = value) }
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = s.lockscreenShortcutBackgroundMode == 3,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .96f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .96f),
        ) {
            Column {
                ShortcutBackgroundColorPreference(
                    color = s.shortcutSoftGlassColor,
                    onColorChange = { value -> update { it.copy(shortcutSoftGlassColor = value) } },
                )
                ParameterIntSlide(
                    title = "\u4e0d\u900f\u660e\u5ea6",
                    value = s.shortcutSoftGlassOpacity,
                    range = 0..35,
                    suffix = "%",
                ) { value -> update { it.copy(shortcutSoftGlassOpacity = value) } }
                ParameterIntSlide(
                    title = "\u80cc\u666f\u6a21\u7cca\u5ea6",
                    value = s.shortcutSoftGlassBackdropBlurRadius,
                    range = 0..10,
                ) { value -> update { it.copy(shortcutSoftGlassBackdropBlurRadius = value) } }
                ParameterIntSlide(
                    title = "Glass \u6a21\u7cca\u5ea6",
                    value = s.shortcutSoftGlassBlurRadius,
                    range = 0..10,
                ) { value -> update { it.copy(shortcutSoftGlassBlurRadius = value) } }
                ParameterFloatSlide(
                    title = "\u67d4\u5149\u5f3a\u5ea6",
                    value = s.shortcutSoftGlassLuminance,
                    range = 0f..0.4f,
                ) { value -> update { it.copy(shortcutSoftGlassLuminance = value) } }
            }
        }
    } } }
}

@Composable
private fun ShortcutBackgroundColorPreference(
    title: String = "\u80cc\u666f\u989c\u8272",
    color: Int,
    onColorChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { showPicker = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
        )
        Box(
            Modifier.size(28.dp)
                .background(MiuixTheme.colorScheme.outline.copy(alpha = .45f), CircleShape)
                .padding(2.dp)
                .background(ComposeColor(color), CircleShape),
        )
    }
    ShortcutBackgroundColorDialog(
        show = showPicker,
        initialColor = color,
        onDismiss = { showPicker = false },
        onConfirm = { selected ->
            onColorChange(selected)
            showPicker = false
        },
    )
}

@Composable
private fun ShortcutBackgroundColorDialog(
    show: Boolean,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draftColor by remember(show, initialColor) { mutableIntStateOf(initialColor) }
    WindowDialog(show = show, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "\u9009\u62e9\u80cc\u666f\u989c\u8272",
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
            )
            ColorPalette(
                color = ComposeColor(draftColor or 0xFF000000.toInt()),
                onColorChanged = { selected ->
                    draftColor = (draftColor and 0xFF000000.toInt()) or (selected.toArgb() and 0x00FFFFFF)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onDismiss, Modifier.weight(1f)) { Text("\u53d6\u6d88") }
                Button(
                    onClick = { onConfirm(draftColor) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("\u786e\u5b9a") }
            }
        }
    }
}

@Composable
private fun Camera(c: CameraSettings, update: ((CameraSettings) -> CameraSettings) -> Unit, back: () -> Unit) = AppPage("\u76f8\u673a\u4e0e\u76f8\u518c\u7f16\u8f91", back) { p, scroll ->
    AppList(p, scroll, 28) { item { Card(Modifier.fillMaxWidth()) {
        SwitchPreference(title = "\u542f\u7528\u76f8\u673a\u6a21\u5757", checked = c.masterEnabled, onCheckedChange = { v -> update { it.copy(masterEnabled = v) } })
        SwitchPreference(title = "Leica LCC UI", checked = c.leicaUi, enabled = c.masterEnabled, onCheckedChange = { v -> update { it.copy(leicaUi = v) } })
        SwitchPreference(title = "\u4fdd\u7559\u539f\u751f\u7126\u6bb5", checked = c.preserveNativeFocalLengths, enabled = c.masterEnabled, onCheckedChange = { v -> update { it.copy(preserveNativeFocalLengths = v) } })
        SwitchPreference(title = "\u76f8\u518c\u7f16\u8f91\u5168\u6c34\u5370", checked = c.galleryAllWatermarks, enabled = c.masterEnabled, onCheckedChange = { v -> update { it.copy(galleryAllWatermarks = v) } })
    } } }
}

@Composable
private fun Dim(title: String, enabled: Boolean, changeEnabled: (Boolean) -> Unit, value: Float, range: ClosedFloatingPointRange<Float>, save: (Float) -> Unit) {
    SwitchPreference(title = "\u81ea\u5b9a\u4e49$title", checked = enabled, onCheckedChange = changeEnabled)
    if (enabled) FloatSlide(title, value, range, save)
}

@Composable
private fun Corner(title: String, enabled: Boolean, changeEnabled: (Boolean) -> Unit, value: Float, save: (Float) -> Unit) {
    SwitchPreference(title = "\u81ea\u5b9a\u4e49$title\u5706\u89d2", checked = enabled, onCheckedChange = changeEnabled)
    if (enabled) FloatSlide(title, value, 0f..60f, save)
}

@Composable
private fun FloatSlide(title: String, value: Float, range: ClosedFloatingPointRange<Float>, save: (Float) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value) }
    SliderPreference(value = current, onValueChange = { current = it }, onValueChangeFinished = { save((current * 10f).toInt() / 10f) }, title = title, valueText = "${(current * 10f).toInt() / 10f} dp", valueRange = range, steps = ((range.endInclusive - range.start) * 10f).toInt() - 1)
}

@Composable
private fun IntSlide(title: String, value: Int, range: IntRange, save: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    SliderPreference(value = current, onValueChange = { current = it }, onValueChangeFinished = { save(current.toInt().coerceIn(range.first, range.last)) }, title = title, valueText = "${current.toInt()} dp", valueRange = range.first.toFloat()..range.last.toFloat(), steps = range.last - range.first - 1)
}

@Composable
private fun ParameterIntSlide(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    save: (Int) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    SliderPreference(
        value = current,
        onValueChange = { current = it },
        onValueChangeFinished = { save(current.toInt().coerceIn(range.first, range.last)) },
        title = title,
        valueText = "${current.toInt()}$suffix",
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
    )
}

@Composable
private fun ParameterFloatSlide(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    save: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    SliderPreference(
        value = current,
        onValueChange = { current = it },
        onValueChangeFinished = { save((current * 100f).toInt() / 100f) },
        title = title,
        valueText = "${(current * 100f).toInt() / 100f}",
        valueRange = range,
        steps = ((range.endInclusive - range.start) * 100f).toInt() - 1,
    )
}

@Composable
private fun About(back: () -> Unit) = AppPage("\u5173\u4e8e", back) { padding, scroll ->
    val context = LocalContext.current
    AppList(padding, scroll, 28) {
        item {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                Image(painterResource(R.drawable.ic_os4changer_full), "OS4 Changer", Modifier.size(72.dp), contentScale = ContentScale.Fit)
                Text("OS4 Changer", style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                Text("\u4e00\u4e2a\u4e34\u65f6\u7528\u4e8e\u89e3\u9501\u5c0f\u7c73\u6f8e\u6e43 OS 4 Beta \u7248\u9650\u5236\u7684\u6a21\u5757\u3002", style = MiuixTheme.textStyles.body1, modifier = Modifier.padding(top = 6.dp))
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp).background(MiuixTheme.colorScheme.outline.copy(alpha = .22f)))
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MiuixTheme.textStyles.body2)
            }
        }
        item {
            Group("\u5f00\u53d1\u8005") {
                Row(Modifier.fillMaxWidth().clickable { openUrl(context, "https://btm-m.site") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.btm_m_avatar), "btm_m", Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("btm_m", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
                        Text("https://btm-m.site", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 3.dp))
                    }
                    Image(MiuixIcons.Regular.ChevronForward, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary))
                }
            }
        }
        item { Card(Modifier.fillMaxWidth()) { Text("\u672c\u9879\u76ee\u57fa\u4e8e MIT \u534f\u8bae\u5f00\u6e90", style = MiuixTheme.textStyles.body1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)); ArrowPreference(title = "GitHub Repository", summary = "github.com/ColdP/OS4Changer", onClick = { openUrl(context, "https://github.com/ColdP/OS4Changer") }) } }
        item { Text("\u00a9 ${Year.now().value} btm_m", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .56f), modifier = Modifier.padding(start = 12.dp)) }
    }
}

@Composable
private fun Donate(back: () -> Unit) = AppPage("\u6350\u8d60", back) { padding, scroll ->
    val context = LocalContext.current
    AppList(padding, scroll, 28) {
        item { Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Image(painterResource(R.drawable.btm_m_avatar), "btm_m", Modifier.size(84.dp).clip(androidx.compose.foundation.shape.CircleShape), contentScale = ContentScale.Crop); Text("btm_m", style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) } }
        item { Group("\u7231\u53d1\u7535") { ArrowPreference(title = "\u901a\u8fc7\u7231\u53d1\u7535\u652f\u6301\u6211", summary = "\u7231\u53d1\u7535\uff1abtm_m", onClick = { openUrl(context, "https://afdian.com/a/btm_m") }) } }
        item { Group("\u5fae\u4fe1\u8d5e\u8d4f\u7801") { Image(painterResource(R.drawable.mm_reward_lightmode), "\u5fae\u4fe1\u8d5e\u8d4f\u7801", Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp), contentScale = ContentScale.Fit) } }
    }
}

private data class OpenProject(val name: String, val version: String, val description: String, val url: String)
private val openProjects = listOf(
    OpenProject("MIUIX", "0.9.3", "HyperOS \u98ce\u683c\u754c\u9762\u3001\u504f\u597d\u8bbe\u7f6e\u3001\u56fe\u6807\u4e0e\u6a21\u7cca\u6548\u679c", "https://github.com/compose-miuix-ui/miuix"),
    OpenProject("LSPosed API", "102", "LSPosed \u6a21\u5757 API \u4e0e\u670d\u52a1\u901a\u4fe1", "https://github.com/LSPosed/LSPosed"),
    OpenProject("Backdrop / AndroidLiquidGlass", "2.0.0", "\u6db2\u6001\u73bb\u7483\u6e32\u67d3\u4e0e\u5e95\u90e8\u5bfc\u822a\u4ea4\u4e92", "https://github.com/Kyant0/AndroidLiquidGlass"),
    OpenProject("Compose Multiplatform", "1.11.x", "\u58f0\u660e\u5f0f\u754c\u9762\u3001\u5e03\u5c40\u4e0e\u52a8\u753b", "https://github.com/JetBrains/compose-multiplatform"),
    OpenProject("AndroidX", "\u591a\u4e2a\u7ec4\u4ef6", "Activity\u3001Lifecycle\u3001Core \u7b49 Android \u57fa\u7840\u5e93", "https://github.com/androidx/androidx")
)

@Composable
private fun OpenSource(back: () -> Unit) = AppPage("\u5f00\u6e90\u4ee3\u7801\u58f0\u660e", back) { padding, scroll ->
    val context = LocalContext.current
    AppList(padding, scroll, 28) {
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) { Text("OS4 Changer \u4f7f\u7528\u4e86\u4ee5\u4e0b\u5f00\u6e90\u9879\u76ee\u3002\u611f\u8c22\u6240\u6709\u9879\u76ee\u4f5c\u8005\u4e0e\u8d21\u732e\u8005\u3002", style = MiuixTheme.textStyles.body1) } }
        item { SmallTitle("\u754c\u9762\u3001\u529f\u80fd\u4e0e\u5e73\u53f0", insideMargin = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)) }
        items(openProjects.size) { i -> val item = openProjects[i]; Card(Modifier.fillMaxWidth().clickable { openUrl(context, item.url) }, insideMargin = PaddingValues(16.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold); Text("${item.version} \u00b7 Apache License 2.0", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 3.dp)); Text(item.description, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp)) }; Image(MiuixIcons.Regular.ChevronForward, null, Modifier.padding(start = 12.dp).size(22.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary)) } } }
    }
}

@Composable
private fun AppPage(title: String, onBack: (() -> Unit)? = null, content: @Composable (PaddingValues, ScrollBehavior) -> Unit) {
    val scroll = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdrop()
    val surface = MiuixTheme.colorScheme.surface
    val collapsedFraction = scroll.state.collapsedFraction.coerceIn(0f, 1f)
    Scaffold(
        topBar = {
            Box {
                if (isRuntimeShaderSupported()) {
                    Box(
                        Modifier.matchParentSize()
                            .graphicsLayer { alpha = 1f - collapsedFraction }
                            .drawPlainBackdrop(
                                backdrop = backdrop,
                                shape = { androidx.compose.ui.graphics.RectangleShape },
                                effects = { blur(6.dp.toPx()) },
                                onDrawSurface = { drawRect(surface.copy(alpha = 0.55f)) }
                            )
                    )
                    ProgressiveBlurLayer(backdrop, collapsedFraction)
                } else {
                    Box(Modifier.matchParentSize().background(surface.copy(alpha = 0.82f * (1f - collapsedFraction))))
                }
                TopAppBar(
                    title = "",
                    largeTitle = title,
                    color = ComposeColor.Transparent,
                    scrollBehavior = scroll,
                    navigationIcon = {
                        if (onBack != null) GlassBackButton(backdrop, collapsedFraction, onBack)
                    }
                )
                Box(
                    Modifier.windowInsetsPadding(WindowInsets.statusBars).height(52.dp).fillMaxWidth()
                        .graphicsLayer { alpha = scroll.state.collapsedFraction }
                        .padding(horizontal = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, style = MiuixTheme.textStyles.title3, maxLines = 1)
                }
            }
        }
    ) { padding -> Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content(padding, scroll) } }
}

@Composable
private fun BoxScope.ProgressiveBlurLayer(backdrop: LayerBackdrop, collapsedFraction: Float) {
    Box(
        Modifier.matchParentSize()
            .expandDrawHeight(1.184625f)
            .graphicsLayer { alpha = collapsedFraction }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { androidx.compose.ui.graphics.RectangleShape },
                effects = {
                    blur(16.dp.toPx())
                    runtimeShaderEffect(
                        key = "progressive-blur-alpha-mask",
                        shaderString = PROGRESSIVE_BLUR_ALPHA_MASK_SHADER,
                        uniformShaderName = "content"
                    ) { setFloatUniform("size", size.width, size.height) }
                }
            )
    )
}

@Composable
private fun GlassBackButton(backdrop: LayerBackdrop, collapsedFraction: Float, onClick: () -> Unit) {
    val animationScope = rememberCoroutineScope()
    val drag = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = 0f,
            valueRange = -1f..1f,
            visibilityThreshold = .001f,
            initialScale = 1f,
            pressedScale = 1.08f,
            onDragStopped = { animateToValue(0f) },
            onDrag = { size, amount -> updateValue((targetValue + amount.x / size.width.coerceAtLeast(1)).coerceIn(-1f, 1f)) }
        )
    }
    val highlight = remember(animationScope) {
        InteractiveHighlight(animationScope) { size, offset -> androidx.compose.ui.geometry.Offset(offset.x.coerceIn(0f, size.width), offset.y.coerceIn(0f, size.height)) }
    }
    val glassTint = MiuixTheme.colorScheme.surface.copy(alpha = .42f)
    val iconColor = MiuixTheme.colorScheme.onSurface
    val glassAlpha by animateFloatAsState(collapsedFraction, tween(180), label = "backButtonGlassAlpha")
    Box(
        Modifier.padding(start = 8.dp).size(46.dp)
            .then(highlight.gestureModifier)
            .then(drag.modifier)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = glassAlpha }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        val progress = drag.pressProgress
                        lens(8.dp.toPx() * progress, 12.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = .65f + drag.pressProgress * .35f) },
                    shadow = {
                        Shadow.Default.copy(
                            radius = 5.dp,
                            color = ComposeColor.Black,
                            alpha = .24f,
                        )
                    },
                    innerShadow = null,
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val velocity = abs(drag.velocity.coerceIn(-1f, 1f))
                        scaleX *= 1f + velocity * .12f
                        scaleY /= 1f + velocity * .08f
                        translationX = drag.value * 5.dp.toPx()
                    },
                    onDrawSurface = { drawCircle(glassTint) }
                )
                .then(highlight.modifier)
        )
        Image(
            MiuixIcons.Regular.ChevronBackward,
            "\u8fd4\u56de",
            Modifier.size(22.dp).graphicsLayer { translationX = -1.15.dp.toPx() },
            colorFilter = ColorFilter.tint(iconColor),
        )
    }
}

private const val PROGRESSIVE_BLUR_ALPHA_MASK_SHADER = """
uniform shader content;
uniform float2 size;
half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return content.eval(coord) * blurAlpha;
}
"""

private fun Modifier.expandDrawHeight(factor: Float) = layout { measurable, constraints ->
    val expandedHeight = (constraints.maxHeight * factor).toInt()
    val placeable = measurable.measure(constraints.copy(minHeight = expandedHeight, maxHeight = expandedHeight))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(0, 0) }
}

@Composable
private fun AppList(padding: PaddingValues, scroll: ScrollBehavior, bottom: Int = 112, items: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + bottom.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = items
    )
}

private fun openUrl(context: android.content.Context, url: String) = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
