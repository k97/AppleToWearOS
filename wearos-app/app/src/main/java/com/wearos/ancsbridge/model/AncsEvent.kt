package com.wearos.ancsbridge.model

import com.wearos.ancsbridge.ble.AncsConstants

/**
 * Parsed 8-byte Notification Source event from ANCS.
 */
data class AncsEvent(
    val eventId: Int,
    val eventFlags: Int,
    val categoryId: Int,
    val categoryCount: Int,
    val notificationUid: Long // UInt32 stored as Long to avoid sign issues
) {
    val isAdded get() = eventId == AncsConstants.EVENT_ID_ADDED
    val isModified get() = eventId == AncsConstants.EVENT_ID_MODIFIED
    val isRemoved get() = eventId == AncsConstants.EVENT_ID_REMOVED

    val isSilent get() = eventFlags and AncsConstants.EVENT_FLAG_SILENT != 0
    val isImportant get() = eventFlags and AncsConstants.EVENT_FLAG_IMPORTANT != 0
    val isPreExisting get() = eventFlags and AncsConstants.EVENT_FLAG_PRE_EXISTING != 0
    val hasPositiveAction get() = eventFlags and AncsConstants.EVENT_FLAG_POSITIVE_ACTION != 0
    val hasNegativeAction get() = eventFlags and AncsConstants.EVENT_FLAG_NEGATIVE_ACTION != 0

    val isIncomingCall get() = categoryId == AncsConstants.CATEGORY_INCOMING_CALL
}
