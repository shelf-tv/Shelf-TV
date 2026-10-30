package com.hayatayubi.shelf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.hayatayubi.shelf.AppEntry
import com.hayatayubi.shelf.Backdrop
import com.hayatayubi.shelf.HomeButtonService
import com.hayatayubi.shelf.ShelfViewModel
import kotlinx.coroutines.launch

const val COLUMNS = 5
val SidePadding = 48.dp
private val Gap = 20.dp
private val RowTop = 16.dp
private val RowBottom = 38.dp

/** How much of the previous row stays visible above the focused one once the shelf scrolls away. */
private val PreviousRowPeek = 92.dp

private sealed interface Tile {
    val key: String
}

private data class AppTileItem(val app: AppEntry) : Tile {
    override val key get() = app.packageName
}

private data object SettingsTileItem : Tile {
    override val key = "shelf:settings"
}

private sealed interface Menu
private data class AppMenu(val app: AppEntry) : Menu
private data object SettingsMenu : Menu

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(vm: ShelfViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val apps = vm.apps
    val tiles = remember(apps) { apps.map(::AppTileItem) + SettingsTileItem }
    val rows = remember(tiles) { tiles.chunked(COLUMNS) }
    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }

    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedRow by remember { mutableIntStateOf(0) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    // A tile that changed rows and should take focus again once it's recomposed there.
    var followKey by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var didInitialFocus by remember { mutableStateOf(false) }

    fun focus(key: String?) {
        if (key != null) runCatching { requester(key).requestFocus() }
    }
    fun focusFirst() = focus(tiles.first().key)

    // The first row may have scrolled out of composition, so bring it back before focusing it.
    suspend fun returnToTop() {
        listState.scrollToItem(0)
        withFrameNanos { }
        focusFirst()
    }

    val idleBackdrop = remember { Backdrop.idle.asImageBitmap() }
    val heroApp = if (focusedRow == 0) apps.firstOrNull { it.packageName == focusedKey } else null
    val dim by animateFloatAsState(if (focusedRow == 0) 0f else 1f, tween(500), label = "dim")

    LaunchedEffect(tiles, vm.loaded) {
        if (!vm.loaded) return@LaunchedEffect
        withFrameNanos { }
        when {
            !didInitialFocus -> {
                focusFirst()
                didInitialFocus = true
            }
            movingKey != null || followKey != null -> {
                focus(movingKey ?: followKey)
                followKey = null
            }
            tiles.none { it.key == focusedKey } -> returnToTop()
        }
    }

    LaunchedEffect(vm.homePresses) {
        if (vm.homePresses == 0) return@LaunchedEffect
        movingKey = null
        menu = null
        returnToTop()
    }

    val scope = rememberCoroutineScope()
    BackHandler(enabled = movingKey == null) { scope.launch { returnToTop() } }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            HeroBackground(backdrop = heroApp?.backdrop ?: idleBackdrop, dim = { dim })

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val tileWidth = (maxWidth - SidePadding * 2 - Gap * (COLUMNS - 1)) / COLUMNS
                val tileHeight = tileWidth * 9f / 16f
                val shelfHeight = maxHeight * 0.6f
                val rowHeightPx = with(density) { (RowTop + tileHeight + RowBottom).roundToPx() }
                val peekPx = with(density) { PreviousRowPeek.roundToPx() }

                // tvOS-style paging: the top row sits under the Top Shelf; any lower row
                // scrolls the shelf away and parks itself just below the previous row.
                LaunchedEffect(focusedRow) {
                    if (focusedRow == 0) {
                        listState.animateScrollToItem(0)
                    } else {
                        listState.animateScrollToItem(index = focusedRow, scrollOffset = rowHeightPx - peekPx)
                    }
                }

                CompositionLocalProvider(LocalBringIntoViewSpec provides ManualScroll) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = maxHeight * 0.5f),
                    ) {
                        item(key = "shelf") {
                            ShelfHero(
                                app = heroApp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(shelfHeight)
                                    .graphicsLayer { alpha = 1f - dim; compositingStrategy = CompositingStrategy.ModulateAlpha },
                            )
                        }
                        itemsIndexed(rows, key = { index, _ -> "row$index" }) { rowIndex, row ->
                            Row(
                                Modifier.padding(start = SidePadding, end = SidePadding, top = RowTop, bottom = RowBottom),
                                horizontalArrangement = Arrangement.spacedBy(Gap),
                            ) {
                                row.forEach { tile ->
                                    key(tile.key) {
                                        val onFocused = {
                                            focusedKey = tile.key
                                            focusedRow = rowIndex
                                        }
                                        when (tile) {
                                            is AppTileItem -> AppTile(
                                                app = tile.app,
                                                width = tileWidth,
                                                height = tileHeight,
                                                moving = movingKey == tile.key,
                                                focusRequester = requester(tile.key),
                                                onFocused = onFocused,
                                                onClick = { launch(context, tile.app) },
                                                onLongClick = { menu = AppMenu(tile.app) },
                                                onMove = { by -> vm.move(tile.key, by) },
                                                onMoveDone = { movingKey = null },
                                            )
                                            SettingsTileItem -> SettingsTile(
                                                width = tileWidth,
                                                height = tileHeight,
                                                focusRequester = requester(tile.key),
                                                onFocused = onFocused,
                                                onClick = { start(context, Intent(Settings.ACTION_SETTINGS)) },
                                                onLongClick = { menu = SettingsMenu },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Clock(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = SidePadding)
                    .graphicsLayer { alpha = 1f - dim; compositingStrategy = CompositingStrategy.ModulateAlpha },
            )

            AnimatedVisibility(
                visible = movingKey != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            ) {
                MoveHint()
            }
        }

        when (val m = menu) {
            is AppMenu -> {
                val pkg = m.app.packageName
                val index = apps.indexOfFirst { it.packageName == pkg }
                OptionsDialog(
                    title = m.app.label,
                    options = buildList {
                        add("Open" to { launch(context, m.app) })
                        add("Move" to { movingKey = pkg })
                        if (index >= COLUMNS) {
                            add("Move to Top Row" to {
                                followKey = pkg
                                vm.moveTo(pkg, 0)
                            })
                        }
                        add("Hide" to { vm.hide(pkg) })
                        add("App Info" to {
                            start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
                        })
                        add("Uninstall" to { start(context, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))) })
                    },
                    onDismiss = { menu = null },
                )
            }
            SettingsMenu -> OptionsDialog(
                title = "Shelf",
                options = buildList {
                    if (!HomeButtonService.isEnabled(context)) {
                        add("Set Up Home Button" to { start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
                    }
                    add("Open Settings" to { start(context, Intent(Settings.ACTION_SETTINGS)) })
                    if (vm.hiddenCount > 0) add("Show ${vm.hiddenCount} Hidden App${if (vm.hiddenCount == 1) "" else "s"}" to vm::unhideAll)
                    add("Reset Layout" to vm::resetLayout)
                },
                onDismiss = { menu = null },
            )
            null -> Unit
        }
    }
}

/** Focus changes never auto-scroll; [ShelfScreen] pages rows itself. */
@OptIn(ExperimentalFoundationApi::class)
private object ManualScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

private fun launch(context: Context, app: AppEntry) {
    if (!start(context, app.launchIntent)) {
        Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

private fun start(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
