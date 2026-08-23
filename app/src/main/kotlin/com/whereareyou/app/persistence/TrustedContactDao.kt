package com.whereareyou.app.persistence

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY displayName")
    fun observeAll(): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts WHERE id = :id")
    suspend fun findById(id: String): TrustedContactEntity?

    @Query("SELECT * FROM trusted_contacts WHERE canonicalPhoneNumber = :canonicalPhoneNumber LIMIT 1")
    suspend fun findByCanonicalNumber(canonicalPhoneNumber: String): TrustedContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrustedContactEntity)

    @Update
    suspend fun update(entity: TrustedContactEntity)

    @Delete
    suspend fun delete(entity: TrustedContactEntity)

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteById(id: String)
}
