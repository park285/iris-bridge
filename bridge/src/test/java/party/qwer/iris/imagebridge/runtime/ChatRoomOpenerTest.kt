package party.qwer.iris.imagebridge.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import party.qwer.iris.imagebridge.runtime.room.ChatRoomOpener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class ChatRoomOpenerTest {
    @Test
    fun `open uses recovered enter chatroom intent`() {
        val context = RuntimeEnvironment.getApplication() as Context
        val started = mutableListOf<Intent>()
        val opener = ChatRoomOpener(context, startActivity = { intent -> started += intent })

        opener.open(123L)

        val intent = started.single()
        assertEquals(
            ComponentName("com.kakao.talk", "com.kakao.talk.activity.RecentExcludeIntentFilterActivity"),
            intent.component,
        )
        assertEquals("com.kakao.talk.intent.action.ENTER_CHAT_ROOM", intent.action)
        assertEquals(123L, intent.getLongExtra("chatRoomId", 0L))
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            intent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        assertFalse(intent.hasExtra("chatRoomType"))
    }
}
