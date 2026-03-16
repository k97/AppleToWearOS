package com.wearos.ancsbridge.ancs

import com.wearos.ancsbridge.ble.AncsConstants
import java.io.ByteArrayOutputStream

/**
 * Builds byte arrays for ANCS Control Point commands.
 *
 * Important: Variable-length attributes (Title, Subtitle, Message, action labels)
 * require a 2-byte LE max-length parameter. Fixed attributes (AppIdentifier, Date,
 * MessageSize) do NOT take a max-length parameter.
 */
object ControlPointWriter {

    /**
     * Build a GetNotificationAttributes command.
     *
     * @param uid The notification UID from the Notification Source event
     * @param hasPositiveAction Whether to request the positive action label
     * @param hasNegativeAction Whether to request the negative action label
     * @param maxLength Maximum length for variable-length attributes
     */
    fun buildGetNotificationAttributes(
        uid: Long,
        hasPositiveAction: Boolean = false,
        hasNegativeAction: Boolean = false,
        maxLength: Int = AncsConstants.DEFAULT_MAX_ATTRIBUTE_LENGTH
    ): ByteArray {
        val buffer = ByteArrayOutputStream()

        // Command ID
        buffer.write(AncsConstants.COMMAND_GET_NOTIFICATION_ATTRIBUTES)

        // NotificationUID (4 bytes, little-endian)
        writeUInt32LE(buffer, uid)

        // AppIdentifier (attribute 0) — fixed length, no max-length param
        buffer.write(AncsConstants.ATTR_APP_IDENTIFIER)

        // Title (attribute 1) — variable length
        buffer.write(AncsConstants.ATTR_TITLE)
        writeUInt16LE(buffer, maxLength)

        // Subtitle (attribute 2) — variable length
        buffer.write(AncsConstants.ATTR_SUBTITLE)
        writeUInt16LE(buffer, maxLength)

        // Message (attribute 3) — variable length
        buffer.write(AncsConstants.ATTR_MESSAGE)
        writeUInt16LE(buffer, maxLength)

        // Date (attribute 5) — fixed length, no max-length param
        buffer.write(AncsConstants.ATTR_DATE)

        // PositiveActionLabel (attribute 6) — variable length
        if (hasPositiveAction) {
            buffer.write(AncsConstants.ATTR_POSITIVE_ACTION_LABEL)
            writeUInt16LE(buffer, maxLength)
        }

        // NegativeActionLabel (attribute 7) — variable length
        if (hasNegativeAction) {
            buffer.write(AncsConstants.ATTR_NEGATIVE_ACTION_LABEL)
            writeUInt16LE(buffer, maxLength)
        }

        return buffer.toByteArray()
    }

    /**
     * Build a GetAppAttributes command.
     *
     * @param appIdentifier The app bundle ID (e.g., "com.apple.MobileSMS")
     */
    fun buildGetAppAttributes(appIdentifier: String): ByteArray {
        val buffer = ByteArrayOutputStream()

        // Command ID
        buffer.write(AncsConstants.COMMAND_GET_APP_ATTRIBUTES)

        // AppIdentifier (null-terminated UTF-8 string)
        buffer.write(appIdentifier.toByteArray(Charsets.UTF_8))
        buffer.write(0) // null terminator

        // DisplayName (attribute 0)
        buffer.write(AncsConstants.APP_ATTR_DISPLAY_NAME)

        return buffer.toByteArray()
    }

    /**
     * Build a PerformNotificationAction command.
     *
     * @param uid The notification UID
     * @param actionId 0 = positive, 1 = negative
     */
    fun buildPerformNotificationAction(uid: Long, actionId: Int): ByteArray {
        val buffer = ByteArrayOutputStream()

        // Command ID
        buffer.write(AncsConstants.COMMAND_PERFORM_NOTIFICATION_ACTION)

        // NotificationUID (4 bytes, little-endian)
        writeUInt32LE(buffer, uid)

        // ActionID
        buffer.write(actionId)

        return buffer.toByteArray()
    }

    private fun writeUInt32LE(buffer: ByteArrayOutputStream, value: Long) {
        buffer.write((value and 0xFF).toInt())
        buffer.write(((value shr 8) and 0xFF).toInt())
        buffer.write(((value shr 16) and 0xFF).toInt())
        buffer.write(((value shr 24) and 0xFF).toInt())
    }

    private fun writeUInt16LE(buffer: ByteArrayOutputStream, value: Int) {
        buffer.write(value and 0xFF)
        buffer.write((value shr 8) and 0xFF)
    }
}
