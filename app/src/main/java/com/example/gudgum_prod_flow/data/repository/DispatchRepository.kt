package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.GgDispatchRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DispatchRepository @Inject constructor(
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    suspend fun getDispatchedBatches(): Result<List<DispatchedBatchDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getDispatchedBatches()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load dispatched batches: ${response.code()}")
        }
    }

    suspend fun getCustomers(): Result<List<GgCustomerDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgCustomers()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load customers: ${response.code()}")
        }
    }

    suspend fun getOpenBatchCodes(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgBatches()
            if (response.isSuccessful) {
                response.body()?.map { it.batchCode } ?: emptyList()
            } else emptyList()
        }
    }

    suspend fun submitDispatch(
        batchCode: String,
        customerId: String,
        quantityDispatched: Int,
        dispatchDate: String,
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

                val response = api.insertGgDispatch(
                    GgDispatchRequest(
                        batchId = batchId,
                        customerId = customerId,
                        quantityDispatched = quantityDispatched,
                        dispatchDate = dispatchDate,
                        recordedBy = workerId,
                    )
                )
                if (!response.isSuccessful && response.code() != 201) {
                    error("Dispatch insert failed: ${response.code()}")
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = batchCode,
                        quantity = quantityDispatched.toDouble(),
                        unit = "units",
                        summary = "Dispatch queued — $quantityDispatched units for batch $batchCode",
                        payloadJson = JSONObject().apply {
                            put("batch_code", batchCode)
                            put("customer_id", customerId)
                            put("quantity_dispatched", quantityDispatched)
                            put("dispatch_date", dispatchDate)
                        }.toString(),
                    )
                )
            }
        }
    }
}
