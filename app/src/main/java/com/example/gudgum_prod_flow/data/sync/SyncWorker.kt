package com.example.gudgum_prod_flow.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.remote.api.OperationsApiClient
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingDao: PendingOperationEventDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pendingEvents = pendingDao.getAllPendingEvents()
            if (pendingEvents.isEmpty()) return@withContext Result.success()

            var allSuccessful = true

            for (event in pendingEvents) {
                val success = when (event.module) {
                    "production" -> syncProductionBatch(event.payloadJson, event.batchCode)
                    "packing"    -> syncPackingSession(event.payloadJson, event.batchCode, event.workerId)
                    "dispatch"   -> syncDispatchEvents(event.payloadJson, event.workerId)
                    "inwarding"  -> syncInwardEvent(event.payloadJson, event.workerId)
                    "returns"    -> syncReturnEvent(event.payloadJson, event.workerId)
                    else         -> syncLegacyOpsEvent(event)  // fallback for old-format events
                }

                if (success) {
                    pendingDao.deleteEventById(event.id)
                } else {
                    allSuccessful = false
                    if (event.syncAttemptCount >= 3) {
                        // After 3 failures, mark as permanently failed (keep for admin review)
                        pendingDao.updateEvent(event.copy(
                            syncAttemptCount = event.syncAttemptCount + 1,
                            lastSyncError = "Max retries exceeded",
                        ))
                    } else {
                        pendingDao.updateEvent(event.copy(
                            syncAttemptCount = event.syncAttemptCount + 1,
                        ))
                    }
                }
            }

            if (allSuccessful) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncProductionBatch(payloadJson: String, batchCode: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val ingredientsJson = payload.optJSONArray("ingredients") ?: JSONArray()

            val ingredients = (0 until ingredientsJson.length()).map { i ->
                val ing = ingredientsJson.getJSONObject(i)
                SubmitBatchIngredientRequest(
                    batchCode = batchCode,
                    ingredientId = ing.getString("ingredient_id"),
                    plannedQty = ing.getDouble("planned_qty"),
                    actualQty = ing.getDouble("actual_qty"),
                )
            }

            // Insert ingredients first (trigger reads them)
            val ingResp = SupabaseApiClient.api.insertBatchIngredients(ingredients)
            if (!ingResp.isSuccessful && ingResp.code() != 201) return false

            val batchResp = SupabaseApiClient.api.insertProductionBatch(
                SubmitProductionBatchRequest(
                    batchCode = batchCode,
                    skuId = payload.getString("sku_id"),
                    recipeId = payload.getString("recipe_id"),
                    productionDate = payload.getString("production_date"),
                    workerId = payload.optString("worker_id", ""),
                    plannedYield = if (payload.isNull("planned_yield")) null else payload.optDouble("planned_yield"),
                )
            )
            batchResp.isSuccessful || batchResp.code() == 201
        } catch (e: Exception) { false }
    }

    private suspend fun syncPackingSession(payloadJson: String, batchCode: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val resp = SupabaseApiClient.api.insertPackingSession(
                SubmitPackingSessionRequest(
                    batchCode = batchCode,
                    sessionDate = payload.getString("session_date"),
                    workerId = workerId,
                    boxesPacked = payload.getInt("boxes_packed"),
                )
            )
            resp.isSuccessful || resp.code() == 201
        } catch (e: Exception) { false }
    }

    private suspend fun syncDispatchEvents(payloadJson: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val allocationJson = payload.getJSONArray("allocation")
            var allOk = true
            for (i in 0 until allocationJson.length()) {
                val line = allocationJson.getJSONObject(i)
                val resp = SupabaseApiClient.api.insertDispatchEvent(
                    SubmitDispatchEventRequest(
                        batchCode = line.getString("batch_code"),
                        skuId = payload.getString("sku_id"),
                        boxesDispatched = line.getInt("boxes_to_take"),
                        customerName = if (payload.isNull("customer_name")) null else payload.getString("customer_name"),
                        invoiceNumber = payload.getString("invoice_number"),
                        dispatchDate = payload.getString("dispatch_date"),
                        workerId = workerId,
                    )
                )
                if (!resp.isSuccessful && resp.code() != 201) allOk = false
            }
            allOk
        } catch (e: Exception) { false }
    }

    private suspend fun syncInwardEvent(payloadJson: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val resp = SupabaseApiClient.api.insertInwardEvent(
                SubmitInwardEventRequest(
                    ingredientId = payload.getString("ingredient_id"),
                    qty = payload.getDouble("qty"),
                    unit = payload.getString("unit"),
                    inwardDate = payload.getString("inward_date"),
                    expiryDate = if (payload.isNull("expiry_date")) null else payload.getString("expiry_date"),
                    lotRef = if (payload.isNull("lot_ref")) null else payload.getString("lot_ref"),
                    supplier = if (payload.isNull("supplier")) null else payload.getString("supplier"),
                    workerId = workerId,
                )
            )
            resp.isSuccessful || resp.code() == 201
        } catch (e: Exception) { false }
    }

    private suspend fun syncReturnEvent(payloadJson: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val resp = SupabaseApiClient.api.insertReturnEvent(
                SubmitReturnEventRequest(
                    batchCode = payload.getString("batch_code"),
                    skuId = payload.getString("sku_id"),
                    qtyReturned = payload.getInt("qty_returned"),
                    reason = if (payload.isNull("reason")) null else payload.getString("reason"),
                    returnDate = payload.getString("return_date"),
                    workerId = workerId,
                )
            )
            resp.isSuccessful || resp.code() == 201
        } catch (e: Exception) { false }
    }

    private suspend fun syncLegacyOpsEvent(event: com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity): Boolean {
        return try {
            val payloadMap = try {
                val jsonObject = JSONObject(event.payloadJson)
                val map = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.get(key).toString()
                }
                map
            } catch (e: Exception) { emptyMap() }

            val resp = OperationsApiClient.operationsApi.submitOperationEvent(
                SubmitOperationEventRequest(
                    module = event.module,
                    workerId = event.workerId,
                    workerName = event.workerName,
                    workerRole = event.workerRole,
                    batchCode = event.batchCode,
                    quantity = event.quantity,
                    unit = event.unit,
                    summary = event.summary,
                    payload = payloadMap,
                )
            )
            resp.isSuccessful
        } catch (e: Exception) { false }
    }
}
