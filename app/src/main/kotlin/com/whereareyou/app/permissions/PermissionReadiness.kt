package com.whereareyou.app.permissions

/** Mirrors ANDROID_ARCHITECTURE.md section 14 "Permission readiness model". */
enum class ReadinessStatus { GRANTED, MISSING, NOT_YET_REQUIRED }

data class PermissionReadinessItem(
    val featureLabel: String,
    val permissionLabel: String,
    val status: ReadinessStatus,
    val explanation: String,
)

/**
 * Phase 1A/1B has no platform event adapters wired yet (see AppContainer's KDoc), so every
 * entry here is currently [ReadinessStatus.NOT_YET_REQUIRED] — this screen exists as the
 * structural placeholder AGENTS.md "First coding milestone" step 6 asks for
 * ("permission readiness UI"), ready to report real state once Phase 1C/1D wire the
 * receivers that actually need each permission. Nothing here requests a permission itself.
 */
object PermissionReadiness {
    fun currentSnapshot(): List<PermissionReadinessItem> = listOf(
        PermissionReadinessItem(
            featureLabel = "Missed-call trigger",
            permissionLabel = "Call log / phone state",
            status = ReadinessStatus.NOT_YET_REQUIRED,
            explanation = "Requested only once the missed-call receiver (Phase 1D) is enabled.",
        ),
        PermissionReadinessItem(
            featureLabel = "SMS command trigger + status replies",
            permissionLabel = "Receive SMS / send SMS",
            status = ReadinessStatus.NOT_YET_REQUIRED,
            explanation = "Requested only once the SMS receiver/transport (Phase 1C) is enabled.",
        ),
        PermissionReadinessItem(
            featureLabel = "Location samples in a safety session",
            permissionLabel = "Fine/coarse location",
            status = ReadinessStatus.NOT_YET_REQUIRED,
            explanation = "Requested only once safety-session location sampling (Phase 1E) is enabled.",
        ),
        PermissionReadinessItem(
            featureLabel = "Background follow-up samples",
            permissionLabel = "Background location",
            status = ReadinessStatus.NOT_YET_REQUIRED,
            explanation = "Only requested for the optional Enhanced Reliability mode, and only after standard-mode testing (ANDROID_ARCHITECTURE.md section 7).",
        ),
        PermissionReadinessItem(
            featureLabel = "Emergency Callback",
            permissionLabel = "Call phone",
            status = ReadinessStatus.NOT_YET_REQUIRED,
            explanation = "Requested only if a trusted contact has Emergency Callback enabled (Phase 1G).",
        ),
        PermissionReadinessItem(
            featureLabel = "Safety-session notification",
            permissionLabel = "Notifications",
            status = ReadinessStatus.NOT_YET_REQUIRED,
            explanation = "Requested once an active safety session needs a visible local notification (Phase 1E).",
        ),
    )
}
