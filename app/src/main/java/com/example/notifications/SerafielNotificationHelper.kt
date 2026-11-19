package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

object NotificationSessionState {
    @Volatile
    var isAppInForeground: Boolean = false

    @Volatile
    var currentRoute: String = "radar"

    @Volatile
    var currentlyOpenChatId: String? = null

    @Volatile
    var currentlyOpenGroupId: String? = null

    private val _currentZoneKey = MutableStateFlow("plaza_central")
    val currentZoneKey: StateFlow<String> = _currentZoneKey.asStateFlow()

    private val _currentZoneName = MutableStateFlow("Zona Abierta")
    val currentZoneName: StateFlow<String> = _currentZoneName.asStateFlow()

    fun updateZone(zoneKey: String, zoneName: String) {
        if (zoneKey.isNotBlank()) {
            _currentZoneKey.value = zoneKey
            _currentZoneName.value = zoneName
        }
    }
}

object SerafielNotificationHelper {

    const val CHANNEL_DIRECT_MESSAGES = "serafiel_messages"
    const val CHANNEL_ZONE_MESSAGES = "serafiel_zone_messages"
    private const val LEGACY_SERVICE_CHANNEL_ID = "serafiel_service"

    const val EXTRA_OPEN_ROUTE = "serafiel_open_route"
    const val EXTRA_OPEN_CHAT_ID = "serafiel_open_chat_id"
    const val EXTRA_OPEN_GROUP_ID = "serafiel_open_group_id"

    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 4001

    const val KEY_REPLY_TEXT = "serafiel_reply_text"
    const val EXTRA_NOTIFICATION_ID = "serafiel_notification_id"
    const val ACTION_REPLY = "com.example.serafiel.ACTION_REPLY"
    const val ACTION_MARK_READ = "com.example.serafiel.ACTION_MARK_READ"

    /** Acción "Responder" con campo de texto en la propia notificación. */
    private fun replyAction(
        context: Context,
        notificationId: Int,
        chatId: String?,
        groupId: String?,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            chatId?.let { putExtra(EXTRA_OPEN_CHAT_ID, it) }
            groupId?.let { putExtra(EXTRA_OPEN_GROUP_ID, it) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, notificationId, intent, flags)
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Escribe una respuesta")
            .build()
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_serafiel, "Responder", pending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun markReadAction(context: Context, notificationId: Int, chatId: String): NotificationCompat.Action {
        val intent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_serafiel, "Marcar como leído", pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun messagingStyle(title: String?, senderName: String, text: String): NotificationCompat.MessagingStyle {
        val me = Person.Builder().setName("Tú").build()
        val sender = Person.Builder().setName(senderName).build()
        return NotificationCompat.MessagingStyle(me)
            .setConversationTitle(title)
            .setGroupConversation(title != null)
            .addMessage(text, System.currentTimeMillis(), sender)
    }

    fun showReplyFailedNotification(context: Context, notificationId: Int, chatId: String?, groupId: String?) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_DIRECT_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_serafiel)
            .setContentTitle("No se pudo enviar tu respuesta")
            .setContentText("Revisa tu conexión y vuelve a intentarlo.")
            .setAutoCancel(true)
            .addAction(replyAction(context, notificationId, chatId, groupId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val grantedRuntime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return grantedRuntime && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasRuntimePermissionOnly(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun clearLegacyForegroundNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(FOREGROUND_SERVICE_NOTIFICATION_ID)
    }

    fun ensureChannels(context: Context) {
        clearLegacyForegroundNotification(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(LEGACY_SERVICE_CHANNEL_ID)

        val dmChannel = NotificationChannel(
            CHANNEL_DIRECT_MESSAGES,
            context.getString(R.string.notif_channel_dm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_dm_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val zoneChannel = NotificationChannel(
            CHANNEL_ZONE_MESSAGES,
            context.getString(R.string.notif_channel_zone_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_zone_desc)
            setShowBadge(true)
        }

        manager.createNotificationChannels(listOf(dmChannel, zoneChannel))
    }

    fun showDirectMessageNotification(
        context: Context,
        chatId: String,
        senderName: String,
        messageText: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, "chats")
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        val requestCode = 10_000 + (abs(chatId.hashCode()) % 50_000)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DIRECT_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_serafiel)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(messagingStyle(null, senderName, messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(replyAction(context, requestCode, chatId = chatId, groupId = null))
            .addAction(markReadAction(context, requestCode, chatId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun showZoneMessageNotification(
        context: Context,
        messageId: String,
        zoneName: String,
        senderName: String,
        messageText: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, "zone_room")
        }
        val requestCode = 70_000 + (abs(messageId.hashCode()) % 20_000)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ZONE_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_serafiel)
            .setContentTitle("$senderName · $zoneName")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun showGroupMessageNotification(
        context: Context,
        groupId: String,
        groupName: String,
        senderName: String,
        messageText: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, "groups")
            putExtra(EXTRA_OPEN_GROUP_ID, groupId)
        }
        val requestCode = 90_000 + (abs(groupId.hashCode()) % 30_000)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DIRECT_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_serafiel)
            .setContentTitle("$groupName · $senderName")
            .setContentText(messageText)
            .setStyle(messagingStyle(groupName, senderName, messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(replyAction(context, requestCode, chatId = null, groupId = groupId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
