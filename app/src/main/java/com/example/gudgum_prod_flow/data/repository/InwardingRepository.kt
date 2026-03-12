package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.CachedIngredientDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedIngredientEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.SubmitInwardEventRequest
import com.example.gudgum_prod_flow.data.remote.dto.SubmitReturnEventRequest
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
                    CachedIngredientEntity(id = it.id, name = it.name, unit = it.unit, active = it.active)
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
                    error("Inward event insert failed: ${response.code()}")
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
}
