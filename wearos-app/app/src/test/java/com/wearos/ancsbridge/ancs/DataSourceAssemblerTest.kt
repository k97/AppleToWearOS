package com.wearos.ancsbridge.ancs

import com.wearos.ancsbridge.ble.AncsConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class DataSourceAssemblerTest {

    private lateinit var assembler: DataSourceAssembler

    @Before
    fun setup() {
        assembler = DataSourceAssembler()
    }

    @Test
    fun `parse single-fragment response`() {
        val requestedAttrs = listOf(
            AncsConstants.ATTR_APP_IDENTIFIER,
            AncsConstants.ATTR_TITLE,
            AncsConstants.ATTR_MESSAGE
        )

        assembler.expectNotificationAttributes(
            uid = 0x42,
            requestedAttributes = requestedAttrs,
            category = AncsConstants.CATEGORY_SOCIAL,
            flags = 0
        )

        val response = buildResponse(
            commandId = 0,
            uid = 0x42,
            attributes = listOf(
                0 to "com.apple.MobileSMS",
                1 to "John",
                3 to "Hello world"
            )
        )

        val notification = assembler.onDataReceived(response)

        assertNotNull(notification)
        assertEquals(0x42L, notification!!.uid)
        assertEquals("com.apple.MobileSMS", notification.appIdentifier)
        assertEquals("John", notification.title)
        assertEquals("Hello world", notification.message)
        assertEquals(AncsConstants.CATEGORY_SOCIAL, notification.categoryId)
    }

    @Test
    fun `parse multi-fragment response`() {
        val requestedAttrs = listOf(
            AncsConstants.ATTR_APP_IDENTIFIER,
            AncsConstants.ATTR_TITLE
        )

        assembler.expectNotificationAttributes(
            uid = 1,
            requestedAttributes = requestedAttrs,
            category = AncsConstants.CATEGORY_EMAIL,
            flags = 0
        )

        val fullResponse = buildResponse(
            commandId = 0,
            uid = 1,
            attributes = listOf(
                0 to "com.google.Gmail",
                1 to "Alice"
            )
        )

        // Split into 3 fragments
        val frag1 = fullResponse.copyOfRange(0, 8)
        val frag2 = fullResponse.copyOfRange(8, 20)
        val frag3 = fullResponse.copyOfRange(20, fullResponse.size)

        assertNull(assembler.onDataReceived(frag1))
        assertNull(assembler.onDataReceived(frag2))

        val notification = assembler.onDataReceived(frag3)
        assertNotNull(notification)
        assertEquals("com.google.Gmail", notification!!.appIdentifier)
        assertEquals("Alice", notification.title)
    }

    @Test
    fun `parse empty attribute values`() {
        val requestedAttrs = listOf(
            AncsConstants.ATTR_APP_IDENTIFIER,
            AncsConstants.ATTR_TITLE,
            AncsConstants.ATTR_SUBTITLE
        )

        assembler.expectNotificationAttributes(
            uid = 5,
            requestedAttributes = requestedAttrs,
            category = 0,
            flags = 0
        )

        val response = buildResponse(
            commandId = 0,
            uid = 5,
            attributes = listOf(
                0 to "com.app.test",
                1 to "",  // empty title
                2 to ""   // empty subtitle
            )
        )

        val notification = assembler.onDataReceived(response)
        assertNotNull(notification)
        assertEquals("", notification!!.title)
        assertEquals("", notification.subtitle)
    }

    @Test
    fun `fragment boundary in middle of length field`() {
        val requestedAttrs = listOf(
            AncsConstants.ATTR_APP_IDENTIFIER,
            AncsConstants.ATTR_TITLE
        )

        assembler.expectNotificationAttributes(uid = 1, requestedAttributes = requestedAttrs, category = 0, flags = 0)

        val fullResponse = buildResponse(0, 1, listOf(0 to "app", 1 to "Bob"))

        // Cut right in the middle of the second attribute's length field
        // Header(5) + attr0_id(1) + attr0_len(2) + "app"(3) + attr1_id(1) = 12
        // So byte 12 is the first byte of attr1's length
        val splitAt = 12
        val frag1 = fullResponse.copyOfRange(0, splitAt + 1) // includes first length byte
        val frag2 = fullResponse.copyOfRange(splitAt + 1, fullResponse.size)

        assertNull(assembler.onDataReceived(frag1))
        val notification = assembler.onDataReceived(frag2)
        assertNotNull(notification)
        assertEquals("Bob", notification!!.title)
    }

    @Test
    fun `reset clears state`() {
        assembler.expectNotificationAttributes(
            uid = 1,
            requestedAttributes = listOf(AncsConstants.ATTR_APP_IDENTIFIER),
            category = 0,
            flags = 0
        )

        // Send partial data
        assembler.onDataReceived(byteArrayOf(0x00, 0x01, 0x00, 0x00))

        // Reset
        assembler.reset()

        // New request should work fresh
        assembler.expectNotificationAttributes(
            uid = 2,
            requestedAttributes = listOf(AncsConstants.ATTR_APP_IDENTIFIER),
            category = 0,
            flags = 0
        )

        val response = buildResponse(0, 2, listOf(0 to "test"))
        val notification = assembler.onDataReceived(response)
        assertNotNull(notification)
        assertEquals(2L, notification!!.uid)
    }

    // Helper to build a raw ANCS Data Source response
    private fun buildResponse(
        commandId: Int,
        uid: Long,
        attributes: List<Pair<Int, String>>
    ): ByteArray {
        val buffer = ByteArrayOutputStream()

        // Command ID
        buffer.write(commandId)

        // NotificationUID (4 bytes LE)
        buffer.write((uid and 0xFF).toInt())
        buffer.write(((uid shr 8) and 0xFF).toInt())
        buffer.write(((uid shr 16) and 0xFF).toInt())
        buffer.write(((uid shr 24) and 0xFF).toInt())

        // Attribute tuples
        for ((attrId, value) in attributes) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            buffer.write(attrId)
            buffer.write(bytes.size and 0xFF)
            buffer.write((bytes.size shr 8) and 0xFF)
            buffer.write(bytes)
        }

        return buffer.toByteArray()
    }
}
