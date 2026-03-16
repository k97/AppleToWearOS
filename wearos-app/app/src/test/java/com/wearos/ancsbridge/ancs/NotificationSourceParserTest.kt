package com.wearos.ancsbridge.ancs

import com.wearos.ancsbridge.ble.AncsConstants
import org.junit.Assert.*
import org.junit.Test

class NotificationSourceParserTest {

    @Test
    fun `parse valid ADD event`() {
        // EventID=0(Added), Flags=0x08(PositiveAction), Category=4(Social), Count=3, UID=0x00000042
        val data = byteArrayOf(0x00, 0x08, 0x04, 0x03, 0x42, 0x00, 0x00, 0x00)
        val event = NotificationSourceParser.parse(data)

        assertNotNull(event)
        assertEquals(AncsConstants.EVENT_ID_ADDED, event!!.eventId)
        assertTrue(event.isAdded)
        assertFalse(event.isRemoved)
        assertTrue(event.hasPositiveAction)
        assertFalse(event.hasNegativeAction)
        assertFalse(event.isSilent)
        assertEquals(AncsConstants.CATEGORY_SOCIAL, event.categoryId)
        assertEquals(3, event.categoryCount)
        assertEquals(0x42L, event.notificationUid)
    }

    @Test
    fun `parse REMOVE event`() {
        val data = byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00)
        val event = NotificationSourceParser.parse(data)

        assertNotNull(event)
        assertTrue(event!!.isRemoved)
        assertEquals(1L, event.notificationUid)
    }

    @Test
    fun `parse MODIFY event with all flags`() {
        // Flags: Silent(0x01) | Important(0x02) | PositiveAction(0x08) | NegativeAction(0x10) = 0x1B
        val data = byteArrayOf(0x01, 0x1B, 0x01, 0x01, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00)
        val event = NotificationSourceParser.parse(data)

        assertNotNull(event)
        assertTrue(event!!.isModified)
        assertTrue(event.isSilent)
        assertTrue(event.isImportant)
        assertTrue(event.hasPositiveAction)
        assertTrue(event.hasNegativeAction)
        assertTrue(event.isIncomingCall)
        assertEquals(0xFFFFL, event.notificationUid)
    }

    @Test
    fun `parse incoming call`() {
        val data = byteArrayOf(0x00, 0x18, 0x01, 0x01, 0x0A, 0x00, 0x00, 0x00)
        val event = NotificationSourceParser.parse(data)

        assertNotNull(event)
        assertTrue(event!!.isIncomingCall)
        assertTrue(event.hasPositiveAction)
        assertTrue(event.hasNegativeAction)
    }

    @Test
    fun `parse large UID (max uint32)`() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val event = NotificationSourceParser.parse(data)

        assertNotNull(event)
        assertEquals(0xFFFFFFFFL, event!!.notificationUid)
    }

    @Test
    fun `parse returns null for short data`() {
        val data = byteArrayOf(0x00, 0x00, 0x00)
        assertNull(NotificationSourceParser.parse(data))
    }

    @Test
    fun `parse returns null for empty data`() {
        assertNull(NotificationSourceParser.parse(byteArrayOf()))
    }

    @Test
    fun `parse all categories`() {
        for (cat in 0..11) {
            val data = byteArrayOf(0x00, 0x00, cat.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)
            val event = NotificationSourceParser.parse(data)
            assertNotNull(event)
            assertEquals(cat, event!!.categoryId)
        }
    }
}
