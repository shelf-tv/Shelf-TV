package com.hayatayubi.shelf.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hayatayubi.shelf.AppEntry

private val TileShape = RoundedCornerShape(14.dp)
private const val FocusedScale = 1.12f

@Composable
fun AppTile(
    app: AppEntry,
    width: Dp,
    height: Dp,
    moving: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
) {
    val wiggle = if (moving) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            initialValue = -1.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(130), RepeatMode.Reverse),
            label = "rotation",
        )
    } else {
        null
    }

    TileFrame(
        label = app.label,
        width = width,
        height = height,
        background = app.colors.tile,
        focusRequester = focusRequester,
        onFocused = onFocused,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .graphicsLayer { rotationZ = wiggle?.value ?: 0f }
            .onPreviewKeyEvent { event ->
                if (!moving) return@onPreviewKeyEvent false
                // While moving, the D-pad drags the tile instead of moving focus.
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> onMove(-1)
                        Key.DirectionRight -> onMove(1)
                        Key.DirectionUp -> onMove(-COLUMNS)
                        Key.DirectionDown -> onMove(COLUMNS)
                    }
                } else if (event.type == KeyEventType.KeyUp && event.key in DoneKeys) {
                    onMoveDone()
                }
                true
            },
    ) {
        when {
            app.banner != null -> Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            app.icon != null -> Image(app.icon, null, Modifier.align(Alignment.Center).fillMaxHeight(0.6f).aspectRatio(1f))
        }
    }
}

@Composable
fun SettingsTile(
    width: Dp,
    height: Dp,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    TileFrame(
        label = "Settings",
        width = width,
        height = height,
        background = Color.White.copy(alpha = 0.14f),
        focusRequester = focusRequester,
        onFocused = onFocused,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Image(
            Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(height * 0.42f),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

/** Shared tvOS-style tile: lifts, glows and catches the light when focused; label appears beneath. */
@Composable
private fun TileFrame(
    label: String,
    width: Dp,
    height: Dp,
    background: Color,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "label")

    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .size(width, height)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = ClickableSurfaceDefaults.shape(TileShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = background,
                focusedContainerColor = background,
                pressedContainerColor = background,
                contentColor = Color.White,
                focusedContentColor = Color.White,
                pressedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = FocusedScale, pressedScale = 1.04f),
        ) {
            content()
            // Specular sheen across the focused tile.
            if (focused) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.28f),
                            0.5f to Color.White.copy(alpha = 0.04f),
                            1f to Color.Transparent,
                        ),
                    ),
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 14.dp)
                .graphicsLayer { alpha = labelAlpha; compositingStrategy = CompositingStrategy.ModulateAlpha },
        )
    }
}

private val DoneKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back)
