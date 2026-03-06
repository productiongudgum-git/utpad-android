package com.example.gudgum_prod_flow.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.remote.api.OperationsApiClient
import com.example.gudgum_prod_flow.data.remote.dto.SubmitOperationEventRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingDao: PendingOperationEventDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pendingEvents = pendingDao.getAllPendingEvents()
            
            if (pendingEvents.isEmpty()) {
                return@withContext Result.success()
            }

            var allSuccessful = true

            for (event in pendingEvents) {
                // Parse the JSON payload back into a Map
                val payloadMap = try {
                    val jsonObject = JSONObject(event.payloadJson)
                    val map = mutableMapOf<String, String>()
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = jsonObject.get(key).toString()
                    }
                    map
                } catch (e: Exception) {
                    emptyMap<String, String>()
                }

                val request = SubmitOperationEventRequest(
                    module = event.module,
                    workerId = event.workerId,
                    workerName = event.workerName,
                    workerRole = event.workerRole,
                    batchCode = event.batchCode,
                    quantity = event.quantity,
                    unit = event.unit,
                    summary = event.summary,
                    payload = payloadMap
                )

                try {
                    val response = OperationsApiClient.operationsApi.submitOperationEvent(request)
                    if (response.isSuccessful) {
                        // De-queue on success
                        pendingDao.deleteEventById(event.id)
                    } else {
                        // Mark error but don't delete immediately, let it retry
                        allSuccessful = false
                        val updated = event.copy(
                            syncAttemptCount = event.syncAttemptCount + 1,
                            lastSyncError = "HTTP ${response.code()}"
                        )
                        pendingDao.updateEvent(updated)
                    }
                } catch (e: Exception) {
                    allSuccessful = false
                    val updated = event.copy(
                        syncAttemptCount = event.syncAttemptCount + 1,
                        lastSyncError = e.message
                    )
                    pendingDao.updateEvent(updated)
                }
            }

            if (allSuccessful) {
                Result.success()
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            Result.retry()
        }
    }
}
