package com.zhuangxiu.ledger.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.zhuangxiu.ledger.data.db.AppDatabase
import com.zhuangxiu.ledger.data.repository.ImageStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Hilt 模块：提供数据库、DataStore、图片存储等全局单例 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        com.zhuangxiu.ledger.data.repository.DatabaseSafety.prepare(context)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addCallback(AppDatabase.SEED_CALLBACK) // 首次建库时写入默认分类
            .build()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    /** 凭证图片保存在 App 私有目录 files/receipts 下 */
    @Provides
    @Singleton
    fun provideImageStore(@ApplicationContext context: Context): ImageStore =
        ImageStore(File(context.filesDir, "receipts"))
}
