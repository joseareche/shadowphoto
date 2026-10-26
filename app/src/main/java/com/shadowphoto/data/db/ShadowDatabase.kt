package com.shadowphoto.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProtectedImageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ShadowDatabase : RoomDatabase() {
    abstract fun protectedImages(): ProtectedImageDao

    companion object {
        fun create(context: Context): ShadowDatabase =
            Room.databaseBuilder(context, ShadowDatabase::class.java, "shadowphoto.db")
                .build()
    }
}
