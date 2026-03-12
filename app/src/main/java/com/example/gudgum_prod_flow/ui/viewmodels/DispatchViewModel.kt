package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.entity.CachedBatchEntity
import com.example.gudgum_prod_flow.data.remote.dto.FifoAllocationLine
import com.example.gudgum_prod_flow.data.repository.DispatchRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val repository: DispatchRepository,
) : ViewModel() {

    private val _packedBatches = MutableStateFlow<List<CachedBatchEntity>>(emptyList())
    val packedBatches: StateFlow<List<CachedBatchEntity>> = _packedBatches.asStateFlow()

    // Group packed batches by SKU for selection
    private val _availableSkus = MutableStateFlow<List<Pair<String, String>>>(emptyList()) // (skuId, skuName)
    val availableSkus: StateFlow<List<Pair<String, String>>> = _availableSkus.asStateFlow()

    private val _selectedSkuId = MutableStateFlow("")
    val selectedSkuId: StateFlow<String> = _selectedSkuId.asStateFlow()

    private val _selectedSkuName = MutableStateFlow("")
    val selectedSkuName: StateFlow<String> = _selectedSkuName.asStateFlow()

    private val _qtyRequested = MutableStateFlow("")
    val qtyRequested: StateFlow<String> = _qtyRequested.asStateFlow()

    // FIFO allocation result — shown to worker before confirmation
    private val _fifoAllocation = MutableStateFlow<List<FifoAllocationLine>>(emptyList())
    val fifoAllocation: StateFlow<List<FifoAllocationLine>> = _fifoAllocation.asStateFlow()

    private val _allocationLoading = MutableStateFlow(false)
    val allocationLoading: StateFlow<Boolean> = _allocationLoading.asStateFlow()

    private val _invoiceNumber = MutableStateFlow("")
    val invoiceNumber: StateFlow<String> = _invoiceNumber.asStateFlow()

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _dispatchDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val dispatchDate: StateFlow<String> = _dispatchDate.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    // Fields expected by DispatchScreen
    private val _batchCode = MutableStateFlow("")
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _qtyTakenFromPacking = MutableStateFlow("")
    val qtyTakenFromPacking: StateFlow<String> = _qtyTakenFromPacking.asStateFlow()

    private val _qtyDispatched = MutableStateFlow("")
    val qtyDispatched: StateFlow<String> = _qtyDispatched.asStateFlow()

    val currentStock: StateFlow<Int> = _packedBatches
        .map { batches -> batches.sumOf { it.totalPacked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val remainingBalance: StateFlow<Int> = combine(_packedBatches, _qtyDispatched) { batches, qty ->
        val stock = batches.sumOf { it.totalPacked }
        val dispatched = qty.toIntOrNull() ?: 0
        stock - dispatched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isPending: StateFlow<Boolean> = remainingBalance
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _destination = MutableStateFlow("")
    val destination: StateFlow<String> = _destination.asStateFlow()

    private val _vehicleNumber = MutableStateFlow("")
    val vehicleNumber: StateFlow<String> = _vehicleNumber.asStateFlow()

    private var isOnline: Boolean = true

    init {
        viewModelScope.launch {
            repository.getPackedBatches().collect { batches ->
                _packedBatches.value = batches
                // Derive unique SKUs
                _availableSkus.value = batches
                    .distinctBy { it.skuId }
                    .map { it.skuId to it.skuName }
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun refreshData() {
        viewModelScope.launch { repository.refreshPackedBatches() }
    }

    fun onSkuSelected(skuId: String, skuName: String) {
        _selectedSkuId.value = skuId
        _selectedSkuName.value = skuName
        _fifoAllocation.value = emptyList() // Reset allocation when SKU changes
    }

    fun onQtyRequestedChanged(value: String) { _qtyRequested.value = value }
    fun onInvoiceNumberChanged(value: String) { _invoiceNumber.value = value }
    fun onCustomerNameChanged(value: String) { _customerName.value = value }
    fun onDispatchDateChanged(value: String) { _dispatchDate.value = value }

    fun onBatchCodeChanged(value: String) { _batchCode.value = value }
    fun onQtyTakenChanged(value: String) { _qtyTakenFromPacking.value = value }
    fun onQtyDispatchedChanged(value: String) { _qtyDispatched.value = value }
    fun onDestinationChanged(value: String) { _destination.value = value }
    fun onVehicleNumberChanged(value: String) { _vehicleNumber.value = value }

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    // Called on step 1→2 to compute FIFO allocation
    fun computeAllocation() {
        val qty = _qtyRequested.value.toIntOrNull()
        if (qty == null || qty <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid quantity (> 0)")
            return
        }
        val skuId = _selectedSkuId.value
        if (skuId.isBlank()) {
            _submitState.value = SubmitState.Error("Select a SKU first")
            return
        }
        if (!isOnline) {
            _submitState.value = SubmitState.Error("Network required for FIFO allocation")
            return
        }
        viewModelScope.launch {
            _allocationLoading.value = true
            val result = repository.getFifoAllocation(skuId = skuId, qty = qty)
            result.onSuccess { lines ->
                if (lines.isEmpty()) {
                    _submitState.value = SubmitState.Error("Insufficient stock for ${_selectedSkuName.value}")
                } else {
                    val totalAllocated = lines.sumOf { it.boxesToTake }
                    if (totalAllocated < qty) {
                        _submitState.value = SubmitState.Error(
                            "Insufficient stock: only $totalAllocated boxes available for ${_selectedSkuName.value}"
                        )
                    } else {
                        _fifoAllocation.value = lines
                        nextStep()
                    }
                }
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Allocation failed")
            }
            _allocationLoading.value = false
        }
    }

    fun submit() {
        if (_invoiceNumber.value.isBlank()) {
            _submitState.value = SubmitState.Error("Invoice number is required")
            return
        }
        val allocation = _fifoAllocation.value
        if (allocation.isEmpty()) {
            _submitState.value = SubmitState.Error("No allocation computed — go back and check quantity")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val result = repository.confirmDispatch(
                allocation = allocation,
                skuId = _selectedSkuId.value,
                invoiceNumber = _invoiceNumber.value,
                customerName = _customerName.value.ifBlank { null },
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )
            result.onSuccess {
                val totalBoxes = allocation.sumOf { it.boxesToTake }
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Dispatched $totalBoxes boxes — invoice ${_invoiceNumber.value}"
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
        _selectedSkuId.value = ""
        _selectedSkuName.value = ""
        _qtyRequested.value = ""
        _fifoAllocation.value = emptyList()
        _invoiceNumber.value = ""
        _customerName.value = ""
        _dispatchDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
        _batchCode.value = ""
        _qtyTakenFromPacking.value = ""
        _qtyDispatched.value = ""
        _destination.value = ""
        _vehicleNumber.value = ""
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
