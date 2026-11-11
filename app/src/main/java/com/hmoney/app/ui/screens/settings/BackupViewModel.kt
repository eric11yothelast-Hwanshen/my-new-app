package com.hmoney.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 備份 ViewModel：手動匯出 CSV／JSON、還原、顯示最近自動備份時間。
 * 所有操作有 loading／成功／失敗三態回饋。
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    enum class Status { IDLE, WORKING, SUCCESS, FAILED }

    data class UiState(
        val status: Status = Status.IDLE,
        val latestBackupMillis: Long? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        refreshLatestBackup()
    }

    fun refreshLatestBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                latestBackupMillis = backupManager.latestAutoBackupMillis(),
            )
        }
    }

    fun exportCsv(uri: Uri) {
        if (_uiState.value.status == Status.WORKING) return
        _uiState.value = _uiState.value.copy(status = Status.WORKING)
        viewModelScope.launch {
            val result = backupManager.exportCsvTo(uri)
            _uiState.value = _uiState.value.copy(
                status = if (result.isSuccess) Status.SUCCESS else Status.FAILED,
            )
        }
    }

    fun exportJson(uri: Uri) {
        if (_uiState.value.status == Status.WORKING) return
        _uiState.value = _uiState.value.copy(status = Status.WORKING)
        viewModelScope.launch {
            val result = backupManager.exportJsonTo(uri)
            _uiState.value = _uiState.value.copy(
                status = if (result.isSuccess) Status.SUCCESS else Status.FAILED,
            )
        }
    }

    fun restore(uri: Uri) {
        if (_uiState.value.status == Status.WORKING) return
        _uiState.value = _uiState.value.copy(status = Status.WORKING)
        viewModelScope.launch {
            val result = backupManager.restoreFrom(uri)
            _uiState.value = _uiState.value.copy(
                status = if (result.isSuccess) Status.SUCCESS else Status.FAILED,
            )
        }
    }

    fun resetStatus() {
        _uiState.value = _uiState.value.copy(status = Status.IDLE)
    }
}
