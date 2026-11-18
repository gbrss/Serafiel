package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatLocationPoint
import com.example.data.LocationHelper
import com.example.model.DirectMessage
import com.example.model.GroupCategory
import com.example.model.GroupMessage
import com.example.model.ProximityBeacon
import com.example.model.ZoneLocation
import com.example.notifications.SerafielNotificationHelper
import com.example.ui.screens.MentionCandidate
import com.example.ui.screens.SERAFIEL_EMOJI_CATEGORIES
import com.example.ui.screens.buildMentionAnnotatedText
import com.example.ui.screens.filterMentionCandidates
import com.example.ui.screens.findActiveMentionQuery
import com.example.ui.screens.insertMention
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Serafiel", appName)
  }

  @Test
  fun `notification channels are registered`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SerafielNotificationHelper.ensureChannels(context)
    val manager = context.getSystemService(NotificationManager::class.java)
    assertNotNull(manager.getNotificationChannel(SerafielNotificationHelper.CHANNEL_DIRECT_MESSAGES))
    assertNotNull(manager.getNotificationChannel(SerafielNotificationHelper.CHANNEL_ZONE_MESSAGES))
    assertNull(manager.getNotificationChannel("serafiel_service"))
  }

  @Test
  fun `distance formats sub-kilometer distances in meters and kilometers above 1km`() {
    assertTrue(LocationHelper.fallbackZones.none { it.zoneName.contains("Plaza Central", ignoreCase = true) })
    assertEquals("plaza_central", LocationHelper.fallbackZones.first().zoneKey)
    assertEquals("Zona Abierta", LocationHelper.fallbackZones.first().zoneName)

    assertEquals("45 m", LocationHelper.formatCompactDistance(0.045))
    assertEquals("850 m", LocationHelper.formatCompactDistance(0.85))
    val sameZoneLabel = LocationHelper.formatApproximateDistance(km = 0.0, sameZone = true)
    assertEquals("A 0 m · Misma zona", sameZoneLabel)
    val midLabel = LocationHelper.formatApproximateDistance(km = 4.6, sameZone = false)
    assertTrue(midLabel.contains("km"))
    assertFalse(midLabel.contains("Misma zona"))
  }

  @Test
  fun `beacon online status validates freshness window`() {
    val now = 1_700_000_000_000L
    val freshBeacon = ProximityBeacon(
      online = true,
      lastSeenAt = Timestamp(Date(now - 60_000L)),
    )
    val staleBeacon = ProximityBeacon(
      online = true,
      lastSeenAt = Timestamp(Date(now - ProximityBeacon.ONLINE_STALE_MS - 60_000L)),
    )
    val offlineBeacon = ProximityBeacon(
      online = false,
      lastSeenAt = Timestamp(Date(now)),
    )

    assertTrue(freshBeacon.isCurrentlyOnline(now))
    assertFalse(staleBeacon.isCurrentlyOnline(now))
    assertFalse(offlineBeacon.isCurrentlyOnline(now))
  }

  @Test
  fun `rich direct and group message preview labels format correctly`() {
    assertEquals("Mensaje de voz (0:12)", DirectMessage(messageType = DirectMessage.TYPE_VOICE, durationSec = 12).previewLabel())
    assertEquals("Foto", DirectMessage(messageType = DirectMessage.TYPE_IMAGE).previewLabel())
    assertEquals("Audio: nota.mp3", DirectMessage(messageType = DirectMessage.TYPE_AUDIO, mediaName = "nota.mp3").previewLabel())
    assertEquals("Ubicación: Madrid, ES", DirectMessage(messageType = DirectMessage.TYPE_LOCATION, locationLabel = "Madrid, ES").previewLabel())
    assertEquals("Ubicación en tiempo real", DirectMessage(messageType = DirectMessage.TYPE_LIVE_LOCATION, liveActive = true).previewLabel())
    assertEquals(
      "Ubicación en tiempo real: Llego en 5 min",
      DirectMessage(messageType = DirectMessage.TYPE_LIVE_LOCATION, liveActive = true, text = "Llego en 5 min").previewLabel(),
    )

    val groupMsg = GroupMessage(
      id = "gm1",
      groupId = "g1",
      senderId = "u1",
      senderName = "Ana",
      messageType = DirectMessage.TYPE_VOICE,
      durationSec = 25,
    )
    assertEquals("Mensaje de voz (0:25)", groupMsg.previewLabel())
    assertEquals(GroupCategory.FAMILY, GroupCategory.fromKey("FAMILY"))
  }

  @Test
  fun `emoji catalog provides all 8 categories`() {
    assertEquals(8, SERAFIEL_EMOJI_CATEGORIES.size)
    assertTrue(SERAFIEL_EMOJI_CATEGORIES.all { it.emojis.size >= 40 })
  }

  @Test
  fun `unresolved default zone has invalid coords and global beacon reach is detected`() {
    assertFalse(LocationHelper.UNRESOLVED_DEFAULT_ZONE.hasValidCoords)
    val validZone = ZoneLocation(
      zoneKey = "santiago_cl",
      zoneName = "Santiago, CL",
      approxLat = -33.4489,
      approxLon = -70.6693,
      isFromGps = true,
    )
    assertTrue(validZone.hasValidCoords)

    assertFalse(ProximityBeacon(radiusKm = 50L).isGlobal())
    assertFalse(ProximityBeacon(radiusKm = 1000L).isGlobal())
    assertTrue(ProximityBeacon(radiusKm = ProximityBeacon.GLOBAL_RADIUS_KM.toLong()).isGlobal())
  }

  @Test
  fun `group message reply and edited metadata converts cleanly to direct message view`() {
    val groupMsg = GroupMessage(
      id = "gm_edit_1",
      groupId = "g1",
      senderId = "u2",
      senderName = "Carlos",
      text = "Mensaje corregido",
      replyToMessageId = "m0",
      replyToSenderName = "Carlos",
      replyToPreview = "Hola",
      edited = true,
    )
    val view = groupMsg.toDirectMessageView()
    assertEquals("m0", view.replyToMessageId)
    assertEquals("Carlos", view.replyToSenderName)
    assertEquals("Hola", view.replyToPreview)
    assertTrue(view.edited)
    assertEquals("Mensaje corregido", view.text)
  }

  @Test
  fun `mention detection, filtering, insertion, and highlighting work accurately`() {
    assertEquals(0 to "", findActiveMentionQuery("@", 1))
    assertEquals(5 to "ju", findActiveMentionQuery("Hola @ju", 8))
    assertNull(findActiveMentionQuery("correo@ejemplo.com", 18))
    assertNull(findActiveMentionQuery("Hola\n", 5))

    val candidates = listOf(
      MentionCandidate(userId = "1", displayName = "Ana Lucia"),
      MentionCandidate(userId = "2", displayName = "Juan Perez"),
      MentionCandidate(userId = "3", displayName = "Carlos"),
    )
    assertEquals(listOf("Juan Perez"), filterMentionCandidates(candidates, "ju").map { it.displayName })
    assertTrue(filterMentionCandidates(candidates, "Carlos ").isEmpty())

    val inserted = insertMention(
      value = TextFieldValue("Hola @ju", TextRange(8)),
      atIndex = 5,
      displayName = "Juan Perez",
    )
    assertEquals("Hola @Juan Perez ", inserted.text)
    assertEquals(17, inserted.selection.end)

    val annotated = buildMentionAnnotatedText(
      text = "Hola @Juan Perez y @Carlos nos vemos allá",
      mentionNames = listOf("Juan Perez", "Carlos"),
      highlightColor = Color.Blue,
    )
    assertEquals(2, annotated.spanStyles.size)
    assertEquals("Hola @Juan Perez y @Carlos nos vemos allá", annotated.text)
  }

  @Test
  fun `location point diagnostic constants and precision helpers are well defined`() {
    assertEquals("NO_PERMISSION", ChatLocationPoint.STATUS_NO_PERMISSION)
    assertEquals("COARSE_ONLY", ChatLocationPoint.STATUS_COARSE_ONLY)
    assertEquals("GPS_DISABLED", ChatLocationPoint.STATUS_GPS_DISABLED)
    assertEquals("UNAVAILABLE", ChatLocationPoint.STATUS_UNAVAILABLE)

    val context = ApplicationProvider.getApplicationContext<Context>()
    assertFalse(LocationHelper.hasPreciseLocationPermission(context))
    assertFalse(LocationHelper.hasCoarseOnlyLocationPermission(context))
    assertEquals("-3345_-7067", LocationHelper.formatZoneKey(-33.4488912, -70.6692655))
  }

  @Test
  fun `blocked profile cache persists photo and display name across sessions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ownerId = "owner_123"
    val blocked = com.example.model.KnownContact(
      userId = "ieZv9vQG_abc",
      displayName = "Antonio Osses",
      avatarColor = "teal",
      photoData = "AQIDBA==",
      photoUrl = "https://example.com/avatar.jpg",
      subtitle = "Retiro, CL",
    )
    com.example.ui.viewmodel.BlockedProfilePrefs.save(context, ownerId, mapOf(blocked.userId to blocked))
    val loaded = com.example.ui.viewmodel.BlockedProfilePrefs.load(context, ownerId)
    assertEquals(1, loaded.size)
    val restored = loaded[blocked.userId]
    assertNotNull(restored)
    assertEquals("Antonio Osses", restored?.displayName)
    assertEquals("teal", restored?.avatarColor)
    assertEquals("AQIDBA==", restored?.photoData)
    assertEquals("https://example.com/avatar.jpg", restored?.photoUrl)
    assertEquals("Retiro, CL", restored?.subtitle)
  }
}
