package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity): Long

    @Update
    suspend fun updateLog(log: AuditLogEntity)

    @Query("UPDATE audit_logs SET status = :status, resultDetails = :result WHERE id = :id")
    suspend fun updateLogStatus(id: Long, status: String, result: String?)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}
