package com.xiaoai.islandnotify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ComposeRefreshBus {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    @JvmStatic
    fun bump() {
        _tick.value = _tick.value + 1
    }
}
