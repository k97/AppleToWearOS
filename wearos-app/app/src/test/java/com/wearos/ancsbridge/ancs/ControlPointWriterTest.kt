package com.wearos.ancsbridge.ancs

import com.wearos.ancsbridge.ble.AncsConstants
import org.junit.Assert.*
import org.junit.Test

class ControlPointWriterTest {

    @Test
    fun `buildGetNotificationAttributes basic`() {
        val data = ControlPointWriter.buildGetNotificationAttributes(
            uid = 0x42,
            hasPositiveAction = false,
            hasNegativeAction = false,
            maxLength = 255
        )

        // Command ID
        assertEquals(AncsConstants.COMMAND_GET_NOTIFICATION_ATTRIBUTES, data[0].toInt() and 0xFF)

        // UID (little-endian)
        assertEquals(0x42, data[1].toInt() and 0xFF)
        assertEquals(0x00, data[2].toInt() and 0xFF)
        assertEquals(0x00, data[3].toInt() and 0xFF)
        assertEquals(0x00, data[4].toInt() and 0xFF)

        // AppIdentifier (attr 0, no max length)
        assertEquals(AncsConstants.ATTR_APP_IDENTIFIER, data[5].toInt() and 0xFF)

        // Title (attr 1, with max length 255 = 0xFF 0x00)
        assertEquals(AncsConstants.ATTR_TITLE, data[6].toInt() and 0xFF)
        assertEquals(0xFF, data[7].toInt() and 0xFF)
        assertEquals(0x00, data[8].toInt() and 0xFF)

        // Subtitle (attr 2)
        assertEquals(AncsConstants.ATTR_SUBTITLE, data[9].toInt() and 0xFF)

        // Message (attr 3)
        assertEquals(AncsConstants.ATTR_MESSAGE, data[12].toInt() and 0xFF)

        // Date (attr 5, no max length)
        assertEquals(AncsConstants.ATTR_DATE, data[15].toInt() and 0xFF)
    }

    @Test
    fun `buildGetNotificationAttributes with actions`() {
        val data = ControlPointWriter.buildGetNotificationAttributes(
            uid = 1,
            hasPositiveAction = true,
            hasNegativeAction = true,
            maxLength = 128
        )

        // Should contain positive action label (attr 6) and negative action label (attr 7)
        val attrs = mutableListOf<Int>()
        var i = 5 // skip command + uid
        while (i < data.size) {
            val attrId = data[i].toInt() and 0xFF
            attrs.add(attrId)
            i++
            if (attrId in AncsConstants.VARIABLE_LENGTH_ATTRIBUTES) {
                i += 2 // skip max length
            }
        }

        assertTrue("Should contain positive action label", attrs.contains(AncsConstants.ATTR_POSITIVE_ACTION_LABEL))
        assertTrue("Should contain negative action label", attrs.contains(AncsConstants.ATTR_NEGATIVE_ACTION_LABEL))
    }

    @Test
    fun `buildGetNotificationAttributes without actions`() {
        val data = ControlPointWriter.buildGetNotificationAttributes(
            uid = 1,
            hasPositiveAction = false,
            hasNegativeAction = false
        )

        val bytes = data.map { it.toInt() and 0xFF }
        assertFalse("Should not contain positive action label",
            bytes.contains(AncsConstants.ATTR_POSITIVE_ACTION_LABEL))
        assertFalse("Should not contain negative action label",
            bytes.contains(AncsConstants.ATTR_NEGATIVE_ACTION_LABEL))
    }

    @Test
    fun `buildGetAppAttributes`() {
        val data = ControlPointWriter.buildGetAppAttributes("com.apple.MobileSMS")

        assertEquals(AncsConstants.COMMAND_GET_APP_ATTRIBUTES, data[0].toInt() and 0xFF)

        // App identifier should be null-terminated
        val appId = data.slice(1 until data.size - 1).toByteArray().toString(Charsets.UTF_8)
        assertTrue(appId.startsWith("com.apple.MobileSMS"))

        // Null terminator
        assertEquals(0, data[1 + "com.apple.MobileSMS".length].toInt())

        // Display name attribute
        assertEquals(AncsConstants.APP_ATTR_DISPLAY_NAME,
            data[1 + "com.apple.MobileSMS".length + 1].toInt() and 0xFF)
    }

    @Test
    fun `buildPerformNotificationAction positive`() {
        val data = ControlPointWriter.buildPerformNotificationAction(0x42, AncsConstants.ACTION_POSITIVE)

        assertEquals(AncsConstants.COMMAND_PERFORM_NOTIFICATION_ACTION, data[0].toInt() and 0xFF)
        assertEquals(0x42, data[1].toInt() and 0xFF) // UID byte 0
        assertEquals(AncsConstants.ACTION_POSITIVE, data[5].toInt() and 0xFF)
    }

    @Test
    fun `buildPerformNotificationAction negative`() {
        val data = ControlPointWriter.buildPerformNotificationAction(0x42, AncsConstants.ACTION_NEGATIVE)

        assertEquals(AncsConstants.ACTION_NEGATIVE, data[5].toInt() and 0xFF)
    }

    @Test
    fun `UID encoding is little-endian`() {
        val data = ControlPointWriter.buildGetNotificationAttributes(uid = 0x01020304)

        assertEquals(0x04, data[1].toInt() and 0xFF) // least significant byte first
        assertEquals(0x03, data[2].toInt() and 0xFF)
        assertEquals(0x02, data[3].toInt() and 0xFF)
        assertEquals(0x01, data[4].toInt() and 0xFF)
    }
}
