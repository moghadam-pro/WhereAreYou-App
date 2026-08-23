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
    // Schema export can be turned on later (with a configured schema directory) once
    // migrations matter; not worth the build-config overhead for a single-table Phase 1A.
    exportSchema = false,
)
abstract class WhereAreYouDatabase : RoomDatabase() {
    abstract fun trustedContactDao(): TrustedContactDao

    companion object {
        const val DATABASE_NAME = "whereareyou.db"
    }
}
