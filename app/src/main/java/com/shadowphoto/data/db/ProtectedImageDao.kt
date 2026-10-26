package com.shadowphoto.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedImageDao {
    @Query("SELECT * FROM protected_images ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProtectedImageEntity>>

    @Query("SELECT COUNT(*) FROM protected_images")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM protected_images WHERE id = :id")
    suspend fun get(id: String): ProtectedImageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProtectedImageEntity)

    @Query("DELETE FROM protected_images WHERE id = :id")
    suspend fun delete(id: String)
}
