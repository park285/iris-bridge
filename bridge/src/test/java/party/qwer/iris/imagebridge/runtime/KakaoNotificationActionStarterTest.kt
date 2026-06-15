@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import party.qwer.iris.imagebridge.runtime.notification.KakaoNotificationActionStarter
import party.qwer.iris.imagebridge.runtime.notification.isKakaoNotificationActionServiceAvailable
import party.qwer.iris.imagebridge.runtime.server.isNotificationActionSupported
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KakaoNotificationActionStarterTest {
    @Test
    fun `mark read starts Kakao notification action service with recovered extras`() {
        val context = RuntimeEnvironment.getApplication() as Context
        val started = mutableListOf<Intent>()
        val starter =
            KakaoNotificationActionStarter(
                context = context,
                notificationReferer = { "referer-20" },
                startService = { intent: Intent ->
                    started += intent
                    ComponentName("com.kakao.talk", "com.kakao.talk.notification.NotificationActionService")
                },
            )

        starter.markChatRoomRead(123L)

        val intent = started.single()
        assertEquals("com.kakao.talk.notification.NotificationActionService", intent.component?.className)
        assertEquals("com.kakao.talk.notification.READ_MESSAGE", intent.action)
        assertEquals(123L, intent.getLongExtra("chat_id", 0L))
        assertEquals("referer-20", intent.getStringExtra("noti_referer"))
        assertEquals(false, intent.getBooleanExtra("is_chat_thread_notification", true))
        assertEquals(false, intent.getBooleanExtra("is_yessage_notification", true))
    }

    @Test
    fun `mark read fails when Kakao notification action service cannot be started`() {
        val context = RuntimeEnvironment.getApplication() as Context
        val starter =
            KakaoNotificationActionStarter(
                context = context,
                notificationReferer = { "referer-20" },
                startService = { null },
            )

        val error =
            assertFailsWith<IllegalStateException> {
                starter.markChatRoomRead(123L)
            }

        assertEquals("Kakao notification action service unavailable", error.message)
    }

    @Test
    fun `mark read fails closed when notification referer is missing`() {
        val context = RuntimeEnvironment.getApplication() as Context
        val starter =
            KakaoNotificationActionStarter(
                context = context,
                notificationReferer = { null },
                startService = { error("should not be called") },
            )

        val error =
            assertFailsWith<IllegalStateException> {
                starter.markChatRoomRead(123L)
            }

        assertEquals("Kakao notification referer unavailable", error.message)
    }

    @Test
    fun `notification action support follows package manager service resolution`() {
        val context = RuntimeEnvironment.getApplication() as Context
        assertFalse(isKakaoNotificationActionServiceAvailable(context, kakaoPackage = "com.kakao.talk"))

        shadowOf(context.packageManager).addServiceIfNotPresent(
            ComponentName("com.kakao.talk", "com.kakao.talk.notification.NotificationActionService"),
        )

        assertTrue(isKakaoNotificationActionServiceAvailable(context, kakaoPackage = "com.kakao.talk"))
    }

    @Test
    fun `notification action support requires stored Kakao notification referer`() {
        val context = RuntimeEnvironment.getApplication() as Context
        shadowOf(context.packageManager).addServiceIfNotPresent(
            ComponentName("com.kakao.talk", "com.kakao.talk.notification.NotificationActionService"),
        )

        assertFalse(isNotificationActionSupported(context, registry = null))

        context
            .getSharedPreferences("KakaoTalk.hw.perferences", Context.MODE_PRIVATE)
            .edit()
            .putString("NotificationReferer", "referer-20")
            .commit()

        assertTrue(isNotificationActionSupported(context, registry = null))
    }
}
