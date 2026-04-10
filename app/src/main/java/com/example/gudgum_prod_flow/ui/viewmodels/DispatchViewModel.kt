package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.remote.dto.FifoDispatchAllocation
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.GgFlavorDto
import com.example.gudgum_prod_flow.data.repository.DispatchRepository
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

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val repository: DispatchRepository,
) : ViewModel() {

    // ── Step 1: Invoice + Customer ───────────────────────────────
    private val _invoiceNumber = MutableStateFlow("")
    val invoiceNumber: StateFlow<String> = _invoiceNumber.asStateFlow()

    private val _customers = MutableStateFlow<List<GgCustomerDto>>(emptyList())
    val customers: StateFlow<List<GgCustomerDto>> = _customers.asStateFlow()

    private val _customersLoading = MutableStateFlow(false)
    val customersLoading: StateFlow<Boolean> = _customersLoading.asStateFlow()

    private val _selectedCustomerId = MutableStateFlow("")
    val selectedCustomerId: StateFlow<String> = _selectedCustomerId.asStateFlow()

    private val _selectedCustomerName = MutableStateFlow("")
    val selectedCustomerName: StateFlow<String> = _selectedCustomerName.asStateFlow()

    // ── Step 2: Flavor ───────────────────────────────────────────
    private val _flavors = MutableStateFlow<List<GgFlavorDto>>(emptyList())
    val flavors: StateFlow<List<GgFlavorDto>> = _flavors.asStateFlow()

    private val _flavorsLoading = MutableStateFlow(false)
    val flavorsLoading: StateFlow<Boolean> = _flavorsLoading.asStateFlow()

    private val _selectedFlavorId = MutableStateFlow("")
    val selectedFlavorId: StateFlow<String> = _selectedFlavorId.asStateFlow()

    private val _selectedFlavorName = MutableStateFlow("")
    val selectedFlavorName: StateFlow<String> = _selectedFlavorName.asStateFlow()

    // ── Step 3: Boxes + Date ─────────────────────────────────────
    private val _boxesNeeded = MutableStateFlow("")
    val boxesNeeded: StateFlow<String> = _boxesNeeded.asStateFlow()

    private val _dispatchDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val dispatchDate: StateFlow<String> = _dispatchDate.asStateFlow()

    // ── Step 4: FIFO Allocation review ───────────────────────────
    private val _fifoAllocations = MutableStateFlow<List<FifoDispatchAllocation>>(emptyList())
    val fifoAllocations: StateFlow<List<FifoDispatchAllocation>> = _fifoAllocations.asStateFlow()

    private val _fifoLoading = MutableStateFlow(false)
    val fifoLoading: StateFlow<Boolean> = _fifoLoading.asStateFlow()

    private val _fifoError = MutableStateFlow<String?>(null)
    val fifoError: StateFlow<String?> = _fifoError.asStateFlow()

    // ── Shared ───────────────────────────────────────────────────
    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        loadFlavors()
        loadCustomers()
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun loadFlavors() {
        viewModelScope.launch {
            _flavorsLoading.value = true
            repository.getFlavors().onSuccess { _flavors.value = it }
            _flavorsLoading.value = false
        }
    }

    fun loadCustomers() {
        viewModelScope.launch {
            _customersLoading.value = true
            repository.getCustomers().onSuccess { _customers.value = it }
            _customersLoading.value = false
        }
    }

    fun onInvoiceNumberChanged(value: String) { _invoiceNumber.value = value }

    fun onCustomerSelected(id: String, name: String) {
        _selectedCustomerId.value = id
        _selectedCustomerName.value = name
    }

    fun onFlavorSelected(id: String, name: String) {
        _selectedFlavorId.value = id
        _selectedFlavorName.value = name
    }

    fun onBoxesNeededChanged(value: String) { _boxesNeeded.value = value }
    fun onDispatchDateChanged(value: String) { _dispatchDate.value = value }

    fun nextStep() {
        when (_currentWizardStep.value) {
            1 -> {
                if (_invoiceNumber.value.isBlank()) {
                    _submitState.value = SubmitState.Error("Enter an invoice number")
                    return
                }
                _currentWizardStep.value = 2
            }
            2 -> {
                if (_selectedFlavorId.value.isBlank()) {
                    _submitState.value = SubmitState.Error("Select a flavor")
                    return
                }
                _currentWizardStep.value = 3
            }
            3 -> {
                val boxes = _boxesNeeded.value.toIntOrNull()
                if (boxes == null || boxes <= 0) {
                    _submitState.value = SubmitState.Error("Enter a valid number of boxes (> 0)")
                    return
                }
                calculateFifoAndAdvance(boxes)
            }
        }
    }

    private fun calculateFifoAndAdvance(boxesNeeded: Int) {
        viewModelScope.launch {
            _fifoLoading.value = true
            _fifoError.value = null
            val result = repository.getProductionBatchesByFlavor(_selectedFlavorId.value)
            result.onSuccess { batches ->
                val allocations = repository.allocateFifo(batches, boxesNeeded)
                val allocated = allocations.sumOf { it.boxesToTake }
                _fifoAllocations.value = allocations
                if (allocated < boxesNeeded) {
                    _fifoError.value = "Insufficient stock: only $allocated of $boxesNeeded boxes available for ${_selectedFlavorName.value}"
                } else {
                    _currentWizardStep.value = 4
                }
            }
            result.onFailure { e ->
                _fifoError.value = e.message ?: "Failed to load production batches"
            }
            _fifoLoading.value = false
        }
    }

    fun previousStep() {
        if (_currentWizardStep.value > 1) {
            _currentWizardStep.value--
            _fifoError.value = null
        }
    }

    fun submit() {
        val invoice = _invoiceNumber.value.trim()
        val allocations = _fifoAllocations.value
        if (allocations.isEmpty()) {
            _submitState.value = SubmitState.Error("No FIFO allocation done")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val result = repository.submitDispatch(
                allocations = allocations,
                flavorId = _selectedFlavorId.value,
                invoiceNumber = invoice,
                customerName = _selectedCustomerName.value.ifBlank { null },
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )
            result.onSuccess {
                val total = allocations.sumOf { it.boxesToTake }
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Dispatched $total boxes of ${_selectedFlavorName.value} (invoice $invoice)"
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
        _invoiceNumber.value = ""
        _selectedFlavorId.value = ""
        _selectedFlavorName.value = ""
        _selectedCustomerId.value = ""
        _selectedCustomerName.value = ""
        _boxesNeeded.value = ""
        _fifoAllocations.value = emptyList()
        _fifoError.value = null
        _dispatchDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
