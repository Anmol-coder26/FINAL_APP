package com.guardian.app.callprotect

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "call_history")
data class CallHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val number: String,
    val timestamp: Long,
    val riskScore: Int,
    val topSignals: String,
    val transcriptSummary: String,
    val actionTaken: String,
    val wasUserReported: Boolean = false
)

@Dao
interface CallHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallHistoryEntry): Long

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<CallHistoryEntry>

    @Query("SELECT * FROM call_history WHERE number = :number ORDER BY timestamp DESC")
    suspend fun getByNumber(number: String): List<CallHistoryEntry>

    @Query("UPDATE call_history SET wasUserReported = 1 WHERE id = :id")
    suspend fun markReported(id: Long)
}
