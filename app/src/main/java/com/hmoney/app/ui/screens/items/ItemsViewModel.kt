package com.hmoney.app.ui.screens.items

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.data.repository.AiCallException
import com.hmoney.app.data.scanner.isSameBarcode
import com.hmoney.app.data.scanner.shouldScanAt
import com.hmoney.app.domain.logic.ItemSection
import com.hmoney.app.domain.logic.groupItemsByExpiry
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.Item
import com.hmoney.app.domain.model.ItemSuggestion
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.ItemRepository
import com.hmoney.app.domain.scanner.BarcodeScanError
import com.hmoney.app.domain.scanner.BarcodeScanResult
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * S46 物品管理 ViewModel：清單依到期狀態分節（純函式 groupItemsByExpiry）、
 * 新增／編輯儲存（名稱空 → 錯誤）、刪除、AI 智慧填選（建議預覽，逐欄採用，不自動覆寫）、
 * 條碼掃描（ScanThrottle 模式，照抄 AddTransactionViewModel.onScanFrame）。
 */
@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val aiRepository: AiRepository,
    private val barcodeScannerSource: BarcodeScannerSource,
) : ViewModel() {

    data class UiState(
        val sections: List<ItemSection> = emptyList(),
        val nameError: Boolean = false,
        val suggestion: ItemSuggestion? = null,
        val suggesting: Boolean = false,
        val aiConfigured: Boolean = true,
        val aiNotConfigured: Boolean = false,
        val aiFailed: Boolean = false,
        val scannedBarcode: String? = null,
        val scanError: BarcodeScanError? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var scanInFlight = false
    private var lastScanAt = 0L
    private var lastBarcode: String? = null

    init {
        viewModelScope.launch {
            itemRepository.observeAll().collect { items ->
                val sections = groupItemsByExpiry(
                    items,
                    LocalDate.now(),
                    ZoneId.systemDefault(),
                )
                _uiState.update { it.copy(sections = sections) }
            }
        }
        viewModelScope.launch {
            aiRepository.observeConfig().collect { config ->
                _uiState.update { it.copy(aiConfigured = config.isConfigured) }
            }
        }
    }

    /** 儲存（新增／編輯共用）：名稱空 → nameError；物品寫入一律走 ItemRepository（非交易，不經 ConfirmDraftsUseCase） */
    fun saveItem(
        editing: Item?,
        name: String,
        barcode: String?,
        storageLocation: String?,
        quantity: Int,
        expiryDateEpochMillis: Long?,
        onSaved: () -> Unit,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        _uiState.update { it.copy(nameError = false) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (editing == null) {
                itemRepository.addItem(
                    Item(
                        name = trimmed,
                        barcode = barcode?.takeIf { it.isNotBlank() },
                        storageLocation = storageLocation?.takeIf { it.isNotBlank() },
                        quantity = quantity,
                        expiryDateEpochMillis = expiryDateEpochMillis,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            } else {
                itemRepository.updateItem(
                    editing.copy(
                        name = trimmed,
                        barcode = barcode?.takeIf { it.isNotBlank() },
                        storageLocation = storageLocation?.takeIf { it.isNotBlank() },
                        quantity = quantity,
                        expiryDateEpochMillis = expiryDateEpochMillis,
                        updatedAtEpochMillis = now,
                    ),
                )
            }
            clearSuggestion()
            onSaved()
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { itemRepository.deleteItem(id) }
    }

    fun clearNameError() {
        _uiState.update { it.copy(nameError = false) }
    }

    /** AI 智慧填選：僅產生建議預覽，採用與否由使用者逐欄決定 */
    fun suggestItemFields(name: String, barcode: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _uiState.value.suggesting) return
        _uiState.update { it.copy(suggesting = true, aiNotConfigured = false, aiFailed = false) }
        viewModelScope.launch {
            try {
                aiRepository.suggestItemFields(trimmed, barcode?.takeIf { it.isNotBlank() })
                    .onSuccess { suggestion ->
                        _uiState.update {
                            it.copy(
                                suggestion = suggestion.takeIf { s ->
                                    s.storageLocation != null ||
                                        s.expiryDateEpochMillis != null ||
                                        s.quantity != null
                                },
                                suggesting = false,
                            )
                        }
                    }
                    .onFailure { e ->
                        val error = (e as? AiCallException)?.error
                        _uiState.update {
                            it.copy(
                                suggesting = false,
                                aiNotConfigured = error == AiError.NotConfigured,
                                aiFailed = error != AiError.NotConfigured,
                            )
                        }
                    }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "AI 填選失敗", t)
                _uiState.update { it.copy(suggesting = false, aiFailed = true) }
            }
        }
    }

    fun clearSuggestion() {
        _uiState.update { it.copy(suggestion = null, aiNotConfigured = false, aiFailed = false) }
    }

    // ===== 條碼掃描（ScanThrottle 模式，照抄 AddTransactionViewModel.onScanFrame） =====

    fun onScanFrame(bitmap: Bitmap, rotationDegrees: Int) {
        if (scanInFlight) return
        val now = System.currentTimeMillis()
        if (!shouldScanAt(lastScanAt, now)) return
        lastScanAt = now
        scanInFlight = true
        viewModelScope.launch {
            try {
                when (val result = barcodeScannerSource.scan(bitmap, rotationDegrees)) {
                    is BarcodeScanResult.Success -> {
                        val barcode = result.barcode
                        if (isSameBarcode(lastBarcode, barcode)) return@launch
                        lastBarcode = barcode
                        _uiState.update { it.copy(scannedBarcode = barcode, scanError = null) }
                    }
                    is BarcodeScanResult.Failure ->
                        _uiState.update { it.copy(scanError = result.error) }
                }
            } finally {
                scanInFlight = false
            }
        }
    }

    fun clearScanState() {
        lastBarcode = null
        lastScanAt = 0L
        scanInFlight = false
        _uiState.update { it.copy(scannedBarcode = null, scanError = null) }
    }

    private companion object {
        private const val TAG = "ItemsVM"
    }
}
