package com.hayatayubi.shelf.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.hayatayubi.shelf.AppEntry
import kotlin.math.roundToInt

/**
 * The focused app's pre-rendered backdrop ([com.hayatayubi.shelf.Backdrop]).
 *
 * The crossfade and dimming are drawn straight onto one canvas: the new image fades in
 * over the old one, which is opaque, so the result matches a true crossfade. Fading
 * whole full-screen layers instead makes the GPU render each one off-screen first, and
 * the Chromecast can't afford that. Nothing animates while idle.
 */
@Composable
fun HeroBackground(backdrop: ImageBitmap, dim: () -> Float) {
    var current by remember { mutableStateOf(backdrop) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fadeIn = remember { Animatable(1f) }
    LaunchedEffect(backdrop) {
        if (backdrop === current) return@LaunchedEffect
        previous = current
        current = backdrop
        fadeIn.snapTo(0f)
        fadeIn.animateTo(1f, tween(400))
        previous = null
    }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
            val under = previous
            if (under != null) drawImage(under, dstSize = dst, filterQuality = FilterQuality.Low)
            drawImage(
                current,
                dstSize = dst,
                alpha = if (under == null) 1f else fadeIn.value,
                filterQuality = FilterQuality.Low,
            )
            val d = dim()
            if (d > 0f) drawRect(Color.Black.copy(alpha = 0.55f * d))
        },
    )
}

/** The Top Shelf: what the focused top-row app is, at a glance. */
@Composable
fun ShelfHero(app: AppEntry?, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = SidePadding, end = SidePadding, top = 72.dp, bottom = 20.dp)) {
        // Crossfade only the text block, so its fade layer stays small.
        Crossfade(
            targetState = app,
            animationSpec = tween(300),
            label = "hero",
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.6f),
        ) { current ->
            if (current == null) {
                IdleHero(Modifier)
            } else {
                Column {
                    Text(
                        text = current.label,
                        color = Color.White,
                        fontSize = 52.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Press OK to open  ·  Hold OK for options",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleHero(modifier: Modifier) {
    val now = rememberNow()
    Column(modifier) {
        Text(greeting(now), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(longDate(now), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
    }
}
