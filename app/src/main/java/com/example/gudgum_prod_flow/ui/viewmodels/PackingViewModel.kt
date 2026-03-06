package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class Sku(val id: String, val name: String, val unitsPerBox: Int)
data class ShiftSummary(val totalPacked: Int, val totalBoxes: Int, val shift: String)

@HiltViewModel
class PackingViewModel @Inject constructor(
    private val pendingDao: PendingOperationEventDao
) : ViewModel() {

    val skus = listOf(
        Sku("1", "Mint Gum 10-pack", 24),
        Sku("2", "Strawberry Gum 10-pack", 24),
        Sku("3", "Orange Gum 5-pack", 48),
        Sku("4", "Watermelon Gum 20-pack", 12),
    )

    val batches = listOf("BTH-20260222-1234", "BTH-20260221-5678", "BTH-20260220-9012")

    // Intelligent Default: Map batches to their flavor so we can filter SKUs
    private val batchFlavorMap = mapOf(
        "BTH-20260222-1234" to "Mint",
        "BTH-20260221-5678" to "Strawberry",
        "BTH-20260220-9012" to "Orange",
    )

    private val skuFlavorMap = mapOf(
        "1" to "Mint",
        "2" to "Strawberry",
        "3" to "Orange",
        "4" to "Watermelon",
    )

    private val _selectedBatch = MutableStateFlow("")
    val selectedBatch: StateFlow<String> = _selectedBatch.asStateFlow()

    private val _selectedSku = MutableStateFlow<Sku?>(null)
    val selectedSku: StateFlow<Sku?> = _selectedSku.asStateFlow()

    private val _availableSkus = MutableStateFlow(skus)
    val availableSkus: StateFlow<List<Sku>> = _availableSkus.asStateFlow()

    private val _qtyPacked = MutableStateFlow("")
    val qtyPacked: StateFlow<String> = _qtyPacked.asStateFlow()

    private val _boxesMade = MutableStateFlow("")
    val boxesMade: StateFlow<String> = _boxesMade.asStateFlow()

    private val _packingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val packingDate: StateFlow<String> = _packingDate.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    val shiftSummary: StateFlow<ShiftSummary> = combine(_qtyPacked, _boxesMade) { qty, boxes ->
        ShiftSummary(
            totalPacked = qty.toIntOrNull() ?: 0,
            totalBoxes = boxes.toIntOrNull() ?: 0,
            shift = if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 14) "Morning" else "Evening",
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ShiftSummary(0, 0, "Morning"))

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    // Wizard state
    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    fun onBatchSelected(batch: String) {
        _selectedBatch.value = batch
        // Intelligent Default: filter SKUs by the flavor of the selected batch
        val batchFlavor = batchFlavorMap[batch]
        if (batchFlavor != null) {
            _availableSkus.value = skus.filter { skuFlavorMap[it.id] == batchFlavor }
        } else {
            _availableSkus.value = skus
        }
        // Clear SKU if it's no longer in the filtered list
        val currentSku = _selectedSku.value
        if (currentSku != null && currentSku !in _availableSkus.value) {
            _selectedSku.value = null
        }
    }

    fun onSkuSelected(sku: Sku) { _selectedSku.value = sku }
    fun onQtyPackedChanged(value: String) { _qtyPacked.value = value }
    fun onBoxesMadeChanged(value: String) { _boxesMade.value = value }
    fun onPackingDateChanged(value: String) { _packingDate.value = value }
    fun onNotesChanged(value: String) { _notes.value = value }

    fun nextStep() {
        if (_currentWizardStep.value < 3) {
            _currentWizardStep.value += 1
        }
    }

    fun previousStep() {
        if (_currentWizardStep.value > 1) {
            _currentWizardStep.value -= 1
        }
    }

    fun clear() {
        _selectedBatch.value = ""
        _selectedSku.value = null
        _availableSkus.value = skus
        _qtyPacked.value = ""
        _boxesMade.value = ""
        _packingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _notes.value = ""
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun submit() {
        if (_selectedBatch.value.isBlank() || _selectedSku.value == null || _qtyPacked.value.isBlank()) {
            _submitState.value = SubmitState.Error("Please fill in batch, SKU, and quantity")
            return
        }
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val qtyPacked = _qtyPacked.value.toDoubleOrNull()
            val boxes = _boxesMade.value.toDoubleOrNull() ?: 0.0
            val sku = _selectedSku.value

            if (qtyPacked == null || qtyPacked <= 0.0 || sku == null) {
                _submitState.value = SubmitState.Error("Enter valid packing values")
                return@launch
            }

            val payloadMap = mapOf(
                "qtyPackedKg" to qtyPacked.toString(),
                "boxesMade" to boxes.toString(),
                "skuCode" to sku.id,
                "skuName" to sku.name,
                "packingDate" to _packingDate.value,
                "notes" to _notes.value,
            )
            val jsonPayload = JSONObject(payloadMap).toString()

            val entity = PendingOperationEventEntity(
                module = "packing",
                workerId = WorkerIdentityStore.workerId,
                workerName = WorkerIdentityStore.workerName,
                workerRole = WorkerIdentityStore.workerRole,
                batchCode = _selectedBatch.value,
                quantity = boxes,
                unit = "boxes",
                summary = "${sku.name} packed",
                payloadJson = jsonPayload
            )

            try {
                pendingDao.insertEvent(entity)
                _submitState.value = SubmitState.Success("Packing entry for batch ${_selectedBatch.value} saved offline.")
                clear()
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error("Could not save offline: ${e.message}")
            }
        }
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
