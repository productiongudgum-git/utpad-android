package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.FifoDispatchAllocation
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.GgFlavorDto
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchForDispatchDto
import com.example.gudgum_prod_flow.data.remote.dto.SubmitDispatchEventRequest
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

    suspend fun getFlavors(): Result<List<GgFlavorDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgFlavors()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load flavors: ${response.code()}")
        }
    }

    suspend fun getCustomers(): Result<List<GgCustomerDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgCustomers()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load customers: ${response.code()}")
        }
    }

    suspend fun getDispatchedBatches(): Result<List<DispatchedBatchDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getDispatchedBatches()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load dispatched batches: ${response.code()}")
        }
    }

    suspend fun getProductionBatchesByFlavor(flavorId: String): Result<List<ProductionBatchForDispatchDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getProductionBatchesByFlavor(flavorId = "eq.$flavorId")
                if (response.isSuccessful) response.body() ?: emptyList()
                else error("Failed to load production batches: ${response.code()}")
            }
        }

    /** Pure FIFO allocation — batches must already be sorted by production_date ASC. */
    fun allocateFifo(
        batches: List<ProductionBatchForDispatchDto>,
        boxesNeeded: Int,
    ): List<FifoDispatchAllocation> {
        val allocations = mutableListOf<FifoDispatchAllocation>()
        var remaining = boxesNeeded
        for (batch in batches) {
            if (remaining <= 0) break
            val available = batch.expectedBoxes ?: 0
            if (available <= 0) continue
            val take = minOf(remaining, available)
            allocations.add(
                FifoDispatchAllocation(
                    batchCode = batch.batchCode,
                    productionDate = batch.productionDate,
                    boxesToTake = take,
                    boxesAvailable = available,
                )
            )
            remaining -= take
        }
        return allocations
    }

    suspend fun submitDispatch(
        allocations: List<FifoDispatchAllocation>,
        flavorId: String,
        invoiceNumber: String,
        customerName: String?,
        dispatchDate: String,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                for (line in allocations) {
                    val response = api.insertDispatchEvent(
                        SubmitDispatchEventRequest(
                            batchCode = line.batchCode,
                            skuId = flavorId,
                            boxesDispatched = line.boxesToTake,
                            customerName = customerName,
                            invoiceNumber = invoiceNumber,
                            dispatchDate = dispatchDate,
                            workerId = workerId,
                        )
                    )
                    if (!response.isSuccessful && response.code() != 201) {
                        error("Dispatch insert failed for ${line.batchCode}: ${response.code()}")
                    }
                }
            }
        } else {
            runCatching {
                val totalBoxes = allocations.sumOf { it.boxesToTake }
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = allocations.firstOrNull()?.batchCode ?: "",
                        quantity = totalBoxes.toDouble(),
                        unit = "boxes",
                        summary = "Dispatch queued — $totalBoxes boxes, invoice $invoiceNumber",
                        payloadJson = JSONObject().apply {
                            put("invoice_number", invoiceNumber)
                            put("flavor_id", flavorId)
                            put("customer_name", customerName ?: "")
                            put("total_boxes", totalBoxes)
                            put("dispatch_date", dispatchDate)
                        }.toString(),
                    )
                )
            }
        }
    }
}
