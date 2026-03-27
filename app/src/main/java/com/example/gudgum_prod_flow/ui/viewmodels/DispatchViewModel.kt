package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.remote.api.OpsApiClient
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.repository.DispatchRepository
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

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val repository: DispatchRepository,
) : ViewModel() {

    // Batch codes fetched from gg_batches + today's code from ops API
    private val _batchCodes = MutableStateFlow<List<String>>(emptyList())
    val batchCodes: StateFlow<List<String>> = _batchCodes.asStateFlow()

    private val _batchCodesLoading = MutableStateFlow(false)
    val batchCodesLoading: StateFlow<Boolean> = _batchCodesLoading.asStateFlow()

    private val _batchCode = MutableStateFlow("")
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    // Quantity in units (integer)
    private val _qtyDispatched = MutableStateFlow("")
    val qtyDispatched: StateFlow<String> = _qtyDispatched.asStateFlow()

    private val _dispatchDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val dispatchDate: StateFlow<String> = _dispatchDate.asStateFlow()

    private val _customers = MutableStateFlow<List<GgCustomerDto>>(emptyList())
    val customers: StateFlow<List<GgCustomerDto>> = _customers.asStateFlow()

    private val _selectedCustomerId = MutableStateFlow("")
    val selectedCustomerId: StateFlow<String> = _selectedCustomerId.asStateFlow()

    private val _selectedCustomerName = MutableStateFlow("")
    val selectedCustomerName: StateFlow<String> = _selectedCustomerName.asStateFlow()

    private val _customersLoading = MutableStateFlow(false)
    val customersLoading: StateFlow<Boolean> = _customersLoading.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        loadBatchCodes()
        loadCustomers()
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun loadBatchCodes() {
        viewModelScope.launch {
            _batchCodesLoading.value = true

            val codesResult = repository.getOpenBatchCodes()
            val todayCode = OpsApiClient.fetchTodayBatchCode() ?: BatchCodeGenerator.generate()

            val codes = codesResult.getOrDefault(emptyList()).toMutableList()
            // If today's code already exists in gg_batches, move it to the top
            if (todayCode.isNotBlank() && codes.contains(todayCode)) {
                codes.remove(todayCode)
                codes.add(0, todayCode)
            }
            _batchCodes.value = codes

            // Default to today's code if it's in the list, otherwise fall back to first entry
            _batchCode.value = if (codes.contains(todayCode)) todayCode
                               else codes.firstOrNull() ?: ""

            _batchCodesLoading.value = false
        }
    }

    fun loadCustomers() {
        viewModelScope.launch {
            _customersLoading.value = true
            val result = repository.getCustomers()
            result.onSuccess { list -> _customers.value = list }
            _customersLoading.value = false
        }
    }

    fun onBatchCodeSelected(code: String) { _batchCode.value = code }
    fun onQtyDispatchedChanged(value: String) { _qtyDispatched.value = value }
    fun onDispatchDateChanged(value: String) { _dispatchDate.value = value }

    fun onCustomerSelected(id: String, name: String) {
        _selectedCustomerId.value = id
        _selectedCustomerName.value = name
    }

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    fun submit() {
        val code = _batchCode.value.trim()
        if (code.isBlank()) {
            _submitState.value = SubmitState.Error("Select a batch code")
            return
        }
        val qty = _qtyDispatched.value.toIntOrNull()
        if (qty == null || qty <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid number of units (> 0)")
            return
        }
        val customerId = _selectedCustomerId.value
        if (customerId.isBlank()) {
            _submitState.value = SubmitState.Error("Select a customer")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val result = repository.submitDispatch(
                batchCode = code,
                customerId = customerId,
                quantityDispatched = qty,
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )
            result.onSuccess {
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Dispatched $qty units for batch $code to ${_selectedCustomerName.value}"
                    else "Dispatch saved offline — will sync when connected"
                )
                reset()
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Dispatch failed")
            }
        }
    }

    fun reset() {
        _batchCode.value = _batchCodes.value.firstOrNull() ?: ""
        _qtyDispatched.value = ""
        _dispatchDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _selectedCustomerId.value = ""
        _selectedCustomerName.value = ""
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
