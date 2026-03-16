package com.wearos.ancsbridge.ancs

import android.util.Log
import com.wearos.ancsbridge.ble.AncsConstants
import com.wearos.ancsbridge.model.AncsNotification
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reassembles fragmented ANCS Data Source responses into complete notifications.
 *
 * ANCS responses can span multiple GATT notifications due to MTU limits.
 * This state machine accumulates bytes and parses attribute tuples incrementally.
 *
 * Response format for GetNotificationAttributes (Command 0):
 *   [0]    CommandID = 0x00
 *   [1-4]  NotificationUID (uint32 LE)
 *   [5+]   Attribute tuples: [AttrID(1)] [Length(2 LE)] [Data(Length bytes)]
 *
 * Response format for GetAppAttributes (Command 1):
 *   [0]    CommandID = 0x01
 *   [1+]   AppIdentifier (null-terminated UTF-8)
 *   [n+]   Attribute tuples: [AttrID(1)] [Length(2 LE)] [Data(Length bytes)]
 */
class DataSourceAssembler {

    companion object {
        private const val TAG = "DataSourceAssembler"
    }

    private enum class State {
        IDLE,
        READING_HEADER,
        READING_ATTRIBUTES
    }

    private var state = State.IDLE
    private val buffer = ByteArrayOutputStream()
    private var commandId: Int = -1
    private var notificationUid: Long = 0
    private var headerParsed = false

    // Parsed attributes
    private val attributes = mutableMapOf<Int, String>()

    // Expected attributes from the request (in order)
    private var expectedAttributes = listOf<Int>()
    private var currentAttrIndex = 0

    // Current attribute being read
    private var currentAttrId: Int = -1
    private var currentAttrLength: Int = -1
    private var currentAttrBytesRead: Int = 0
    private val currentAttrData = ByteArrayOutputStream()

    // Category and flags from the original NS event (set externally)
    private var categoryId: Int = 0
    private var eventFlags: Int = 0

    /**
     * Start expecting a GetNotificationAttributes response.
     *
     * @param uid The notification UID we requested attributes for
     * @param requestedAttributes The attribute IDs in the order we requested them
     * @param category The category ID from the original NS event
     * @param flags The event flags from the original NS event
     */
    fun expectNotificationAttributes(
        uid: Long,
        requestedAttributes: List<Int>,
        category: Int,
        flags: Int
    ) {
        reset()
        notificationUid = uid
        expectedAttributes = requestedAttributes
        categoryId = category
        eventFlags = flags
        state = State.READING_HEADER
    }

    /**
     * Feed incoming Data Source bytes. Call this for each onCharacteristicChanged.
     *
     * @return A completed AncsNotification if all attributes have been parsed, null otherwise.
     */
    fun onDataReceived(data: ByteArray): AncsNotification? {
        if (state == State.IDLE) {
            // Unexpected data — start fresh header parse
            state = State.READING_HEADER
            headerParsed = false
        }

        buffer.write(data)
        return tryParse()
    }

    private fun tryParse(): AncsNotification? {
        val bytes = buffer.toByteArray()
        var offset = 0

        // Parse header if not yet done
        if (!headerParsed) {
            if (bytes.isEmpty()) return null

            commandId = bytes[0].toInt() and 0xFF
            offset = 1

            when (commandId) {
                AncsConstants.COMMAND_GET_NOTIFICATION_ATTRIBUTES -> {
                    if (bytes.size < 5) return null // Need at least CommandID + UID
                    notificationUid = readUInt32LE(bytes, 1)
                    offset = 5
                    headerParsed = true
                    state = State.READING_ATTRIBUTES
                }
                AncsConstants.COMMAND_GET_APP_ATTRIBUTES -> {
                    // Read null-terminated app identifier
                    val nullIndex = bytes.indexOf(0.toByte(), fromIndex = 1)
                    if (nullIndex == -1) return null // Haven't received full app ID yet
                    offset = nullIndex + 1
                    headerParsed = true
                    state = State.READING_ATTRIBUTES
                }
                else -> {
                    Log.w(TAG, "Unknown command ID: $commandId")
                    reset()
                    return null
                }
            }
        }

        // Parse attribute tuples
        while (state == State.READING_ATTRIBUTES && currentAttrIndex < expectedAttributes.size) {
            if (currentAttrId == -1) {
                // Start reading a new attribute
                if (offset >= bytes.size) break

                currentAttrId = bytes[offset].toInt() and 0xFF
                offset++

                // Read 2-byte length
                if (offset + 1 >= bytes.size) {
                    // Need more data for length
                    compactBuffer(bytes, offset)
                    return null
                }

                currentAttrLength = readUInt16LE(bytes, offset)
                offset += 2
                currentAttrBytesRead = 0
                currentAttrData.reset()
            }

            // Read attribute data
            val remaining = currentAttrLength - currentAttrBytesRead
            val available = bytes.size - offset
            val toRead = minOf(remaining, available)

            if (toRead > 0) {
                currentAttrData.write(bytes, offset, toRead)
                currentAttrBytesRead += toRead
                offset += toRead
            }

            if (currentAttrBytesRead >= currentAttrLength) {
                // Attribute complete
                val value = currentAttrData.toString(Charsets.UTF_8.name())
                attributes[currentAttrId] = value
                Log.d(TAG, "Attribute $currentAttrId = \"$value\" (${currentAttrLength} bytes)")

                currentAttrId = -1
                currentAttrLength = -1
                currentAttrBytesRead = 0
                currentAttrData.reset()
                currentAttrIndex++
            } else {
                // Need more data
                compactBuffer(bytes, offset)
                return null
            }
        }

        // Check if all attributes are parsed
        if (currentAttrIndex >= expectedAttributes.size) {
            val notification = buildNotification()
            reset()
            return notification
        }

        // Still need more data
        compactBuffer(bytes, offset)
        return null
    }

    private fun buildNotification(): AncsNotification {
        return AncsNotification(
            uid = notificationUid,
            appIdentifier = attributes[AncsConstants.ATTR_APP_IDENTIFIER] ?: "",
            appDisplayName = null, // Fetched separately via GetAppAttributes
            title = attributes[AncsConstants.ATTR_TITLE] ?: "",
            subtitle = attributes[AncsConstants.ATTR_SUBTITLE],
            message = attributes[AncsConstants.ATTR_MESSAGE] ?: "",
            date = attributes[AncsConstants.ATTR_DATE],
            categoryId = categoryId,
            eventFlags = eventFlags,
            positiveActionLabel = attributes[AncsConstants.ATTR_POSITIVE_ACTION_LABEL],
            negativeActionLabel = attributes[AncsConstants.ATTR_NEGATIVE_ACTION_LABEL]
        )
    }

    private fun compactBuffer(currentBytes: ByteArray, offset: Int) {
        buffer.reset()
        if (offset < currentBytes.size) {
            buffer.write(currentBytes, offset, currentBytes.size - offset)
        }
    }

    private fun readUInt32LE(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF)) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun readUInt16LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.indexOf(byte: Byte, fromIndex: Int = 0): Int {
        for (i in fromIndex until size) {
            if (this[i] == byte) return i
        }
        return -1
    }

    fun reset() {
        state = State.IDLE
        buffer.reset()
        commandId = -1
        notificationUid = 0
        headerParsed = false
        attributes.clear()
        expectedAttributes = emptyList()
        currentAttrIndex = 0
        currentAttrId = -1
        currentAttrLength = -1
        currentAttrBytesRead = 0
        currentAttrData.reset()
        categoryId = 0
        eventFlags = 0
    }
}
