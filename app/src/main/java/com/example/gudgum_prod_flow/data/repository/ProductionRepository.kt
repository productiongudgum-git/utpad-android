package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.CachedBatchDao
import com.example.gudgum_prod_flow.data.local.dao.CachedFlavorDao
import com.example.gudgum_prod_flow.data.local.dao.CachedRecipeLineDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedBatchEntity
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.local.entity.CachedRecipeLineEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.*
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionRepository @Inject constructor(
    private val flavorDao: CachedFlavorDao,
    private val recipeLineDao: CachedRecipeLineDao,
    private val batchDao: CachedBatchDao,
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    companion object {
        private const val TAG = "ProductionRepository"
    }

    // Returns flavors from Room cache; call refreshFlavors() when online to populate
    fun getActiveFlavors(): Flow<List<CachedFlavorEntity>> = flavorDao.getActiveFlavors()

    // Returns BOM lines from Room cache for the given flavorId
    fun getRecipeLines(flavorId: String): Flow<List<CachedRecipeLineEntity>> =
        recipeLineDao.getByRecipeId(flavorId)

    // Returns open batches from Room cache
    fun getOpenBatches(): Flow<List<CachedBatchEntity>> = batchDao.getOpenBatches()

    // Refreshes flavor cache from Supabase gg_flavors table
    suspend fun refreshFlavors(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgFlavors()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                flavorDao.deleteAll()
                flavorDao.insertAll(dtos.map {
                    CachedFlavorEntity(
                        id = it.id,
                        name = it.name,
                        code = it.code,
                        recipeId = null, // gg_flavors has no recipeId; we use flavor.id to load recipes
                        active = it.active,
                        yieldThreshold = null,
                        shelfLifeDays = null,
                    )
                })
            } else {
                error("Flavor refresh failed: ${response.code()}")
            }
        }
    }

    // Refreshes recipe lines for a specific flavor from gg_recipes table
    // Returns the recipe id and batch_size_kg from the recipe for expected yield display
    suspend fun refreshRecipeLines(flavorId: String): Result<Pair<String?, Double?>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgRecipe(flavorId = "eq.$flavorId")
            if (response.isSuccessful) {
                val recipes = response.body() ?: emptyList()
                val targetRecipe = recipes.firstOrNull()

                recipeLineDao.deleteByRecipeId(flavorId)

                if (targetRecipe != null) {
                    recipeLineDao.insertAll(targetRecipe.ingredients.map { ingredient ->
                        CachedRecipeLineEntity(
                            recipeId = flavorId, // Use flavorId as the recipe key
                            ingredientId = ingredient.ingredientId,
                            ingredientName = ingredient.ingredientName,
                            plannedQty = ingredient.quantity,
                            unit = ingredient.unit,
                        )
                    })
                }

                Pair(targetRecipe?.id, targetRecipe?.batchSizeKg)
            } else {
                error("Recipe line refresh failed: ${response.code()}")
            }
        }
    }

    // Refreshes open batch cache from Supabase
    suspend fun refreshOpenBatches(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getOpenBatches()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                batchDao.deleteAll()
                batchDao.upsertAll(dtos.map {
                    CachedBatchEntity(
                        batchCode = it.batchCode,
                        skuId = it.skuId,
                        skuName = it.skuName ?: it.skuId,
                        skuCode = "",
                        productionDate = it.productionDate,
                        status = it.status,
                        plannedYield = it.plannedYield,
                        totalPacked = it.actualYield?.toInt() ?: 0,
                    )
                })
            } else {
                error("Open batches refresh failed: ${response.code()}")
            }
        }
    }

    // Submits a production batch — online writes directly; offline queues for sync
    // IMPORTANT: Inserts ingredients FIRST (so DB trigger fn_deduct_raw_materials can read them)
    suspend fun submitBatch(
        batchCode: String,
        skuId: String,
        skuCode: String,
        recipeId: String,
        productionDate: String,
        workerId: String,
        plannedYield: Double?,
        ingredients: List<SubmitBatchIngredientRequest>,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isOnline) {
                // 1. Insert ingredients first (trigger reads them)
                val ingredientsBody = JSONArray().also { arr ->
                    ingredients.forEach { ing ->
                        arr.put(JSONObject().apply {
                            put("batch_code", ing.batchCode)
                            put("ingredient_id", ing.ingredientId)
                            put("planned_qty", ing.plannedQty)
                            put("actual_qty", ing.actualQty)
                        })
                    }
                }.toString()
                // Delete existing ingredients first to avoid uq_batch_ingredient conflict
                // (Supabase merge-duplicates only works on primary key, not on secondary unique constraints)
                Log.d(TAG, "── deleteBatchIngredients ──────────────────────────────")
                Log.d(TAG, "URL:     DELETE ${SupabaseApiClient.BASE_URL}rest/v1/production_batch_ingredients?batch_code=eq.$batchCode")
                val deleteResp = api.deleteBatchIngredients(batchCode = "eq.$batchCode")
                Log.d(TAG, "Status:  ${deleteResp.code()}")

                Log.d(TAG, "── insertBatchIngredients ──────────────────────────────")
                Log.d(TAG, "URL:     POST ${SupabaseApiClient.BASE_URL}rest/v1/production_batch_ingredients")
                Log.d(TAG, "Headers: apikey=<anon_key>, Authorization=Bearer <anon_key>, Prefer=return=minimal")
                Log.d(TAG, "Body:    $ingredientsBody")

                val ingredientsResp = api.insertBatchIngredients(ingredients)
                val ingredientsError = ingredientsResp.errorBody()?.string()
                Log.d(TAG, "Status:  ${ingredientsResp.code()}")
                Log.d(TAG, "Body:    ${ingredientsError ?: "<empty — success>"}")

                if (!ingredientsResp.isSuccessful && ingredientsResp.code() != 201) {
                    error("Failed to insert batch ingredients: ${ingredientsResp.code()} — $ingredientsError")
                }

                // 2. Insert batch row (triggers fn_deduct_raw_materials)
                val batchBody = JSONObject().apply {
                    put("batch_code", batchCode)
                    put("sku_id", skuId)
                    put("recipe_id", recipeId)
                    put("production_date", productionDate)
                    put("worker_id", workerId)
                    put("planned_yield", plannedYield ?: JSONObject.NULL)
                }.toString()
                Log.d(TAG, "── insertProductionBatch ───────────────────────────────")
                Log.d(TAG, "URL:     POST ${SupabaseApiClient.BASE_URL}rest/v1/production_batches")
                Log.d(TAG, "Headers: apikey=<anon_key>, Authorization=Bearer <anon_key>, Prefer=return=minimal,resolution=merge-duplicates")
                Log.d(TAG, "Body:    $batchBody")

                val batchResp = api.insertProductionBatch(
                    SubmitProductionBatchRequest(
                        batchCode = batchCode,
                        skuId = skuId,
                        recipeId = recipeId,
                        productionDate = productionDate,
                        workerId = workerId,
                        plannedYield = plannedYield,
                    )
                )
                val batchError = batchResp.errorBody()?.string()
                Log.d(TAG, "Status:  ${batchResp.code()}")
                Log.d(TAG, "Body:    ${batchError ?: "<empty — success>"}")

                if (!batchResp.isSuccessful && batchResp.code() != 201) {
                    error("Failed to insert production batch: ${batchResp.code()} — $batchError")
                }

                // 3. Insert into gg_batches only if no record exists for this batch_code
                // (re-submitting the same day must not generate a new UUID, which would cause a FK conflict)
                Log.d(TAG, "── checkGgBatch ────────────────────────────────────────")
                val existingBatchResp = api.getGgBatchByCode(batchCode = "eq.$batchCode")
                val existingBatch = if (existingBatchResp.isSuccessful) existingBatchResp.body()?.firstOrNull() else null
                if (existingBatch != null) {
                    Log.d(TAG, "gg_batches record already exists for $batchCode — skipping insert")
                } else {
                    Log.d(TAG, "── insertGgBatch ───────────────────────────────────────")
                    Log.d(TAG, "URL:     POST ${SupabaseApiClient.BASE_URL}rest/v1/gg_batches")
                    val ggBatchResp = api.insertGgBatch(
                        GgBatchInsertRequest(
                            id = java.util.UUID.randomUUID().toString(),
                            batchCode = batchCode,
                            flavorId = skuId,
                            recipeId = recipeId,
                            createdBy = workerId,
                        )
                    )
                    val ggBatchError = ggBatchResp.errorBody()?.string()
                    Log.d(TAG, "Status:  ${ggBatchResp.code()}")
                    Log.d(TAG, "Body:    ${ggBatchError ?: "<empty — success>"}")
                    if (!ggBatchResp.isSuccessful && ggBatchResp.code() != 201) {
                        error("Failed to insert gg_batches: ${ggBatchResp.code()} — $ggBatchError")
                    }
                }
            } else {
                // Queue for sync
                val ingredientsArray = JSONArray()
                ingredients.forEach { ing ->
                    ingredientsArray.put(JSONObject().apply {
                        put("batch_code", batchCode)
                        put("ingredient_id", ing.ingredientId)
                        put("planned_qty", ing.plannedQty)
                        put("actual_qty", ing.actualQty)
                    })
                }
                val payload = JSONObject().apply {
                    put("sku_id", skuId)
                    put("sku_code", skuCode)
                    put("recipe_id", recipeId)
                    put("production_date", productionDate)
                    put("planned_yield", plannedYield ?: JSONObject.NULL)
                    put("ingredients", ingredientsArray)
                }
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "production",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = batchCode,
                        quantity = plannedYield ?: 0.0,
                        unit = "boxes",
                        summary = "Production batch $batchCode queued",
                        payloadJson = payload.toString(),
                    )
                )
            }
        }
    }
}

// Import for WorkerIdentityStore
private val WorkerIdentityStore get() = com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
