package com.whereareyou.app.persistence

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local-only database. Per AGENTS.md core invariant #8 and SECURITY_PRIVACY.md section 7,
 * nothing in here is ever synced anywhere — no cloud backup (see the manifest's
 * `android:allowBackup="false"`), no remote replication.
 */
@Database(
    entities = [TrustedContactEntity::class],
    version = 1,
    // Schemas are exported to app/schemas (see ksp arg in build.gradle.kts) and committed, so
    // every version bump needs a reviewed Migration — never fallbackToDestructiveMigration.
    exportSchema = true,
)
abstract class WhereAreYouDatabase : RoomDatabase() {
    abstract fun trustedContactDao(): TrustedContactDao

    companion object {
        const val DATABASE_NAME = "whereareyou.db"
    }
}
