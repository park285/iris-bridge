@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import party.qwer.iris.imagebridge.runtime.send.discoverShareManagerImageMethods
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SignatureMethodDiscoveryTest {
    @Test
    fun `share manager image methods resolve by signature when names are renamed`() {
        val shareManagerClass = RenamedShareManager::class.java
        val (intentMethod, dispatchMethod) =
            discoverShareManagerImageMethods(
                shareManagerClass = shareManagerClass,
                chatRoomClass = FakeChatRoom::class.java,
                messageTypeClass = FakeMessageType::class.java,
                listenerClass = FakeListener::class.java,
            )

        assertEquals("buildImageIntent", intentMethod.name)
        assertEquals("dispatchImage", dispatchMethod.name)
    }
}
