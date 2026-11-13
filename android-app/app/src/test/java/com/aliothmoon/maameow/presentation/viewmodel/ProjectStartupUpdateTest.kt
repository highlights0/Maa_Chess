package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.service.update.UpdateService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectStartupUpdateTest {
    @Test fun startupNeverChecksUpstreamResourcesAndOnlyPromptsForAnAvailableProjectApp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = mockk<AppSettingsManager>(relaxed = true)
        every { settings.mirrorChyanCdk } returns MutableStateFlow("")
        every { settings.autoCheckUpdate } returns MutableStateFlow(true)
        every { settings.autoDownloadUpdate } returns MutableStateFlow(false)
        every { settings.updateChannel } returns MutableStateFlow(UpdateChannel.BETA)
        val service = mockk<UpdateService>(relaxed = true)
        coEvery { service.checkResourceUpdate(any()) } returns UpdateCheckResult.Available(UpdateInfo("upstream-resource"))
        try {
            for (available in listOf(false, true)) {
                coEvery { service.checkAppUpdate(any()) } returns if (available)
                    UpdateCheckResult.Available(UpdateInfo("project-release")) else UpdateCheckResult.UpToDate("current")
                val vm = UpdateViewModel(mockk(relaxed = true), service, settings, mockk(relaxed = true), mockk(relaxed = true))
                try {
                    vm.checkUpdatesOnStartup()
                    runCurrent()
                    if (available) {
                        assertEquals("project-release", vm.startupUpdateDialog.value?.appUpdate?.version)
                        assertNull(vm.startupUpdateDialog.value?.resourceUpdate)
                    } else assertNull(vm.startupUpdateDialog.value)
                } finally { vm.viewModelScope.cancel() }
            }
            coVerify(exactly = 0) { service.checkResourceUpdate(any()) }
        } finally { Dispatchers.resetMain() }
    }
}
