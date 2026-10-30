package com.hayatayubi.shelf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ShelfViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private var loadJob: Job? = null

    var apps by mutableStateOf(emptyList<AppEntry>())
        private set
    var loaded by mutableStateOf(false)
        private set
    var hiddenCount by mutableIntStateOf(0)
        private set

    /** Bumped when Home is pressed while Shelf is already showing. */
    var homePresses by mutableIntStateOf(0)
        private set

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            apps = repo.loadApps()
            hiddenCount = repo.hiddenPackages().size
            loaded = true
        }
    }

    fun move(pkg: String, by: Int) {
        val from = apps.indexOfFirst { it.packageName == pkg }
        if (from < 0) return
        moveTo(pkg, (from + by).coerceIn(0, apps.lastIndex))
    }

    fun moveTo(pkg: String, index: Int) {
        val list = apps.toMutableList()
        val from = list.indexOfFirst { it.packageName == pkg }
        if (from < 0 || from == index) return
        list.add(index, list.removeAt(from))
        apps = list
        repo.saveOrder(list.map { it.packageName })
    }

    fun hide(pkg: String) {
        repo.hide(pkg)
        refresh()
    }

    fun unhideAll() {
        repo.unhideAll()
        refresh()
    }

    fun resetLayout() {
        repo.resetOrder()
        refresh()
    }

    fun onHomePressed() {
        homePresses++
    }
}
