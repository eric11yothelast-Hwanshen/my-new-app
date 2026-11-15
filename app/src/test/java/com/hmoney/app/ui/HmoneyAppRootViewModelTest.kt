package com.hmoney.app.ui

import com.hmoney.app.domain.model.AppearanceSettings
import com.hmoney.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S50 coach 步驟狀態機：sheet 完成＝step 1；FAB 點擊→step 2；離開總覽→step 3
 * （inventoryEnabled 關閉時直接完成）；抵達物品分頁→完成旗標＋步驟歸零；跳過→完成旗標。
 * 步驟持久化於 DataStore（fake），StateFlow 斷言前先收集。
 */
class HmoneyAppRootViewModelTest {

    private lateinit var settingsRepo: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settingsRepo = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HmoneyAppRootViewModel = HmoneyAppRootViewModel(settingsRepo)

    @Test
    fun `FAB 點擊在 step 1 時推進到 step 2`() {
        settingsRepo.featureFlags.update { it.copy(onboardingCoachStep = 1) }
        val vm = createViewModel()
        vm.onCoachFabTapped()
        val flags = settingsRepo.featureFlags.value
        assertEquals(2, flags.onboardingCoachStep)
        assertFalse(flags.onboardingCompleted)
    }

    @Test
    fun `FAB 點擊在非 step 1 時不推進`() {
        settingsRepo.featureFlags.update { it.copy(onboardingCoachStep = 0) }
        val vm = createViewModel()
        vm.onCoachFabTapped()
        assertEquals(0, settingsRepo.featureFlags.value.onboardingCoachStep)
    }

    @Test
    fun `step 2 離開總覽推進到 step 3`() {
        settingsRepo.featureFlags.update { it.copy(onboardingCoachStep = 2) }
        val vm = createViewModel()
        vm.onCoachRouteChanged("transactions")
        assertEquals(3, settingsRepo.featureFlags.value.onboardingCoachStep)
        assertFalse(settingsRepo.featureFlags.value.onboardingCompleted)
    }

    @Test
    fun `step 2 且 inventoryEnabled 關閉時離開總覽直接完成`() {
        settingsRepo.featureFlags.update {
            it.copy(onboardingCoachStep = 2, inventoryEnabled = false)
        }
        val vm = createViewModel()
        vm.onCoachRouteChanged("transactions")
        val flags = settingsRepo.featureFlags.value
        assertTrue(flags.onboardingCompleted)
        assertEquals(0, flags.onboardingCoachStep)
    }

    @Test
    fun `step 3 抵達物品分頁即完成並歸零`() {
        settingsRepo.featureFlags.update { it.copy(onboardingCoachStep = 3) }
        val vm = createViewModel()
        vm.onCoachRouteChanged("items")
        val flags = settingsRepo.featureFlags.value
        assertTrue(flags.onboardingCompleted)
        assertEquals(0, flags.onboardingCoachStep)
    }

    @Test
    fun `step 3 在其他分頁不完成`() {
        settingsRepo.featureFlags.update { it.copy(onboardingCoachStep = 3) }
        val vm = createViewModel()
        vm.onCoachRouteChanged("stats")
        assertEquals(3, settingsRepo.featureFlags.value.onboardingCoachStep)
    }

    @Test
    fun `跳過引導任何步驟皆寫完成旗標且步驟歸零`() {
        settingsRepo.featureFlags.update { it.copy(onboardingCoachStep = 2) }
        val vm = createViewModel()
        vm.skipCoach()
        val flags = settingsRepo.featureFlags.value
        assertTrue(flags.onboardingCompleted)
        assertEquals(0, flags.onboardingCoachStep)
    }
}

/** 記憶體版 SettingsRepository：僅實作 coach 相關成員，其餘成員沿用既有 fake 慣例 */
private class FakeSettingsRepository : SettingsRepository {
    val featureFlags = MutableStateFlow(SettingsRepository.FeatureFlagSettings())

    override fun observeAppearance(): Flow<AppearanceSettings> = emptyFlow()
    override suspend fun setDarkMode(modeName: String) = Unit
    override suspend fun setDynamicColor(enabled: Boolean) = Unit
    override fun observeReminder(): Flow<SettingsRepository.ReminderSettings> = emptyFlow()
    override suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = Unit
    override suspend fun getFlag(key: String): Boolean = false
    override suspend fun setFlag(key: String, value: Boolean) = Unit
    override fun observeAiAssistant(): Flow<SettingsRepository.AiAssistantSettings> = emptyFlow()
    override suspend fun setAiAssistantIncludeContext(enabled: Boolean) = Unit
    override suspend fun setAiAssistantKeepHistory(enabled: Boolean) = Unit
    override suspend fun setAiAssistantAutoCleanup(enabled: Boolean) = Unit
    override suspend fun setAiAssistantShowQuickPrompts(enabled: Boolean) = Unit
    override suspend fun setAiAssistantQueryEnabled(enabled: Boolean) = Unit
    override suspend fun setAiCurrentSessionId(sessionId: Long) = Unit
    override fun observeFeatureFlags(): Flow<SettingsRepository.FeatureFlagSettings> = featureFlags
    override suspend fun setAiNlEntryEnabled(enabled: Boolean) = Unit
    override suspend fun setAiImageEntryEnabled(enabled: Boolean) = Unit
    override suspend fun setBarcodeEnabled(enabled: Boolean) = Unit
    override suspend fun setInventoryEnabled(enabled: Boolean) = Unit
    override suspend fun setOnboardingCompleted(enabled: Boolean) {
        featureFlags.update { it.copy(onboardingCompleted = enabled) }
    }
    override suspend fun setOnboardingCoachStep(step: Int) {
        featureFlags.update { it.copy(onboardingCoachStep = step.coerceIn(0, 3)) }
    }
    override fun observeLastSeenInsight(): Flow<Long> = emptyFlow()
    override suspend fun setLastSeenInsightEpochMillis(epochMillis: Long) = Unit
}
