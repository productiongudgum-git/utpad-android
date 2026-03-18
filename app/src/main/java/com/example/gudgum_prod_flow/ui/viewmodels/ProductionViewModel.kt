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

    private val _flavors = MutableStateFlow<List<FlavorProfile>>(emptyList())
    val flavors: StateFlow<List<FlavorProfile>> = _flavors.asStateFlow()

    private val _selectedFlavor = MutableStateFlow<FlavorProfile?>(null)
    val selectedFlavor: StateFlow<FlavorProfile?> = _selectedFlavor.asStateFlow()

    private val _batchCode = MutableStateFlow(BatchCodeGenerator.generate())
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _manufacturingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val manufacturingDate: StateFlow<String> = _manufacturingDate.asStateFlow()

    private val _recipe = MutableStateFlow<List<RecipeIngredient>>(emptyList())
    val recipe: StateFlow<List<RecipeIngredient>> = _recipe.asStateFlow()

    private val _plannedYield = MutableStateFlow<Double?>(null)
    val plannedYield: StateFlow<Double?> = _plannedYield.asStateFlow()

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
                        recipeId = it.recipeId, // null for gg_flavors — we use flavor.id instead
                    )
                }
            }
        }
        // Eagerly refresh from Supabase gg_flavors so dashboard data appears
        viewModelScope.launch {
            repository.refreshFlavors()
        }

        // --- Supabase Realtime: auto-refresh when dashboard makes changes ---
        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                when (table) {
                    "gg_flavors" -> {
                        repository.refreshFlavors()
                    }
                    "gg_recipes" -> {
                        // If a flavor is currently selected, refresh its recipe lines too
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

    // Called when module opens and network is available
    fun refreshData() {
        viewModelScope.launch {
            repository.refreshFlavors()
        }
    }

    fun onFlavorSelected(flavor: FlavorProfile) {
        _selectedFlavor.value = flavor
        _recipe.value = emptyList()

        // Load recipe lines using flavorId (gg_recipes uses flavor_id, not recipe_id)
        val recipeKey = flavor.recipeId ?: flavor.id
        viewModelScope.launch {
            if (isOnline) {
                val yieldResult = repository.refreshRecipeLines(recipeKey)
                yieldResult.onSuccess { batchSizeKg ->
                    _plannedYield.value = batchSizeKg
                }
            }
            repository.getRecipeLines(recipeKey).collect { lines ->
                _recipe.value = lines.map {
                    RecipeIngredient(
                        ingredientId = it.ingredientId,
                        name = it.ingredientName,
                        plannedQty = it.plannedQty.toString(),
                        actualQty = it.plannedQty.toString(), // Default actual = planned
                        unit = it.unit,
                    )
                }
            }
        }
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
        _plannedYield.value = null
        _actualOutput.value = ""
        _manufacturingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
