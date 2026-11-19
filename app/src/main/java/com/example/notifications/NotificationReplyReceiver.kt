package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.data.SerafielRepository
import com.example.model.DirectMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Recibe la respuesta escrita directamente en la notificación (RemoteInput) y la
 * envía al chat o grupo sin abrir la app.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val notificationId = intent.getIntExtra(SerafielNotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val chatId = intent.getStringExtra(SerafielNotificationHelper.EXTRA_OPEN_CHAT_ID)
        val groupId = intent.getStringExtra(SerafielNotificationHelper.EXTRA_OPEN_GROUP_ID)

        if (intent.action == SerafielNotificationHelper.ACTION_MARK_READ) {
            val pending = goAsync()
            scope.launch {
                try {
                    val uid = Firebase.auth.currentUser?.uid
                    if (uid != null && chatId != null) SerafielRepository(appContext).markChatRead(uid, chatId)
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(SerafielNotificationHelper.KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (replyText.isEmpty()) return

        val pending = goAsync()
        scope.launch {
            val ok = try {
                sendReply(appContext, chatId, groupId, replyText)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enviar la respuesta rápida", e)
                false
            }
            try {
                if (ok) {
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                } else {
                    SerafielNotificationHelper.showReplyFailedNotification(appContext, notificationId, chatId, groupId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun sendReply(context: Context, chatId: String?, groupId: String?, text: String): Boolean {
        val uid = Firebase.auth.currentUser?.uid ?: return false
        val repository = SerafielRepository(context)
        val db = com.example.data.ChunkedMediaStore.db(context)
        return when {
            chatId != null -> {
                val snap = db.collection("chats").document(chatId).get().await()
                @Suppress("UNCHECKED_CAST")
                val participants = snap.get("participantIds") as? List<String> ?: return false
                @Suppress("UNCHECKED_CAST")
                val names = snap.get("participantNames") as? Map<String, String> ?: emptyMap()
                val res = repository.sendDirectMessage(
                    userId = uid,
                    chatId = chatId,
                    participantIds = participants,
                    senderName = names[uid] ?: Firebase.auth.currentUser?.displayName ?: "Usuario Serafiel",
                    text = text,
                )
                if (res.isSuccess) repository.markChatRead(uid, chatId)
                res.isSuccess
            }
            groupId != null -> {
                val snap = db.collection("groups").document(groupId).get().await()
                @Suppress("UNCHECKED_CAST")
                val members = snap.get("memberIds") as? List<String> ?: return false
                @Suppress("UNCHECKED_CAST")
                val names = snap.get("memberNames") as? Map<String, String> ?: emptyMap()
                repository.sendGroupRichMessage(
                    userId = uid,
                    groupId = groupId,
                    memberIds = members,
                    senderName = names[uid] ?: Firebase.auth.currentUser?.displayName ?: "Usuario Serafiel",
                    messageType = DirectMessage.TYPE_TEXT,
                    text = text,
                    groupPreview = text,
                ).isSuccess
            }
            else -> false
        }
    }

    companion object {
        private const val TAG = "SerafielReply"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
