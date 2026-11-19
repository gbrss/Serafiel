package com.example.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SocialPurpose(
    val labelEs: String,
    val shortDescriptionEs: String,
) {
    FRIENDSHIP("Amistad", "Conocer gente maja y compartir planes"),
    NETWORKING("Networking", "Charlar sobre proyectos, ideas o trabajo"),
    TOURISM("Turismo", "Recomendaciones locales y exploración"),
    HELP("Ayuda", "Orientación rápida o apoyo vecinal"),
    CASUAL("Charla", "Conversación espontánea sin presiones");

    companion object {
        fun fromRaw(raw: String?): SocialPurpose =
            entries.firstOrNull { it.name == raw } ?: CASUAL
    }
}

enum class AvatarColor(
    val key: String,
    val labelEs: String,
    val bgColor: Color,
    val contentColor: Color,
) {
    AMBER("amber", "Ámbar", Color(0xFFE77E3A), Color(0xFFFFFFFF)),
    SAGE("sage", "Salvia", Color(0xFF398E8A), Color(0xFFFFFFFF)),
    CORAL("coral", "Coral", Color(0xFFE06358), Color(0xFFFFFFFF)),
    SUN("sun", "Sol", Color(0xFFE5A429), Color(0xFF2B1B00)),
    TEAL("teal", "Brisa", Color(0xFF2E6F8E), Color(0xFFFFFFFF)),
    INDIGO("indigo", "Noche", Color(0xFF5D679B), Color(0xFFFFFFFF));

    companion object {
        fun fromKey(key: String?): AvatarColor =
            entries.firstOrNull { it.key == key } ?: AMBER
    }
}

enum class ReportReason(
    val key: String,
    val labelEs: String,
) {
    HARASSMENT("harassment", "Acoso o trato irrespetuoso"),
    SPAM("spam", "Spam o publicidad no solicitada"),
    INAPPROPRIATE("inappropriate", "Contenido inapropiado"),
    IMPERSONATION("impersonation", "Suplantación de identidad"),
    OTHER("other", "Otro motivo de seguridad");
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarColor: String = AvatarColor.AMBER.key,
    val photoData: String? = null,
    val photoUrl: String? = null,
    val online: Boolean = false,
    val showOnline: Boolean = true,
    val lastSeenAt: Timestamp? = null,
    val preferredPurposes: List<String> = listOf(SocialPurpose.FRIENDSHIP.name, SocialPurpose.CASUAL.name),
    val blockedUserIds: List<String> = emptyList(),
    val safetyAccepted: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ProximityBeacon(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val purpose: String = SocialPurpose.CASUAL.name,
    val statusText: String = "",
    val avatarColor: String = AvatarColor.AMBER.key,
    val photoData: String? = null,
    val photoUrl: String? = null,
    val online: Boolean = false,
    val lastSeenAt: Timestamp? = null,
    val accuracyMeters: Int? = null,
    val languages: List<String> = listOf("Español"),
    val zoneKey: String = "plaza_central",
    val zoneName: String = "Zona Abierta",
    val approxLat: Double = 0.0,
    val approxLon: Double = 0.0,
    val active: Boolean = true,
    val verifiedGoogle: Boolean = true,
    val radiusKm: Long = 50L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun isCurrentlyOnline(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!online) return false
        val seenMs = lastSeenAt?.toDate()?.time ?: updatedAt?.toDate()?.time ?: return true
        return (nowMs - seenMs) <= ONLINE_STALE_MS
    }

    fun isGlobal(): Boolean = radiusKm > MAX_LOCAL_RADIUS_KM

    companion object {
        const val ONLINE_STALE_MS = 10 * 60_000L
        const val MAX_LOCAL_RADIUS_KM = 1000
        const val GLOBAL_RADIUS_KM = 25_000
    }
}

@Keep
@IgnoreExtraProperties
data class ZoneMessage(
    @DocumentId val id: String = "",
    val zoneKey: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderColor: String = AvatarColor.AMBER.key,
    val purpose: String = SocialPurpose.CASUAL.name,
    val text: String = "",
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val edited: Boolean = false,
    val editedAt: Timestamp? = null,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DirectChat(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantColors: Map<String, String> = emptyMap(),
    val initiatorId: String = "",
    val purpose: String = SocialPurpose.CASUAL.name,
    val status: String = "active",
    val lastMessage: String = "",
    val lastSenderId: String = "",
    val zoneName: String = "",
    /** uid -> último instante en que ese participante vio la conversación. */
    val lastReadAt: Map<String, Timestamp> = emptyMap(),
    /** uid -> último instante en que al dispositivo de ese participante le llegaron mensajes. */
    val lastDeliveredAt: Map<String, Timestamp> = emptyMap(),
    /** uid -> último pulso de "escribiendo..." (se borra al enviar o dejar de escribir). */
    val typingAt: Map<String, Timestamp> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun isPeerTyping(myUserId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        return typingAt.any { (uid, ts) ->
            uid != myUserId && (nowMs - ts.toDate().time) in -TYPING_SKEW_MS..TYPING_WINDOW_MS
        }
    }

    companion object {
        const val TYPING_WINDOW_MS = 7_000L
        private const val TYPING_SKEW_MS = 30_000L
    }
}

/** Estado de entrega de un mensaje propio en un chat 1:1. */
enum class MessageDeliveryStatus { SENT, DELIVERED, SEEN }

@Keep
@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = TYPE_TEXT,
    val mediaData: String? = null,
    val mediaMimeType: String? = null,
    val mediaName: String? = null,
    val durationSec: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val liveActive: Boolean = false,
    val liveExpiresAtMs: Long = 0L,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val edited: Boolean = false,
    val editedAt: Timestamp? = null,
    /** uid -> emoji con el que reaccionó. */
    val reactions: Map<String, String> = emptyMap(),
    /**
     * Nº de trozos en la subcolección `chunks` con el archivo completo: HD en imágenes,
     * el audio/video completo en el resto. 0 = todo va en [mediaData].
     */
    val chunkCount: Long = 0L,
    val mediaSizeBytes: Long = 0L,
    /** Colección padre ("chats" o "groups"); solo para la vista local, no se guarda. */
    val sourceCollection: String = SOURCE_CHATS,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun deliveryStatus(chat: DirectChat?): MessageDeliveryStatus {
        val sentMs = createdAt?.toDate()?.time ?: return MessageDeliveryStatus.SENT
        val peerId = chat?.participantIds?.firstOrNull { it != senderId } ?: return MessageDeliveryStatus.SENT
        val readMs = chat.lastReadAt[peerId]?.toDate()?.time ?: 0L
        if (readMs >= sentMs) return MessageDeliveryStatus.SEEN
        val deliveredMs = chat.lastDeliveredAt[peerId]?.toDate()?.time ?: 0L
        if (deliveredMs >= sentMs || readMs >= sentMs) return MessageDeliveryStatus.DELIVERED
        return MessageDeliveryStatus.SENT
    }

    fun previewLabel(): String = when (messageType) {
        TYPE_IMAGE -> text.ifBlank { "Imagen compartida" }.let { if (text.isBlank()) "Foto" else "Foto: $text" }
        TYPE_VIDEO -> if (text.isBlank()) "Video (${formatDuration(durationSec)})" else "Video: $text"
        TYPE_VOICE -> "Mensaje de voz (${formatDuration(durationSec)})"
        TYPE_AUDIO -> mediaName?.takeIf { it.isNotBlank() }?.let { "Audio: $it" } ?: "Archivo de audio"
        TYPE_LOCATION -> locationLabel?.takeIf { it.isNotBlank() }?.let { "Ubicación: $it" } ?: "Ubicación actual"
        TYPE_LIVE_LOCATION -> when {
            liveActive && text.isNotBlank() -> "Ubicación en tiempo real: $text"
            liveActive -> "Ubicación en tiempo real"
            else -> "Ubicación compartida"
        }
        else -> text
    }

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_VOICE = "voice"
        const val TYPE_AUDIO = "audio"
        const val TYPE_LOCATION = "location"
        const val TYPE_LIVE_LOCATION = "live_location"
        const val TYPE_VIDEO = "video"

        const val SOURCE_CHATS = "chats"
        const val SOURCE_GROUPS = "groups"

        fun formatDuration(seconds: Long): String {
            val safe = seconds.coerceAtLeast(0L)
            val mins = safe / 60L
            val secs = safe % 60L
            return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
        }
    }
}

@Keep
@IgnoreExtraProperties
data class SafetyReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val targetType: String = "beacon",
    val targetId: String = "",
    val reason: String = ReportReason.OTHER.key,
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class ZoneLocation(
    val zoneKey: String,
    val zoneName: String,
    val approxLat: Double,
    val approxLon: Double,
    val isFromGps: Boolean,
    val accuracyMeters: Int? = null,
    val isManualChoice: Boolean = false,
) {
    val hasValidCoords: Boolean
        get() = approxLat != 0.0 || approxLon != 0.0
}

data class NearbyPlaceSuggestion(
    val id: String,
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val categoryLabel: String = "",
    val distanceMeters: Int? = null,
)

data class PeerVisualInfo(
    val photoData: String? = null,
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val distanceKm: Double? = null,
    val lastSeenAt: Timestamp? = null,
    val zoneName: String = "",
)

enum class GroupCategory(
    val key: String,
    val labelEs: String,
) {
    FAMILY("FAMILY", "Familia"),
    FRIENDS("FRIENDS", "Amigos"),
    WORK("WORK", "Trabajo"),
    NEIGHBORS("NEIGHBORS", "Vecinos"),
    COMMUNITY("COMMUNITY", "Comunidad"),
    CUSTOM("CUSTOM", "Personalizado");

    companion object {
        fun fromKey(raw: String?): GroupCategory =
            entries.firstOrNull { it.key == raw } ?: FRIENDS
    }
}

@Keep
@IgnoreExtraProperties
data class SocialGroup(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = GroupCategory.FRIENDS.key,
    val colorKey: String = AvatarColor.AMBER.key,
    val creatorId: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val memberColors: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class GroupMessage(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val memberIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = DirectMessage.TYPE_TEXT,
    val mediaData: String? = null,
    val mediaMimeType: String? = null,
    val mediaName: String? = null,
    val durationSec: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val liveActive: Boolean = false,
    val liveExpiresAtMs: Long = 0L,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreview: String? = null,
    val edited: Boolean = false,
    val editedAt: Timestamp? = null,
    val reactions: Map<String, String> = emptyMap(),
    val chunkCount: Long = 0L,
    val mediaSizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toDirectMessageView(): DirectMessage = DirectMessage(
        id = id,
        chatId = groupId,
        participantIds = memberIds,
        senderId = senderId,
        senderName = senderName,
        text = text,
        messageType = messageType,
        mediaData = mediaData,
        mediaMimeType = mediaMimeType,
        mediaName = mediaName,
        durationSec = durationSec,
        latitude = latitude,
        longitude = longitude,
        locationLabel = locationLabel,
        liveActive = liveActive,
        liveExpiresAtMs = liveExpiresAtMs,
        replyToMessageId = replyToMessageId,
        replyToSenderName = replyToSenderName,
        replyToPreview = replyToPreview,
        edited = edited,
        editedAt = editedAt,
        reactions = reactions,
        chunkCount = chunkCount,
        mediaSizeBytes = mediaSizeBytes,
        sourceCollection = DirectMessage.SOURCE_GROUPS,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun previewLabel(): String = toDirectMessageView().previewLabel()
}

data class KnownContact(
    val userId: String,
    val displayName: String,
    val avatarColor: String = AvatarColor.AMBER.key,
    val photoData: String? = null,
    val photoUrl: String? = null,
    val subtitle: String = "",
)
