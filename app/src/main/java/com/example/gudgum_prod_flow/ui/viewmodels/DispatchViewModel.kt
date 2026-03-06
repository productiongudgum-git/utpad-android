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
import javax.inject.Inject

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val pendingDao: PendingOperationEventDao
) : ViewModel() {

    // Mock current stock — in real app comes from API
    private val _currentStock = MutableStateFlow(1500)
    val currentStock: StateFlow<Int> = _currentStock.asStateFlow()

    private val _batchCode = MutableStateFlow("")
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _qtyTakenFromPacking = MutableStateFlow("")
    val qtyTakenFromPacking: StateFlow<String> = _qtyTakenFromPacking.asStateFlow()

    private val _qtyDispatched = MutableStateFlow("")
    val qtyDispatched: StateFlow<String> = _qtyDispatched.asStateFlow()

    private val _destination = MutableStateFlow("")
    val destination: StateFlow<String> = _destination.asStateFlow()

    private val _vehicleNumber = MutableStateFlow("")
    val vehicleNumber: StateFlow<String> = _vehicleNumber.asStateFlow()

    // Derived: remaining balance = current stock - qty dispatched
    val remainingBalance: StateFlow<Int> = combine(_currentStock, _qtyDispatched) { stock, dispatched ->
        stock - (dispatched.toIntOrNull() ?: 0)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 1500)

    private val _isPending = MutableStateFlow(true)
    val isPending: StateFlow<Boolean> = _isPending.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    // Wizard state
    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    fun onBatchCodeChanged(value: String) { _batchCode.value = value }
    fun onQtyTakenChanged(value: String) { _qtyTakenFromPacking.value = value }
    fun onQtyDispatchedChanged(value: String) { _qtyDispatched.value = value }
    fun onDestinationChanged(value: String) { _destination.value = value }
    fun onVehicleNumberChanged(value: String) { _vehicleNumber.value = value }

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

    fun reset() {
        _batchCode.value = ""
        _qtyTakenFromPacking.value = ""
        _qtyDispatched.value = ""
        _destination.value = ""
        _vehicleNumber.value = ""
        _isPending.value = true
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun submit() {
        val dispatched = _qtyDispatched.value.toIntOrNull()
        if (_batchCode.value.isBlank() || dispatched == null || dispatched <= 0) {
            _submitState.value = SubmitState.Error("Please enter a valid batch code and dispatch quantity")
            return
        }
        if (dispatched > _currentStock.value) {
            _submitState.value = SubmitState.Error("Cannot dispatch more than current stock (${_currentStock.value})")
            return
        }
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading

            val payloadMap = mapOf(
                "qtyTakenKg" to (_qtyTakenFromPacking.value.toDoubleOrNull() ?: 0.0).toString(),
                "qtyDispatchedKg" to dispatched.toString(),
                "destination" to _destination.value,
                "vehicleNumber" to _vehicleNumber.value,
            )
            val jsonPayload = JSONObject(payloadMap).toString()

            val entity = PendingOperationEventEntity(
                module = "dispatch",
                workerId = WorkerIdentityStore.workerId,
                workerName = WorkerIdentityStore.workerName,
                workerRole = WorkerIdentityStore.workerRole,
                batchCode = _batchCode.value,
                quantity = dispatched.toDouble(),
                unit = "kg",
                summary = "Dispatch updated for batch ${_batchCode.value}",
                payloadJson = jsonPayload
            )

            try {
                pendingDao.insertEvent(entity)
                _currentStock.value -= dispatched
                _isPending.value = false
                _submitState.value = SubmitState.Success("Dispatch for batch ${_batchCode.value} saved offline.")
                reset()
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error("Could not save offline: ${e.message}")
            }
        }
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
