package com.example.data

import com.example.i18n.tr

import android.content.Context
import com.example.R
import com.example.model.AvatarColor
import com.example.model.DirectChat
import com.example.model.DirectMessage
import com.example.model.GroupCategory
import com.example.model.GroupMessage
import com.example.model.KnownContact
import com.example.model.ProximityBeacon
import com.example.model.ReportReason
import com.example.model.SocialGroup
import com.example.model.SocialPurpose
import com.example.model.UserProfile
import com.example.model.ZoneLocation
import com.example.model.ZoneMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

class SerafielRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException(tr(R.string.s_debes_iniciar_sesion_con_google))

    private fun normalizeDisplayName(raw: String?, fallback: String = tr(R.string.s_usuario_serafiel)): String {
        val trimmed = raw.orEmpty().trim().take(40)
        return if (trimmed.length >= 2) trimmed else fallback
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (userId.isBlank()) return flow { emit(null) }
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun saveUserProfile(
        userId: String,
        displayName: String,
        bio: String,
        avatarColor: AvatarColor,
        preferredPurposes: List<SocialPurpose>,
        photoData: String? = null,
        photoUrl: String? = null,
        online: Boolean? = null,
        showOnline: Boolean? = null,
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { tr(R.string.s_la_sesion_activa_cambio_operacion) }
            val cleanName = displayName.trim().take(40)
            require(cleanName.length >= 2) { tr(R.string.s_el_nombre_debe_tener_al) }
            val cleanBio = bio.trim().take(200)
            val purposes = preferredPurposes.take(5).map { it.name }

            val docRef = db.collection("users").document(uid)
            val exists = docRef.get().await().exists()
            if (!exists) {
                val effectiveShowOnline = showOnline ?: true
                val effectiveOnline = (online ?: true) && effectiveShowOnline
                val payload = linkedMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "bio" to cleanBio,
                    "avatarColor" to avatarColor.key,
                    "photoData" to (photoData ?: "").take(160000),
                    "photoUrl" to (photoUrl ?: "").take(500),
                    "online" to effectiveOnline,
                    "showOnline" to effectiveShowOnline,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "preferredPurposes" to purposes,
                    "blockedUserIds" to emptyList<String>(),
                    "safetyAccepted" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val updates = linkedMapOf<String, Any>(
                    "displayName" to cleanName,
                    "bio" to cleanBio,
                    "avatarColor" to avatarColor.key,
                    "preferredPurposes" to purposes,
                    "safetyAccepted" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (photoData != null) updates["photoData"] = photoData.take(160000)
                if (photoUrl != null) updates["photoUrl"] = photoUrl.take(500)
                if (showOnline != null) updates["showOnline"] = showOnline
                if (online != null) updates["online"] = online
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setUserOnlineStatus(
        userId: String,
        online: Boolean,
        showOnlineOpt: Boolean? = null,
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val showPref = showOnlineOpt ?: userSnap.getBoolean("showOnline") ?: true
            val effectiveOnline = online && showPref

            if (!userSnap.exists()) {
                val fallbackName = normalizeDisplayName(auth.currentUser?.displayName)
                userRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to fallbackName,
                        "bio" to "",
                        "avatarColor" to AvatarColor.AMBER.key,
                        "online" to effectiveOnline,
                        "showOnline" to showPref,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "preferredPurposes" to listOf(SocialPurpose.CASUAL.name),
                        "blockedUserIds" to emptyList<String>(),
                        "safetyAccepted" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                val updates = linkedMapOf<String, Any>(
                    "online" to effectiveOnline,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (showOnlineOpt != null) {
                    updates["showOnline"] = showOnlineOpt
                }
                userRef.update(updates).await()
            }

            val beaconRef = db.collection("beacons").document(uid)
            if (beaconRef.get().await().exists()) {
                beaconRef.update(
                    mapOf(
                        "online" to effectiveOnline,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePhoto(
        userId: String,
        photoData: String,
        photoUrl: String,
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanData = photoData.take(160000)
            val cleanUrl = photoUrl.take(500)
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                val fallbackName = normalizeDisplayName(auth.currentUser?.displayName)
                userRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to fallbackName,
                        "bio" to "",
                        "avatarColor" to AvatarColor.AMBER.key,
                        "photoData" to cleanData,
                        "photoUrl" to cleanUrl,
                        "preferredPurposes" to listOf(SocialPurpose.CASUAL.name),
                        "blockedUserIds" to emptyList<String>(),
                        "safetyAccepted" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                userRef.update(
                    mapOf(
                        "photoData" to cleanData,
                        "photoUrl" to cleanUrl,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            val beaconRef = db.collection("beacons").document(uid)
            if (beaconRef.get().await().exists()) {
                beaconRef.update(
                    mapOf(
                        "photoData" to cleanData,
                        "photoUrl" to cleanUrl,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setUserBlocked(userId: String, targetUserId: String, blocked: Boolean): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(targetUserId.isNotBlank() && targetUserId != uid)
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val fallbackName = normalizeDisplayName(auth.currentUser?.displayName)
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to fallbackName,
                        "bio" to "",
                        "avatarColor" to AvatarColor.AMBER.key,
                        "preferredPurposes" to listOf(SocialPurpose.CASUAL.name),
                        "blockedUserIds" to if (blocked) listOf(targetUserId) else emptyList(),
                        "safetyAccepted" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                val updateOp = if (blocked) FieldValue.arrayUnion(targetUserId) else FieldValue.arrayRemove(targetUserId)
                docRef.update(
                    mapOf(
                        "blockedUserIds" to updateOp,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- PROXIMITY BEACONS ---

    fun observeMyBeacon(userId: String): Flow<ProximityBeacon?> {
        if (userId.isBlank()) return flow { emit(null) }
        val docRef = db.collection("beacons").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(ProximityBeacon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    fun observeActiveBeacons(userId: String): Flow<List<ProximityBeacon>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "beacons"
        return db.collection(path)
            .whereEqualTo("active", true)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ProximityBeacon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun fetchActiveBeaconSnapshot(beaconUserId: String): ProximityBeacon? {
        if (beaconUserId.isBlank()) return null
        return try {
            val snap = db.collection("beacons").document(beaconUserId).get().await()
            if (snap.exists()) {
                snap.toObject(ProximityBeacon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun upsertMyBeacon(
        userId: String,
        displayName: String,
        purpose: SocialPurpose,
        statusText: String,
        avatarColor: AvatarColor,
        languages: List<String>,
        zone: ZoneLocation,
        active: Boolean,
        radiusKm: Int = 50,
        photoData: String? = null,
        photoUrl: String? = null,
        online: Boolean? = null,
    ): Result<Unit> {
        val docPath = "beacons/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanName = displayName.trim().take(40)
            require(cleanName.length >= 2) { tr(R.string.s_el_nombre_debe_tener_al) }
            val cleanStatus = statusText.trim().take(160)
            val cleanLangs = languages.map { it.trim() }.filter { it.isNotBlank() }.take(5)
            val cleanZoneKey = zone.zoneKey.take(40)
            // El nombre puede ser una calle exacta: nunca se publica.
            val cleanZoneName = tr(R.string.s_zona_aproximada)
            val clampedRadius = radiusKm.coerceIn(1, ProximityBeacon.GLOBAL_RADIUS_KM).toLong()
            // Privacidad: la baliza publica una posición ajustada, no la real.
            val (pubLat, pubLon) = PrivacySettings.blur(zone.approxLat, zone.approxLon)
            val cleanAcc: Int? = null

            val docRef = db.collection("beacons").document(uid)
            val exists = docRef.get().await().exists()
            if (!exists) {
                val payload = linkedMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "purpose" to purpose.name,
                    "statusText" to cleanStatus,
                    "avatarColor" to avatarColor.key,
                    "photoData" to (photoData ?: "").take(160000),
                    "photoUrl" to (photoUrl ?: "").take(500),
                    "online" to (online ?: true),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "languages" to cleanLangs,
                    "zoneKey" to cleanZoneKey,
                    "zoneName" to cleanZoneName,
                    "approxLat" to pubLat,
                    "approxLon" to pubLon,
                    "active" to active,
                    "verifiedGoogle" to true,
                    "radiusKm" to clampedRadius,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (cleanAcc != null) payload["accuracyMeters"] = cleanAcc
                docRef.set(payload).await()
            } else {
                val updates = linkedMapOf<String, Any>(
                    "displayName" to cleanName,
                    "purpose" to purpose.name,
                    "statusText" to cleanStatus,
                    "avatarColor" to avatarColor.key,
                    "languages" to cleanLangs,
                    "zoneKey" to cleanZoneKey,
                    "zoneName" to cleanZoneName,
                    "approxLat" to pubLat,
                    "approxLon" to pubLon,
                    "active" to active,
                    "verifiedGoogle" to true,
                    "radiusKm" to clampedRadius,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (photoData != null) updates["photoData"] = photoData.take(160000)
                if (photoUrl != null) updates["photoUrl"] = photoUrl.take(500)
                if (online != null) updates["online"] = online
                if (cleanAcc != null) updates["accuracyMeters"] = cleanAcc
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBeaconRadius(userId: String, radiusKm: Int): Result<Unit> {
        val docPath = "beacons/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("beacons").document(uid)
            if (docRef.get().await().exists()) {
                docRef.update(
                    mapOf(
                        "radiusKm" to radiusKm.coerceIn(1, ProximityBeacon.GLOBAL_RADIUS_KM).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /** Fija ([messageId] != null) o desfija el mensaje destacado de un chat o grupo. */
    suspend fun setPinnedMessage(
        userId: String,
        isGroup: Boolean,
        parentId: String,
        messageId: String?,
        preview: String?,
    ): Result<Unit> {
        val collection = if (isGroup) "groups" else "chats"
        val path = "$collection/$parentId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection(collection).document(parentId).update(
                mapOf(
                    "pinnedMessageId" to (messageId ?: "").take(120),
                    "pinnedPreview" to (if (messageId == null) "" else preview.orEmpty().trim().take(160)),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setBeaconActive(userId: String, active: Boolean): Result<Unit> {
        val docPath = "beacons/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("beacons").document(uid)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- LOCAL ZONE ROOM MESSAGES ---

    fun observeZoneMessages(userId: String, zoneKey: String): Flow<List<ZoneMessage>> {
        if (userId.isBlank() || zoneKey.isBlank()) return flow { emit(emptyList()) }
        val primaryPath = "zone_rooms/$zoneKey/messages"
        val primaryFlow = db.collection("zone_rooms")
            .document(zoneKey)
            .collection("messages")
            .whereEqualTo("zoneKey", zoneKey)
            .whereEqualTo("archived", false)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ZoneMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, primaryPath)
                }
                throw error
            }

        if (zoneKey != "plaza_central") {
            return primaryFlow.map { list ->
                list.sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
        }

        // Merge any messages posted during the previous build under "zona_abierta" with "plaza_central"
        val secondaryFlow = db.collection("zone_rooms")
            .document("zona_abierta")
            .collection("messages")
            .whereEqualTo("zoneKey", "zona_abierta")
            .whereEqualTo("archived", false)
            .limit(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ZoneMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { emit(emptyList()) }

        return combine(primaryFlow, secondaryFlow) { primary, secondary ->
            (primary + secondary)
                .distinctBy { it.id }
                .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
        }
    }

    suspend fun sendZoneMessage(
        userId: String,
        zoneKey: String,
        senderName: String,
        senderColor: AvatarColor,
        purpose: SocialPurpose,
        text: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
    ): Result<Unit> {
        val path = "zone_rooms/$zoneKey/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanText = text.trim().take(500)
            require(cleanText.isNotEmpty()) { tr(R.string.s_el_mensaje_no_puede_estar) }
            val cleanName = normalizeDisplayName(senderName)
            val docRef = db.collection("zone_rooms")
                .document(zoneKey)
                .collection("messages")
                .document()

            val payload = linkedMapOf<String, Any?>(
                "zoneKey" to zoneKey,
                "senderId" to uid,
                "senderName" to cleanName,
                "senderColor" to senderColor.key,
                "purpose" to purpose.name,
                "text" to cleanText,
                "replyToMessageId" to replyToMessageId?.takeIf { it.isNotBlank() }?.take(120),
                "replyToSenderName" to replyToSenderName?.takeIf { it.isNotBlank() }?.take(60),
                "replyToPreview" to replyToPreview?.takeIf { it.isNotBlank() }?.take(160),
                "archived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }

            commitQueued(db.batch().also { it.set(docRef, payload) }, OperationType.CREATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editZoneMessage(
        userId: String,
        zoneKey: String,
        messageId: String,
        newText: String,
    ): Result<Unit> {
        val path = "zone_rooms/$zoneKey/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanText = newText.trim().take(500)
            require(cleanText.isNotEmpty()) { tr(R.string.s_el_mensaje_no_puede_estar) }
            db.collection("zone_rooms")
                .document(zoneKey)
                .collection("messages")
                .document(messageId)
                .update(
                    mapOf(
                        "text" to cleanText,
                        "edited" to true,
                        "editedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun archiveZoneMessage(userId: String, zoneKey: String, messageId: String): Result<Unit> {
        val path = "zone_rooms/$zoneKey/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("zone_rooms")
                .document(zoneKey)
                .collection("messages")
                .document(messageId)
                .update(
                    mapOf(
                        "archived" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 1:1 DIRECT CHATS ---

    fun observeMyChats(userId: String): Flow<List<DirectChat>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "chats"
        return db.collection(path)
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(DirectChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun startOrOpenDirectChat(
        userId: String,
        myName: String,
        myColor: AvatarColor,
        peerId: String,
        peerName: String,
        peerColor: AvatarColor,
        purpose: SocialPurpose,
        zoneName: String,
        initialGreeting: String = "",
        sendGreetingIfExists: Boolean = false,
    ): Result<String> {
        val sortedIds = listOf(userId, peerId).sorted()
        val chatId = "${sortedIds[0]}_${sortedIds[1]}"
        val chatPath = "chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(peerId.isNotBlank() && peerId != uid)

            val chatRef = db.collection("chats").document(chatId)
            val snapshot = chatRef.get().await()
            val cleanGreeting = initialGreeting.trim().take(500)
            val cleanMyName = normalizeDisplayName(myName)
            val cleanPeerName = normalizeDisplayName(peerName, fallback = tr(R.string.s_persona_cercana))

            if (!snapshot.exists()) {
                val preview = cleanGreeting.ifBlank { tr(R.string.s_conexion_iniciada_para, purpose.labelEs) }.take(240)
                chatRef.set(
                    mapOf(
                        "participantIds" to sortedIds,
                        "participantNames" to mapOf(
                            userId to cleanMyName,
                            peerId to cleanPeerName,
                        ),
                        "participantColors" to mapOf(
                            userId to myColor.key,
                            peerId to peerColor.key,
                        ),
                        "initiatorId" to uid,
                        "purpose" to purpose.name,
                        "status" to "pending",
                        "lastMessage" to preview,
                        "zoneName" to zoneName.trim().take(80),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                if (cleanGreeting.isNotEmpty()) {
                    chatRef.collection("messages").document("greeting").set(
                        mapOf(
                            "chatId" to chatId,
                            "participantIds" to sortedIds,
                            "senderId" to uid,
                            "senderName" to cleanMyName,
                            "text" to cleanGreeting,
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            } else if (snapshot.getString("status") == "pending" || snapshot.getString("status") == "declined") {
                // Solicitud sin resolver: no se reenvía ni se reabre desde aquí.
            } else if (sendGreetingIfExists && cleanGreeting.isNotEmpty()) {
                chatRef.collection("messages").document().set(
                    mapOf(
                        "chatId" to chatId,
                        "participantIds" to sortedIds,
                        "senderId" to uid,
                        "senderName" to cleanMyName,
                        "text" to cleanGreeting,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                chatRef.update(
                    mapOf(
                        "status" to "active",
                        "lastMessage" to cleanGreeting.take(240),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else if (snapshot.getString("status") == "closed") {
                chatRef.update(
                    mapOf(
                        "status" to "active",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, chatPath)
            Result.failure(e)
        }
    }

    /** La persona que recibió la solicitud la acepta o la rechaza. */
    suspend fun respondToChatRequest(chatId: String, accept: Boolean): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            requireUserId()
            db.collection("chats").document(chatId).update(
                mapOf(
                    "status" to if (accept) "active" else "declined",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeChatMessages(userId: String, chatId: String): Flow<List<DirectMessage>> {
        if (userId.isBlank() || chatId.isBlank()) return flow { emit(emptyList()) }
        val path = "chats/$chatId/messages"
        return db.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(DirectMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun sendDirectMessage(
        userId: String,
        chatId: String,
        participantIds: List<String>,
        senderName: String,
        text: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
    ): Result<Unit> {
        val path = "chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(participantIds.size == 2 && uid in participantIds)
            val cleanText = text.trim().take(1000)
            require(cleanText.isNotEmpty())
            val cleanSenderName = normalizeDisplayName(senderName)

            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document()
            val payload = linkedMapOf<String, Any?>(
                "chatId" to chatId,
                "participantIds" to participantIds,
                "senderId" to uid,
                "senderName" to cleanSenderName,
                "text" to cleanText,
                "replyToMessageId" to replyToMessageId?.takeIf { it.isNotBlank() }?.take(120),
                "replyToSenderName" to replyToSenderName?.takeIf { it.isNotBlank() }?.take(60),
                "replyToPreview" to replyToPreview?.takeIf { it.isNotBlank() }?.take(160),
                "createdAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            val batch = db.batch()

            batch.set(msgRef, payload)

            batch.update(chatRef,
                mapOf(
                    "status" to "active",
                    "lastMessage" to cleanText.take(240),
                    "lastSenderId" to uid,
                    "typingAt.$uid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ) + unreadIncrement(uid, participantIds)

            )

            commitQueued(batch, OperationType.CREATE, path)


            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendRichDirectMessage(
        userId: String,
        chatId: String,
        participantIds: List<String>,
        senderName: String,
        messageType: String,
        text: String = "",
        mediaData: String? = null,
        mediaMimeType: String? = null,
        mediaName: String? = null,
        durationSec: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String? = null,
        liveActive: Boolean? = null,
        liveExpiresAtMs: Long? = null,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
        chatPreview: String,
        chunkFile: File? = null,
        waveform: String? = null,
        forwarded: Boolean = false,
    ): Result<String> {
        val path = "chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(participantIds.size == 2 && uid in participantIds)
            val cleanSenderName = normalizeDisplayName(senderName)
            val cleanText = text.trim().take(1000)

            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document()

            // Los trozos se suben antes que el mensaje para que el receptor los encuentre completos.
            val chunkCount = chunkFile?.let { ChunkedMediaStore.upload(msgRef, uid, it).toLong() }

            val payload = linkedMapOf<String, Any?>(
                "chatId" to chatId,
                "participantIds" to participantIds,
                "senderId" to uid,
                "senderName" to cleanSenderName,
                "text" to cleanText,
                "messageType" to messageType,
                "mediaData" to mediaData,
                "mediaMimeType" to mediaMimeType?.take(64),
                "mediaName" to mediaName?.take(120),
                "durationSec" to durationSec?.coerceIn(0L, 3600L),
                "latitude" to latitude,
                "longitude" to longitude,
                "locationLabel" to locationLabel?.trim()?.take(240),
                "liveActive" to liveActive,
                "liveExpiresAtMs" to liveExpiresAtMs,
                "replyToMessageId" to replyToMessageId?.takeIf { it.isNotBlank() }?.take(120),
                "replyToSenderName" to replyToSenderName?.takeIf { it.isNotBlank() }?.take(60),
                "replyToPreview" to replyToPreview?.takeIf { it.isNotBlank() }?.take(160),
                "chunkCount" to chunkCount,
                "mediaSizeBytes" to chunkFile?.length(),
                "waveform" to waveform?.take(64),
                "forwarded" to (if (forwarded) true else null),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
                .filterNot { (key, value) -> key == "mediaData" && value == "" }

            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(chatRef,
                mapOf(
                    "status" to "active",
                    "lastMessage" to chatPreview.trim().take(240),
                    "lastSenderId" to uid,
                    "typingAt.$uid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ) + unreadIncrement(uid, participantIds)
            )
            commitQueued(batch, OperationType.CREATE, path)
            chunkFile?.delete()


            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editDirectMessage(
        userId: String,
        chatId: String,
        messageId: String,
        newText: String,
        isLatestMessage: Boolean,
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanText = newText.trim().take(1000)
            require(cleanText.isNotEmpty())
            val chatRef = db.collection("chats").document(chatId)
            chatRef.collection("messages").document(messageId).update(
                mapOf(
                    "text" to cleanText,
                    "edited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            if (isLatestMessage) {
                chatRef.update(
                    mapOf(
                        "lastMessage" to cleanText.take(240),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDirectMessage(
        userId: String,
        chatId: String,
        messageId: String,
        fallbackLastMessage: String?,
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(messageId)
            ChunkedMediaStore.deleteChunks(msgRef)
            msgRef.delete().await()
            if (fallbackLastMessage != null) {
                chatRef.update(
                    mapOf(
                        "lastMessage" to fallbackLastMessage.take(240),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLiveLocationMessage(
        userId: String,
        chatId: String,
        messageId: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        liveActive: Boolean,
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val msgRef = db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            msgRef.update(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "locationLabel" to locationLabel.trim().take(240).ifBlank { tr(R.string.s_ubicacion_en_vivo) },
                    "liveActive" to liveActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            if (!liveActive) {
                db.collection("chats").document(chatId).update(
                    mapOf(
                        "lastMessage" to tr(R.string.s_ubicacion_compartida_finalizada),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDirectChats(userId: String, chatIds: List<String>): Result<Int> {
        val cleanIds = chatIds.distinct().filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return Result.success(0)
        return try {
            val uid = requireUserId()
            check(uid == userId)
            var deletedCount = 0
            for (chatId in cleanIds) {
                val chatRef = db.collection("chats").document(chatId)
                // Delete parent chat first so security rules permit cleanup of all peer + self messages
                chatRef.delete().await()
                val msgSnaps = chatRef.collection("messages")
                    .whereArrayContains("participantIds", uid)
                    .get()
                    .await()
                val chunks = msgSnaps.documents.chunked(400)
                for (chunk in chunks) {
                    val batch = db.batch()
                    chunk.forEach { doc -> batch.delete(doc.reference) }
                    batch.commit().await()
                }
                deletedCount += 1
            }
            Result.success(deletedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "chats")
            Result.failure(e)
        }
    }

    // --- SOCIAL GROUPS & GROUP MESSAGES ---

    fun observeMyGroups(userId: String): Flow<List<SocialGroup>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "groups"
        return db.collection(path)
            .whereArrayContains("memberIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SocialGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeGroupMessages(userId: String, groupId: String): Flow<List<GroupMessage>> {
        if (userId.isBlank() || groupId.isBlank()) return flow { emit(emptyList()) }
        val path = "groups/$groupId/messages"
        return db.collection("groups")
            .document(groupId)
            .collection("messages")
            .whereArrayContains("memberIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(GroupMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createGroup(
        userId: String,
        myName: String,
        myColor: AvatarColor,
        name: String,
        category: GroupCategory,
        color: AvatarColor,
        initialMembers: List<KnownContact>,
    ): Result<String> {
        val path = "groups"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanGroupName = name.trim().take(50)
            require(cleanGroupName.length >= 2) { tr(R.string.s_el_nombre_del_grupo_debe) }

            val cleanMyName = normalizeDisplayName(myName)
            val memberIds = linkedSetOf(uid)
            val memberNames = linkedMapOf(uid to cleanMyName)
            val memberColors = linkedMapOf(uid to myColor.key)

            initialMembers.take(49).forEach { member ->
                if (member.userId.isNotBlank() && member.userId != uid) {
                    memberIds.add(member.userId)
                    memberNames[member.userId] = normalizeDisplayName(member.displayName, tr(R.string.s_persona))
                    memberColors[member.userId] = AvatarColor.fromKey(member.avatarColor).key
                }
            }

            val groupRef = db.collection("groups").document()
            groupRef.set(
                mapOf(
                    "name" to cleanGroupName,
                    "category" to category.key,
                    "colorKey" to color.key,
                    "creatorId" to uid,
                    "memberIds" to memberIds.toList(),
                    "memberNames" to memberNames,
                    "memberColors" to memberColors,
                    "lastMessage" to tr(R.string.s_grupo_creado_miembro, memberIds.size, if (memberIds.size == 1) "" else "s"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(groupRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroup(
        userId: String,
        groupId: String,
        name: String,
        category: GroupCategory,
        color: AvatarColor,
        memberIds: List<String>,
        memberNames: Map<String, String>,
        memberColors: Map<String, String>,
    ): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanName = name.trim().take(50)
            require(cleanName.length >= 2) { tr(R.string.s_el_nombre_del_grupo_debe) }
            require(memberIds.isNotEmpty())
            val normalizedIds = memberIds.distinct().take(50)
            db.collection("groups").document(groupId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "category" to category.key,
                        "colorKey" to color.key,
                        "memberIds" to normalizedIds,
                        "memberNames" to memberNames.filterKeys { it in normalizedIds },
                        "memberColors" to memberColors.filterKeys { it in normalizedIds },
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(userId: String, group: SocialGroup): Result<Unit> {
        val path = "groups/${group.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val remainingIds = group.memberIds.filter { it != uid }
            require(remainingIds.isNotEmpty() && group.creatorId in remainingIds)
            db.collection("groups").document(group.id)
                .update(
                    mapOf(
                        "memberIds" to remainingIds,
                        "memberNames" to group.memberNames.filterKeys { it in remainingIds },
                        "memberColors" to group.memberColors.filterKeys { it in remainingIds },
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(userId: String, groupId: String): Result<Unit> {
        val path = "groups/$groupId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("groups").document(groupId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun sendGroupRichMessage(
        userId: String,
        groupId: String,
        memberIds: List<String>,
        senderName: String,
        messageType: String,
        text: String = "",
        mediaData: String? = null,
        mediaMimeType: String? = null,
        mediaName: String? = null,
        durationSec: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String? = null,
        liveActive: Boolean? = null,
        liveExpiresAtMs: Long? = null,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToPreview: String? = null,
        groupPreview: String,
        chunkFile: File? = null,
        waveform: String? = null,
        forwarded: Boolean = false,
        pollOptions: List<String>? = null,
    ): Result<String> {
        val path = "groups/$groupId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(uid in memberIds && memberIds.size <= 50)
            val cleanSenderName = normalizeDisplayName(senderName)
            val cleanText = text.trim().take(1000)

            val groupRef = db.collection("groups").document(groupId)
            val msgRef = groupRef.collection("messages").document()
            val chunkCount = chunkFile?.let { ChunkedMediaStore.upload(msgRef, uid, it).toLong() }

            val payload = linkedMapOf<String, Any?>(
                "groupId" to groupId,
                "memberIds" to memberIds,
                "senderId" to uid,
                "senderName" to cleanSenderName,
                "text" to cleanText,
                "messageType" to messageType,
                "mediaData" to mediaData,
                "mediaMimeType" to mediaMimeType?.take(64),
                "mediaName" to mediaName?.take(120),
                "durationSec" to durationSec?.coerceIn(0L, 3600L),
                "latitude" to latitude,
                "longitude" to longitude,
                "locationLabel" to locationLabel?.trim()?.take(240),
                "liveActive" to liveActive,
                "liveExpiresAtMs" to liveExpiresAtMs,
                "replyToMessageId" to replyToMessageId?.takeIf { it.isNotBlank() }?.take(120),
                "replyToSenderName" to replyToSenderName?.takeIf { it.isNotBlank() }?.take(60),
                "replyToPreview" to replyToPreview?.takeIf { it.isNotBlank() }?.take(160),
                "chunkCount" to chunkCount,
                "mediaSizeBytes" to chunkFile?.length(),
                "waveform" to waveform?.take(64),
                "forwarded" to (if (forwarded) true else null),
                "pollOptions" to pollOptions?.map { it.trim().take(60) }?.filter { it.isNotEmpty() }?.take(6),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
                .filterNot { (key, value) -> key == "mediaData" && value == "" }

            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(groupRef,
                mapOf(
                    "lastMessage" to "$cleanSenderName: $groupPreview".trim().take(240),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            commitQueued(batch, OperationType.CREATE, path)
            chunkFile?.delete()


            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editGroupMessage(
        userId: String,
        groupId: String,
        messageId: String,
        senderName: String,
        newText: String,
        isLatestMessage: Boolean,
    ): Result<Unit> {
        val path = "groups/$groupId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanText = newText.trim().take(1000)
            require(cleanText.isNotEmpty())
            val groupRef = db.collection("groups").document(groupId)
            groupRef.collection("messages").document(messageId).update(
                mapOf(
                    "text" to cleanText,
                    "edited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            if (isLatestMessage) {
                val cleanSenderName = normalizeDisplayName(senderName)
                groupRef.update(
                    mapOf(
                        "lastMessage" to "$cleanSenderName: $cleanText".trim().take(240),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteGroupMessage(
        userId: String,
        groupId: String,
        messageId: String,
        fallbackLastMessage: String?,
    ): Result<Unit> {
        val path = "groups/$groupId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val groupRef = db.collection("groups").document(groupId)
            val msgRef = groupRef.collection("messages").document(messageId)
            ChunkedMediaStore.deleteChunks(msgRef)
            msgRef.delete().await()
            if (fallbackLastMessage != null) {
                groupRef.update(
                    mapOf(
                        "lastMessage" to fallbackLastMessage.take(240),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupLiveLocationMessage(
        userId: String,
        groupId: String,
        messageId: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        liveActive: Boolean,
    ): Result<Unit> {
        val path = "groups/$groupId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val msgRef = db.collection("groups").document(groupId)
                .collection("messages").document(messageId)
            msgRef.update(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "locationLabel" to locationLabel.trim().take(240).ifBlank { tr(R.string.s_ubicacion_en_vivo) },
                    "liveActive" to liveActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            if (!liveActive) {
                db.collection("groups").document(groupId).update(
                    mapOf(
                        "lastMessage" to tr(R.string.s_ubicacion_compartida_finalizada),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- SAFETY REPORTS ---

    // --- Reacciones, recibos de lectura y "escribiendo..." ---

    /** Pone (o quita con [emoji] = null) la reacción del usuario en un mensaje. */
    suspend fun setMessageReaction(
        userId: String,
        sourceCollection: String,
        parentId: String,
        messageId: String,
        emoji: String?,
    ): Result<Unit> {
        val path = "$sourceCollection/$parentId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(sourceCollection == DirectMessage.SOURCE_CHATS || sourceCollection == DirectMessage.SOURCE_GROUPS)
            val value: Any = emoji?.trim()?.take(16)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()
            db.collection(sourceCollection).document(parentId)
                .collection("messages").document(messageId)
                .update("reactions.$uid", value)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /** Vota en una encuesta de grupo ([optionIndex] = null retira el voto). */
    suspend fun setPollVote(
        userId: String,
        groupId: String,
        messageId: String,
        optionIndex: Int?,
    ): Result<Unit> {
        val path = "groups/$groupId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val value: Any = optionIndex?.toLong() ?: FieldValue.delete()
            db.collection("groups").document(groupId)
                .collection("messages").document(messageId)
                .update("pollVotes.$uid", value)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /** Marca la conversación como vista (y por tanto entregada) por el usuario. */
    suspend fun markChatRead(userId: String, chatId: String): Result<Unit> = runCatchingChatUpdate(userId, chatId) { uid ->
        mapOf(
            "lastReadAt.$uid" to FieldValue.serverTimestamp(),
            "lastDeliveredAt.$uid" to FieldValue.serverTimestamp(),
            "unreadCount.$uid" to 0L,
        )
    }

    /** Suma 1 a los no leídos del otro participante (chats 1:1). */
    /**
     * Envía sin esperar al servidor: Firestore guarda la escritura en disco y la manda sola
     * al volver la conexión (cola de envío sin conexión). El mensaje aparece al instante.
     */
    private fun commitQueued(batch: com.google.firebase.firestore.WriteBatch, op: OperationType, path: String) {
        batch.commit().addOnFailureListener { handleFirestoreError(it, op, path) }
    }

    private fun unreadIncrement(uid: String, participantIds: List<String>): Map<String, Any> =
        participantIds.filter { it != uid }.associate { "unreadCount.$it" to FieldValue.increment(1L) }

    /** Marca que los mensajes ya llegaron al dispositivo del usuario. */
    suspend fun markChatDelivered(userId: String, chatId: String): Result<Unit> = runCatchingChatUpdate(userId, chatId) { uid ->
        mapOf("lastDeliveredAt.$uid" to FieldValue.serverTimestamp())
    }

    suspend fun setTyping(userId: String, chatId: String, typing: Boolean): Result<Unit> = runCatchingChatUpdate(userId, chatId) { uid ->
        mapOf("typingAt.$uid" to if (typing) FieldValue.serverTimestamp() else FieldValue.delete())
    }

    private suspend fun runCatchingGroupUpdate(
        userId: String,
        groupId: String,
        build: (String) -> Map<String, Any>,
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("groups").document(groupId).update(build(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setGroupTyping(userId: String, groupId: String, typing: Boolean): Result<Unit> =
        runCatchingGroupUpdate(userId, groupId) { uid ->
            mapOf("typingAt.$uid" to if (typing) FieldValue.serverTimestamp() else FieldValue.delete())
        }

    suspend fun markGroupRead(userId: String, groupId: String): Result<Unit> =
        runCatchingGroupUpdate(userId, groupId) { uid ->
            mapOf("lastReadAt.$uid" to FieldValue.serverTimestamp())
        }

    private suspend fun runCatchingChatUpdate(
        userId: String,
        chatId: String,
        build: (String) -> Map<String, Any>,
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("chats").document(chatId).update(build(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitSafetyReport(
        userId: String,
        reportedUserId: String,
        targetType: String,
        targetId: String,
        reason: ReportReason,
        details: String,
    ): Result<Unit> {
        val path = "reports"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(reportedUserId.isNotBlank() && reportedUserId != uid)
            val docRef = db.collection(path).document()
            docRef.set(
                mapOf(
                    "reporterId" to uid,
                    "reportedUserId" to reportedUserId,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "reason" to reason.key,
                    "details" to details.trim().take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
