@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.server.BridgeHookSpecVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KakaoClassRegistryTest {
    @Test
    fun `registry constructed with fake classes exposes all fields`() {
        val registry = buildFakeRegistry()

        assertEquals(FakeMediaSender::class.java, registry.chatMediaSenderClass)
        assertEquals(FakeMessageType::class.java, registry.messageTypeClass)
        assertEquals(FakeChatRoomManager::class.java, registry.chatRoomManagerClass)
        assertEquals(FakeMediaItem::class.java, registry.mediaItemClass)
        assertNotNull(registry.singleSendMethod)
        assertNotNull(registry.multiSendMethod)
        assertNotNull(registry.mediaItemConstructor)
        assertNotNull(registry.photoType)
        assertEquals(FakeMessageType.Photo, registry.photoType)
        assertNotNull(registry.multiPhotoType)
        assertEquals(FakeMessageType.MultiPhoto, registry.multiPhotoType)
        assertNotNull(registry.videoType)
        assertEquals(FakeMessageType.Video, registry.videoType)
        assertNotNull(registry.writeTypeNone)
        assertEquals(FakeWriteType.None, registry.writeTypeNone)
    }

    @Test
    fun `method selector prefers concrete candidate over abstract one`() {
        val method =
            KakaoClassRegistry.selectMethodCandidateForTest(
                label = "roomDao",
                candidates =
                    listOf(
                        AbstractRoomDaoContainer::class.java.getMethod("O"),
                        ConcreteRoomDaoContainer::class.java.getMethod("O"),
                    ),
            )

        assertEquals(ConcreteRoomDaoContainer::class.java, method.declaringClass)
    }

    @Test
    fun `method selector rejects ambiguous concrete candidates`() {
        val error =
            assertFailsWith<IllegalStateException> {
                KakaoClassRegistry.selectMethodCandidateForTest(
                    label = "ambiguous",
                    candidates =
                        listOf(
                            AmbiguousMethodOwner::class.java.getMethod("a", Long::class.javaPrimitiveType),
                            AmbiguousMethodOwner::class.java.getMethod("b", Long::class.javaPrimitiveType),
                        ),
                )
            }

        assertTrue(error.message?.contains("ambiguous") == true)
    }

    @Test
    fun `method selector prefers known method name when candidates are ambiguous`() {
        val method =
            KakaoClassRegistry.selectMethodCandidateForTest(
                label = "direct resolver",
                candidates =
                    listOf(
                        AmbiguousMethodOwner::class.java.getMethod("a", Long::class.javaPrimitiveType),
                        AmbiguousMethodOwner::class.java.getMethod("b", Long::class.javaPrimitiveType),
                    ),
                preferredNames = setOf("b"),
            )

        assertEquals("b", method.name)
    }

    @Test
    fun `chat media sender selector accepts concrete subclass inheriting send methods`() {
        val selected =
            KakaoClassRegistry.selectChatMediaSenderCandidateForTest(
                candidates =
                    listOf(
                        AbstractInheritedMediaSender::class.java,
                        ConcreteInheritedMediaSender::class.java,
                    ),
                messageTypeClass = FakeMessageType::class.java,
                function0Class = kotlin.jvm.functions.Function0::class.java,
                function1Class = kotlin.jvm.functions.Function1::class.java,
            )

        assertEquals(ConcreteInheritedMediaSender::class.java, selected)
    }

    @Test
    fun `chat media sender selector rejects ambiguous concrete classes`() {
        val error =
            assertFailsWith<IllegalStateException> {
                KakaoClassRegistry.selectChatMediaSenderCandidateForTest(
                    candidates =
                        listOf(
                            ConcreteInheritedMediaSender::class.java,
                            AlternateConcreteInheritedMediaSender::class.java,
                        ),
                    messageTypeClass = FakeMessageType::class.java,
                    function0Class = kotlin.jvm.functions.Function0::class.java,
                    function1Class = kotlin.jvm.functions.Function1::class.java,
                )
            }

        assertTrue(error.message?.contains("ambiguous") == true)
    }

    @Test
    fun `chat media sender method resolver accepts inherited methods`() {
        val methods =
            KakaoClassRegistry.resolveChatMediaSenderMethodsForTest(
                chatMediaSenderClass = ConcreteInheritedMediaSender::class.java,
                mediaItemClass = FakeMediaItem::class.java,
                messageTypeClass = FakeMessageType::class.java,
            )

        val singleSend = assertNotNull(methods.first)
        assertEquals("n", singleSend.name)
        assertEquals("p", methods.second.name)
        assertEquals(AbstractInheritedMediaSender::class.java, singleSend.declaringClass)
        assertEquals(AbstractInheritedMediaSender::class.java, methods.second.declaringClass)
    }

    @Test
    fun `chat media sender selector accepts sender with only multi photo path`() {
        val selected =
            KakaoClassRegistry.selectChatMediaSenderCandidateForTest(
                candidates = listOf(MultiOnlyMediaSender::class.java),
                messageTypeClass = FakeMessageType::class.java,
                function0Class = kotlin.jvm.functions.Function0::class.java,
                function1Class = kotlin.jvm.functions.Function1::class.java,
            )

        assertEquals(MultiOnlyMediaSender::class.java, selected)
    }

    @Test
    fun `chat media sender selector accepts 26_4_2 three argument constructor`() {
        val selected =
            KakaoClassRegistry.selectChatMediaSenderCandidateForTest(
                candidates = listOf(ModernChatMediaSender26_4_2::class.java),
                messageTypeClass = FakeMessageType::class.java,
                function0Class = kotlin.jvm.functions.Function0::class.java,
                function1Class = kotlin.jvm.functions.Function1::class.java,
            )

        assertEquals(ModernChatMediaSender26_4_2::class.java, selected)
    }

    @Test
    fun `chat media sender method resolver treats single send as optional`() {
        val methods =
            KakaoClassRegistry.resolveChatMediaSenderMethodsForTest(
                chatMediaSenderClass = MultiOnlyMediaSender::class.java,
                mediaItemClass = null,
                messageTypeClass = FakeMessageType::class.java,
            )

        assertEquals(null, methods.first)
        assertEquals("p", methods.second.name)
    }

    @Test
    fun `bridge spec remains ready without optional single send media item path`() {
        val status = BridgeHookSpecVerifier(buildMultiOnlyRegistry()).verify()

        assertTrue(status.ready)
        assertFalse(status.checks.any { !it.ok && it.name.contains("sendSingle") })
        assertFalse(status.checks.any { !it.ok && it.name.contains("MediaItem") })
    }

    @Test
    fun `bridge spec remains ready for share manager image fallback path`() {
        val status = BridgeHookSpecVerifier(buildModernEntityNameRegistry()).verify()

        assertTrue(status.ready)
        assertTrue(status.checks.any { it.name == "ShareManager#imageIntent" && it.ok })
        assertTrue(status.checks.any { it.name == "ShareManager#imageDispatch" && it.ok })
    }

    @Test
    fun `chat media sender method resolver prefers k over A on 26_5_2`() {
        val methods =
            KakaoClassRegistry.resolveChatMediaSenderMethodsForTest(
                chatMediaSenderClass = ChatMediaSender26_5_2::class.java,
                mediaItemClass = FakeMediaItem::class.java,
                messageTypeClass = FakeMessageType::class.java,
            )

        assertEquals("k", assertNotNull(methods.first).name)
        assertEquals("m", methods.second.name)
    }

    @Test
    fun `chat media sender method resolver accepts inherited non public methods`() {
        val methods =
            KakaoClassRegistry.resolveChatMediaSenderMethodsForTest(
                chatMediaSenderClass = ConcreteProtectedInheritedMediaSender::class.java,
                mediaItemClass = FakeMediaItem::class.java,
                messageTypeClass = FakeMessageType::class.java,
            )

        val singleSend = assertNotNull(methods.first)
        assertEquals("n", singleSend.name)
        assertEquals("p", methods.second.name)
        assertEquals(AbstractProtectedInheritedMediaSender::class.java, singleSend.declaringClass)
        assertEquals(AbstractProtectedInheritedMediaSender::class.java, methods.second.declaringClass)
    }
}
