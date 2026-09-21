package io.github.painter99.wswolomouc.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces Dispatchers.Main with Dispatchers.Unconfined so viewModelScope
 * runs eagerly in JVM unit tests. Uses only kotlinx-coroutines-core symbols
 * — UnconfinedTestDispatcher resolved unreliably on CI (runs 18/21/22).
 */
class MainDispatcherRule : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
