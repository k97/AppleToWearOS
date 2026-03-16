package com.wearos.ancsbridge.model

/**
 * Fully resolved notification with attributes fetched from ANCS Data Source.
 */
data class AncsNotification(
    val uid: Long,
    val appIdentifier: String,
    val appDisplayName: String?,
    val title: String,
    val subtitle: String?,
    val message: String,
    val date: String?, // ANCS format: yyyyMMdd'T'HHmmss
    val categoryId: Int,
    val eventFlags: Int,
    val positiveActionLabel: String?,
    val negativeActionLabel: String?
)
