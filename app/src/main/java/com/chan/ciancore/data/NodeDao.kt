package com.chan.ciancore.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Query("SELECT * FROM nodes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Node>>

    @Query("SELECT * FROM nodes ORDER BY createdAt DESC")
    suspend fun getAll(): List<Node>

    @Query("SELECT * FROM nodes WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelected(): Node?

    @Query("SELECT * FROM nodes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Node?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: Node): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<Node>)

    @Update
    suspend fun update(node: Node)

    @Query("UPDATE nodes SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE nodes SET isSelected = 1 WHERE id = :id")
    suspend fun selectById(id: Long)

    @Query("UPDATE nodes SET latencyMs = :latency WHERE id = :id")
    suspend fun updateLatency(id: Long, latency: Long)

    @Delete
    suspend fun delete(node: Node)

    @Query("DELETE FROM nodes")
    suspend fun deleteAll()
}
