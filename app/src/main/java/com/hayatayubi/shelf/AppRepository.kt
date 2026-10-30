package com.hayatayubi.shelf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Colours pulled from an app's artwork, used to tint the Top Shelf behind it. */
data class AppColors(val glow: Color, val deep: Color, val tile: Color)

val IdleColors = AppColors(
    glow = Color(0xFF5B6CFF),
    deep = Color(0xFF1B1F3B),
    tile = Color(0xFF2A2D40),
)

data class AppEntry(
    val packageName: String,
    val label: String,
    val launchIntent: Intent,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val colors: AppColors,
    /** Pre-rendered Top Shelf background; see [Backdrop]. */
    val backdrop: ImageBitmap,
)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("shelf", Context.MODE_PRIVATE)
    private val lock = Mutex()

    // Decoded artwork keyed by package, invalidated when the app updates.
    private val cache = HashMap<String, Pair<Long, AppEntry>>()

    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        lock.withLock {
            val hidden = hiddenPackages()
            // Leanback (TV) entry points win over phone-style ones for the same package.
            val found = LinkedHashMap<String, Pair<ResolveInfo, String>>()
            for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val query = Intent(Intent.ACTION_MAIN).addCategory(category)
                for (info in pm.queryIntentActivities(query, 0)) {
                    found.putIfAbsent(info.activityInfo.packageName, info to category)
                }
            }
            found.keys.removeAll { it == context.packageName || it in hidden || it in SETTINGS_APPS }
            cache.keys.retainAll(found.keys)
            sortByUserOrder(found.map { (pkg, match) -> entryFor(pkg, match.first, match.second) })
        }
    }

    private fun entryFor(pkg: String, info: ResolveInfo, category: String): AppEntry {
        val updated = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
        cache[pkg]?.let { (stamp, entry) -> if (stamp == updated) return entry }

        val activity = info.activityInfo
        val banner = runCatching { activity.loadBanner(pm)?.toBitmap(BANNER_W, BANNER_H) }.getOrNull()
        val icon = if (banner == null) {
            runCatching { info.loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE) }.getOrNull()
        } else {
            null
        }
        val colors = colorsFrom(banner ?: icon)
        val entry = AppEntry(
            packageName = pkg,
            label = info.loadLabel(pm).toString(),
            launchIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(category)
                .setComponent(ComponentName(pkg, activity.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            banner = banner?.asImageBitmap(),
            icon = icon?.asImageBitmap(),
            colors = colors,
            backdrop = Backdrop.render(colors, banner).asImageBitmap(),
        )
        cache[pkg] = updated to entry
        return entry
    }

    private fun colorsFrom(bitmap: Bitmap?): AppColors {
        if (bitmap == null) return IdleColors
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val dominant = palette.getDominantColor(IdleColors.tile.toArgb())
        val glow = palette.getVibrantColor(palette.getLightVibrantColor(dominant))
        val deep = palette.getDarkVibrantColor(palette.getDarkMutedColor(dominant))
        return AppColors(
            glow = Color(glow),
            deep = Color(deep),
            tile = lerp(Color(dominant), Color.Black, 0.25f),
        )
    }

    private fun sortByUserOrder(apps: List<AppEntry>): List<AppEntry> {
        val order = prefs.getString(KEY_ORDER, null)?.split(',') ?: DEFAULT_ORDER
        val rank = order.withIndex().associate { (index, pkg) -> pkg to index }
        return apps.sortedWith(
            compareBy<AppEntry> { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() },
        )
    }

    fun saveOrder(packages: List<String>) = prefs.edit { putString(KEY_ORDER, packages.joinToString(",")) }

    fun resetOrder() = prefs.edit { remove(KEY_ORDER) }

    fun hiddenPackages(): Set<String> = prefs.getStringSet(KEY_HIDDEN, null) ?: emptySet()

    fun hide(pkg: String) = prefs.edit { putStringSet(KEY_HIDDEN, hiddenPackages() + pkg) }

    fun unhideAll() = prefs.edit { remove(KEY_HIDDEN) }

    private companion object {
        const val KEY_ORDER = "order"
        const val KEY_HIDDEN = "hidden"
        // Roughly the focused tile's size on a 1080p screen; no point uploading more texture than that.
        const val BANNER_W = 400
        const val BANNER_H = 225
        const val ICON_SIZE = 192

        // Shelf has its own Settings tile.
        val SETTINGS_APPS = setOf("com.android.tv.settings", "com.google.android.tv.settings")

        // First-run Top Row: the usual suspects, if installed. Everything else follows A–Z.
        val DEFAULT_ORDER = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "uk.co.bbc.iplayer",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android",
            "com.plexapp.android",
            "air.ITVMobilePlayer",
            "com.channel4.ondemand",
        )
    }
}
