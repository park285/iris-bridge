package party.qwer.iris.imagebridge.runtime.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent

internal class KakaoNotificationActionStarter(
    context: Context,
    private val kakaoPackage: String = context.packageName,
    notificationReferer: (() -> String?)? = null,
    startService: ((Intent) -> ComponentName?)? = null,
) {
    private val appContext = context.applicationContext
    private val refererStore = KakaoNotificationRefererStore(appContext)
    private val notificationReferer = notificationReferer ?: refererStore::get
    private val startService = startService ?: { intent: Intent -> appContext.startService(intent) }

    fun markChatRoomRead(roomId: Long) {
        require(roomId > 0L) { "roomId must be positive" }
        checkNotNull(startService(markReadIntent(roomId))) {
            "Kakao notification action service unavailable"
        }
    }

    private fun markReadIntent(roomId: Long): Intent = markReadIntent(kakaoPackage, roomId, notificationReferer())
}

private const val NOTIFICATION_ACTION_SERVICE = "com.kakao.talk.notification.NotificationActionService"
private const val ACTION_READ_MESSAGE = "com.kakao.talk.notification.READ_MESSAGE"
private const val EXTRA_CHAT_ID = "chat_id"
private const val EXTRA_NOTI_REFERER = "noti_referer"
private const val EXTRA_IS_CHAT_THREAD_NOTIFICATION = "is_chat_thread_notification"
private const val EXTRA_IS_YESSAGE_NOTIFICATION = "is_yessage_notification"

internal fun isKakaoNotificationActionServiceAvailable(
    context: Context,
    kakaoPackage: String = context.packageName,
): Boolean {
    val intent = markReadIntent(kakaoPackage, roomId = 1L, notificationReferer = "probe")
    @Suppress("DEPRECATION")
    return context.packageManager.resolveService(intent, 0) != null
}

internal fun isKakaoNotificationMarkReadAvailable(
    context: Context,
    kakaoPackage: String = context.packageName,
): Boolean =
    isKakaoNotificationActionServiceAvailable(context, kakaoPackage) &&
        KakaoNotificationRefererStore(context.applicationContext).get() != null

private fun markReadIntent(
    kakaoPackage: String,
    roomId: Long,
    notificationReferer: String?,
): Intent {
    val referer =
        notificationReferer
            ?.takeIf { it.isNotBlank() }
            ?: error("Kakao notification referer unavailable")
    return Intent()
        .setClassName(kakaoPackage, NOTIFICATION_ACTION_SERVICE)
        .setAction(ACTION_READ_MESSAGE)
        .apply {
            putExtra(EXTRA_CHAT_ID, roomId)
            putExtra(EXTRA_NOTI_REFERER, referer)
            putExtra(EXTRA_IS_CHAT_THREAD_NOTIFICATION, false)
            putExtra(EXTRA_IS_YESSAGE_NOTIFICATION, false)
        }
}

private class KakaoNotificationRefererStore(
    private val context: Context,
) {
    fun get(): String? {
        val preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
        return preferences.getString(KEY_NOTIFICATION_REFERER, null)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val PREFERENCE_NAME = "KakaoTalk.hw.perferences"
        private const val KEY_NOTIFICATION_REFERER = "NotificationReferer"
    }
}
