package com.example.gudgum_prod_flow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity

@Database(
    entities = [PendingOperationEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class GudGumDatabase : RoomDatabase() {
    abstract val pendingOperationEventDao: PendingOperationEventDao
}
