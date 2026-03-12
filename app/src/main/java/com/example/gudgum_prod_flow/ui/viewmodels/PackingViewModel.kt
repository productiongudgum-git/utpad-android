package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.entity.CachedBatchEntity
import com.example.gudgum_prod_flow.data.repository.PackingRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SkuItem(val id: String, val name: String)
data class ShiftSummary(val shift: String, val totalPacked: String, val totalBoxes: String)

@HiltViewModel
class PackingViewModel @Inject constructor(
    private val repository: PackingRepository,
) : ViewModel() {

    private val _openBatches = MutableStateFlow<List<CachedBatchEntity>>(emptyList())
    val openBatches: StateFlow<List<CachedBatchEntity>> = _openBatches.asStateFlow()

    // Renamed: entity-typed batch for internal use / submit
    private val _selectedBatchEntity = MutableStateFlow<CachedBatchEntity?>(null)
    val selectedBatchEntity: StateFlow<CachedBatchEntity?> = _selectedBatchEntity.asStateFlow()

    // String-typed batch code for the screen dropdown
    private val _selectedBatch = MutableStateFlow("")
    val selectedBatch: StateFlow<String> = _selectedBatch.asStateFlow()

    // Plain list of batch codes for the dropdown
    val batches: List<String>
        get() = _openBatches.value.map { it.batchCode }

    private val _selectedSku = MutableStateFlow<SkuItem?>(null)
    val selectedSku: StateFlow<SkuItem?> = _selectedSku.asStateFlow()

    private val _availableSkus = MutableStateFlow<List<SkuItem>>(emptyList())
    val availableSkus: StateFlow<List<SkuItem>> = _availableSkus.asStateFlow()

    private val _boxesPacked = MutableStateFlow("")
    val boxesPacked: StateFlow<String> = _boxesPacked.asStateFlow()
    val qtyPacked: StateFlow<String> = _boxesPacked

    private val _boxesMade = MutableStateFlow("")
    val boxesMade: StateFlow<String> = _boxesMade.asStateFlow()

    private val _sessionDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val sessionDate: StateFlow<String> = _sessionDate.asStateFlow()
    val packingDate: StateFlow<String> = _sessionDate

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _shiftSummary = MutableStateFlow(ShiftSummary("Morning", "0", "0"))
    val shiftSummary: StateFlow<ShiftSummary> = _shiftSummary.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        viewModelScope.launch {
            repository.getOpenBatches().collect { batches ->
                _openBatches.value = batches
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshOpenBatches()
        }
    }

    fun onBatchEntitySelected(batch: CachedBatchEntity) { _selectedBatchEntity.value = batch }
    fun onBatchSelected(batch: String) {
        _selectedBatch.value = batch
        _selectedBatchEntity.value = _openBatches.value.find { it.batchCode == batch }
    }
    fun onSkuSelected(sku: SkuItem) { _selectedSku.value = sku }
    fun onBoxesPackedChanged(value: String) { _boxesPacked.value = value }
    fun onQtyPackedChanged(value: String) { _boxesPacked.value = value }
    fun onBoxesMadeChanged(value: String) { _boxesMade.value = value }
    fun onSessionDateChanged(value: String) { _sessionDate.value = value }
    fun onPackingDateChanged(value: String) { _sessionDate.value = value }
    fun onNotesChanged(value: String) { _notes.value = value }

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    fun submit() {
        val batch = _selectedBatchEntity.value ?: run {
            _submitState.value = SubmitState.Error("Select a batch first")
            return
        }
        val boxes = _boxesPacked.value.toIntOrNull()
        if (boxes == null || boxes <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid number of boxes (> 0)")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val result = repository.submitPackingSession(
                batchCode = batch.batchCode,
                sessionDate = _sessionDate.value,
                workerId = WorkerIdentityStore.workerId,
                boxesPacked = boxes,
                isOnline = isOnline,
            )
            result.onSuccess {
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Packed $boxes boxes for batch ${batch.batchCode}"
                    else "Packing session saved offline — will sync when connected"
                )
                clear()
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun clear() {
        _selectedBatchEntity.value = null
        _selectedBatch.value = ""
        _selectedSku.value = null
        _boxesPacked.value = ""
        _boxesMade.value = ""
        _sessionDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _notes.value = ""
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
