package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.CachedIngredientDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedIngredientEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.CreateIngredientRequest
import com.example.gudgum_prod_flow.data.remote.dto.CreateIngredientSupplierLinkRequest
import com.example.gudgum_prod_flow.data.remote.dto.CreateSupplierRequest
import com.example.gudgum_prod_flow.data.remote.dto.IngredientDto
import com.example.gudgum_prod_flow.data.remote.dto.SubmitInwardEventRequest
import com.example.gudgum_prod_flow.data.remote.dto.SubmitReturnEventRequest
import com.example.gudgum_prod_flow.data.remote.dto.SupplierDto
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InwardingRepository @Inject constructor(
    private val ingredientDao: CachedIngredientDao,
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    fun getActiveIngredients(): Flow<List<CachedIngredientEntity>> =
        ingredientDao.getActiveIngredients()

    suspend fun refreshIngredients(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getIngredients()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                ingredientDao.deleteAll()
                ingredientDao.insertAll(dtos.map {
                    CachedIngredientEntity(
                        id = it.id,
                        name = it.name,
                        unit = it.unit,
                        active = it.active,
                        defaultSupplierName = it.defaultSupplierName,
                    )
                })
            } else {
                error("Ingredient refresh failed: ${response.code()}")
            }
        }
    }

    suspend fun submitInwardEvent(
        request: SubmitInwardEventRequest,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val response = api.insertInwardEvent(request)
                if (!response.isSuccessful && response.code() != 201) {
                    val body = response.errorBody()?.string() ?: ""
                    error("Inward event insert failed: ${response.code()} | $body")
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "inwarding",
                        workerId = request.workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = request.lotRef ?: "N/A",
                        quantity = request.qty,
                        unit = request.unit,
                        summary = "Inward event queued for ingredient ${request.ingredientId}",
                        payloadJson = JSONObject().apply {
                            put("ingredient_id", request.ingredientId)
                            put("qty", request.qty)
                            put("unit", request.unit)
                            put("inward_date", request.inwardDate)
                            put("expiry_date", request.expiryDate ?: JSONObject.NULL)
                            put("lot_ref", request.lotRef ?: JSONObject.NULL)
                            put("supplier", request.supplier ?: JSONObject.NULL)
                        }.toString(),
                    )
                )
            }
        }
    }

    suspend fun submitReturnEvent(
        request: SubmitReturnEventRequest,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val response = api.insertReturnEvent(request)
                if (!response.isSuccessful && response.code() != 201) {
                    error("Return event insert failed: ${response.code()}")
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "returns",
                        workerId = request.workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = request.batchCode,
                        quantity = request.qtyReturned.toDouble(),
                        unit = "boxes",
                        summary = "Return queued for batch ${request.batchCode}",
                        payloadJson = JSONObject().apply {
                            put("batch_code", request.batchCode)
                            put("sku_id", request.skuId)
                            put("qty_returned", request.qtyReturned)
                            put("reason", request.reason ?: JSONObject.NULL)
                            put("return_date", request.returnDate)
                        }.toString(),
                    )
                )
            }
        }
    }

    suspend fun getSuppliers(): List<SupplierDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSuppliers()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createSupplier(name: String, contact: String? = null): Result<SupplierDto> = withContext(Dispatchers.IO) {
        runCatching {
            val id = "sup-${System.currentTimeMillis()}"
            val response = api.insertSupplier(CreateSupplierRequest(id = id, name = name, contact = contact))
            if (!response.isSuccessful) error("Failed to create supplier: ${response.code()}")
            response.body()?.firstOrNull() ?: SupplierDto(id = id, name = name, contact = contact)
        }
    }

    suspend fun createIngredient(
        name: String,
        unit: String,
        supplierId: String? = null,
        supplierName: String? = null,
    ): Result<CachedIngredientEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val id = "ing-${System.currentTimeMillis()}"
            val response = api.insertIngredient(
                CreateIngredientRequest(
                    id = id,
                    name = name,
                    unit = unit,
                    defaultSupplierId = supplierId,
                    defaultSupplierName = supplierName,
                )
            )
            if (!response.isSuccessful) error("Failed to create ingredient: ${response.code()}")
            val dto = response.body()?.firstOrNull()
                ?: IngredientDto(id = id, name = name, unit = unit, defaultSupplierId = supplierId, defaultSupplierName = supplierName)
            val entity = CachedIngredientEntity(
                id = dto.id,
                name = dto.name,
                unit = dto.unit,
                active = dto.active,
                defaultSupplierName = dto.defaultSupplierName,
            )
            ingredientDao.insertAll(listOf(entity))
            // Also record the link in ingredient_suppliers junction table
            if (supplierId != null) {
                val linkId = "isl-${System.currentTimeMillis()}"
                api.insertIngredientSupplierLink(
                    CreateIngredientSupplierLinkRequest(
                        id = linkId,
                        ingredientId = id,
                        supplierId = supplierId,
                        isDefault = true,
                    )
                )
            }
            entity
        }
    }
}
