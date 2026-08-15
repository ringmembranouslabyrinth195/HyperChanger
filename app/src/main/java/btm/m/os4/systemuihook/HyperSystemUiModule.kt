package btm.m.os4.systemuihook

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

private enum class NotificationMaterialType { NORMAL, MEDIA, FOCUS }

class HyperSystemUiModule : XposedModule() {
    override fun onPackageLoaded(param: PackageLoadedParam) {
        runCatching {
            val preferences = getRemotePreferences(REMOTE_PREFERENCE_GROUP)
            when (param.packageName) {
                SYSTEM_UI, SYSTEM_UI_PLUGIN -> {
                    if (!resourceHooksInstalled) {
                        installDimensionHooks(preferences)
                        resourceHooksInstalled = true
                    }
                    if (!cornerHooksInstalled) {
                        installCornerRadiusHooks(preferences)
                        cornerHooksInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI && !dynamicIslandClassDiscoveryInstalled) {
                        installDynamicIslandClassDiscovery(preferences)
                        dynamicIslandClassDiscoveryInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI_PLUGIN && !dynamicIslandHooksInstalled) {
                        installDynamicIslandHooks(param.defaultClassLoader, preferences)
                        dynamicIslandHooksInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI && !focusIslandWhitelistSystemUiHooksInstalled) {
                        installFocusIslandWhitelistSystemUiHooks(param.defaultClassLoader, preferences)
                        focusIslandWhitelistSystemUiHooksInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI_PLUGIN && !focusIslandWhitelistPluginHooksInstalled) {
                        focusIslandWhitelistPluginHooksInstalled = true
                        installFocusIslandWhitelistPluginHooks(param.defaultClassLoader, preferences)
                    }
                    if (param.packageName == SYSTEM_UI && !lockscreenNotificationHookInstalled) {
                        installLockscreenNotificationHook(param.defaultClassLoader, preferences)
                        lockscreenNotificationHookInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI && !fingerprintIconHookInstalled) {
                        installFingerprintIconVisualHook(param.defaultClassLoader, preferences)
                        fingerprintIconHookInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI && !systemUiDepthHookInstalled) {
                        installSystemUiDepthHooks(param.defaultClassLoader, preferences)
                        systemUiDepthHookInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI && !lockscreenChargingHookInstalled) {
                        installLockscreenChargingTextHook(param.defaultClassLoader, preferences)
                        lockscreenChargingHookInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI && !lockscreenShortcutGlassHookInstalled) {
                        installLockscreenShortcutGlassHook(param.defaultClassLoader, preferences)
                        lockscreenShortcutGlassHookInstalled = true
                    }
                    if (param.packageName == SYSTEM_UI && !shadeMaterialHooksInstalled) {
                        installShadeMaterialHooks(preferences)
                        shadeMaterialHooksInstalled = true
                    }
                }
                AOD -> {
                    if (!depthEffectHookInstalled) {
                        installDepthEffectHook(param.defaultClassLoader, preferences)
                        depthEffectHookInstalled = true
                    }
                }
                else -> {
                    detach()
                    return
                }
            }
            log(Log.INFO, TAG, "Installed hooks for ${param.packageName}")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install hooks for ${param.packageName}", error)
        }
    }

    private fun installDynamicIslandHooks(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        installDynamicIslandBackgroundHooks(classLoader, preferences)
        installDynamicIslandLayoutHooks(classLoader, preferences)
        installDynamicIslandSelfBlurHook(classLoader, preferences)
        if (!focusIslandWhitelistPluginHooksInstalled) {
            focusIslandWhitelistPluginHooksInstalled = true
            installFocusIslandWhitelistPluginHooks(classLoader, preferences)
        }
        log(Log.INFO, TAG, "Installed dynamic-island hooks")
    }

    private fun installFocusIslandWhitelistPluginHooks(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        runCatching {
            val settingsClass = classLoader.loadClass(PLUGIN_NOTIFICATION_SETTINGS_MANAGER_CLASS)
            listOf("canCustomFocus", "mediaIslandSupportMiniWindow").forEach { name ->
                hook(settingsClass.getMethod(name, String::class.java))
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId("focus-island-whitelist-plugin:$name")
                    .intercept { chain ->
                        if (preferences.getBoolean(KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT, false)) {
                            true
                        } else {
                            chain.proceed()
                        }
                    }
            }
            hook(settingsClass.getMethod("canShowFocus", android.content.Context::class.java, String::class.java))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("focus-island-whitelist-plugin:canShowFocus")
                .intercept { chain ->
                    if (preferences.getBoolean(KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT, false)) {
                        true
                    } else {
                        chain.proceed()
                    }
                }

            val focusUtilsClass = classLoader.loadClass(FOCUS_NOTIFICATION_UTILS_CLASS)
            val focusMethod = focusUtilsClass.declaredMethods.first {
                it.name == "canShowFocus" && it.parameterCount == 3
            }
            hook(focusMethod)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("focus-island-whitelist-plugin:focus-permission")
                .intercept { chain ->
                    if (preferences.getBoolean(KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT, false)) {
                        true
                    } else {
                        chain.proceed()
                    }
                }

            val coordinatorClass = classLoader.loadClass(DYNAMIC_ISLAND_EVENT_COORDINATOR_CLASS)
            hook(coordinatorClass.getMethod("mediaIslandSupportMiniWindow", String::class.java))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("focus-island-whitelist-plugin:event-coordinator")
                .intercept { chain ->
                    if (preferences.getBoolean(KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT, false)) {
                        true
                    } else {
                        chain.proceed()
                    }
                }

            val stateCallbackClass = classLoader.loadClass(ISLAND_STATE_CALLBACK_CONTROLLER_CLASS)
            val callbackMethod = stateCallbackClass.declaredMethods.first {
                it.name == "buildPendingStateCallback" && it.parameterCount == 3
            }
            hook(callbackMethod)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("focus-island-whitelist-plugin:state-callback")
                .intercept { chain ->
                    if (preferences.getBoolean(KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT, false)) {
                        allowIslandStateCallbackPackage(chain.thisObject, stateCallbackClass, chain.getArg(1))
                    }
                    chain.proceed()
                }
            log(Log.INFO, TAG, "Installed focus-notification and island whitelist hooks for plugin")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install focus-notification and island whitelist plugin hooks", error)
        }
    }

    private fun allowIslandStateCallbackPackage(
        controller: Any?,
        controllerClass: Class<*>,
        islandView: Any?,
    ) {
        val sourcePackage = runCatching {
            val data = islandView?.javaClass?.getMethod("getCurrentIslandData")?.invoke(islandView)
            val extras = data?.javaClass?.getMethod("getExtras")?.invoke(data) as? android.os.Bundle
            extras?.getString(DYNAMIC_ISLAND_SOURCE_PACKAGE_KEY)
        }.getOrNull() ?: return
        runCatching {
            val packagesField = controllerClass.getDeclaredField("callbackPackages").apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val current = packagesField.get(controller) as? List<String>
            if (current?.contains(sourcePackage) != true) {
                packagesField.set(controller, ArrayList(current.orEmpty()).apply { add(sourcePackage) })
            }
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not extend the island state-callback whitelist", error)
        }
    }

    private fun installFocusIslandWhitelistSystemUiHooks(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        runCatching {
            val settingsClass = classLoader.loadClass(SYSTEM_UI_NOTIFICATION_SETTINGS_MANAGER_CLASS)
            listOf(
                "canShowFocusState",
                "canShowFocusStateApp",
                "canShowFocusMediaState",
            ).forEach { name ->
                hook(settingsClass.getMethod(name, android.content.Context::class.java, String::class.java))
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId("focus-island-whitelist-systemui:$name")
                    .intercept { chain ->
                        if (preferences.getBoolean(KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT, false)) {
                            1
                        } else {
                            chain.proceed()
                        }
                    }
            }
            log(Log.INFO, TAG, "Installed focus-notification whitelist hooks for SystemUI")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install focus-notification whitelist SystemUI hooks", error)
        }
    }

    private fun installDynamicIslandClassDiscovery(preferences: SharedPreferences) {
        runCatching {
            hook(ClassLoader::class.java.getMethod("loadClass", String::class.java))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("dynamic-island-class-discovery")
                .intercept { chain ->
                    val loadedClass = chain.proceed() as? Class<*> ?: return@intercept null
                    if (loadedClass.name == DYNAMIC_ISLAND_BACKGROUND_CLASS && !dynamicIslandHooksInstalled) {
                        dynamicIslandHooksInstalled = true
                        loadedClass.classLoader?.let { pluginClassLoader ->
                            runCatching {
                                installDynamicIslandHooks(pluginClassLoader, preferences)
                            }.onFailure { error ->
                                dynamicIslandHooksInstalled = false
                                log(Log.ERROR, TAG, "Could not initialize dynamic-island hooks from plugin loader", error)
                            }
                        }
                    }
                    if (loadedClass.name == PLUGIN_NOTIFICATION_SETTINGS_MANAGER_CLASS &&
                        !focusIslandWhitelistPluginHooksInstalled
                    ) {
                        focusIslandWhitelistPluginHooksInstalled = true
                        loadedClass.classLoader?.let { pluginClassLoader ->
                            runCatching {
                                installFocusIslandWhitelistPluginHooks(pluginClassLoader, preferences)
                            }.onFailure { error ->
                                focusIslandWhitelistPluginHooksInstalled = false
                                log(Log.ERROR, TAG, "Could not initialize focus/island whitelist hooks from plugin loader", error)
                            }
                        }
                    }
                    loadedClass
                }
            log(Log.INFO, TAG, "Installed dynamic-island plugin class discovery hook")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install dynamic-island plugin class discovery hook", error)
        }
    }

    private fun installDynamicIslandBackgroundHooks(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        runCatching {
            val backgroundClass = classLoader.loadClass(DYNAMIC_ISLAND_BACKGROUND_CLASS)
            listOf("setDrawable", "setActualHeight", "setActualWidth").forEach { name ->
                val method = when (name) {
                    "setDrawable" -> backgroundClass.getMethod(name, Drawable::class.java)
                    else -> backgroundClass.getMethod(name, Int::class.javaPrimitiveType)
                }
                hook(method)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId("dynamic-island-background-$name")
                    .intercept { chain ->
                        val result = chain.proceed()
                        runCatching {
                            if (name == "setDrawable") {
                                (chain.thisObject as? View)?.let(expandedIslandMaterialSettings::remove)
                            }
                            applyExpandedIslandBackground(
                                chain.thisObject as? View,
                                preferences,
                                classLoader,
                            )
                        }
                        result
                    }
            }
            log(Log.INFO, TAG, "Installed DynamicIslandBackgroundView update hooks")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install DynamicIslandBackgroundView update hooks", error)
        }
    }

    private fun installDynamicIslandLayoutHooks(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        DYNAMIC_ISLAND_LAYOUT_CLASSES.forEach { className ->
            runCatching {
                val layoutClass = classLoader.loadClass(className)
                val methods = layoutClass.declaredMethods.filter {
                    it.name.startsWith("updateBigIslandLayout")
                }
                check(methods.isNotEmpty()) { "$className has no updateBigIslandLayout method" }
                methods.forEachIndexed { index, method ->
                    hook(method)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .setId("dynamic-island-layout:${layoutClass.name}#$index")
                        .intercept { chain ->
                            val result = chain.proceed()
                            val source = chain.thisObject as? View
                            applyExpandedIslandBackground(
                                source?.let(::findDynamicIslandBackground),
                                preferences,
                                classLoader,
                            )
                            result
                        }
                }
                log(Log.INFO, TAG, "Installed ${methods.size} big-island layout hooks for $className")
            }.onFailure { error ->
                log(Log.INFO, TAG, "Skipped dynamic-island layout class $className", error)
            }
        }
    }

    private fun installDynamicIslandSelfBlurHook(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        runCatching {
            val blurClass = classLoader.loadClass(MI_BLUR_COMPAT_CLASS)
            hook(blurClass.getMethod("setMiSelfBlur", View::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("dynamic-island-self-blur")
                .intercept { chain ->
                    val view = chain.getArg(0) as? View
                    if (view != null && isDynamicIslandView(view) &&
                        preferences.getBoolean(KEY_EXPANDED_ISLAND_BACKGROUND_ENABLED, false)
                    ) {
                        val radius = preferences.getInt(KEY_EXPANDED_ISLAND_SELF_BLUR_RADIUS, 0)
                            .coerceIn(0, 200)
                        chain.proceedWith(chain.thisObject, arrayOf(view, radius, chain.getArg(2)))
                    } else {
                        chain.proceed()
                    }
                }
            log(Log.INFO, TAG, "Installed dynamic-island self-blur hook")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install dynamic-island self-blur hook", error)
        }
    }

    private fun applyExpandedIslandBackground(view: View?, preferences: SharedPreferences, classLoader: ClassLoader) {
        if (view == null || !preferences.getBoolean(KEY_EXPANDED_ISLAND_BACKGROUND_ENABLED, false)) return
        if (view.javaClass.name != DYNAMIC_ISLAND_BACKGROUND_CLASS) return
        val opacity = preferences.getInt(KEY_EXPANDED_ISLAND_BACKGROUND_OPACITY, 35).coerceIn(0, 35)
        val smallBlur = preferences.getInt(KEY_EXPANDED_ISLAND_GLASS_BLUR_RADIUS, 10).coerceIn(0, 10)
        val largeBlur = preferences.getInt(KEY_EXPANDED_ISLAND_GLASS_LARGE_BLUR_RADIUS, 10).coerceIn(0, 10)
        val selfBlur = preferences.getInt(KEY_EXPANDED_ISLAND_SELF_BLUR_RADIUS, 0).coerceIn(0, 10)
        val highlight = preferences.getBoolean(KEY_EXPANDED_ISLAND_SHOW_HIGHLIGHT, false)
        val configuration = listOf(opacity, smallBlur, largeBlur, selfBlur, highlight).hashCode()
        if (expandedIslandMaterialSettings[view] == configuration) return
        val drawable = runCatching {
            view.javaClass.getMethod("getDrawable").invoke(view) as? Drawable
        }.getOrNull() ?: view.background
        drawable?.mutate()?.let { drawableValue ->
            drawableValue.alpha = opacity * 255 / 100
        }
        runCatching {
            val style = classLoader.loadClass(MI_BACKGROUND_STYLE_CLASS)
            val instance = style.getField("INSTANCE").get(null)
            val glassToken = style.getMethod("getDEFAULT_GLASS_TOKEN").invoke(instance)
            // This public entry point applies the material type and registers the view with
            // HyperOS's Glass renderer before the lower-level radius parameters are changed.
            style.methods
                .first { it.name == "setMiBackgroundStyle" && it.parameterCount == 3 }
                .invoke(null, view, null, glassToken)

            val blurUtils = classLoader.loadClass(MIUI_BLUR_UTILS_CLASS)
            blurUtils.getMethod(
                "setMiGlassBlurRadius",
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(
                null,
                view,
                smallBlur,
                largeBlur,
            )

            if (highlight) {
                val params = style.getDeclaredField("defaultBloomStrokeParams").apply { isAccessible = true }
                    .get(null) as FloatArray
                style.getMethod("setMiBloomStrokeCompat", View::class.java, FloatArray::class.java)
                    .invoke(null, view, params.clone())
            }
            view.invalidate()
            expandedIslandMaterialSettings[view] = configuration
            log(Log.DEBUG, TAG, "Applied expanded island glass: opacity=$opacity")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not apply expanded island glass", error)
        }
    }

    private fun findDynamicIslandBackground(view: View): View? {
        val root = findViewRoot(view)
        val pending = ArrayDeque<View>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val candidate = pending.removeFirst()
            if (candidate.javaClass.name == DYNAMIC_ISLAND_BACKGROUND_CLASS) return candidate
            if (candidate is ViewGroup) {
                repeat(candidate.childCount) { index ->
                    pending.add(candidate.getChildAt(index))
                }
            }
        }
        return null
    }

    private fun findViewRoot(view: View): View {
        var root = view
        while (root.parent is View) root = root.parent as View
        return root
    }

    private fun isDynamicIslandView(view: View): Boolean =
        generateSequence<View>(view) { it.parent as? View }.any { it.javaClass.name.contains("dynamicisland", true) }

    private fun installShadeMaterialHooks(preferences: SharedPreferences) {
        runCatching {
            val setGlass = View::class.java.getMethod("setMiGlass", FloatArray::class.java)
            hook(setGlass)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("shade-notification-glass-material")
                .intercept { chain ->
                    val original = chain.getArg(0) as? FloatArray
                    val controlCenter = isControlCenterCall()
                    val notification = isNotificationCenterCall()
                    val tuning = elementMaterialOverride(preferences, controlCenter, notification)
                    if (original != null && original.size >= MIN_GLASS_PARAMS_SIZE && tuning?.enabled == true) {
                        chain.proceedWith(chain.thisObject, arrayOf(applyMaterialOverride(original, tuning)))
                    } else {
                        chain.proceed()
                    }
                }

            val setGlassRadius = View::class.java.getMethod(
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            hook(setGlassRadius)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("shade-notification-glass-radius")
                .intercept { chain ->
                    val tuning = elementMaterialOverride(
                        preferences,
                        isControlCenterCall(),
                        isNotificationCenterCall(),
                    )
                    if (tuning?.enabled == true && tuning.glassRadius > 0) {
                        chain.proceedWith(
                            chain.thisObject,
                            arrayOf(tuning.glassRadius, tuning.glassRadius),
                        )
                    } else {
                        chain.proceed()
                    }
                }

            // Exact NotificationRowGlassEffect path from hyperos4-glass-blur-main.
            hook(View::class.java.getDeclaredMethod("onAttachedToWindow"))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("notification-row-glass-on-attach")
                .intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { view ->
                        requestNotificationRowGlass(view, preferences, "attach")
                    }
                    result
                }

            hook(View::class.java.getMethod("setBackground", Drawable::class.java))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("notification-row-glass-on-background")
                .intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { view ->
                        requestNotificationRowGlass(view, preferences, "background")
                    }
                    result
                }

            // NotificationRowBlurEffect may reset the material type to BLUR after the
            // system glass recipe has been applied; retain the GLASS material for rows.
            val setMaterialType = View::class.java.getMethod(
                "setMiViewMaterialType",
                Int::class.javaPrimitiveType,
            )
            hook(setMaterialType)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("notification-row-glass-material-type")
                .intercept { chain ->
                    val view = chain.thisObject as? View
                    if (view != null && isNotificationRowBackground(view) && notificationMaterialEnabled(preferences)) {
                        chain.proceedWith(chain.thisObject, arrayOf(1))
                    } else {
                        chain.proceed()
                    }
                }

            // Glass outlines are cleared by the blur recipe on some OS 4 builds.
            runCatching {
                val setBlurEnhanceFlag = View::class.java.getMethod(
                    "setMiBackgroundBlurEnhanceFlag",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                hook(setBlurEnhanceFlag)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId("notification-row-glass-outline")
                    .intercept { chain ->
                        val view = chain.thisObject as? View
                        if (view != null && isNotificationRowBackground(view) && notificationMaterialEnabled(preferences)) {
                            val flags = chain.getArg(0) as Int
                            val mask = chain.getArg(1) as Int
                            chain.proceedWith(chain.thisObject, arrayOf(flags or 8192, mask or 8192))
                        } else {
                            chain.proceed()
                        }
                    }
            }.onFailure { error ->
                log(Log.INFO, TAG, "Notification glass outline API is unavailable", error)
            }

            // The final notification background is sometimes stretched to the
            // bottom of the shade.  This is the reference module's SDF-height
            // guard, keeping the Glass layer within the visible row content.
            runCatching {
                val setSdfMaxSize = View::class.java.getMethod(
                    "setMiGlassSdfMaxSize",
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                )
                hook(setSdfMaxSize)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId("notification-row-glass-sdf-size")
                    .intercept { chain ->
                        val view = chain.thisObject as? View
                        if (view != null && isNotificationRowBackground(view) && notificationMaterialEnabled(preferences)) {
                            val visibleHeight = notificationVisibleHeight(view)
                            val wantedHeight = chain.getArg(1) as Float
                            if (visibleHeight > 0 && wantedHeight > visibleHeight) {
                                chain.proceedWith(
                                    chain.thisObject,
                                    arrayOf(chain.getArg(0), visibleHeight.toFloat()),
                                )
                            } else {
                                chain.proceed()
                            }
                        } else {
                            chain.proceed()
                        }
                    }
            }.onFailure { error ->
                log(Log.INFO, TAG, "Notification glass SDF API is unavailable", error)
            }

            val setBackgroundBlur = View::class.java.getMethod(
                "setMiBackgroundBlurRadius",
                Int::class.javaPrimitiveType,
            )
            hook(setBackgroundBlur)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("shade-panel-background-radius")
                .intercept { chain ->
                    val tuning = backgroundMaterialOverride(preferences)
                    if (tuning?.enabled == true && isShadeBlurProviderCall()) {
                        chain.proceedWith(
                            chain.thisObject,
                            arrayOf((chain.getArg(0) as Int) * tuning.blurPercent / 100),
                        )
                    } else {
                        chain.proceed()
                    }
                }
            val setScaleRatio = View::class.java.getMethod(
                "setMiBackgroundBlurScaleRatio",
                Float::class.javaPrimitiveType,
            )
            hook(setScaleRatio)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("shade-panel-background-scale")
                .intercept { chain ->
                    val tuning = backgroundMaterialOverride(preferences)
                    if (tuning?.enabled == true && isShadeBlurProviderCall()) {
                        chain.proceedWith(
                            chain.thisObject,
                            arrayOf((chain.getArg(0) as Float) * tuning.scalePercent / 100f),
                        )
                    } else chain.proceed()
                }
            val setBlendColors = View::class.java.getMethod("setMiBackgroundBlendColors", ArrayList::class.java)
            hook(setBlendColors)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("shade-panel-background-tint")
                .intercept { chain ->
                    val tuning = backgroundMaterialOverride(preferences)
                    val original = chain.getArg(0) as? ArrayList<*>
                    if (tuning?.enabled == true && tuning.tintEnabled && tuning.tintStrength > 0 &&
                        original != null && isShadeBlurProviderCall()
                    ) {
                        chain.proceedWith(
                            chain.thisObject,
                            arrayOf(applyBackgroundTint(original, tuning)),
                        )
                    } else chain.proceed()
                }
            log(Log.INFO, TAG, "Installed configurable notification and control-center material hooks")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install notification and shade material hooks", error)
        }
    }

    private fun notificationTuningFor(view: View, preferences: SharedPreferences): GlassTuning? {
        if (!isNotificationRowBackground(view)) return null
        val onKeyguard = runCatching {
            (readInstanceField(view, "mOnKeyguard") as? Boolean)
                ?: (view.javaClass.methods.firstOrNull {
                    it.name == "isOnKeyguard" && it.parameterCount == 0
                }?.invoke(view) as? Boolean)
                ?: false
        }.getOrDefault(false)
        val type = notificationTypeFor(view)
        val contextIsLockscreen = !preferences.getBoolean(KEY_NOTIFICATION_CONTEXT_UNIFIED, true) && onKeyguard
        val typeIsSeparate = !preferences.getBoolean(KEY_NOTIFICATION_TYPE_UNIFIED, true)
        return when {
            !typeIsSeparate -> if (contextIsLockscreen) {
                glassTuning(preferences, KEY_LOCKSCREEN_NORMAL)
            } else {
                glassTuning(preferences, KEY_NOTIFICATION_CENTER_NORMAL)
            }
            contextIsLockscreen && type == NotificationMaterialType.MEDIA ->
                glassTuning(preferences, KEY_LOCKSCREEN_MEDIA)
            contextIsLockscreen && type == NotificationMaterialType.FOCUS ->
                glassTuning(preferences, KEY_LOCKSCREEN_FOCUS)
            !contextIsLockscreen && type == NotificationMaterialType.MEDIA ->
                glassTuning(preferences, KEY_NOTIFICATION_CENTER_MEDIA)
            !contextIsLockscreen && type == NotificationMaterialType.FOCUS ->
                glassTuning(preferences, KEY_NOTIFICATION_CENTER_FOCUS)
            contextIsLockscreen -> glassTuning(preferences, KEY_LOCKSCREEN_NORMAL)
            else -> glassTuning(preferences, KEY_NOTIFICATION_CENTER_NORMAL)
        }
    }

    /**
     * Mirrors hyperos4-glass-blur-main: the material setter belongs to View,
     * therefore its owner has to be identified from the SystemUI call stack,
     * not from the anonymous child view receiving the setter call.
     */
    private fun materialTuningFor(view: View, preferences: SharedPreferences): GlassTuning? = when {
        isNotificationCenterCall() && isNotificationRowBackground(view) ->
            notificationTuningFor(view, preferences)
        isControlCenterCall() -> controlCenterTuningFor(view, preferences)
        else -> null
    }

    private fun notificationTypeFor(background: View): NotificationMaterialType {
        val row = generateSequence(background.parent) { it.parent }
            .filterIsInstance<View>()
            .firstOrNull { it.javaClass.name.contains("ExpandableNotificationRow") }
            ?: return NotificationMaterialType.NORMAL
        return runCatching {
            val entry = row.javaClass.methods.firstOrNull {
                it.name == "getEntry" && it.parameterCount == 0
            }?.invoke(row) ?: return@runCatching NotificationMaterialType.NORMAL
            val sbn = readInstanceField(entry, "mSbn")
                ?: entry.javaClass.methods.firstOrNull {
                    it.name == "getSbn" && it.parameterCount == 0
                }?.invoke(entry)
                ?: return@runCatching NotificationMaterialType.NORMAL
            val isFocus = (readInstanceField(sbn, "mIsFocusNotification") as? Boolean)
                ?: (sbn.javaClass.methods.firstOrNull {
                    it.name == "isFocusNotification" && it.parameterCount == 0
                }?.invoke(sbn) as? Boolean)
                ?: false
            if (isFocus) {
                NotificationMaterialType.FOCUS
            } else {
                val notification = sbn.javaClass.methods.firstOrNull {
                    it.name == "getNotification" && it.parameterCount == 0
                }?.invoke(sbn) ?: return@runCatching NotificationMaterialType.NORMAL
                if (notification.javaClass.methods.firstOrNull {
                        it.name == "isMediaNotification" && it.parameterCount == 0
                    }?.invoke(notification) as? Boolean == true
                ) {
                    NotificationMaterialType.MEDIA
                } else {
                    NotificationMaterialType.NORMAL
                }
            }
        }.getOrDefault(NotificationMaterialType.NORMAL)
    }

    private fun controlCenterTuningFor(view: View, preferences: SharedPreferences): GlassTuning? = when {
        // The material setter runs on anonymous child views.  The owning panel type is
        // present in the call stack, which is the same identification route used by the
        // verified HyperOS 4 reference module.
        stackContainsClass(SLIDER_VIEW_HOLDER_CLASS) ||
            stackContainsClass("ToggleSlider") ||
            isControlCenterSliderPart(view) -> glassTuning(preferences, KEY_CONTROL_CENTER_SLIDER)
        stackContainsClass(TOP_BUTTONS_CLASS) ||
            view.javaClass.name == TOP_BUTTONS_CLASS ||
            hasAncestorClass(view, "QSCardItemView") -> glassTuning(preferences, KEY_CONTROL_CENTER_BUTTON)
        else -> null
    }

    private fun logControlCenterMaterialHit(view: View?, method: String) {
        val type = when {
            stackContainsClass(SLIDER_VIEW_HOLDER_CLASS) || stackContainsClass("ToggleSlider") -> "slider"
            stackContainsClass(TOP_BUTTONS_CLASS) -> "button"
            else -> "fallback"
        }
        if (controlCenterMaterialHits.add("$type:$method")) {
            log(Log.INFO, TAG, "Control-center $type material matched $method on ${view?.javaClass?.name}")
        }
    }

    private fun requestNotificationRowGlass(
        view: View,
        preferences: SharedPreferences,
        source: String,
    ) {
        if (!isNotificationRowBackground(view) ||
            !notificationMaterialEnabled(preferences) ||
            notificationGlassApplying.get() == true
        ) {
            return
        }
        applySystemNotificationRowGlass(view, source)
        // Notification backgrounds can be attached before their parent row has finished
        // binding.  One posted retry covers that lifecycle without permanent listeners.
        view.post {
            if (view.isAttachedToWindow && notificationMaterialEnabled(preferences)) {
                applySystemNotificationRowGlass(view, "$source-post")
            }
        }
    }

    private fun applySystemNotificationRowGlass(background: View, source: String) {
        if (!isNotificationRowBackground(background) || notificationGlassApplying.get() == true) return
        notificationGlassApplying.set(true)
        try {
            val row = generateSequence(background.parent) { it.parent }
                .filterIsInstance<View>()
                .firstOrNull { it.javaClass.name.contains("ExpandableNotificationRow") }
                ?: return
            val effectClass = row.javaClass.classLoader.loadClass(NOTIFICATION_ROW_GLASS_EFFECT_CLASS)
            val instance = effectClass.fields.firstOrNull { it.name == "INSTANCE" }?.get(null)
                ?: effectClass.declaredFields.firstOrNull { it.name == "INSTANCE" }
                    ?.apply { isAccessible = true }
                    ?.get(null)
                ?: return
            val apply = effectClass.methods.firstOrNull {
                it.name == "apply" && it.parameterCount == 2
            } ?: return
            apply.invoke(instance, row, background.context)
            log(Log.DEBUG, TAG, "Applied system notification glass through $source")
        } catch (error: Throwable) {
            log(Log.ERROR, TAG, "Could not apply system notification glass", error)
        } finally {
            notificationGlassApplying.remove()
        }
    }

    private fun hasCustomizedNotificationTuning(view: View, preferences: SharedPreferences): Boolean =
        notificationTuningFor(view, preferences)?.let { it != GlassTuning() } == true

    private fun isNotificationRowBackground(view: View): Boolean {
        if (!view.javaClass.name.contains("NotificationBackgroundView")) return false
        val idName = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        return idName == null || idName == "backgroundNormal" || idName == "backgroundDimmed"
    }

    private fun notificationVisibleHeight(view: View): Int = runCatching {
        val actualHeight = (view.javaClass.methods.firstOrNull {
            it.name == "getActualHeight" && it.parameterCount == 0
        }?.invoke(view) as? Number)?.toInt() ?: view.height
        val injector = readInstanceField(view, "mNotificationBackgroundViewInjector") ?: return@runCatching actualHeight
        val clipBottom = (readInstanceField(injector, "clipBottomAmount") as? Number)?.toInt() ?: 0
        val extClipBottom = (readInstanceField(injector, "extClipBottomAmount") as? Number)?.toInt() ?: 0
        (actualHeight - maxOf(clipBottom, extClipBottom)).coerceAtLeast(0)
    }.getOrDefault(view.height)

    private fun hasAncestorClass(view: View, classNamePart: String): Boolean =
        generateSequence(view.parent) { it.parent }
            .filterIsInstance<View>()
            .any { it.javaClass.name.contains(classNamePart) }

    private fun readInstanceField(instance: Any, name: String): Any? {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            val field = runCatching { type.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(instance)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun shadePanelTuning(preferences: SharedPreferences): GlassTuning? = when {
        stackContainsClass("controlcenter") -> glassTuning(preferences, KEY_CONTROL_CENTER_BACKGROUND)
        stackContainsClass("notification") || stackContainsClass("ShadeBlendBlurController") ->
            glassTuning(preferences, KEY_NOTIFICATION_CENTER_BACKGROUND)
        else -> null
    }

    private fun isControlCenterCall(): Boolean = Thread.currentThread().stackTrace.any {
        it.className.startsWith("miui.systemui.controlcenter.")
    }

    private fun isNotificationCenterCall(): Boolean = Thread.currentThread().stackTrace.any {
        it.className.startsWith("com.android.systemui.statusbar.notification.")
    }

    private fun isShadeBlurProviderCall(): Boolean = Thread.currentThread().stackTrace.any {
        it.className.startsWith("com.miui.systemui.shade.blur.ShadeBlendBlurController\$BlurProvider")
    }

    private fun stackContainsClass(classNamePart: String): Boolean =
        Thread.currentThread().stackTrace.any { it.className.contains(classNamePart, ignoreCase = true) }

    private fun stackContains(classNamePart: String, methodNamePart: String): Boolean =
        Thread.currentThread().stackTrace.any {
            it.className.contains(classNamePart) && it.methodName.contains(methodNamePart)
        }

    private fun glassTuning(preferences: SharedPreferences, key: String): GlassTuning {
        val parts = preferences.getString(key, null)?.split('|') ?: return GlassTuning()
        return GlassTuning(
            blurPercent = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 200) ?: 100,
            opacity = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 100,
            color = parts.getOrNull(2)?.toLongOrNull()?.toInt() ?: Color.WHITE,
            customColorEnabled = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
        )
    }

    private fun applyGlassTuning(original: FloatArray, tuning: GlassTuning): FloatArray {
        val tuned = original.clone()
        tuned[GLASS_ALPHA_INDEX] = (tuned[GLASS_ALPHA_INDEX] * tuning.opacity / 100f).coerceAtLeast(0f)
        if (tuning.customColorEnabled) {
            tuned[GLASS_TINT_RED_INDEX] = Color.red(tuning.color) / 255f
            tuned[GLASS_TINT_GREEN_INDEX] = Color.green(tuning.color) / 255f
            tuned[GLASS_TINT_BLUE_INDEX] = Color.blue(tuning.color) / 255f
        }
        return tuned
    }

    private fun elementMaterialOverride(
        preferences: SharedPreferences,
        controlCenter: Boolean,
        notification: Boolean,
    ): MaterialOverride? = when {
        controlCenter -> preferences.getMaterialOverride(KEY_CONTROL_CENTER_ELEMENTS_MATERIAL)
        notification -> preferences.getMaterialOverride(KEY_NOTIFICATION_ELEMENTS_MATERIAL)
        else -> null
    }

    private fun backgroundMaterialOverride(preferences: SharedPreferences): MaterialOverride? = when {
        stackContainsClass("controlcenter") ->
            preferences.getMaterialOverride(KEY_CONTROL_CENTER_BACKGROUND_MATERIAL)
        stackContainsClass("notification") || isShadeBlurProviderCall() ->
            preferences.getMaterialOverride(KEY_NOTIFICATION_CENTER_BACKGROUND_MATERIAL)
        else -> null
    }

    private fun notificationMaterialEnabled(preferences: SharedPreferences): Boolean =
        preferences.getMaterialOverride(KEY_NOTIFICATION_ELEMENTS_MATERIAL).enabled

    private fun applyMaterialOverride(original: FloatArray, tuning: MaterialOverride): FloatArray = original.clone().apply {
        this[6] += tuning.brightness / 100f
        this[7] = (this[7] + tuning.darker / 100f).coerceAtLeast(0f)
        this[32] += tuning.refraction / 100f
        this[35] = (this[35] + tuning.burn / 100f).coerceAtLeast(0f)
        this[5] += tuning.saturation / 100f
        this[14] = (this[14] + tuning.alpha / 100f).coerceAtLeast(0f)
        this[21] += tuning.edgeThickness / 100f
        this[24] += tuning.reflection / 100f
        this[28] += tuning.directionalLight / 100f
        this[33] += tuning.backgroundSaturation / 100f
        this[34] += tuning.backgroundBrightness / 100f
        if (tuning.tintEnabled && tuning.tintStrength > 0) {
            val strength = tuning.tintStrength / 100f
            this[11] += Color.red(tuning.tintColor) / 255f * strength
            this[12] += Color.green(tuning.tintColor) / 255f * strength
            this[13] += Color.blue(tuning.tintColor) / 255f * strength
        }
    }

    private fun applyBackgroundTint(original: ArrayList<*>, tuning: MaterialOverride): ArrayList<Point> =
        ArrayList<Point>(original.size).also { tuned ->
            original.filterIsInstance<Point>().forEach { point ->
                val color = Color.argb(
                    (tuning.tintStrength * 255 / 100).coerceIn(0, 255),
                    Color.red(tuning.tintColor),
                    Color.green(tuning.tintColor),
                    Color.blue(tuning.tintColor),
                )
                tuned += Point(color, point.y)
            }
        }

    private fun applyBlendColorTuning(original: ArrayList<*>, tuning: GlassTuning): ArrayList<Point> =
        ArrayList<Point>(original.size).also { tuned ->
            original.filterIsInstance<Point>().forEach { point ->
                val alpha = (Color.alpha(point.x) * tuning.opacity / 100f).toInt().coerceIn(0, 255)
                val color = if (tuning.customColorEnabled) tuning.color else point.x
                tuned += Point(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), point.y)
            }
        }

    private fun scaleBlur(radius: Int, tuning: GlassTuning): Int =
        (radius * tuning.blurPercent / 100f).toInt().coerceIn(0, MAX_GLASS_BLUR_RADIUS)

    private fun installDepthEffectHook(classLoader: ClassLoader, preferences: SharedPreferences) {
        runCatching {
            // Loading a class does not run its static initializer. Hook the constructor before
            // DepthAvoidEvaluator creates IMAGE_THRESHOLD in <clinit>.
            val thresholdClass = classLoader.loadClass(DEPTH_THRESHOLD_CLASS)
            val evaluatorClass = classLoader.loadClass(DEPTH_EVALUATOR_CLASS)
            val constructor = thresholdClass.getDeclaredConstructor(Double::class.javaPrimitiveType)
            hook(constructor)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("depth-image-threshold")
                .intercept { chain ->
                    val original = chain.getArg(0) as? Double
                    if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false) &&
                        original == DEFAULT_DEPTH_IMAGE_THRESHOLD
                    ) {
                        log(Log.INFO, TAG, "Replacing DepthAvoidEvaluator image threshold: 0.2 -> 1.0")
                        chain.proceed(arrayOf(UNLIMITED_DEPTH_IMAGE_THRESHOLD))
                    } else {
                        chain.proceed()
                    }
                }
            hookClassInitializer(evaluatorClass)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("depth-image-threshold-verification")
                .intercept { chain ->
                    val result = chain.proceed()
                    if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false)) {
                        runCatching {
                            val threshold = evaluatorClass
                                .getDeclaredField(IMAGE_THRESHOLD_FIELD)
                                .get(null)
                            thresholdClass
                                .getDeclaredField(THRESHOLD_RATE_FIELD)
                                .setDouble(threshold, UNLIMITED_DEPTH_IMAGE_THRESHOLD)
                            log(Log.INFO, TAG, "Verified DepthAvoidEvaluator image threshold: 1.0")
                        }.onFailure { error ->
                            log(Log.ERROR, TAG, "Could not verify depth image threshold", error)
                        }
                    }
                    result
                }
            installDepthAvoidanceBypass(classLoader, preferences)
            log(Log.INFO, TAG, "Installed depth limitation hooks")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install depth image threshold hook", error)
        }
    }

    private fun installDepthAvoidanceBypass(classLoader: ClassLoader, preferences: SharedPreferences) {
        val controllerClass = classLoader.loadClass(HIERARCHY_AVOID_CONTROLLER_CLASS)
        hook(controllerClass.getMethod("isHierarchyEnable"))
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("depth-time-overlap-result")
            .intercept { chain ->
                val result = chain.proceed()
                if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false) &&
                    isUserHierarchyEnabled(chain.thisObject, controllerClass)
                ) true else result
            }

        hook(
            controllerClass.getMethod(
                "onHierarchyEnableChange",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ),
        )
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("depth-time-overlap-state")
            .intercept { chain ->
                if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false) &&
                    isUserHierarchyEnabled(chain.thisObject, controllerClass)
                ) {
                    chain.proceedWith(chain.thisObject, arrayOf(true, chain.getArg(1)))
                } else {
                    chain.proceed()
                }
            }
        log(Log.INFO, TAG, "Installed time-overlap depth bypass")
    }

    private fun isUserHierarchyEnabled(instance: Any?, controllerClass: Class<*>): Boolean = runCatching {
        controllerClass.getDeclaredField(USER_OPEN_HIERARCHY_FIELD)
            .apply { isAccessible = true }
            .getBoolean(instance)
    }.getOrDefault(false)

    private fun installLockscreenShortcutGlassHook(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        runCatching {
            val controllerClass = classLoader.loadClass(MIUI_SHORTCUT_CONTROLLER_CLASS)
            val shortcutMethods = controllerClass.declaredMethods
                .filter { it.name == "addShortcutViews" && it.parameterCount == 1 }
            check(shortcutMethods.isNotEmpty()) { "MiuiShortcutController.addShortcutViews was not found" }
            shortcutMethods.forEachIndexed { index, method ->
                hook(method)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId("lockscreen-shortcut-glass-$index")
                    .intercept { chain ->
                        val result = chain.proceed()
                        val root = chain.getArg(0) as? View ?: return@intercept result
                        if (shortcutBackgroundMode(preferences) != SHORTCUT_BACKGROUND_NONE ||
                            shortcutIconColorMode(preferences) != SHORTCUT_ICON_COLOR_AUTO
                        ) {
                            runCatching {
                                installShortcutGlassBackgrounds(root, preferences, classLoader)
                            }.onFailure { error ->
                                log(Log.ERROR, TAG, "Could not apply lockscreen shortcut glass", error)
                            }
                        }
                        result
                    }
            }
            log(Log.INFO, TAG, "Installed ${shortcutMethods.size} lockscreen shortcut glass hook(s)")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install lockscreen shortcut glass hook", error)
        }
    }

    private fun installShortcutGlassBackgrounds(
        root: View,
        preferences: SharedPreferences,
        classLoader: ClassLoader,
    ) {
        val backgroundMode = shortcutBackgroundMode(preferences)
        val radius = preferences.getFloat(KEY_LOCKSCREEN_SHORTCUT_GLASS_RADIUS, DEFAULT_SHORTCUT_GLASS_RADIUS)
            .coerceIn(MIN_SHORTCUT_GLASS_RADIUS, MAX_SHORTCUT_GLASS_RADIUS)
        val diameter = (radius * root.resources.displayMetrics.density).toInt().coerceAtLeast(1) * 2
        findShortcutContainers(root).forEach { shortcutContainer ->
            if (backgroundMode != SHORTCUT_BACKGROUND_NONE) {
                shortcutContainer.clipChildren = false
                shortcutContainer.clipToPadding = false
                for (index in shortcutContainer.childCount - 1 downTo 0) {
                    val child = shortcutContainer.getChildAt(index)
                    if (child.tag == SHORTCUT_GLASS_TAG) shortcutContainer.removeViewAt(index)
                }
                val glassBackground = ImageView(shortcutContainer.context).apply {
                    tag = SHORTCUT_GLASS_TAG
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    if (backgroundMode == SHORTCUT_BACKGROUND_PURE_COLOR) {
                        setBackgroundColor(preferences.getInt(KEY_SHORTCUT_PURE_COLOR, SHORTCUT_PURE_COLOR))
                    } else {
                        // Miui's backdrop renderer only registers views that have drawable content.
                        // A one-alpha source keeps this layer visually transparent until the system
                        // material pipeline has rendered its backdrop into it.
                        setImageDrawable(GradientDrawable().apply { setColor(Color.argb(1, 255, 255, 255)) })
                    }
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(target: View, outline: Outline) {
                            outline.setOval(0, 0, target.width, target.height)
                        }
                    }
                }
                shortcutContainer.addView(
                    glassBackground,
                    0,
                    FrameLayout.LayoutParams(diameter, diameter, Gravity.CENTER),
                )
                glassBackground.invalidateOutline()
                if (backgroundMode == SHORTCUT_BACKGROUND_ADVANCED_MATERIAL) {
                    runCatching {
                        applyLegacyBackdropMaterial(
                            view = glassBackground,
                            opacity = preferences.getInt(
                                KEY_SHORTCUT_ADVANCED_MATERIAL_OPACITY,
                                DEFAULT_ADVANCED_MATERIAL_OPACITY,
                            ).coerceIn(0, 35),
                            blurRadius = preferences.getInt(
                                KEY_SHORTCUT_ADVANCED_MATERIAL_BLUR_RADIUS,
                                DEFAULT_ADVANCED_MATERIAL_BLUR_RADIUS,
                            ).coerceIn(0, 10),
                            color = preferences.getInt(
                                KEY_SHORTCUT_ADVANCED_MATERIAL_COLOR,
                                DEFAULT_ADVANCED_MATERIAL_COLOR,
                            ),
                            showHighlight = preferences.getBoolean(KEY_SHORTCUT_ADVANCED_MATERIAL_HIGHLIGHT, false),
                        )
                    }.onFailure { error -> log(Log.ERROR, TAG, "Could not initialize shortcut backdrop", error) }
                }
                if (backgroundMode == SHORTCUT_BACKGROUND_SOFT_GLASS) {
                    runCatching {
                        applyLegacyBackdropMaterial(
                            view = glassBackground,
                            opacity = preferences.getInt(
                                KEY_SHORTCUT_SOFT_GLASS_OPACITY,
                                DEFAULT_SOFT_GLASS_OPACITY,
                            ).coerceIn(0, 35),
                            blurRadius = preferences.getInt(
                                KEY_SHORTCUT_SOFT_GLASS_BACKDROP_BLUR_RADIUS,
                                DEFAULT_SOFT_GLASS_BACKDROP_BLUR_RADIUS,
                            ).coerceIn(0, 10),
                            color = preferences.getInt(
                                KEY_SHORTCUT_SOFT_GLASS_COLOR,
                                DEFAULT_SOFT_GLASS_COLOR,
                            ),
                            showHighlight = false,
                        )
                        applySystemGlassMaterial(
                            view = glassBackground,
                            classLoader = classLoader,
                            blurRadius = preferences.getInt(
                                KEY_SHORTCUT_SOFT_GLASS_BLUR_RADIUS,
                                DEFAULT_SOFT_GLASS_BLUR_RADIUS,
                            ).coerceIn(0, 10),
                            luminance = preferences.getFloat(
                                KEY_SHORTCUT_SOFT_GLASS_LUMINANCE,
                                DEFAULT_SOFT_GLASS_LUMINANCE,
                            ),
                        )
                    }.onFailure { error -> log(Log.ERROR, TAG, "Could not initialize OS4 shortcut glass", error) }
                }
            }
            applyShortcutIconColorMode(shortcutContainer, shortcutIconColorMode(preferences))
        }
        log(
            Log.INFO,
            TAG,
            "Applied shortcut background to ${findShortcutContainers(root).size} container(s), mode=$backgroundMode, radius=${radius}dp",
        )
    }

    private fun findShortcutContainers(root: View): List<FrameLayout> {
        val result = ArrayList<FrameLayout>(2)
        fun visit(view: View) {
            if (view is FrameLayout && view.idName() in LOCKSCREEN_SHORTCUT_CONTAINER_IDS) {
                result += view
                return
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        visit(root)
        return result
    }

    private fun applyShortcutIconColorMode(container: ViewGroup, mode: Int) {
        fun applyTo(view: View) {
            if (view is ImageView && view.tag != SHORTCUT_GLASS_TAG) {
                when (mode) {
                    SHORTCUT_ICON_COLOR_LIGHT -> view.setColorFilter(SHORTCUT_ICON_LIGHT_COLOR)
                    SHORTCUT_ICON_COLOR_DARK -> view.setColorFilter(SHORTCUT_ICON_DARK_COLOR)
                    else -> view.clearColorFilter()
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) applyTo(view.getChildAt(index))
            }
        }
        applyTo(container)
    }

    private fun View.idName(): String? = runCatching {
        resources.getResourceEntryName(id)
    }.getOrNull()

    /**
     * This is the platform backdrop path used by HyperCeiler for lockscreen shortcuts.
     * It must be initialized before MiGlassCompat: MiGlass provides the OS4 material
     * parameters, while these APIs register the view with the window blur compositor.
     */
    private fun applyLegacyBackdropMaterial(
        view: View,
        opacity: Int,
        blurRadius: Int,
        color: Int,
        showHighlight: Boolean,
    ) {
        val viewClass = View::class.java
        viewClass.getMethod("clearMiBackgroundBlendColor").invoke(view)
        viewClass.getMethod("setPassWindowBlurEnabled", Boolean::class.javaPrimitiveType)
            .invoke(view, true)
        viewClass.getMethod("setMiViewBlurMode", Int::class.javaPrimitiveType)
            .invoke(view, SHORTCUT_GLASS_BLUR_MODE)
        viewClass.getMethod("setMiBackgroundBlurMode", Int::class.javaPrimitiveType)
            .invoke(view, SHORTCUT_GLASS_BLUR_MODE)
        viewClass.getMethod("setMiBackgroundBlurRadius", Int::class.javaPrimitiveType)
            .invoke(view, blurRadius.coerceIn(0, MAX_SHORTCUT_BACKDROP_BLUR_RADIUS))
        viewClass.getMethod(
            "addMiBackgroundBlendColor",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).invoke(
            view,
            Color.argb(
                opacity.coerceIn(0, MAX_SHORTCUT_OPACITY) * 255 / MAX_SHORTCUT_OPACITY,
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            ),
            SHORTCUT_GLASS_BLEND_MODE,
        )
        if (showHighlight) {
            viewClass.getMethod("setMiBloomStroke", FloatArray::class.java)
                .invoke(view, SHORTCUT_BLOOM_STROKE_PARAMETERS)
        }
    }

    private fun applySystemGlassMaterial(
        view: View,
        classLoader: ClassLoader,
        blurRadius: Int,
        luminance: Float,
    ) {
        val glassCompat = Class.forName(MI_GLASS_COMPAT_CLASS, false, classLoader)
        val smallBlur = blurRadius.coerceIn(0, MAX_SHORTCUT_GLASS_BLUR_RADIUS)
        val glassParameters = SHORTCUT_GLASS_PARAMETERS.copyOf().apply {
            this[GLASS_LUMINANCE_AMOUNT_INDEX] = luminance.coerceIn(0f, MAX_SHORTCUT_GLASS_LUMINANCE)
        }
        glassCompat.getMethod(
            "setMiGlassBlurRadius",
            View::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).invoke(null, view, smallBlur, (smallBlur * 2).coerceAtMost(MAX_SHORTCUT_GLASS_LARGE_BLUR_RADIUS))
        glassCompat.getMethod(
            "setMiViewMaterialTypeCompat",
            Int::class.javaPrimitiveType,
            View::class.java,
        ).invoke(null, SHORTCUT_GLASS_MATERIAL_TYPE, view)
        glassCompat.getMethod("setMiGlassCompat", View::class.java, FloatArray::class.java)
            .invoke(null, view, glassParameters)
    }

    private fun shortcutBackgroundMode(preferences: SharedPreferences): Int = preferences.getInt(
        KEY_LOCKSCREEN_SHORTCUT_BACKGROUND_MODE,
        if (preferences.getBoolean(KEY_LOCKSCREEN_SHORTCUT_GLASS_ENABLED, false)) {
            SHORTCUT_BACKGROUND_SOFT_GLASS
        } else {
            SHORTCUT_BACKGROUND_NONE
        },
    ).coerceIn(SHORTCUT_BACKGROUND_NONE, SHORTCUT_BACKGROUND_SOFT_GLASS)

    private fun shortcutIconColorMode(preferences: SharedPreferences): Int = preferences.getInt(
        KEY_SHORTCUT_ICON_COLOR_MODE,
        SHORTCUT_ICON_COLOR_AUTO,
    ).coerceIn(SHORTCUT_ICON_COLOR_AUTO, SHORTCUT_ICON_COLOR_DARK)

    private fun installLockscreenNotificationHook(classLoader: ClassLoader, preferences: SharedPreferences) {
        runCatching {
            val legacyFlowClass = classLoader.loadClass(FOD_SHELF_SPACE_FLOW_CLASS)
            hook(legacyFlowClass.getDeclaredMethod("invokeSuspend", Any::class.java))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("lockscreen-notification-ignore-fod")
                .intercept { chain ->
                    if (notificationFodMode(preferences) != FOD_MODE_DEFAULT) false else chain.proceed()
                }

            val positionFlowClass = classLoader.loadClass(FOD_NOTIFICATION_POSITION_FLOW_CLASS)
            hook(positionFlowClass.getDeclaredMethod("invokeSuspend", Any::class.java))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("lockscreen-notification-fod-position")
                .intercept { chain ->
                    if (notificationFodMode(preferences) != FOD_MODE_DEFAULT) {
                        runCatching {
                            val values = positionFlowClass
                                .getDeclaredField("L\u00241")
                                .apply { isAccessible = true }
                                .get(chain.thisObject) as? Array<Any?>
                            if (values != null && values.size > FOD_FLOW_HAS_ENROLLED_INDEX) {
                                // Change only this layout flow input so it selects the standard
                                // notification position and still emits a valid result.
                                values[FOD_FLOW_HAS_ENROLLED_INDEX] = false
                            }
                        }.onFailure { error ->
                            log(Log.ERROR, TAG, "Could not override lockscreen FOD position", error)
                        }
                    }
                    chain.proceed()
                }

            log(Log.INFO, TAG, "Installed lockscreen notification FOD-position hooks")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install lockscreen notification UDFPS-space hook", error)
        }
    }

    private fun installFingerprintIconVisualHook(
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ) {
        runCatching {
            val iconClass = classLoader.loadClass(MIUI_GXZW_ICON_VIEW_CLASS)
            val dismissIcon = iconClass.getMethod(FOD_DISMISS_ICON_METHOD)
            val displayMethods = iconClass.declaredMethods.filter { method ->
                (method.name == "show" && method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))) ||
                    (method.name == "showFingerprintIcon" && method.parameterCount == 0) ||
                    // A locked-again keyguard reuses the existing FOD window and only makes
                    // its animation surface opaque through this method.
                    (method.name == "setGxzwIconOpaque" && method.parameterCount == 0)
            }
            check(displayMethods.isNotEmpty()) { "MiuiGxzwIconView display methods were not found" }
            displayMethods.forEachIndexed { index, method ->
                hook(method)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .setId("lockscreen-fod-icon-transparent-$index")
                    .intercept { chain ->
                        val result = chain.proceed()
                        if (notificationFodMode(preferences) == FOD_MODE_HIDE_ICON) {
                            // The platform method only clears the animation/icon surface. The
                            // FOD view remains attached and continues receiving touch events.
                            dismissIcon.invoke(chain.thisObject)
                        }
                        result
                    }
            }
            log(Log.INFO, TAG, "Installed ${displayMethods.size} lockscreen FOD icon hook(s)")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install lockscreen FOD icon hook", error)
        }
    }

    private fun installSystemUiDepthHooks(classLoader: ClassLoader, preferences: SharedPreferences) {
        runCatching {
            val thresholdClass = classLoader.loadClass(DEPTH_THRESHOLD_CLASS)
            val evaluatorClass = classLoader.loadClass(DEPTH_EVALUATOR_CLASS)
            val constructor = thresholdClass.getDeclaredConstructor(Double::class.javaPrimitiveType)
            hook(constructor)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("systemui-depth-image-threshold")
                .intercept { chain ->
                    val original = chain.getArg(0) as? Double
                    if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false) &&
                        original == DEFAULT_DEPTH_IMAGE_THRESHOLD
                    ) {
                        chain.proceed(arrayOf(UNLIMITED_DEPTH_IMAGE_THRESHOLD))
                    } else {
                        chain.proceed()
                    }
                }

            val interactorClass = classLoader.loadClass(KEYGUARD_DEPTH_INTERACTOR_CLASS)
            hook(interactorClass.getMethod("updateAvoidStatus"))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("systemui-depth-time-overlap")
                .intercept { chain ->
                    if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false)) {
                        clearDepthAvoidState(chain.thisObject, interactorClass)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            val alphaMethod = interactorClass.declaredMethods.firstOrNull {
                it.name == "setDepthTransitionAlpha" && it.parameterCount == 3
            } ?: error("setDepthTransitionAlpha not found")
            hook(alphaMethod)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("systemui-depth-alpha")
                .intercept { chain ->
                    if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false)) {
                        clearDepthAvoidState(chain.thisObject, interactorClass)
                    }
                    chain.proceed()
                }
            installDepthDisplayStateHook(classLoader, preferences)
            forceDepthImageThreshold(evaluatorClass, thresholdClass, preferences)
            log(Log.INFO, TAG, "Installed SystemUI lockscreen depth hooks")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install SystemUI lockscreen depth hooks", error)
        }
    }

    private fun forceDepthImageThreshold(
        evaluatorClass: Class<*>,
        thresholdClass: Class<*>,
        preferences: SharedPreferences,
    ) {
        if (!preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false)) return
        runCatching {
            val threshold = evaluatorClass.getDeclaredField(IMAGE_THRESHOLD_FIELD).get(null)
            thresholdClass.getDeclaredField(THRESHOLD_RATE_FIELD)
                .apply { isAccessible = true }
                .setDouble(threshold, UNLIMITED_DEPTH_IMAGE_THRESHOLD)
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not set SystemUI depth image threshold", error)
        }
    }

    private fun installDepthDisplayStateHook(classLoader: ClassLoader, preferences: SharedPreferences) {
        val panelClass = classLoader.loadClass(KEYGUARD_PANEL_VIEW_CONTROLLER_CLASS)
        val depthEnabled = panelClass.getDeclaredField("depthEffectEnable").apply { isAccessible = true }
        val interactor = panelClass.getDeclaredField("keyguardDepthInteractor").apply { isAccessible = true }
        val actualDisplayDepth = interactor.type.getDeclaredField("isActualDisplayDepth")
            .apply { isAccessible = true }
        val updateElements = panelClass.getMethod("updateKeyguardElementsVisibility")
        hook(panelClass.getMethod("updateShowDepthState"))
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("systemui-depth-display-state")
            .intercept { chain ->
                val result = chain.proceed()
                if (preferences.getBoolean(KEY_REMOVE_DEPTH_IMAGE_LIMIT, false) &&
                    depthEnabled.getBoolean(chain.thisObject)
                ) {
                    val depthInteractor = interactor.get(chain.thisObject)
                    if (!actualDisplayDepth.getBoolean(depthInteractor)) {
                        actualDisplayDepth.setBoolean(depthInteractor, true)
                        updateElements.invoke(chain.thisObject)
                    }
                }
                result
            }
    }

    private fun clearDepthAvoidState(instance: Any?, interactorClass: Class<*>) {
        runCatching {
            val state = interactorClass.getDeclaredField("_avoidState")
                .apply { isAccessible = true }
                .get(instance)
            val update = state.javaClass.methods.firstOrNull {
                it.name == "updateState\u00241" && it.parameterCount == 2
            } ?: error("StateFlow update method not found")
            update.invoke(state, null, false)
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not clear lockscreen depth avoid state", error)
        }
    }

    private fun notificationFodMode(preferences: SharedPreferences): Int = preferences.getInt(
        KEY_NOTIFICATION_FOD_MODE,
        if (preferences.getBoolean(KEY_NOTIFICATIONS_IGNORE_FOD, false)) FOD_MODE_KEEP_ICON else FOD_MODE_DEFAULT,
    ).coerceIn(FOD_MODE_DEFAULT, FOD_MODE_KEEP_ICON)

    private fun installLockscreenChargingTextHook(classLoader: ClassLoader, preferences: SharedPreferences) {
        runCatching {
            val controllerClass = classLoader.loadClass(KEYGUARD_INDICATION_CONTROLLER_CLASS)
            val rotateField = controllerClass.getDeclaredField("mRotateTextViewController")
                .apply { isAccessible = true }
            hook(controllerClass.getMethod("updateDeviceEntryIndication", Boolean::class.javaPrimitiveType))
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("lockscreen-hide-charging-text")
                .intercept { chain ->
                    val result = chain.proceed()
                    if (preferences.getBoolean(KEY_HIDE_LOCKSCREEN_CHARGING_TEXT, false)) {
                        runCatching {
                            val controller = rotateField.get(chain.thisObject) ?: return@runCatching
                            controller.javaClass.getMethod("hideIndication", Int::class.javaPrimitiveType)
                                .invoke(controller, CHARGING_INDICATION_TYPE)
                        }.onFailure { error ->
                            log(Log.ERROR, TAG, "Could not hide lockscreen charging text", error)
                        }
                    }
                    result
                }
            log(Log.INFO, TAG, "Installed lockscreen charging-text hook")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install lockscreen charging-text hook", error)
        }
    }

    private fun installDimensionHooks(preferences: SharedPreferences) {
        hook(Resources::class.java.getMethod("getDimension", Int::class.javaPrimitiveType))
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("resource-dimension")
            .intercept { chain ->
                replaceDimensionIfNeeded(
                    chain.thisObject as Resources,
                    chain.getArg(0) as Int,
                    chain.proceed() as Float,
                    preferences,
                )
            }

        hook(Resources::class.java.getMethod("getDimensionPixelSize", Int::class.javaPrimitiveType))
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("resource-dimension-pixel-size")
            .intercept { chain ->
                replaceDimensionPixelIfNeeded(
                    chain.thisObject as Resources,
                    chain.getArg(0) as Int,
                    chain.proceed() as Int,
                    preferences,
                )
            }

        hook(Resources::class.java.getMethod("getDimensionPixelOffset", Int::class.javaPrimitiveType))
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("resource-dimension-pixel-offset")
            .intercept { chain ->
                replaceDimensionPixelIfNeeded(
                    chain.thisObject as Resources,
                    chain.getArg(0) as Int,
                    chain.proceed() as Int,
                    preferences,
                )
            }
    }

    private fun installCornerRadiusHooks(preferences: SharedPreferences) {
        // MIUI loads its Control Center implementation through a plugin class loader.
        // Discover target classes at the point that loader resolves them.
        hook(ClassLoader::class.java.getMethod("loadClass", String::class.java))
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("control-center-class-discovery")
            .intercept { chain ->
                val loadedClass = chain.proceed() as? Class<*> ?: return@intercept null
                installLoadedCornerRadiusHook(loadedClass, preferences)
                loadedClass
            }

        hook(View::class.java.getMethod("setBackground", Drawable::class.java))
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .setId("slider-background-radius")
            .intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View
                if (preferences.getBoolean(KEY_SLIDER_RADIUS_ENABLED, false) && isControlCenterSliderPart(view)) {
                    val radius = dpToPixels(view, preferences.getFloat(KEY_SLIDER_RADIUS, DEFAULT_CORNER_RADIUS))
                    (view.background as? GradientDrawable)?.mutate()?.let { drawable ->
                        GradientDrawable::class.java
                            .getMethod("setCornerRadius", Float::class.javaPrimitiveType)
                            .invoke(drawable, radius)
                    }
                }
                result
            }
    }

    private fun installLoadedCornerRadiusHook(
        targetClass: Class<*>,
        preferences: SharedPreferences,
    ) {
        if (!cornerTargetClasses.add(targetClass)) return
        when (targetClass.name) {
            TOP_BUTTONS_CLASS -> hookRadiusSetter(
                targetClass,
                "setCornerRadius",
                KEY_TOP_BUTTONS_RADIUS_ENABLED,
                KEY_TOP_BUTTONS_RADIUS,
                preferences,
            )
            MEDIA_PANEL_CLASS -> hookRadiusSetter(
                targetClass,
                "setCornerRadius",
                KEY_MEDIA_CARD_RADIUS_ENABLED,
                KEY_MEDIA_CARD_RADIUS,
                preferences,
            )
            DEVICE_CENTER_ENTRY -> hookDeviceCenterOutline(targetClass, preferences)
            MI_BACKGROUND_STYLE_CLASS -> hookMaterialStyle(targetClass, preferences)
            else -> cornerTargetClasses.remove(targetClass)
        }
    }

    private fun hookRadiusSetter(
        targetClass: Class<*>,
        methodName: String,
        enabledKey: String,
        radiusKey: String,
        preferences: SharedPreferences,
        applyOutline: Boolean = targetClass.name == TOP_BUTTONS_CLASS,
    ) {
        runCatching {
            val method = targetClass.getMethod(methodName, Float::class.javaPrimitiveType)
            hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("radius:${targetClass.name}#$methodName")
                .intercept { chain ->
                    val view = chain.thisObject as? View
                    if (view == null || !preferences.getBoolean(enabledKey, false)) {
                        chain.proceed()
                    } else {
                        val radius = dpToPixels(view, preferences.getFloat(radiusKey, DEFAULT_CORNER_RADIUS))
                        val result = chain.proceedWith(
                            view,
                            arrayOf(radius),
                        )
                        if (applyOutline) applyRoundedOutline(view, radius)
                        result
                    }
                }
            log(Log.INFO, TAG, "Installed corner-radius hook: ${targetClass.name}#$methodName")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install corner-radius hook: ${targetClass.name}#$methodName", error)
        }
    }

    private fun hookMaterialStyle(targetClass: Class<*>, preferences: SharedPreferences) {
        targetClass.declaredMethods
            .filter { method ->
                method.name == "setMiBackgroundStyle" &&
                    method.parameterTypes.firstOrNull() == View::class.java
            }
            .forEach { method ->
                runCatching {
                    hook(method)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .setId("material-style:${method.parameterCount}")
                        .intercept { chain ->
                            val result = chain.proceed()
                            val view = chain.getArg(0) as? View ?: return@intercept result
                            when {
                                view.javaClass.name == TOP_BUTTONS_CLASS &&
                                    preferences.getBoolean(KEY_TOP_BUTTONS_RADIUS_ENABLED, false) ->
                                    applyRoundedOutline(
                                        view,
                                        dpToPixels(view, preferences.getFloat(KEY_TOP_BUTTONS_RADIUS, DEFAULT_CORNER_RADIUS)),
                                    )
                            }
                            result
                        }
                }
            }
    }

    private fun hookDeviceCenterOutline(targetClass: Class<*>, preferences: SharedPreferences) {
        runCatching {
            val method = targetClass.getMethod("onFinishInflate")
            hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setId("radius:$DEVICE_CENTER_ENTRY#onFinishInflate")
                .intercept { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View ?: return@intercept result
                    if (preferences.getBoolean(KEY_DEVICE_CENTER_RADIUS_ENABLED, false)) {
                        val radius = dpToPixels(view, preferences.getFloat(KEY_DEVICE_CENTER_RADIUS, DEFAULT_CORNER_RADIUS))
                        applyRoundedOutline(view, radius)
                    }
                    result
                }
            log(Log.INFO, TAG, "Installed corner-radius hook: $DEVICE_CENTER_ENTRY#onFinishInflate")
        }.onFailure { error ->
            log(Log.ERROR, TAG, "Could not install corner-radius hook: $DEVICE_CENTER_ENTRY", error)
        }
    }

    private fun replaceDimensionIfNeeded(
        resources: Resources,
        resourceId: Int,
        original: Float,
        preferences: SharedPreferences,
    ): Float = replacementDp(resources.entryName(resourceId), preferences)
        ?.let { it * resources.displayMetrics.density }
        ?: original

    private fun replaceDimensionPixelIfNeeded(
        resources: Resources,
        resourceId: Int,
        original: Int,
        preferences: SharedPreferences,
    ): Int = replacementDp(resources.entryName(resourceId), preferences)
        ?.let { (it * resources.displayMetrics.density + 0.5f).toInt() }
        ?: original

    private fun replacementDp(name: String?, preferences: SharedPreferences): Float? = when (name) {
        "big_island_min_width" -> preferences.takeIf { it.getBoolean(KEY_ISLAND_ENABLED, false) }
            ?.getInt(KEY_ISLAND_WIDTH, 108)?.coerceIn(108, 190)?.toFloat()
        "status_bar_clock_size_new" -> preferences.takeIf { it.getBoolean(KEY_CLOCK_ENABLED, false) }
            ?.getFloat(KEY_CLOCK_SIZE, 14.8f)?.coerceIn(10f, 24f)
        "status_bar_padding_end" -> preferences.takeIf { it.getBoolean(KEY_PADDING_END_ENABLED, false) }
            ?.getFloat(KEY_PADDING_END, 6f)?.coerceIn(0f, 32f)
        "status_bar_padding_start" -> preferences.takeIf { it.getBoolean(KEY_PADDING_START_ENABLED, false) }
            ?.getFloat(KEY_PADDING_START, 12.5f)?.coerceIn(0f, 32f)
        "status_bar_height" -> preferences.takeIf { it.getBoolean(KEY_HEIGHT_ENABLED, false) }
            ?.getInt(KEY_STATUS_BAR_HEIGHT, 40)?.coerceIn(24, 72)?.toFloat()
        "status_bar_padding_top" -> preferences.takeIf { it.getBoolean(KEY_PADDING_TOP_ENABLED, false) }
            ?.getFloat(KEY_PADDING_TOP, 15f)?.coerceIn(0f, 32f)
        else -> null
    }

    private fun Resources.entryName(resourceId: Int): String? = runCatching {
        getResourceEntryName(resourceId)
    }.getOrNull()

    private fun dpToPixels(view: View, value: Float): Float =
        value.coerceIn(0f, 60f) * view.resources.displayMetrics.density

    private fun isControlCenterSliderPart(view: View): Boolean {
        val idName = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        return idName in SLIDER_PART_IDS && generateSequence(view.parent) { it.parent }
            .filterIsInstance<View>()
            .any { parent -> parent.javaClass.name.contains("ToggleSlider") }
    }

    private fun applyRoundedOutline(view: View, radius: Float) {
        val appliedByMiui = runCatching {
            val helper = Class.forName("miui.systemui.util.MiBlurCompat", false, view.javaClass.classLoader)
            helper.getMethod("setBlurOutlineRoundRect", View::class.java, Float::class.javaPrimitiveType)
                .invoke(null, view, radius)
        }.isSuccess
        if (appliedByMiui) return

        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(target: View, outline: Outline) {
                outline.setRoundRect(0, 0, target.width, target.height, radius)
            }
        }
        view.invalidateOutline()
    }

    companion object {
        private const val TAG = "HyperSystemUIHook"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val SYSTEM_UI_PLUGIN = "miui.systemui.plugin"
        private const val AOD = "com.miui.aod"
        private const val DEPTH_EVALUATOR_CLASS =
            "com.miui.clock.utils.avoid.DepthAvoidEvaluator"
        private const val DEPTH_THRESHOLD_CLASS =
            "com.miui.clock.utils.avoid.DepthAvoidEvaluator\$Threshold"
        private const val HIERARCHY_AVOID_CONTROLLER_CLASS =
            "com.miui.keyguard.editor.utils.HierarchyImageAvoidController"
        private const val USER_OPEN_HIERARCHY_FIELD = "isUserOpenHierarchy"
        private const val FOD_SHELF_SPACE_FLOW_CLASS =
            "com.android.systemui.statusbar.notification.stack.domain.interactor.SharedNotificationContainerInteractor\$useExtraShelfSpace\$1"
        private const val FOD_NOTIFICATION_POSITION_FLOW_CLASS =
            "com.android.keyguard.panel.KeyguardPanelViewController\$nsslLockYPosition_delegate\$lambda\$106\$\$inlined\$combine\$1\$3"
        private const val MIUI_GXZW_ICON_VIEW_CLASS =
            "com.miui.keyguard.biometrics.fod.MiuiGxzwIconView"
        private const val FOD_DISMISS_ICON_METHOD = "dismissFingerpirntIcon"
        private const val KEYGUARD_DEPTH_INTERACTOR_CLASS =
            "com.android.keyguard.depth.KeyguardDepthInteractor"
        private const val KEYGUARD_PANEL_VIEW_CONTROLLER_CLASS =
            "com.android.keyguard.panel.KeyguardPanelViewController"
        private const val KEYGUARD_INDICATION_CONTROLLER_CLASS =
            "com.android.systemui.statusbar.KeyguardIndicationController"
        private const val MIUI_SHORTCUT_CONTROLLER_CLASS =
            "com.android.keyguard.shortcut.MiuiShortcutController"
        private const val MI_GLASS_COMPAT_CLASS = "com.miui.systemui.util.MiGlassCompat"
        private const val CHARGING_INDICATION_TYPE = 3
        private const val IMAGE_THRESHOLD_FIELD = "IMAGE_THRESHOLD"
        private const val THRESHOLD_RATE_FIELD = "rate"
        private const val DEFAULT_DEPTH_IMAGE_THRESHOLD = 0.2
        private const val UNLIMITED_DEPTH_IMAGE_THRESHOLD = 1.0
        private const val FOD_FLOW_HAS_ENROLLED_INDEX = 6
        private const val FOD_MODE_DEFAULT = 0
        private const val FOD_MODE_HIDE_ICON = 1
        private const val FOD_MODE_KEEP_ICON = 2
        private const val TOP_BUTTONS_CLASS =
            "miui.systemui.controlcenter.qs.tileview.QSCardItemView"
        private const val NOTIFICATION_BACKGROUND_VIEW_CLASS =
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView"
        private const val NOTIFICATION_ROW_GLASS_EFFECT_CLASS =
            "com.android.systemui.statusbar.notification.style.vieweffect.NotificationRowGlassEffect"
        private const val MEDIA_PANEL_CLASS =
            "miui.systemui.controlcenter.panel.main.media.MediaPlayerPanel"
        private const val SLIDER_VIEW_HOLDER_CLASS =
            "miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder"
        private const val MI_BACKGROUND_STYLE_CLASS = "miui.systemui.util.MiBackgroundStyle"
        private const val MI_BLUR_COMPAT_CLASS = "miui.systemui.util.MiBlurCompat"
        private const val MIUI_BLUR_UTILS_CLASS = "miuix.core.util.MiuiBlurUtils"
        private const val DYNAMIC_ISLAND_BACKGROUND_CLASS = "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
        private const val PLUGIN_NOTIFICATION_SETTINGS_MANAGER_CLASS =
            "miui.systemui.notification.NotificationSettingsManager"
        private const val SYSTEM_UI_NOTIFICATION_SETTINGS_MANAGER_CLASS =
            "com.miui.systemui.notification.NotificationSettingsManager"
        private const val FOCUS_NOTIFICATION_UTILS_CLASS =
            "miui.systemui.notification.focus.FocusNotifUtils"
        private const val DYNAMIC_ISLAND_EVENT_COORDINATOR_CLASS =
            "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
        private const val ISLAND_STATE_CALLBACK_CONTROLLER_CLASS =
            "miui.systemui.dynamicisland.event.IslandStateCallbackController"
        private const val DYNAMIC_ISLAND_SOURCE_PACKAGE_KEY = "miui.source.pkg"
        private val DYNAMIC_ISLAND_LAYOUT_CLASSES = listOf(
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView",
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView",
        )
        private val SLIDER_PART_IDS = setOf("progress_bg", "progress")
        private const val DEVICE_CENTER_ENTRY =
            "miui.systemui.controlcenter.panel.main.devicecenter.entry.DeviceCenterEntryFrameLayout"
        private const val DEFAULT_CORNER_RADIUS = 24f
        private const val MIN_GLASS_PARAMS_SIZE = 36
        private const val GLASS_TINT_RED_INDEX = 11
        private const val GLASS_TINT_GREEN_INDEX = 12
        private const val GLASS_TINT_BLUE_INDEX = 13
        private const val GLASS_ALPHA_INDEX = 14
        private const val MAX_GLASS_BLUR_RADIUS = 500
        private const val DEFAULT_SHORTCUT_GLASS_RADIUS = 48f
        private const val MIN_SHORTCUT_GLASS_RADIUS = 28f
        private const val MAX_SHORTCUT_GLASS_RADIUS = 80f
        private const val SHORTCUT_GLASS_MATERIAL_TYPE = 1
        private const val SHORTCUT_GLASS_BLUR_MODE = 1
        private const val SHORTCUT_GLASS_BLEND_MODE = 101
        private const val SHORTCUT_PURE_COLOR = 0x73FFFFFF
        private const val SHORTCUT_ICON_LIGHT_COLOR = Color.WHITE
        private const val SHORTCUT_ICON_DARK_COLOR = Color.BLACK
        private const val DEFAULT_ADVANCED_MATERIAL_COLOR = 0xFFFFFFFF.toInt()
        private const val DEFAULT_SOFT_GLASS_COLOR = 0xFFFFFFFF.toInt()
        private const val MAX_SHORTCUT_OPACITY = 50
        private const val MAX_SHORTCUT_BACKDROP_BLUR_RADIUS = 120
        private const val MAX_SHORTCUT_GLASS_BLUR_RADIUS = 100
        private const val MAX_SHORTCUT_GLASS_LARGE_BLUR_RADIUS = 500
        private const val MAX_SHORTCUT_GLASS_LUMINANCE = 0.4f
        private const val DEFAULT_ADVANCED_MATERIAL_OPACITY = 14
        private const val DEFAULT_ADVANCED_MATERIAL_BLUR_RADIUS = 80
        private const val DEFAULT_SOFT_GLASS_OPACITY = 10
        private const val DEFAULT_SOFT_GLASS_BACKDROP_BLUR_RADIUS = 80
        private const val DEFAULT_SOFT_GLASS_BLUR_RADIUS = 36
        private const val DEFAULT_SOFT_GLASS_LUMINANCE = 0.14f
        private const val GLASS_LUMINANCE_AMOUNT_INDEX = 4
        private const val SHORTCUT_BACKGROUND_NONE = 0
        private const val SHORTCUT_BACKGROUND_PURE_COLOR = 1
        private const val SHORTCUT_BACKGROUND_ADVANCED_MATERIAL = 2
        private const val SHORTCUT_BACKGROUND_SOFT_GLASS = 3
        private const val SHORTCUT_ICON_COLOR_AUTO = 0
        private const val SHORTCUT_ICON_COLOR_LIGHT = 1
        private const val SHORTCUT_ICON_COLOR_DARK = 2
        private const val SHORTCUT_GLASS_TAG = "os4changer.lockscreen.shortcut.glass"
        private val LOCKSCREEN_SHORTCUT_CONTAINER_IDS = setOf(
            "shortcut_view_left_layout",
            "shortcut_view_right_layout",
        )
        private val SHORTCUT_GLASS_PARAMETERS = floatArrayOf(
            0.67f, 0.16f, 0.09f, 0f, 0.24f, 1.4f, -0.02f, 0.3f, 0.6f, 1f,
            0.03f, 1f, 1f, 1f, 0.1f, 0.2f, 0.3f, 1f, 1f, 72f, 3.8f, 80f, 800f,
            1.2f, 1f, -0.4f, 0.6f, -0.8f, 1.4f, 0.7f, 0.8f, 1.15f, 4f, 2f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        )
        private val SHORTCUT_BLOOM_STROKE_PARAMETERS = floatArrayOf(
            3f, 180f, 1f, 1f, 1f, 0.05f, 8f, 0.5f, 0.5f, -0.5f, 1f,
            1f, 1f, 0.6f, 0.5f, 0.95f, -0.5f, 1f, 1f, 1f, 0.35f,
        )

        private const val KEY_ISLAND_ENABLED = "island_enabled"
        private const val KEY_ISLAND_WIDTH = "island_width"
        private const val KEY_REMOVE_FOCUS_AND_ISLAND_WHITELIST_LIMIT =
            "remove_focus_and_island_whitelist_limit"
        private const val KEY_EXPANDED_ISLAND_BACKGROUND_ENABLED = "expanded_island_background_enabled"
        private const val KEY_EXPANDED_ISLAND_BACKGROUND_OPACITY = "expanded_island_background_opacity"
        private const val KEY_EXPANDED_ISLAND_GLASS_BLUR_RADIUS = "expanded_island_glass_blur_radius"
        private const val KEY_EXPANDED_ISLAND_GLASS_LARGE_BLUR_RADIUS = "expanded_island_glass_large_blur_radius"
        private const val KEY_EXPANDED_ISLAND_SELF_BLUR_RADIUS = "expanded_island_self_blur_radius"
        private const val KEY_EXPANDED_ISLAND_SHOW_HIGHLIGHT = "expanded_island_show_highlight"
        private const val KEY_NOTIFICATION_CONTEXT_UNIFIED = "notification_context_unified"
        private const val KEY_NOTIFICATION_ELEMENTS_MATERIAL = "shade_notification_elements_material_v2"
        private const val KEY_CONTROL_CENTER_ELEMENTS_MATERIAL = "shade_control_center_elements_material_v2"
        private const val KEY_NOTIFICATION_CENTER_BACKGROUND_MATERIAL = "shade_notification_center_background_material_v2"
        private const val KEY_CONTROL_CENTER_BACKGROUND_MATERIAL = "shade_control_center_background_material_v2"
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
        private const val KEY_LOCKSCREEN_SHORTCUT_GLASS_ENABLED = "lockscreen_shortcut_glass_enabled"
        private const val KEY_LOCKSCREEN_SHORTCUT_BACKGROUND_MODE = "lockscreen_shortcut_background_mode"
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

        private var resourceHooksInstalled = false
        private var cornerHooksInstalled = false
        private var depthEffectHookInstalled = false
        private var lockscreenNotificationHookInstalled = false
        private var fingerprintIconHookInstalled = false
        private var systemUiDepthHookInstalled = false
        private var lockscreenChargingHookInstalled = false
        private var lockscreenShortcutGlassHookInstalled = false
        private var shadeMaterialHooksInstalled = false
        private var dynamicIslandHooksInstalled = false
        private var dynamicIslandClassDiscoveryInstalled = false
        private var focusIslandWhitelistSystemUiHooksInstalled = false
        private var focusIslandWhitelistPluginHooksInstalled = false
        private val notificationGlassApplying = ThreadLocal<Boolean>()
        private val controlCenterMaterialHits = Collections.synchronizedSet(mutableSetOf<String>())
        private val expandedIslandMaterialSettings =
            Collections.synchronizedMap(WeakHashMap<View, Int>())
        private val cornerTargetClasses = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    }
}
