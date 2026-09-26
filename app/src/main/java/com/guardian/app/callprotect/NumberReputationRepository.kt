package com.guardian.app.callprotect

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "flagged_numbers")
data class FlaggedNumber(
    @PrimaryKey
    val number: String,
    val riskLevel: String, // "SPAM" | "HIGH_RISK" | "SCAM"
    val reason: String,
    val flaggedAt: Long,
    val reportedCount: Int = 1
)

@Dao
interface FlaggedNumberDao {
    @Query("SELECT * FROM flagged_numbers WHERE number = :number LIMIT 1")
    suspend fun lookup(number: String): FlaggedNumber?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FlaggedNumber)

    @Query("SELECT * FROM flagged_numbers ORDER BY flaggedAt DESC")
    suspend fun getAll(): List<FlaggedNumber>
}

@Database(
    entities = [FlaggedNumber::class, CallHistoryEntry::class, com.guardian.app.protect.advanced.ContactProfile::class],
    version = 2,
    exportSchema = false
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun flaggedNumberDao(): FlaggedNumberDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun contactProfileDao(): com.guardian.app.protect.advanced.ContactProfileDao

    companion object {
        @Volatile
        private var INSTANCE: GuardianDatabase? = null

        fun getInstance(context: Context): GuardianDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GuardianDatabase::class.java,
                    "guardian_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class NumberReputationRepository(private val context: Context) {
    private val db = GuardianDatabase.getInstance(context)

    suspend fun lookup(number: String): FlaggedNumber? {
        val normalized = normalize(number)
        if (normalized.isBlank()) return null
        return db.flaggedNumberDao().lookup(normalized)
    }

    suspend fun flag(number: String, level: String, reason: String) {
        val normalized = normalize(number)
        if (normalized.isBlank()) return
        val existing = db.flaggedNumberDao().lookup(normalized)
        val count = (existing?.reportedCount ?: 0) + 1
        db.flaggedNumberDao().upsert(
            FlaggedNumber(
                number = normalized,
                riskLevel = level,
                reason = reason,
                flaggedAt = System.currentTimeMillis(),
                reportedCount = count
            )
        )
    }

    suspend fun getAllFlagged(): List<FlaggedNumber> = db.flaggedNumberDao().getAll()

    suspend fun saveCallHistory(entry: CallHistoryEntry): Long {
        return db.callHistoryDao().insert(entry)
    }

    suspend fun getAllHistory(): List<CallHistoryEntry> {
        return db.callHistoryDao().getAll()
    }

    suspend fun markReported(id: Long) {
        db.callHistoryDao().markReported(id)
    }

    private fun normalize(number: String): String = number.replace(Regex("[^0-9+]"), "")
}
