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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class FlavorProfile(val id: String, val name: String, val code: String)
data class RecipeIngredient(val name: String, var quantity: String, val unit: String)

@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val pendingDao: PendingOperationEventDao
) : ViewModel() {

    val flavorProfiles = listOf(
        FlavorProfile("1", "Spearmint Blast", "MNT"),
        FlavorProfile("2", "Strawberry Burst", "STR"),
        FlavorProfile("3", "Orange Zest", "ORG"),
        FlavorProfile("4", "Watermelon Wave", "WTR"),
    )

    private var batchSequence = 1

    private val _selectedFlavor = MutableStateFlow<FlavorProfile?>(null)
    val selectedFlavor: StateFlow<FlavorProfile?> = _selectedFlavor.asStateFlow()

    private val _batchCode = MutableStateFlow("")
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _manufacturingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val manufacturingDate: StateFlow<String> = _manufacturingDate.asStateFlow()

    private val _recipe = MutableStateFlow<List<RecipeIngredient>>(emptyList())
    val recipe: StateFlow<List<RecipeIngredient>> = _recipe.asStateFlow()

    private val _expectedYield = MutableStateFlow("0.0 kg")
    val expectedYield: StateFlow<String> = _expectedYield.asStateFlow()

    private val recipeMap = mapOf(
        "1" to Pair(
            listOf(
                RecipeIngredient("Gum Base A", "45.0", "kg"),
                RecipeIngredient("Sweetener X", "12.5", "kg"),
                RecipeIngredient("Flavor Spearmint", "3.0", "kg"),
            ), "60.5 kg"
        ),
        "2" to Pair(
            listOf(
                RecipeIngredient("Gum Base B", "40.0", "kg"),
                RecipeIngredient("Sweetener Y", "15.0", "kg"),
                RecipeIngredient("Flavor Strawberry", "4.0", "kg"),
            ), "59.0 kg"
        ),
        "3" to Pair(
            listOf(
                RecipeIngredient("Gum Base A", "45.0", "kg"),
                RecipeIngredient("Sweetener X", "10.0", "kg"),
                RecipeIngredient("Flavor Orange", "5.0", "kg"),
                RecipeIngredient("Coloring Orange", "0.2", "kg"),
            ), "60.2 kg"
        ),
        "4" to Pair(
            listOf(
                RecipeIngredient("Gum Base C", "35.0", "kg"),
                RecipeIngredient("Sweetener Z", "20.0", "kg"),
                RecipeIngredient("Flavor Watermelon", "4.5", "kg"),
                RecipeIngredient("Coloring Red", "0.3", "kg"),
            ), "59.8 kg"
        )
    )

    private val _actualOutput = MutableStateFlow("")
    val actualOutput: StateFlow<String> = _actualOutput.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    fun onFlavorSelected(flavor: FlavorProfile) {
        _selectedFlavor.value = flavor
        _batchCode.value = generateBatchCode(flavor.code)
        
        // Intelligent Defaults: Auto-populate ingredients based on selected recipe
        val recipeData = recipeMap[flavor.id]
        if (recipeData != null) {
            _recipe.value = recipeData.first.map { it.copy() } // Deep copy to allow editing
            _expectedYield.value = recipeData.second
        } else {
            _recipe.value = emptyList()
            _expectedYield.value = "0.0 kg"
        }
    }

    fun generateBatchCode(flavorCode: String): String {
        val date = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())
        val sequence = batchSequence.toString().padStart(2, '0')
        batchSequence += 1
        return "$date-$flavorCode-$sequence"
    }

    fun onRecipeQuantityChanged(index: Int, value: String) {
        val updated = _recipe.value.toMutableList()
        updated[index] = updated[index].copy(quantity = value)
        _recipe.value = updated
    }

    fun onManufacturingDateChanged(value: String) { _manufacturingDate.value = value }
    fun onActualOutputChanged(value: String) { _actualOutput.value = value }

    fun reset() {
        _selectedFlavor.value = null
        _batchCode.value = ""
        _recipe.value = emptyList()
        _expectedYield.value = "0.0 kg"
        _manufacturingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _actualOutput.value = ""
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
        if (_selectedFlavor.value == null || _actualOutput.value.isBlank()) {
            _submitState.value = SubmitState.Error("Please select a flavor and enter actual output")
            return
        }
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val actualOutput = _actualOutput.value.toDoubleOrNull()
            if (actualOutput == null || actualOutput <= 0.0) {
                _submitState.value = SubmitState.Error("Enter a valid production output")
                return@launch
            }

            val expected = _expectedYield.value.substringBefore(" ").toDoubleOrNull() ?: 0.0
            val wastage = (expected - actualOutput).coerceAtLeast(0.0)
            val flavorName = _selectedFlavor.value?.name ?: "Unknown Flavor"

            val payloadMap = mapOf(
                "expectedYieldKg" to expected.toString(),
                "actualOutputKg" to actualOutput.toString(),
                "wastageKg" to wastage.toString(),
                "manufacturingDate" to _manufacturingDate.value,
            )
            val jsonPayload = JSONObject(payloadMap).toString()

            val entity = PendingOperationEventEntity(
                module = "production",
                workerId = WorkerIdentityStore.workerId,
                workerName = WorkerIdentityStore.workerName,
                workerRole = WorkerIdentityStore.workerRole,
                batchCode = _batchCode.value.ifBlank { "N/A" },
                quantity = actualOutput,
                unit = "kg",
                summary = "$flavorName batch processed",
                payloadJson = jsonPayload
            )

            try {
                pendingDao.insertEvent(entity)
                _submitState.value = SubmitState.Success("Production batch ${_batchCode.value} saved offline.")
                reset()
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error("Could not save offline: ${e.message}")
            }
        }
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
