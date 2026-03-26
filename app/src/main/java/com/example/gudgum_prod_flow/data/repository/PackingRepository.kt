package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.GgBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgPackingRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackingRepository @Inject constructor(
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    suspend fun getOpenBatchCodes(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgBatches()
            if (response.isSuccessful) {
                response.body()?.map { it.batchCode } ?: emptyList()
            } else emptyList()
        }
    }

    suspend fun submitPacking(
        batchCode: String,
        quantityKg: Double,
        boxesCount: Int,
        packingDate: String,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                // Look up batch UUID from batch_code
                val batchResponse = api.getGgBatchByCode(batchCode = "eq.$batchCode")
                val batches = if (batchResponse.isSuccessful) batchResponse.body() ?: emptyList() else emptyList()
                val batchId = batches.firstOrNull()?.id
                    ?: error("Batch '$batchCode' not found. Check the code and try again.")

                val response = api.insertGgPacking(
                    GgPackingRequest(
                        batchId = batchId,
                        quantityKg = quantityKg,
                        boxesCount = boxesCount,
                        packingDate = packingDate,
                        recordedBy = workerId,
                    )
                )
                if (!response.isSuccessful && response.code() != 201) {
                    error("Packing insert failed: ${response.code()}")
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "packing",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = batchCode,
                        quantity = quantityKg,
                        unit = "kg",
                        summary = "Packed $boxesCount boxes (${quantityKg}kg) for batch $batchCode",
                        payloadJson = JSONObject().apply {
                            put("batch_code", batchCode)
                            put("quantity_kg", quantityKg)
                            put("boxes_count", boxesCount)
                            put("packing_date", packingDate)
                        }.toString(),
                    )
                )
            }
        }
    }
}
