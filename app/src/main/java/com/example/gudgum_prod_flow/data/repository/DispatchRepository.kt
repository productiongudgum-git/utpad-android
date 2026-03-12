package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.CachedBatchDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedBatchEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.*
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DispatchRepository @Inject constructor(
    private val batchDao: CachedBatchDao,
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    fun getPackedBatches(): Flow<List<CachedBatchEntity>> = batchDao.getPackedBatches()

    suspend fun refreshPackedBatches(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getPackedBatches()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                dtos.forEach { dto ->
                    batchDao.upsertBatch(
                        CachedBatchEntity(
                            batchCode = dto.batchCode,
                            skuId = dto.skuId,
                            skuName = dto.skuName ?: dto.skuId,
                            skuCode = "",
                            productionDate = dto.productionDate,
                            status = dto.status,
                            plannedYield = dto.plannedYield,
                            totalPacked = dto.actualYield?.toInt() ?: 0,
                        )
                    )
                }
            } else {
                error("Packed batch refresh failed: ${response.code()}")
            }
        }
    }

    // Calls FIFO RPC — requires network (live inventory data needed)
    suspend fun getFifoAllocation(skuId: String, qty: Int): Result<List<FifoAllocationLine>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.fifoAllocate(FifoAllocationRequest(skuId = skuId, qty = qty))
                if (response.isSuccessful) {
                    response.body() ?: emptyList()
                } else {
                    error("FIFO allocation failed: ${response.code()}")
                }
            }
        }

    // Inserts one dispatch_event per allocation line — online or offline queue
    suspend fun confirmDispatch(
        allocation: List<FifoAllocationLine>,
        skuId: String,
        invoiceNumber: String,
        customerName: String?,
        dispatchDate: String,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                allocation.forEach { line ->
                    val response = api.insertDispatchEvent(
                        SubmitDispatchEventRequest(
                            batchCode = line.batchCode,
                            skuId = skuId,
                            boxesDispatched = line.boxesToTake,
                            customerName = customerName,
                            invoiceNumber = invoiceNumber,
                            dispatchDate = dispatchDate,
                            workerId = workerId,
                        )
                    )
                    if (!response.isSuccessful && response.code() != 201) {
                        error("Dispatch insert failed for batch ${line.batchCode}: ${response.code()}")
                    }
                }
            }
        } else {
            runCatching {
                val allocationArray = JSONArray()
                allocation.forEach { line ->
                    allocationArray.put(JSONObject().apply {
                        put("batch_code", line.batchCode)
                        put("boxes_to_take", line.boxesToTake)
                    })
                }
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = allocation.firstOrNull()?.batchCode ?: "N/A",
                        quantity = allocation.sumOf { it.boxesToTake }.toDouble(),
                        unit = "boxes",
                        summary = "Dispatch queued — invoice $invoiceNumber",
                        payloadJson = JSONObject().apply {
                            put("sku_id", skuId)
                            put("invoice_number", invoiceNumber)
                            put("customer_name", customerName ?: JSONObject.NULL)
                            put("dispatch_date", dispatchDate)
                            put("allocation", allocationArray)
                        }.toString(),
                    )
                )
            }
        }
    }

    suspend fun getDispatchedBatches(): Result<List<DispatchedBatchDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getDispatchedBatches()
                if (response.isSuccessful) response.body() ?: emptyList()
                else error("Dispatched batches fetch failed: ${response.code()}")
            }
        }
}
