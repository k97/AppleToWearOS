package com.wearos.ancsbridge.ancs

import com.wearos.ancsbridge.model.AncsEvent

/**
 * Parses 8-byte Notification Source characteristic payloads from ANCS.
 *
 * Byte layout:
 *   [0]   EventID      (uint8): 0=Added, 1=Modified, 2=Removed
 *   [1]   EventFlags   (uint8 bitmask): Silent, Important, PreExisting, PositiveAction, NegativeAction
 *   [2]   CategoryID   (uint8): 0-11 category classification
 *   [3]   CategoryCount(uint8): active notifications in this category
 *   [4-7] NotificationUID (uint32, little-endian)
 */
object NotificationSourceParser {

    private const val EXPECTED_LENGTH = 8

    fun parse(data: ByteArray): AncsEvent? {
        if (data.size < EXPECTED_LENGTH) return null

        val eventId = data[0].toInt() and 0xFF
        val eventFlags = data[1].toInt() and 0xFF
        val categoryId = data[2].toInt() and 0xFF
        val categoryCount = data[3].toInt() and 0xFF
        val notificationUid = readUInt32LE(data, 4)

        return AncsEvent(
            eventId = eventId,
            eventFlags = eventFlags,
            categoryId = categoryId,
            categoryCount = categoryCount,
            notificationUid = notificationUid
        )
    }

    private fun readUInt32LE(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF)) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }
}
