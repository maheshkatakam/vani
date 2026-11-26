package com.maheshkatakam.vani.system

import android.Manifest
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.maheshkatakam.vani.R
import com.maheshkatakam.vani.access.VaniAccessibilityService

/** Status checks and shortcuts into Android settings for the setup screen. */
object SystemSetup {

    fun hasMic(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Is Vani the default digital assistant (what "hold power" launches on Pixel)? */
    fun isAssistant(ctx: Context): Boolean {
        val viaRole = runCatching {
            ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        }.getOrDefault(false)
        if (viaRole) return true
        val setting = runCatching { Settings.Secure.getString(ctx.contentResolver, "assistant") }.getOrNull()
        return setting?.startsWith(ctx.packageName + "/") == true
    }

    /** true / false if we can tell, null if Android doesn't let apps read this setting. */
    fun powerButtonOpensAssistant(ctx: Context): Boolean? = runCatching {
        Settings.Global.getInt(ctx.contentResolver, "power_button_long_press") == 5
    }.getOrNull()

    fun isAccessibilityOn(ctx: Context): Boolean {
        if (VaniAccessibilityService.instance != null) return true
        val enabled = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
    }

    fun openAssistantSettings(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openPowerButtonSettings(ctx: Context) = start(
        ctx,
        Intent("android.settings.POWER_MENU_SETTINGS"),
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$PowerMenuSettingsActivity"),
        Intent("android.settings.GESTURE_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openAccessibilitySettings(ctx: Context) {
        val component = ComponentName(ctx, VaniAccessibilityService::class.java).flattenToString()
        val details = if (Build.VERSION.SDK_INT >= 33) {
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        } else {
            null
        }
        start(ctx, *listOfNotNull(details, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)).toTypedArray())
    }

    fun openAppInfo(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    fun openUrl(ctx: Context, url: String) = start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** Android 13+: one-tap prompt to add Vani's Quick Settings tile. */
    fun requestTile(ctx: Context, onResult: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            onResult("Swipe down twice, tap the pencil (Edit), and drag the Vani tile in.")
            return
        }
        val sbm = ctx.getSystemService(StatusBarManager::class.java) ?: return
        sbm.requestAddTileService(
            ComponentName(ctx, VaniTileService::class.java),
            ctx.getString(R.string.tile_label),
            Icon.createWithResource(ctx, R.drawable.ic_tile),
            ctx.mainExecutor,
        ) { result ->
            onResult(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Vani tile added to Quick Settings."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "The Vani tile is already in Quick Settings."
                    else -> "Tile not added."
                }
            )
        }
    }

    private fun start(ctx: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
    }
}
