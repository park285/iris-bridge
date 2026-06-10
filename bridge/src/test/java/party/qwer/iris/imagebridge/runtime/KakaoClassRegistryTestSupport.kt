@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry

internal abstract class AbstractRoomDaoContainer {
    @Suppress("FunctionName")
    abstract fun O(): FakeRoomDao
}

internal class ConcreteRoomDaoContainer : AbstractRoomDaoContainer() {
    @Suppress("FunctionName")
    override fun O(): FakeRoomDao = FakeRoomDao()
}

internal class AmbiguousMethodOwner {
    fun a(roomId: Long): FakeRoomEntity = FakeRoomEntity(roomId)

    fun b(roomId: Long): FakeRoomEntity = FakeRoomEntity(roomId)
}

internal open class FakeBaseChatRoom

internal class FakeDerivedChatRoom : FakeBaseChatRoom()

internal class FakePolymorphicMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    private val roomClassName = chatRoom.javaClass.name

    companion object {
        val sentPaths = mutableListOf<String>()

        fun reset() {
            sentPaths.clear()
        }
    }

    init {
        check(roomClassName.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        sentPaths += mediaItem.path
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        sentPaths += uris.map { uri -> uri.toString().removePrefix("uri:") }
        check(type == FakeMessageType.Photo)
        check(message == null)
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType == FakeWriteType.None)
        check(!shareOriginal)
        check(highQuality)
        check(listener == null)
    }
}

internal class ExactPreferredMediaSender {
    companion object {
        var exactCalls = 0
        var baseCalls = 0

        fun reset() {
            exactCalls = 0
            baseCalls = 0
        }
    }

    constructor(
        chatRoom: FakeBaseChatRoom,
        threadId: Long?,
        sendWithThread: () -> Boolean,
        attachmentDecorator: (JSONObject) -> JSONObject?,
    ) {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
        baseCalls += 1
    }

    constructor(
        chatRoom: FakeDerivedChatRoom,
        threadId: Long?,
        sendWithThread: () -> Boolean,
        attachmentDecorator: (JSONObject) -> JSONObject?,
    ) {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
        exactCalls += 1
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        check(mediaItem.path.isNotBlank())
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(highQuality)
        check(listener == null)
    }
}

internal class PrimitiveThreadParamMediaSender(
    chatRoom: FakeChatRoom,
    threadId: Long,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    companion object {
        val sentPaths = mutableListOf<String>()

        fun reset() {
            sentPaths.clear()
        }
    }

    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        check(threadId == 0L)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        sentPaths += mediaItem.path
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        sentPaths += uris.map { uri -> uri.toString().removePrefix("uri:") }
        check(type == FakeMessageType.Photo)
        check(message == null)
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType == FakeWriteType.None)
        check(!shareOriginal)
        check(highQuality)
        check(listener == null)
    }
}

internal class MultiOnlyMediaSender(
    chatRoom: FakeChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(highQuality)
        check(listener == null)
    }
}

internal abstract class AbstractInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    init {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        check(mediaItem.path.isNotBlank())
    }

    @Suppress("UNUSED_PARAMETER")
    fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(highQuality)
        check(listener == null)
    }
}

internal class ConcreteInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) : AbstractInheritedMediaSender(chatRoom, threadId, sendWithThread, attachmentDecorator)

internal class AlternateConcreteInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) : AbstractInheritedMediaSender(chatRoom, threadId, sendWithThread, attachmentDecorator)

internal abstract class AbstractProtectedInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    init {
        check(chatRoom.javaClass.name.isNotBlank())
        check(threadId == null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    protected fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(!suppressAnimation)
        check(mediaItem.path.isNotBlank())
    }

    @Suppress("UNUSED_PARAMETER")
    protected fun p(
        uris: List<Any>,
        type: FakeMessageType,
        message: String?,
        attachment: JSONObject?,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        listener: FakeListener?,
    ) {
        check(uris.isNotEmpty() || message == null)
        check(type.name.isNotBlank())
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType.name.isNotBlank())
        check(!shareOriginal)
        check(highQuality)
        check(listener == null)
    }
}

internal class ConcreteProtectedInheritedMediaSender(
    chatRoom: FakeBaseChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) : AbstractProtectedInheritedMediaSender(chatRoom, threadId, sendWithThread, attachmentDecorator)

internal fun buildFakeRegistry(): KakaoClassRegistry {
    val singleSend =
        FakeMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        FakeMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = FakeMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeChatRoomModel::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

internal fun buildPolymorphicRegistry(): KakaoClassRegistry {
    val singleSend =
        FakePolymorphicMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        FakePolymorphicMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = FakePolymorphicMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeBaseChatRoom::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

internal fun buildExactPreferredRegistry(): KakaoClassRegistry {
    val singleSend =
        ExactPreferredMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        ExactPreferredMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = ExactPreferredMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeBaseChatRoom::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

internal fun buildPrimitiveThreadParamRegistry(): KakaoClassRegistry {
    val singleSend =
        PrimitiveThreadParamMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        PrimitiveThreadParamMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = PrimitiveThreadParamMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeChatRoomModel::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

internal fun buildMultiOnlyRegistry(): KakaoClassRegistry {
    val multiSend =
        MultiOnlyMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = null,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = MultiOnlyMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeChatRoomModel::class.java,
        singleSendMethod = null,
        multiSendMethod = multiSend,
        mediaItemConstructor = null,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

internal fun buildLegacyNameSensitiveRegistry(): KakaoClassRegistry {
    val singleSend =
        FakeMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        FakeMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = FakeMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = LegacyNameSensitiveChatRoom::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}

internal fun buildModernEntityNameRegistry(): KakaoClassRegistry {
    val shareManagerClass = com.kakao.talk.manager.ShareManager::class.java
    val intentMethod =
        shareManagerClass.methods.first { method ->
            method.name == "I" &&
                method.parameterCount == 2 &&
                method.returnType.name == "android.content.Intent"
        }
    val dispatchMethod =
        shareManagerClass.methods.first { method ->
            method.name == "h0" &&
                method.parameterCount == 4 &&
                method.parameterTypes[3] == Boolean::class.javaPrimitiveType
        }
    val singleSend =
        FakeMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        FakeMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = shareManagerClass,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = ModernEntityNameChatRoom::class.java,
        singleSendMethod = null,
        multiSendMethod = intentMethod,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
        imageSendStrategy = party.qwer.iris.imagebridge.runtime.kakao.KakaoImageSendStrategy.SHARE_MANAGER_INTENT,
        shareManagerImageIntentMethod = intentMethod,
        shareManagerImageDispatchMethod = dispatchMethod,
    )
}

internal fun buildRenamedThreadedRegistry(): KakaoClassRegistry {
    val singleSend =
        RenamedThreadedEntryMediaSender::class.java.getMethod(
            "n",
            FakeMediaItem::class.java,
            Boolean::class.javaPrimitiveType,
        )
    val multiSend =
        RenamedThreadedEntryMediaSender::class.java.getMethod(
            "p",
            List::class.java,
            FakeMessageType::class.java,
            String::class.java,
            JSONObject::class.java,
            JSONObject::class.java,
            FakeWriteType::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            FakeListener::class.java,
        )
    val mediaItemCtor =
        FakeMediaItem::class.java.getConstructor(
            String::class.java,
            Long::class.javaPrimitiveType,
        )
    val masterDbField = FakeMasterDatabase::class.java.getDeclaredField("INSTANCE")
    val roomDaoMethod = FakeMasterDatabase::class.java.getMethod("O")
    val entityLookupMethod =
        FakeRoomDao::class.java.getMethod(
            "h",
            Long::class.javaPrimitiveType,
        )
    val broadResolver =
        FakeChatRoomManager::class.java.getMethod(
            "e0",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
    val directResolver =
        FakeChatRoomManager::class.java.getMethod(
            "d0",
            Long::class.javaPrimitiveType,
        )
    return KakaoClassRegistry(
        mediaItemClass = FakeMediaItem::class.java,
        function0Class = kotlin.jvm.functions.Function0::class.java,
        function1Class = kotlin.jvm.functions.Function1::class.java,
        masterDatabaseClass = FakeMasterDatabase::class.java,
        writeTypeClass = FakeWriteType::class.java,
        listenerClass = FakeListener::class.java,
        chatMediaSenderClass = RenamedThreadedEntryMediaSender::class.java,
        messageTypeClass = FakeMessageType::class.java,
        chatRoomManagerClass = FakeChatRoomManager::class.java,
        chatRoomClass = FakeChatRoomModel::class.java,
        singleSendMethod = singleSend,
        multiSendMethod = multiSend,
        mediaItemConstructor = mediaItemCtor,
        masterDbSingletonField = masterDbField,
        roomDaoMethod = roomDaoMethod,
        entityLookupMethod = entityLookupMethod,
        broadRoomResolverMethod = broadResolver,
        directRoomResolverMethod = directResolver,
        photoType = FakeMessageType.Photo,
        multiPhotoType = FakeMessageType.MultiPhoto,
        writeTypeNone = FakeWriteType.None,
    )
}
