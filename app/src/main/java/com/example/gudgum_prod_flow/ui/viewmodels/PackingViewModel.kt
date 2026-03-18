package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.repository.PackingRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import com.example.gudgum_prod_flow.util.BatchCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ShiftSummary(val shift: String, val totalPacked: String, val totalBoxes: String)

@HiltViewModel
class PackingViewModel @Inject constructor(
    private val repository: PackingRepository,
) : ViewModel() {

    private val _batchCode = MutableStateFlow(BatchCodeGenerator.generate())
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _qtyPacked = MutableStateFlow("")
    val qtyPacked: StateFlow<String> = _qtyPacked.asStateFlow()

    private val _boxesMade = MutableStateFlow("")
    val boxesMade: StateFlow<String> = _boxesMade.asStateFlow()

    private val _packingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val packingDate: StateFlow<String> = _packingDate.asStateFlow()



    private val _shiftSummary = MutableStateFlow(ShiftSummary("Morning", "0", "0"))
    val shiftSummary: StateFlow<ShiftSummary> = _shiftSummary.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun onQtyPackedChanged(value: String) { _qtyPacked.value = value }
    fun onBoxesMadeChanged(value: String) { _boxesMade.value = value }
    fun onPackingDateChanged(value: String) { _packingDate.value = value }


    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    fun submit() {
        val code = _batchCode.value.trim()
        val qty = _qtyPacked.value.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid quantity (> 0)")
            return
        }
        val boxes = _boxesMade.value.toIntOrNull()
        if (boxes == null || boxes <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid box count (> 0)")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val result = repository.submitPacking(
                batchCode = code,
                quantityKg = qty,
                boxesCount = boxes,
                packingDate = _packingDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )
            result.onSuccess {
                _shiftSummary.value = ShiftSummary(
                    shift = "Current",
                    totalPacked = qty.toString(),
                    totalBoxes = boxes.toString(),
                )
                _submitState.value = SubmitState.Success("Packed $boxes boxes (${qty}kg) for batch $code")
                clear()
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun clear() {
        _batchCode.value = BatchCodeGenerator.generate()
        _qtyPacked.value = ""
        _boxesMade.value = ""
        _packingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
