package com.example.model

import com.example.R
import com.example.i18n.tr

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
    FRIENDSHIP(tr(R.string.s_amistad), tr(R.string.s_conocer_gente_maja_y_compartir)),
    NETWORKING(tr(R.string.s_networking), tr(R.string.s_charlar_sobre_proyectos_ideas_o)),
    TOURISM(tr(R.string.s_turismo), tr(R.string.s_recomendaciones_locales_y_exploracion)),
    HELP(tr(R.string.s_ayuda), tr(R.string.s_orientacion_rapida_o_apoyo_vecinal)),
    CASUAL(tr(R.string.s_charla), tr(R.string.s_conversacion_espontanea_sin_presiones));

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
    AMBER("amber", tr(R.string.s_ambar), Color(0xFFE77E3A), Color(0xFFFFFFFF)),
    SAGE("sage", tr(R.string.s_salvia), Color(0xFF398E8A), Color(0xFFFFFFFF)),
    CORAL("coral", tr(R.string.s_coral), Color(0xFFE06358), Color(0xFFFFFFFF)),
    SUN("sun", tr(R.string.s_sol), Color(0xFFE5A429), Color(0xFF2B1B00)),
    TEAL("teal", tr(R.string.s_brisa), Color(0xFF2E6F8E), Color(0xFFFFFFFF)),
    INDIGO("indigo", tr(R.string.s_noche), Color(0xFF5D679B), Color(0xFFFFFFFF));

    companion object {
        fun fromKey(key: String?): AvatarColor =
            entries.firstOrNull { it.key == key } ?: AMBER
    }
}

enum class ReportReason(
    val key: String,
    val labelEs: String,
) {
    HARASSMENT("harassment", tr(R.string.s_acoso_o_trato_irrespetuoso)),
    SPAM("spam", tr(R.string.s_spam_o_publicidad_no_solicitada)),
    INAPPROPRIATE("inappropriate", tr(R.string.s_contenido_inapropiado)),
    IMPERSONATION("impersonation", tr(R.string.s_suplantacion_de_identidad)),
    OTHER("other", tr(R.string.s_otro_motivo_de_seguridad));
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
    val languages: List<String> = listOf(tr(R.string.s_espanol)),
    val zoneKey: String = "plaza_central",
    val zoneName: String = tr(R.string.s_zona_abierta),
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
    /** Mensaje fijado en la parte superior del chat ('' = ninguno). */
    val pinnedMessageId: String = "",
    val pinnedPreview: String = "",
    val zoneName: String = "",
    /** uid -> último instante en que ese participante vio la conversación. */
    val lastReadAt: Map<String, Timestamp> = emptyMap(),
    /** uid -> último instante en que al dispositivo de ese participante le llegaron mensajes. */
    val lastDeliveredAt: Map<String, Timestamp> = emptyMap(),
    /** uid -> último pulso de "escribiendo..." (se borra al enviar o dejar de escribir). */
    val typingAt: Map<String, Timestamp> = emptyMap(),
    /** uid -> mensajes sin leer de ese participante. */
    val unreadCount: Map<String, Long> = emptyMap(),
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
    /** Onda de la nota de voz (48 niveles codificados). */
    val waveform: String = "",
    /** true si el mensaje es una copia reenviada de otro chat. */
    val forwarded: Boolean = false,
    /** Encuesta: opciones y votos (uid -> índice de la opción elegida). */
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, Long> = emptyMap(),
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
        TYPE_IMAGE -> text.ifBlank { tr(R.string.s_imagen_compartida) }.let { if (text.isBlank()) tr(R.string.s_foto) else tr(R.string.s_foto_2, text) }
        TYPE_POLL -> tr(R.string.s_encuesta, text)
        TYPE_VIDEO -> if (text.isBlank()) tr(R.string.s_video, formatDuration(durationSec)) else tr(R.string.s_video_2, text)
        TYPE_VOICE -> tr(R.string.s_mensaje_de_voz, formatDuration(durationSec))
        TYPE_AUDIO -> mediaName?.takeIf { it.isNotBlank() }?.let { tr(R.string.s_audio, it) } ?: tr(R.string.s_archivo_de_audio)
        TYPE_LOCATION -> locationLabel?.takeIf { it.isNotBlank() }?.let { tr(R.string.s_ubicacion, it) } ?: tr(R.string.s_ubicacion_actual)
        TYPE_LIVE_LOCATION -> when {
            liveActive && text.isNotBlank() -> tr(R.string.s_ubicacion_en_tiempo_real, text)
            liveActive -> tr(R.string.s_ubicacion_en_tiempo_real_2)
            else -> tr(R.string.s_ubicacion_compartida)
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
        const val TYPE_POLL = "poll"

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
    FAMILY("FAMILY", tr(R.string.s_familia)),
    FRIENDS("FRIENDS", tr(R.string.s_amigos)),
    WORK("WORK", tr(R.string.s_trabajo)),
    NEIGHBORS("NEIGHBORS", tr(R.string.s_vecinos)),
    COMMUNITY("COMMUNITY", tr(R.string.s_comunidad)),
    CUSTOM("CUSTOM", tr(R.string.s_personalizado));

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
    val pinnedMessageId: String = "",
    val pinnedPreview: String = "",
    /** uid -> última vez que escribió (para "escribiendo..."). */
    val typingAt: Map<String, Timestamp> = emptyMap(),
    /** uid -> última vez que abrió/leyó el grupo (para "visto"). */
    val lastReadAt: Map<String, Timestamp> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    /** Nombres de quienes están escribiendo ahora (sin contar al usuario). */
    fun typingNames(myUserId: String, nowMs: Long = System.currentTimeMillis()): List<String> =
        typingAt.filter { (uid, ts) ->
            uid != myUserId && uid in memberIds && (nowMs - ts.toDate().time) in -30_000L..7_000L
        }.keys.map { (memberNames[it] ?: "").substringBefore(' ').ifBlank { "…" } }
}

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
    val waveform: String = "",
    /** true si el mensaje es una copia reenviada de otro chat. */
    val forwarded: Boolean = false,
    /** Encuesta: opciones y votos (uid -> índice de la opción elegida). */
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    /** SEEN = todos los demás miembros lo leyeron; DELIVERED = al menos uno; SENT = nadie aún. */
    fun deliveryStatus(group: SocialGroup?): MessageDeliveryStatus {
        val sentMs = createdAt?.toDate()?.time ?: return MessageDeliveryStatus.SENT
        val others = group?.memberIds?.filter { it != senderId }.orEmpty()
        if (others.isEmpty()) return MessageDeliveryStatus.SENT
        val seen = others.count { (group!!.lastReadAt[it]?.toDate()?.time ?: 0L) >= sentMs }
        return when {
            seen == others.size -> MessageDeliveryStatus.SEEN
            seen > 0 -> MessageDeliveryStatus.DELIVERED
            else -> MessageDeliveryStatus.SENT
        }
    }

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
        waveform = waveform,
        forwarded = forwarded,
        pollOptions = pollOptions,
        pollVotes = pollVotes,
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
