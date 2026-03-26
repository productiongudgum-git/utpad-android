package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.local.entity.CachedRecipeLineEntity
import com.example.gudgum_prod_flow.data.remote.dto.SubmitBatchIngredientRequest
import com.example.gudgum_prod_flow.data.repository.ProductionRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import com.example.gudgum_prod_flow.util.BatchCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class FlavorProfile(val id: String, val name: String, val code: String, val recipeId: String?)

data class RecipeIngredient(
    val ingredientId: String,
    val name: String,
    var plannedQty: String,
    var actualQty: String,
    val unit: String,
) {
    val quantity: String get() = actualQty
}

@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val repository: ProductionRepository,
    private val realtimeManager: com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager,
) : ViewModel() {

    companion object {
        /** Batch size (units) the recipe quantities are expressed for. */
        const val BASE_BATCH_UNITS = 7500
        val BATCH_SIZE_OPTIONS = listOf(7500, 10000)
    }

    private val _flavors = MutableStateFlow<List<FlavorProfile>>(emptyList())
    val flavors: StateFlow<List<FlavorProfile>> = _flavors.asStateFlow()

    private val _selectedFlavor = MutableStateFlow<FlavorProfile?>(null)
    val selectedFlavor: StateFlow<FlavorProfile?> = _selectedFlavor.asStateFlow()

    private val _batchCode = MutableStateFlow(BatchCodeGenerator.generate())
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    // Batch size selection — 7500 or 10000 units
    private val _selectedBatchSizeUnits = MutableStateFlow(BASE_BATCH_UNITS)
    val selectedBatchSizeUnits: StateFlow<Int> = _selectedBatchSizeUnits.asStateFlow()

    private val _manufacturingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val manufacturingDate: StateFlow<String> = _manufacturingDate.asStateFlow()

    private val _recipe = MutableStateFlow<List<RecipeIngredient>>(emptyList())
    val recipe: StateFlow<List<RecipeIngredient>> = _recipe.asStateFlow()

    // Base (unscaled) ingredients from the recipe — keyed to BASE_BATCH_UNITS
    private var baseRecipeIngredients: List<RecipeIngredient> = emptyList()

    private val _plannedYield = MutableStateFlow<Double?>(null)
    val plannedYield: StateFlow<Double?> = _plannedYield.asStateFlow()

    // Base planned yield in kg (at BASE_BATCH_UNITS)
    private var basePlannedYieldKg: Double? = null

    val expectedYield: StateFlow<String> = _plannedYield
        .map { it?.let { v -> "%.1f kg".format(v) } ?: "—" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "—")

    private val _actualOutput = MutableStateFlow("")
    val actualOutput: StateFlow<String> = _actualOutput.asStateFlow()

    val flavorProfiles: List<FlavorProfile>
        get() = _flavors.value

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        // Collect flavors from Room cache
        viewModelScope.launch {
            repository.getActiveFlavors().collect { entities ->
                _flavors.value = entities.map {
                    FlavorProfile(
                        id = it.id,
                        name = it.name,
                        code = it.code,
                        recipeId = it.recipeId,
                    )
                }
            }
        }
        // Eagerly refresh from Supabase gg_flavors
        viewModelScope.launch {
            repository.refreshFlavors()
        }

        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                when (table) {
                    "gg_flavors" -> repository.refreshFlavors()
                    "gg_recipes" -> {
                        val currentFlavor = _selectedFlavor.value
                        if (currentFlavor != null) {
                            val recipeKey = currentFlavor.recipeId ?: currentFlavor.id
                            repository.refreshRecipeLines(recipeKey)
                        }
                    }
                }
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun refreshData() {
        viewModelScope.launch { repository.refreshFlavors() }
    }

    fun onFlavorSelected(flavor: FlavorProfile) {
        _selectedFlavor.value = flavor
        _recipe.value = emptyList()
        baseRecipeIngredients = emptyList()

        val recipeKey = flavor.recipeId ?: flavor.id
        viewModelScope.launch {
            if (isOnline) {
                val yieldResult = repository.refreshRecipeLines(recipeKey)
                yieldResult.onSuccess { batchSizeKg ->
                    basePlannedYieldKg = batchSizeKg
                    applyBatchSizeScale()
                }
            }
            repository.getRecipeLines(recipeKey).collect { lines ->
                baseRecipeIngredients = lines.map {
                    RecipeIngredient(
                        ingredientId = it.ingredientId,
                        name = it.ingredientName,
                        plannedQty = it.plannedQty.toString(),
                        actualQty = it.plannedQty.toString(),
                        unit = it.unit,
                    )
                }
                applyBatchSizeScale()
            }
        }
    }

    /** Called when user picks 7500 or 10000 units. Re-scales all ingredient quantities. */
    fun onBatchSizeSelected(sizeUnits: Int) {
        _selectedBatchSizeUnits.value = sizeUnits
        applyBatchSizeScale()
    }

    /** Scales base recipe quantities proportionally to the selected batch size. */
    private fun applyBatchSizeScale() {
        val scale = _selectedBatchSizeUnits.value.toDouble() / BASE_BATCH_UNITS
        _recipe.value = baseRecipeIngredients.map { ing ->
            val baseQty = ing.plannedQty.toDoubleOrNull() ?: 0.0
            val scaledQty = "%.3f".format(baseQty * scale)
            ing.copy(plannedQty = scaledQty, actualQty = scaledQty)
        }
        _plannedYield.value = basePlannedYieldKg?.times(scale)
    }

    fun onActualQtyChanged(index: Int, value: String) {
        val updated = _recipe.value.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(actualQty = value)
            _recipe.value = updated
        }
    }

    fun onRecipeQuantityChanged(index: Int, value: String) = onActualQtyChanged(index, value)
    fun onActualOutputChanged(value: String) { _actualOutput.value = value }
    fun onManufacturingDateChanged(value: String) { _manufacturingDate.value = value }

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    fun submit() {
        val flavor = _selectedFlavor.value
        val batchCode = _batchCode.value

        if (flavor == null) {
            _submitState.value = SubmitState.Error("Select a flavor/SKU first")
            return
        }
        if (batchCode.isBlank()) {
            _submitState.value = SubmitState.Error("Batch code unavailable. Restart the app.")
            return
        }
        if (_recipe.value.isEmpty()) {
            _submitState.value = SubmitState.Error("No recipe ingredients loaded")
            return
        }

        val invalidIngredient = _recipe.value.find { it.actualQty.toDoubleOrNull() == null || it.actualQty.toDouble() < 0 }
        if (invalidIngredient != null) {
            _submitState.value = SubmitState.Error("Invalid quantity for ${invalidIngredient.name}")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading

            val ingredients = _recipe.value.map {
                SubmitBatchIngredientRequest(
                    batchCode = batchCode,
                    ingredientId = it.ingredientId,
                    plannedQty = it.plannedQty.toDoubleOrNull() ?: 0.0,
                    actualQty = it.actualQty.toDouble(),
                )
            }

            val recipeKey = flavor.recipeId ?: flavor.id

            val result = repository.submitBatch(
                batchCode = batchCode,
                skuId = flavor.id,
                skuCode = flavor.code,
                recipeId = recipeKey,
                productionDate = _manufacturingDate.value,
                workerId = WorkerIdentityStore.workerId,
                plannedYield = _plannedYield.value,
                ingredients = ingredients,
                isOnline = isOnline,
            )

            result.onSuccess {
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Batch $batchCode submitted successfully"
                    else "Batch $batchCode saved offline — will sync when connected"
                )
                reset()
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun reset() {
        _selectedFlavor.value = null
        _batchCode.value = BatchCodeGenerator.generate()
        _recipe.value = emptyList()
        baseRecipeIngredients = emptyList()
        basePlannedYieldKg = null
        _selectedBatchSizeUnits.value = BASE_BATCH_UNITS
        _plannedYield.value = null
        _actualOutput.value = ""
        _manufacturingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
