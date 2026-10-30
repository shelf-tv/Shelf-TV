package com.hayatayubi.shelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hayatayubi.shelf.update.InstallState
import com.hayatayubi.shelf.update.Release
import com.hayatayubi.shelf.update.Updates
import kotlinx.coroutines.delay

private val Accent = Color(0xFF0A84FF)
private val Muted = Color.White.copy(alpha = 0.65f)

/**
 * "Update Shelf": what's new in the waiting release and one button for the next step. The first
 * time, Android needs Shelf to be allowed to install apps, so that step comes first.
 */
@Composable
fun UpdateDialog(updates: Updates, release: Release, onLater: () -> Unit) {
    val install by updates.install.collectAsState()
    var allowed by remember { mutableStateOf(updates.canInstall()) }
    var noSettingsScreen by remember { mutableStateOf(false) }
    val primary = remember { FocusRequester() }

    // Re-read while the dialog is up: the user may have just allowed it in Settings and come back.
    LaunchedEffect(Unit) {
        while (true) {
            allowed = updates.canInstall()
            delay(1_000)
        }
    }

    Dialog(onDismissRequest = onLater) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1E1E22))
                .padding(32.dp),
        ) {
            Text(
                "UPDATE",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text("Shelf ${release.version} is ready", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "You have ${updates.currentVersion}. Your layout and hidden apps are kept.",
                color = Muted,
                fontSize = 15.sp,
            )
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text("What's new", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    formatNotes(release.notes),
                    color = Muted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    // Nothing in the notes is focusable, so the D-pad couldn't scroll them: cap instead.
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(28.dp))

            when (val state = install) {
                is InstallState.Downloading -> {
                    Text(
                        if (state.progress == null) "Downloading…" else "Downloading… ${(state.progress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(Modifier.fillMaxWidth(state.progress ?: 0f).fillMaxHeight().background(Accent))
                    }
                }
                InstallState.Installing -> Text(
                    "Installing. If Android asks, choose Update. Shelf will be back in a moment.",
                    color = Color.White,
                    fontSize = 16.sp,
                )
                else -> {
                    val failed = state as? InstallState.Failed
                    val message = when {
                        !allowed && noSettingsScreen ->
                            "Open the TV's Settings → Apps → Security & restrictions → Unknown sources, and turn on Shelf."
                        !allowed ->
                            "First, Android needs your OK for Shelf to install updates. On the next screen, turn on Shelf, then press Back."
                        failed != null -> failed.message
                        else -> null
                    }
                    if (message != null) {
                        Text(message, color = Color.White, fontSize = 16.sp, lineHeight = 23.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DialogButton(
                            text = when {
                                !allowed -> "Allow updates"
                                failed != null -> "Try again"
                                else -> "Update now"
                            },
                            primary = true,
                            modifier = Modifier.focusRequester(primary),
                            onClick = {
                                if (!allowed) noSettingsScreen = !updates.openInstallPermission() else updates.install(release)
                            },
                        )
                        DialogButton(text = "Later", onClick = onLater)
                    }
                    // Focus the main button whenever it (re)appears: on open, after allowing, after a failure.
                    LaunchedEffect(allowed, failed) {
                        delay(50)
                        runCatching { primary.requestFocus() }
                    }
                }
            }
        }
    }
}

/**
 * Release notes are the CHANGELOG section, Markdown bullets hard-wrapped at ~120 characters.
 * Join each bullet's wrapped lines back together so the TV can wrap them to its own width.
 */
private fun formatNotes(notes: String): String {
    val items = mutableListOf<String>()
    for (raw in notes.lines()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        when {
            line.startsWith("- ") || line.startsWith("* ") -> items += "•  " + line.drop(2)
            items.lastOrNull()?.startsWith("•") == true -> items[items.lastIndex] += " $line"
            else -> items += line
        }
    }
    return items.joinToString("\n")
}

@Composable
private fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Accent else Color.White.copy(alpha = 0.1f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    }
}
