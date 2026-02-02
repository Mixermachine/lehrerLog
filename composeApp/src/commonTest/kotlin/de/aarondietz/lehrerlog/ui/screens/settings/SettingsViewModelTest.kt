package de.aarondietz.lehrerlog.ui.screens.settings

import androidx.lifecycle.viewModelScope
import de.aarondietz.lehrerlog.*
import de.aarondietz.lehrerlog.data.repository.StorageRepository
import de.aarondietz.lehrerlog.logging.LogFileWriter
import de.aarondietz.lehrerlog.logging.LogRepository
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.test.*

class SettingsViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var logWriter: LogFileWriter

    @BeforeTest
    fun setup() {
        logWriter = LogFileWriter().apply {
            initialize(maxFileSizeMB = 1, maxFiles = 1, maxAgeDays = 1)
            clearLogs()
            writeLog(
                timestamp = SharedTestFixtures.testLogTimestamp,
                level = SharedTestFixtures.testLogLevel,
                tag = SharedTestFixtures.testLogTag,
                message = SharedTestFixtures.testLogMessage,
                throwable = null
            )
        }
    }

    @AfterTest
    fun tearDown() {
        logWriter.clearLogs()
    }

    @Test
    fun loadsQuotaAndLogs() = runViewModelTest {
        val tokenStorage = InMemoryTokenStorage().apply {
            saveAccessToken(SharedTestFixtures.testAuthAccessToken)
        }
        val quota = SharedTestFixtures.testStorageQuotaDto()

        val httpClient = createTestHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/storage/quota" -> respondJson(json.encodeToString(quota))
                else -> respondJson("{}")
            }
        }

        val viewModel = SettingsViewModel(
            storageRepository = StorageRepository(httpClient, tokenStorage, SharedTestFixtures.testBaseUrl),
            logRepository = LogRepository(logWriter)
        )

        try {
            awaitUntil { viewModel.quotaState.value.quota != null }
            awaitUntil { viewModel.logState.value.overview != null }

            assertNotNull(viewModel.quotaState.value.quota)
            assertNotNull(viewModel.logState.value.overview)

            viewModel.clearLogs()
            awaitUntil { viewModel.logState.value.error == null }

            assertTrue(viewModel.logState.value.error == null)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
