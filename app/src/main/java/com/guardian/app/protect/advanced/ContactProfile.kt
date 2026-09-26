package com.guardian.app.protect.advanced

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "contact_profiles")
data class ContactProfile(
    @PrimaryKey val number: String,
    val displayName: String,
    val usualCallHoursStart: Int = 9,
    val usualCallHoursEnd: Int = 21,
    val usualDurationSec: Int = 180,
    val neverAsksForOtp: Boolean = true,
    val neverAsksForMoney: Boolean = true,
    val callCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface ContactProfileDao {
    @Query("SELECT * FROM contact_profiles WHERE number = :number LIMIT 1")
    suspend fun lookup(number: String): ContactProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ContactProfile)

    @Query("SELECT * FROM contact_profiles ORDER BY lastUpdated DESC")
    suspend fun getAll(): List<ContactProfile>

    @Query("DELETE FROM contact_profiles WHERE number = :number")
    suspend fun delete(number: String)
}
