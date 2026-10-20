package com.chan.weave.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chan.weave.core.CoreType

@Database(entities = [Node::class], version = 4, exportSchema = false)
@TypeConverters(CoreTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ciancore.db"
                )
                    // Skeleton has not shipped; destructive migration is fine.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
