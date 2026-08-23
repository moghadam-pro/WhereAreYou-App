package com.whereareyou.app.contacts

import com.whereareyou.app.persistence.TrustedContactDao
import com.whereareyou.app.persistence.toDomain
import com.whereareyou.app.persistence.toEntity
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId
import com.whereareyou.core.model.phone.NormalizedPhoneNumber
import com.whereareyou.core.model.phone.PhoneNumberNormalizer
import com.whereareyou.core.rules.TrustedContactLookup
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Result of validating a display name + raw phone number before persisting a contact. */
sealed interface ContactInputResult {
    data class Valid(val canonicalPhoneNumber: String, val displayPhoneNumber: String) : ContactInputResult
    data class Invalid(val reason: String) : ContactInputResult
}

/**
 * App-facing CRUD API over Room, and — via [contactLookup] — the synchronous port the
 * (not-yet-wired) [com.whereareyou.core.rules.TriggerEngine] needs.
 *
 * `core.rules.TrustedContactLookup` is a plain synchronous interface because
 * ANDROID_ARCHITECTURE.md section 4 requires event receivers to "return quickly" — a
 * BroadcastReceiver cannot block on a Room suspend query. This repository keeps an
 * in-memory snapshot fed by Room's Flow (cache-aside) so the lookup never touches disk
 * on the hot path; [contactLookup] reads that snapshot.
 */
class TrustedContactRepository(
    private val dao: TrustedContactDao,
    externalScope: CoroutineScope,
    private val defaultCountryCode: String = "98",
) {
    private val normalizer = PhoneNumberNormalizer

    /** Single Room collector; [contactLookup] reads its latest value rather than re-collecting. */
    val contacts: StateFlow<List<TrustedContact>> = dao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    val contactLookup: TrustedContactLookup = object : TrustedContactLookup {
        override fun findByCanonicalNumber(canonicalNumber: String): TrustedContact? =
            contacts.value.firstOrNull { it.canonicalPhoneNumber == canonicalNumber }

        override fun findById(id: TrustedContactId): TrustedContact? =
            contacts.value.firstOrNull { it.id == id }

        override fun allEnabled(): List<TrustedContact> = contacts.value.filter { it.enabled }
    }

    fun validate(rawPhoneNumber: String, countryCode: String? = null): ContactInputResult =
        when (val result = normalizer.normalize(rawPhoneNumber, countryCode ?: defaultCountryCode)) {
            is NormalizedPhoneNumber.Valid -> ContactInputResult.Valid(result.canonical, result.display)
            is NormalizedPhoneNumber.Invalid -> ContactInputResult.Invalid(result.reason)
        }

    suspend fun add(contact: TrustedContact): TrustedContactId {
        val id = contact.id.takeIf { it.value.isNotBlank() } ?: TrustedContactId(UUID.randomUUID().toString())
        dao.upsert(contact.copy(id = id).toEntity())
        return id
    }

    suspend fun update(contact: TrustedContact) {
        dao.update(contact.toEntity())
    }

    suspend fun remove(id: TrustedContactId) {
        dao.deleteById(id.value)
    }

    suspend fun setEnabled(id: TrustedContactId, enabled: Boolean) {
        val existing = dao.findById(id.value) ?: return
        dao.update(existing.copy(enabled = enabled))
    }
}
