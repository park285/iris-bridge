@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
                mediaItemClass = FakeMediaItem::class.java,
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
                    mediaItemClass = FakeMediaItem::class.java,
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

        assertEquals("n", methods.first.name)
        assertEquals("p", methods.second.name)
        assertEquals(AbstractInheritedMediaSender::class.java, methods.first.declaringClass)
        assertEquals(AbstractInheritedMediaSender::class.java, methods.second.declaringClass)
    }

    @Test
    fun `chat media sender method resolver accepts inherited non public methods`() {
        val methods =
            KakaoClassRegistry.resolveChatMediaSenderMethodsForTest(
                chatMediaSenderClass = ConcreteProtectedInheritedMediaSender::class.java,
                mediaItemClass = FakeMediaItem::class.java,
                messageTypeClass = FakeMessageType::class.java,
            )

        assertEquals("n", methods.first.name)
        assertEquals("p", methods.second.name)
        assertEquals(AbstractProtectedInheritedMediaSender::class.java, methods.first.declaringClass)
        assertEquals(AbstractProtectedInheritedMediaSender::class.java, methods.second.declaringClass)
    }
}
