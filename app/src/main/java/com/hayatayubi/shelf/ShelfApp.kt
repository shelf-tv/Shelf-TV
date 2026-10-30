package com.hayatayubi.shelf

import android.app.Application
import com.hayatayubi.shelf.update.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds what outlives the screen: the updater, which the install receiver also reports to. */
class ShelfApp : Application() {
    val updates by lazy { Updates(this, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
}
