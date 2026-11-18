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
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
