package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.DirectChat
import com.example.model.DirectMessage
import com.example.model.GroupMessage
import com.example.model.SocialGroup
import com.example.model.UserProfile
import com.example.model.ZoneMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Collections

/**
 * Silent background-in-process Firestore listener for message notifications.
 * Avoids running an Android Foreground Service so no permanent "Serafiel conectado"
 * status-bar notification is displayed, while delivering instant notifications
 * whenever new direct, group, or local-room messages arrive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object MessageListenerService {

    private const val TAG = "SerafielMsgMonitor"
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var listenerJob: Job? = null

    @Volatile
    private var activeUserId: String? = null

    @Volatile
    private var seededZoneKey: String? = null

    private val blockedUserIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val knownChatUpdatedAtMs = Collections.synchronizedMap(mutableMapOf<String, Long>())
    private val knownGroupUpdatedAtMs = Collections.synchronizedMap(mutableMapOf<String, Long>())
    private val notifiedDirectMessageIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val notifiedGroupMessageIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val notifiedZoneMessageIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun start(context: Context) {
        val appContext = context.applicationContext
        SerafielNotificationHelper.ensureChannels(appContext)
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null || currentUser.uid.isBlank()) {
            stop(appContext)
            return
        }
        val uid = currentUser.uid
        if (activeUserId == uid && listenerJob?.isActive == true) {
            return
        }
        startFirestoreListeners(appContext, uid)
    }

    fun stop(context: Context) {
        listenerJob?.cancel()
        listenerJob = null
        activeUserId = null
        seededZoneKey = null
        SerafielNotificationHelper.clearLegacyForegroundNotification(context.applicationContext)
    }

    private fun startFirestoreListeners(appContext: Context, uid: String) {
        listenerJob?.cancel()
        activeUserId = uid
        seededZoneKey = null
        blockedUserIds.clear()
        knownChatUpdatedAtMs.clear()
        knownGroupUpdatedAtMs.clear()
        notifiedDirectMessageIds.clear()
        notifiedGroupMessageIds.clear()
        notifiedZoneMessageIds.clear()

        val db = FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))

        listenerJob = monitorScope.launch {
            launch {
                db.collection("users").document(uid)
                    .snapshots()
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w(TAG, "Profile listener error: ${err.message}")
                    }
                    .collect { snap ->
                        val profile = if (snap.exists()) {
                            snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        } else {
                            null
                        }
                        blockedUserIds.clear()
                        profile?.blockedUserIds?.let { blockedUserIds.addAll(it) }
                    }
            }

            launch {
                var initialChatsSeeded = false
                db.collection("chats")
                    .whereArrayContains("participantIds", uid)
                    .snapshots()
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w(TAG, "Chats listener error: ${err.message}")
                    }
                    .collect { snapshot ->
                        val chats = snapshot.toObjects(
                            DirectChat::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                        // Recibo de entrega: los mensajes del otro ya llegaron a este dispositivo.
                        chats.forEach { chat -> markDeliveredIfNeeded(db, uid, chat) }
                        if (!initialChatsSeeded) {
                            chats.forEach { chat ->
                                knownChatUpdatedAtMs[chat.id] = chat.updatedAt?.toDate()?.time ?: 0L
                            }
                            initialChatsSeeded = true
                            return@collect
                        }
                        for (chat in chats) {
                            if (chat.status != "active") continue
                            val peerId = chat.participantIds.firstOrNull { it != uid } ?: continue
                            if (peerId in blockedUserIds) continue

                            val updatedMs = chat.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                            val prevMs = knownChatUpdatedAtMs[chat.id]
                            if (prevMs != null && updatedMs <= prevMs) continue
                            knownChatUpdatedAtMs[chat.id] = updatedMs

                            if (NotificationSessionState.isAppInForeground &&
                                NotificationSessionState.currentlyOpenChatId == chat.id
                            ) {
                                continue
                            }

                            checkLatestDirectMessageAndNotify(appContext, db, uid, chat, peerId)
                        }
                    }
            }

            launch {
                NotificationSessionState.currentZoneKey
                    .flatMapLatest { zoneKey ->
                        db.collection("zone_rooms")
                            .document(zoneKey)
                            .collection("messages")
                            .whereEqualTo("zoneKey", zoneKey)
                            .whereEqualTo("archived", false)
                            .limit(50)
                            .snapshots()
                            .catch { err ->
                                if (err is CancellationException) throw err
                                Log.w(TAG, "Zone room listener error: ${err.message}")
                            }
                    }
                    .collect { snapshot ->
                        handleZoneSnapshot(appContext, uid, snapshot)
                    }
            }

            launch {
                var initialGroupsSeeded = false
                db.collection("groups")
                    .whereArrayContains("memberIds", uid)
                    .snapshots()
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w(TAG, "Groups listener error: ${err.message}")
                    }
                    .collect { snapshot ->
                        val groups = snapshot.toObjects(
                            SocialGroup::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                        if (!initialGroupsSeeded) {
                            groups.forEach { group ->
                                knownGroupUpdatedAtMs[group.id] = group.updatedAt?.toDate()?.time ?: 0L
                            }
                            initialGroupsSeeded = true
                            return@collect
                        }

                        for (group in groups) {
                            val updatedMs = group.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                            val prevMs = knownGroupUpdatedAtMs[group.id]
                            if (prevMs != null && updatedMs <= prevMs) continue
                            knownGroupUpdatedAtMs[group.id] = updatedMs

                            if (NotificationSessionState.isAppInForeground &&
                                NotificationSessionState.currentlyOpenGroupId == group.id
                            ) {
                                continue
                            }

                            checkLatestGroupMessageAndNotify(appContext, db, uid, group)
                        }
                    }
            }
        }
    }

    private val deliveredMarks = Collections.synchronizedMap(mutableMapOf<String, Long>())

    private fun markDeliveredIfNeeded(db: FirebaseFirestore, uid: String, chat: DirectChat) {
        if (chat.lastSenderId.isBlank() || chat.lastSenderId == uid) return
        val peerId = chat.participantIds.firstOrNull { it != uid } ?: return
        if (peerId in blockedUserIds) return
        val updatedMs = chat.updatedAt?.toDate()?.time ?: return
        val deliveredMs = chat.lastDeliveredAt[uid]?.toDate()?.time ?: 0L
        if (deliveredMs >= updatedMs) return
        if ((deliveredMarks[chat.id] ?: 0L) >= updatedMs) return
        deliveredMarks[chat.id] = updatedMs
        monitorScope.launch {
            try {
                db.collection("chats").document(chat.id)
                    .update("lastDeliveredAt.$uid", FieldValue.serverTimestamp())
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deliveredMarks.remove(chat.id)
                Log.w(TAG, "No se pudo marcar como entregado: ${e.message}")
            }
        }
    }

    private fun handleZoneSnapshot(
        appContext: Context,
        uid: String,
        snapshot: QuerySnapshot,
    ) {
        val currentKey = NotificationSessionState.currentZoneKey.value
        val zoneName = NotificationSessionState.currentZoneName.value
        val messages = snapshot.toObjects(
            ZoneMessage::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )

        if (seededZoneKey != currentKey) {
            seededZoneKey = currentKey
            messages.forEach { notifiedZoneMessageIds.add(it.id) }
            return
        }

        val isViewingZoneRoom = NotificationSessionState.isAppInForeground &&
            NotificationSessionState.currentRoute == "zone_room"

        for (msg in messages) {
            if (!notifiedZoneMessageIds.add(msg.id)) continue
            if (msg.senderId == uid || msg.senderId in blockedUserIds) continue
            if (isViewingZoneRoom) continue

            SerafielNotificationHelper.showZoneMessageNotification(
                context = appContext,
                messageId = msg.id,
                zoneName = zoneName,
                senderName = msg.senderName.ifBlank { "Alguien cerca" },
                messageText = msg.text,
            )
        }
    }

    private suspend fun checkLatestDirectMessageAndNotify(
        appContext: Context,
        db: FirebaseFirestore,
        uid: String,
        chat: DirectChat,
        peerId: String,
    ) {
        try {
            val msgSnap = db.collection("chats")
                .document(chat.id)
                .collection("messages")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()

            val messages = msgSnap.toObjects(
                DirectMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ).sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }

            val latest = messages.lastOrNull() ?: return
            if (latest.senderId == uid || latest.senderId in blockedUserIds) return
            if (!notifiedDirectMessageIds.add(latest.id)) return

            val senderName = chat.participantNames[peerId]
                ?: latest.senderName.ifBlank { "Nuevo mensaje" }
            val notifBody = latest.text.ifBlank { latest.previewLabel() }
            SerafielNotificationHelper.showDirectMessageNotification(
                context = appContext,
                chatId = chat.id,
                senderName = senderName,
                messageText = notifBody,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching latest message for notification: ${e.message}")
        }
    }

    private suspend fun checkLatestGroupMessageAndNotify(
        appContext: Context,
        db: FirebaseFirestore,
        uid: String,
        group: SocialGroup,
    ) {
        try {
            val msgSnap = db.collection("groups")
                .document(group.id)
                .collection("messages")
                .whereArrayContains("memberIds", uid)
                .get()
                .await()

            val messages = msgSnap.toObjects(
                GroupMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ).sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }

            val latest = messages.lastOrNull() ?: return
            if (latest.senderId == uid || latest.senderId in blockedUserIds) return
            if (!notifiedGroupMessageIds.add(latest.id)) return

            val senderName = group.memberNames[latest.senderId]
                ?: latest.senderName.ifBlank { "Miembro del grupo" }
            val notifBody = latest.text.ifBlank { latest.previewLabel() }
            SerafielNotificationHelper.showGroupMessageNotification(
                context = appContext,
                groupId = group.id,
                groupName = group.name.ifBlank { "Grupo" },
                senderName = senderName,
                messageText = notifBody,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching latest group message for notification: ${e.message}")
        }
    }
}
