package com.example.gudgum_prod_flow.di

import android.content.Context
import androidx.room.Room
import com.example.gudgum_prod_flow.data.local.GudGumDatabase
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGudGumDatabase(
        @ApplicationContext context: Context
    ): GudGumDatabase {
        return Room.databaseBuilder(
            context,
            GudGumDatabase::class.java,
            "gudgum_database"
        ).build()
    }

    @Provides
    @Singleton
    fun providePendingOperationEventDao(
        database: GudGumDatabase
    ): PendingOperationEventDao {
        return database.pendingOperationEventDao
    }
}
