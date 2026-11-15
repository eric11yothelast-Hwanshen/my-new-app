package com.hmoney.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S50 App 主框架 ViewModel：頂層分頁動態過濾（inventoryEnabled）與實機點擊引導（coach marks）
 * 步驟狀態機。步驟持久化於 DataStore（onboardingCoachStep），中斷後回來從原步驟繼續；
 * 三步完成或任何「跳過」→ onboardingCompleted=true 永不再現。
 * 步驟完成偵測只收觀察掛鉤回呼（FAB 點擊、導航 route 變化），FAB 與分頁列內部邏輯零改動。
 */
@HiltViewModel
class HmoneyAppRootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val featureFlags: StateFlow<SettingsRepository.FeatureFlagSettings> =
        settingsRepository.observeFeatureFlags()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsRepository.FeatureFlagSettings(),
            )

    /** 跳過引導（任何步驟）→ 完成旗標＋步驟歸零，永不再現 */
    fun skipCoach() {
        viewModelScope.launch { finishCoach() }
    }

    /** 步驟①：記帳 FAB 被點（觀察掛鉤回呼）→ 進入步驟② */
    fun onCoachFabTapped() {
        viewModelScope.launch { advanceCoach(fromStep = 1) }
    }

    /** 步驟②③：以導航 route 變化驅動（步驟②＝離開總覽即完成；步驟③＝抵達物品分頁即完成） */
    fun onCoachRouteChanged(route: String?) {
        viewModelScope.launch {
            when (settingsRepository.observeFeatureFlags().first().onboardingCoachStep) {
                2 -> if (route != com.hmoney.app.ui.navigation.TopLevelDestination.OVERVIEW.route) {
                    advanceCoach(fromStep = 2)
                }

                3 -> if (route == com.hmoney.app.ui.navigation.TopLevelDestination.ITEMS.route) {
                    finishCoach()
                }
            }
        }
    }

    private suspend fun advanceCoach(fromStep: Int) {
        val flags = settingsRepository.observeFeatureFlags().first()
        val step = flags.onboardingCoachStep
        if (step != fromStep) return
        if (step >= 3 || (step == 2 && !flags.inventoryEnabled)) {
            finishCoach()
        } else {
            settingsRepository.setOnboardingCoachStep(step + 1)
        }
    }

    private suspend fun finishCoach() {
        settingsRepository.setOnboardingCompleted(true)
        settingsRepository.setOnboardingCoachStep(0)
    }
}
