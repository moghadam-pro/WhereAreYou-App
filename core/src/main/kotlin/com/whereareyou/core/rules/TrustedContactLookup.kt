package com.whereareyou.core.rules

import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId

/**
 * Read-only port the rule layer uses to resolve contacts. The Android app provides the
 * real DataStore/Room-backed implementation; tests use an in-memory fake.
 */
interface TrustedContactLookup {
    fun findByCanonicalNumber(canonicalNumber: String): TrustedContact?
    fun findById(id: TrustedContactId): TrustedContact?
    fun allEnabled(): List<TrustedContact>
}

/** Simple in-memory [TrustedContactLookup] — used by app-layer callers and by tests. */
class InMemoryTrustedContactLookup(initial: Collection<TrustedContact> = emptyList()) : TrustedContactLookup {
    private val byId = linkedMapOf<TrustedContactId, TrustedContact>().apply {
        initial.forEach { put(it.id, it) }
    }

    fun upsert(contact: TrustedContact) {
        byId[contact.id] = contact
    }

    fun remove(id: TrustedContactId) {
        byId.remove(id)
    }

    override fun findByCanonicalNumber(canonicalNumber: String): TrustedContact? =
        byId.values.firstOrNull { it.canonicalPhoneNumber == canonicalNumber }

    override fun findById(id: TrustedContactId): TrustedContact? = byId[id]

    override fun allEnabled(): List<TrustedContact> = byId.values.filter { it.enabled }
}
