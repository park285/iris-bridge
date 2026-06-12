@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import android.net.Uri
import com.kakao.talk.manager.ShareManager
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import party.qwer.iris.imagebridge.runtime.send.KakaoSendInvocationFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KakaoShareManagerImageSendTest {
    @Test
    fun `sendSingle uses share manager intent path when legacy media sender is absent`() {
        ShareManager.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildModernEntityNameRegistry(),
                context = RuntimeEnvironment.getApplication(),
                pathArgumentFactory = { path -> Uri.parse("uri:$path") },
            )

        factory.sendSingle(
            chatRoom = FakeChatRoom(),
            imagePath = "/tmp/share-manager.png",
            threadId = null,
            threadScope = null,
        )

        assertEquals(1, ShareManager.imageIntentPaths.size)
        assertEquals(listOf("uri:/tmp/share-manager.png"), ShareManager.imageIntentPaths.single().map { it.toString() })
        assertEquals(FakeMessageType.Photo, ShareManager.imageIntentTypes.single())
        assertEquals(emptyList(), ShareManager.imageIntentForwardExtras.single())
        assertEquals(RuntimeEnvironment.getApplication(), ShareManager.context)
        assertEquals(true, ShareManager.imageDispatchStreamIsArray)
        assertTrue(ShareManager.imageDispatchChatRoom is FakeChatRoom)
        assertEquals(false, ShareManager.imageDispatchFlag)
    }

    @Test
    fun `sendMultiple rejects threaded share manager image path`() {
        ShareManager.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildModernEntityNameRegistry(),
                context = RuntimeEnvironment.getApplication(),
                pathArgumentFactory = { path -> Uri.parse("uri:$path") },
            )

        assertFailsWith<IllegalStateException> {
            factory.sendMultiple(
                chatRoom = FakeChatRoom(),
                imagePaths = listOf("/tmp/a.png", "/tmp/b.png"),
                threadId = 1L,
                threadScope = 3,
            )
        }
    }

    @Test
    fun `sendThreaded rejects share manager image fallback`() {
        ShareManager.reset()
        val factory =
            KakaoSendInvocationFactory(
                registry = buildModernEntityNameRegistry(),
                context = RuntimeEnvironment.getApplication(),
                pathArgumentFactory = { path -> Uri.parse("uri:$path") },
            )

        assertFailsWith<IllegalStateException> {
            factory.sendThreaded(
                roomId = 464252100463241L,
                chatRoom = FakeChatRoom(),
                imagePaths = listOf("/tmp/thread.png"),
                threadId = 3861127076080988161L,
                threadScope = 2,
            )
        }
    }
}
