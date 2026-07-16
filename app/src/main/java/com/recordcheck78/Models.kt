package com.recordcheck78

/**
 * Represents a 78rpm record identified from OCR + image labeling.
 */
data class Record(
    val catalogNumber: String = "",
    val artist: String = "",
    val title: String = "",
    val labelName: String = "",       // e.g., "Victor", "Columbia", "Decca"
    val labelStyle: String = "",      // AI-identified era/style
    val rawOcrText: String = "",
    val photoPath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of checking a record against the Internet Archive.
 */
data class ArchiveCheckResult(
    val record: Record,
    val exists: Boolean,
    val archiveItems: List<ArchiveItem> = emptyList(),
    val searchQueryUsed: String = "",
    val error: String? = null
)

/**
 * A matching item found on the Internet Archive.
 */
data class ArchiveItem(
    val identifier: String,         // IA item ID
    val title: String,
    val creator: String = "",
    val date: String = "",
    val downloadUrl: String = "",
    val detailUrl: String = ""
)

/**
 * A record in the donation list (saved for later donation).
 */
data class DonationListItem(
    val id: Long = 0,
    val record: Record,
    val checkResult: ArchiveCheckResult?,
    val addedAt: Long = System.currentTimeMillis(),
    val status: DonationStatus = DonationStatus.NEEDS_DONATION
)

enum class DonationStatus {
    NEEDS_DONATION,     // Not on IA — candidate for donation
    ALREADY_EXISTS,     // Found on IA — no need to donate
    DONATED,            // User marked as donated
    UPLOADED            // User uploaded to IA
}