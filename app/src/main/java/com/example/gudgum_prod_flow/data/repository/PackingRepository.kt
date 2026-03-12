package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.CachedBatchDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedBatchEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.SubmitPackingSessionRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackingRepository @Inject constructor(
    private val batchDao: CachedBatchDao,
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    fun getOpenBatches(): Flow<List<CachedBatchEntity>> = batchDao.getOpenBatches()

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
                error("Batch refresh failed: ${response.code()}")
            }
        }
    }

    suspend fun submitPackingSession(
        batchCode: String,
        sessionDate: String,
        workerId: String,
        boxesPacked: Int,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val response = api.insertPackingSession(
                    SubmitPackingSessionRequest(
                        batchCode = batchCode,
                        sessionDate = sessionDate,
                        workerId = workerId,
                        boxesPacked = boxesPacked,
                    )
                )
                if (!response.isSuccessful && response.code() != 201) {
                    error("Packing session insert failed: ${response.code()}")
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
                        quantity = boxesPacked.toDouble(),
                        unit = "boxes",
                        summary = "Packed $boxesPacked boxes for batch $batchCode",
                        payloadJson = JSONObject().apply {
                            put("session_date", sessionDate)
                            put("boxes_packed", boxesPacked)
                        }.toString(),
                    )
                )
            }
        }
    }
}
