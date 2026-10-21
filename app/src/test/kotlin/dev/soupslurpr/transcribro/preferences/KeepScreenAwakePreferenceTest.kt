package dev.soupslurpr.transcribro.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class KeepScreenAwakePreferenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsOffAndPersistsAcrossViewModels() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.root.resolve("test.preferences_pb") }
        )
        val first = PreferencesViewModel(store)
        try {
            runCurrent()
            assertFalse(first.uiState.value.keepScreenAwakeWhileListening.second.value)
            first.setPreference(first.uiState.value.keepScreenAwakeWhileListening.first, true)
            first.uiState.first { it.keepScreenAwakeWhileListening.second.value }
            assertTrue(first.uiState.value.keepScreenAwakeWhileListening.second.value)
            val reopened = PreferencesViewModel(store)
            try {
                reopened.uiState.first { it.keepScreenAwakeWhileListening.second.value }
                assertTrue(reopened.uiState.value.keepScreenAwakeWhileListening.second.value)
                reopened.setPreference(reopened.uiState.value.keepScreenAwakeWhileListening.first, false)
                reopened.uiState.first { !it.keepScreenAwakeWhileListening.second.value }
                first.uiState.first { !it.keepScreenAwakeWhileListening.second.value }
                assertFalse(first.uiState.value.keepScreenAwakeWhileListening.second.value)
                assertFalse(reopened.uiState.value.keepScreenAwakeWhileListening.second.value)
            } finally {
                reopened.viewModelScope.cancel()
            }
        } finally {
            first.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
