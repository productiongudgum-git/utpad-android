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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class Ingredient(val id: String, val name: String)
data class Vendor(val id: String, val name: String)

sealed class SubmitState {
    object Idle : SubmitState()
    object Loading : SubmitState()
    data class Success(val message: String) : SubmitState()
    data class Error(val message: String) : SubmitState()
}

@HiltViewModel
class InwardingViewModel @Inject constructor(
    private val pendingDao: PendingOperationEventDao
) : ViewModel() {

    // Hardcoded mock data for demonstration
    private val allIngredients = listOf(
        Ingredient("1", "Gum Base A"),
        Ingredient("2", "Sweetener X"),
        Ingredient("3", "Flavor Mint"),
        Ingredient("4", "Coloring Blue"),
        Ingredient("5", "Preservative E211"),
    )

    val vendors = listOf(
        Vendor("1", "Vendor Alpha"),
        Vendor("2", "Beta Corp"),
        Vendor("3", "Gamma Supplies"),
    )

    // A mock mapping showing which vendor supplies which ingredients (by ID)
    private val vendorIngredientMap = mapOf(
        "1" to listOf("1", "2"), // Alpha supplies Gum Base and Sweetener
        "2" to listOf("3", "4", "5"), // Beta supplies Mint, Color, Preservative
        "3" to listOf("1", "5")  // Gamma supplies Gum Base and Preservative
    )

    val units = listOf("kg", "g", "L", "mL", "pcs", "bags")

    private val _selectedIngredient = MutableStateFlow<Ingredient?>(null)
    val selectedIngredient: StateFlow<Ingredient?> = _selectedIngredient.asStateFlow()

    private val _batchBarcode = MutableStateFlow("")
    val batchBarcode: StateFlow<String> = _batchBarcode.asStateFlow()

    private val _quantity = MutableStateFlow("")
    val quantity: StateFlow<String> = _quantity.asStateFlow()

    private val _selectedUnit = MutableStateFlow("kg")
    val selectedUnit: StateFlow<String> = _selectedUnit.asStateFlow()

    private val _expiryDate = MutableStateFlow("")
    val expiryDate: StateFlow<String> = _expiryDate.asStateFlow()

    private val _selectedVendor = MutableStateFlow<Vendor?>(null)
    val selectedVendor: StateFlow<Vendor?> = _selectedVendor.asStateFlow()

    // Derived state: only show ingredients supplied by the selected vendor, or all if none selected
    val availableIngredients: StateFlow<List<Ingredient>> = combine(
        MutableStateFlow(allIngredients),
        _selectedVendor
    ) { all, vendor ->
        if (vendor == null) {
            all
        } else {
            val suppliedIds = vendorIngredientMap[vendor.id] ?: emptyList()
            all.filter { it.id in suppliedIds }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = allIngredients
    )

    private val _billNumber = MutableStateFlow("")
    val billNumber: StateFlow<String> = _billNumber.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    fun onIngredientSelected(ingredient: Ingredient) { _selectedIngredient.value = ingredient }
    fun onBarcodeChanged(value: String) { _batchBarcode.value = value }
    fun onQuantityChanged(value: String) { _quantity.value = value }
    fun onUnitSelected(unit: String) { _selectedUnit.value = unit }
    fun onExpiryDateChanged(value: String) { _expiryDate.value = value }
    fun onVendorSelected(vendor: Vendor) { 
        _selectedVendor.value = vendor 
        // Auto-clear selected ingredient if it's no longer supplied by the new vendor
        val suppliedIds = vendorIngredientMap[vendor.id] ?: emptyList()
        val currentIngredientId = _selectedIngredient.value?.id
        if (currentIngredientId != null && currentIngredientId !in suppliedIds) {
            _selectedIngredient.value = null
        }
    }
    fun onBillNumberChanged(value: String) { _billNumber.value = value }

    fun reset() {
        _selectedIngredient.value = null
        _batchBarcode.value = ""
        _quantity.value = ""
        _selectedUnit.value = "kg"
        _expiryDate.value = ""
        _selectedVendor.value = null
        _billNumber.value = ""
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

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

    fun submit() {
        if (_selectedIngredient.value == null || _quantity.value.isBlank()) {
            _submitState.value = SubmitState.Error("Please fill in all required fields")
            return
        }
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val quantityValue = _quantity.value.toDoubleOrNull()
            if (quantityValue == null || quantityValue <= 0.0) {
                _submitState.value = SubmitState.Error("Enter a valid quantity")
                return@launch
            }

            val ingredientName = _selectedIngredient.value?.name ?: "Unknown Ingredient"
            val vendorName = _selectedVendor.value?.name ?: "Unknown Vendor"
            val summary = "$ingredientName inwarded from $vendorName"

            val payloadMap = mapOf(
                "ingredientName" to ingredientName,
                "vendorName" to vendorName,
                "billNumber" to _billNumber.value,
                "expiryDate" to _expiryDate.value,
            )
            
            val jsonPayload = JSONObject(payloadMap).toString()

            val entity = PendingOperationEventEntity(
                module = "inwarding",
                workerId = WorkerIdentityStore.workerId,
                workerName = WorkerIdentityStore.workerName,
                workerRole = WorkerIdentityStore.workerRole,
                batchCode = _batchBarcode.value.ifBlank { "N/A" },
                quantity = quantityValue,
                unit = _selectedUnit.value,
                summary = summary,
                payloadJson = jsonPayload
            )

            try {
                pendingDao.insertEvent(entity)
                
                _submitState.value = SubmitState.Success("Inward saved offline. Will sync automatically.")
                _selectedIngredient.value = null
                _batchBarcode.value = ""
                _quantity.value = ""
                _selectedUnit.value = "kg"
                _expiryDate.value = ""
                _selectedVendor.value = null
                _billNumber.value = ""
                _currentWizardStep.value = 1
                
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error("Could not save to local database: ${e.message}")
            }
        }
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
