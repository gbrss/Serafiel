package com.example

import com.example.data.LocationHelper
import com.example.model.DirectChat
import com.example.model.DirectMessage
import com.example.model.GroupMessage
import com.example.model.MessageDeliveryStatus
import com.example.model.SocialGroup
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import com.example.i18n.AppStrings
import java.util.Date

/** Lógica pura de chats: recibos de lectura, "escribiendo..." y zonas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatLogicTest {
    @Before
    fun setUp() {
        AppStrings.init(ApplicationProvider.getApplicationContext())
    }

    private fun ts(ms: Long) = Timestamp(Date(ms))

    private val group = SocialGroup(
        id = "g1",
        memberIds = listOf("a", "b", "c"),
        memberNames = mapOf("a" to "Ana Pérez", "b" to "Beto Ruiz", "c" to "Caro"),
    )

    private fun groupMsg(sentMs: Long) = GroupMessage(senderId = "a", createdAt = ts(sentMs))

    @Test
    fun groupMessage_noOneRead_isSent() {
        assertEquals(MessageDeliveryStatus.SENT, groupMsg(1_000).deliveryStatus(group))
    }

    @Test
    fun groupMessage_someRead_isDelivered() {
        val g = group.copy(lastReadAt = mapOf("b" to ts(2_000)))
        assertEquals(MessageDeliveryStatus.DELIVERED, groupMsg(1_000).deliveryStatus(g))
    }

    @Test
    fun groupMessage_allOthersRead_isSeen() {
        val g = group.copy(lastReadAt = mapOf("b" to ts(2_000), "c" to ts(3_000)))
        assertEquals(MessageDeliveryStatus.SEEN, groupMsg(1_000).deliveryStatus(g))
    }

    @Test
    fun groupMessage_readBeforeSending_doesNotCount() {
        val g = group.copy(lastReadAt = mapOf("b" to ts(500), "c" to ts(500)))
        assertEquals(MessageDeliveryStatus.SENT, groupMsg(1_000).deliveryStatus(g))
    }

    @Test
    fun groupMessage_aloneInGroup_staysSent() {
        val solo = group.copy(memberIds = listOf("a"))
        assertEquals(MessageDeliveryStatus.SENT, groupMsg(1_000).deliveryStatus(solo))
    }

    @Test
    fun groupTyping_listsOthersWithFirstName_andIgnoresMe() {
        val now = 100_000L
        val g = group.copy(typingAt = mapOf("a" to ts(now), "b" to ts(now - 2_000)))
        assertEquals(listOf("Beto"), g.typingNames("a", now))
    }

    @Test
    fun groupTyping_expiresAfterSevenSeconds() {
        val now = 100_000L
        val g = group.copy(typingAt = mapOf("b" to ts(now - 8_000)))
        assertTrue(g.typingNames("a", now).isEmpty())
    }

    @Test
    fun groupTyping_ignoresNonMembers() {
        val now = 100_000L
        val g = group.copy(typingAt = mapOf("zzz" to ts(now)))
        assertTrue(g.typingNames("a", now).isEmpty())
    }

    @Test
    fun directMessage_status_followsReadAndDelivered() {
        val chat = DirectChat(
            participantIds = listOf("a", "b"),
            lastDeliveredAt = mapOf("b" to ts(1_500)),
        )
        val msg = DirectMessage(senderId = "a", createdAt = ts(1_000))
        assertEquals(MessageDeliveryStatus.DELIVERED, msg.deliveryStatus(chat))
        val read = chat.copy(lastReadAt = mapOf("b" to ts(2_000)))
        assertEquals(MessageDeliveryStatus.SEEN, msg.deliveryStatus(read))
        assertEquals(MessageDeliveryStatus.SENT, msg.deliveryStatus(chat.copy(lastDeliveredAt = emptyMap())))
    }

    @Test
    fun directChat_peerTyping_window() {
        val now = 100_000L
        val chat = DirectChat(participantIds = listOf("a", "b"), typingAt = mapOf("b" to ts(now - 3_000)))
        assertTrue(chat.isPeerTyping("a", now))
        assertTrue(!chat.isPeerTyping("b", now))
        assertTrue(!chat.copy(typingAt = mapOf("b" to ts(now - 9_000))).isPeerTyping("a", now))
    }

    @Test
    fun zoneKey_roundsToTwoDecimals() {
        assertEquals("-3375_-7066", LocationHelper.formatZoneKey(-33.7512, -70.6633))
        assertEquals(
            LocationHelper.formatZoneKey(-33.7512, -70.6633),
            LocationHelper.formatZoneKey(-33.7549, -70.6649),
        )
    }

    @Test
    fun distance_isSymmetric_andZeroForSamePoint() {
        val d1 = LocationHelper.distanceKm(-33.45, -70.66, -33.04, -71.62)
        val d2 = LocationHelper.distanceKm(-33.04, -71.62, -33.45, -70.66)
        assertEquals(d1, d2, 1e-9)
        assertEquals(0.0, LocationHelper.distanceKm(10.0, 10.0, 10.0, 10.0), 1e-9)
        assertTrue("Santiago-Valparaíso ~100 km, fue $d1", d1 in 85.0..115.0)
    }
}
