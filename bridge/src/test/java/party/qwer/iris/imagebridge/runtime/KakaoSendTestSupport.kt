@file:Suppress("ClassName", "FunctionName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.send.KakaoChatLogCommitVerifier
import party.qwer.iris.imagebridge.runtime.send.KakaoLeverageAttachmentPatcher
import party.qwer.iris.imagebridge.runtime.send.KakaoLinkSpecSender
import party.qwer.iris.imagebridge.runtime.send.KakaoSendInvoker
import kotlin.test.assertEquals

internal class FakeChatRoom

internal class RecordingKakaoSendInvoker : KakaoSendInvoker {
    var singleCalls = 0
    var multiCalls = 0
    var threadedCalls = 0
    var lastRoomId: Long? = null
    var lastImagePaths: List<String> = emptyList()
    var lastContentTypes: List<String> = emptyList()
    var lastThreadId: Long? = null
    var lastThreadScope: Int? = null

    override fun sendSingle(
        chatRoom: Any,
        imagePath: String,
        contentType: String?,
        threadId: Long?,
        threadScope: Int?,
    ) {
        singleCalls += 1
        lastContentTypes = listOf(contentType.orEmpty())
    }

    override fun sendMultiple(
        chatRoom: Any,
        imagePaths: List<String>,
        contentTypes: List<String>,
        threadId: Long?,
        threadScope: Int?,
    ) {
        multiCalls += 1
        lastContentTypes = contentTypes
    }

    override fun sendThreaded(
        roomId: Long,
        chatRoom: Any,
        imagePaths: List<String>,
        contentTypes: List<String>,
        threadId: Long,
        threadScope: Int,
    ) {
        threadedCalls += 1
        lastRoomId = roomId
        lastImagePaths = imagePaths
        lastContentTypes = contentTypes
        lastThreadId = threadId
        lastThreadScope = threadScope
    }
}

internal class FakeMediaItem(
    val path: String,
    val size: Long,
)

internal class FakeMediaSender(
    chatRoom: FakeChatRoom,
    private val threadId: Long?,
    private val sendWithThread: () -> Boolean,
    private val attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    private val roomHash = chatRoom.hashCode()

    companion object {
        val sentPaths = mutableListOf<String>()
        val threadFlags = mutableListOf<Boolean>()
        val roomHashes = mutableListOf<Int>()
        val multiSentUris = mutableListOf<String>()
        var multiType: Any? = null
        var multiWriteType: Any? = null
        var multiShareOriginal: Boolean? = null
        var multiHighQuality: Boolean? = null

        fun reset() {
            sentPaths.clear()
            threadFlags.clear()
            roomHashes.clear()
            multiSentUris.clear()
            multiType = null
            multiWriteType = null
            multiShareOriginal = null
            multiHighQuality = null
        }
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        sentPaths += mediaItem.path
        threadFlags += sendWithThread()
        roomHashes += roomHash
        check(!suppressAnimation)
        check(attachmentDecorator(JSONObject()) != null)
        check(threadId == null || threadId >= 0L)
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
        multiSentUris += uris.map { uri -> uri.toString().removePrefix("uri:") }
        multiType = type
        multiWriteType = writeType
        multiShareOriginal = shareOriginal
        multiHighQuality = highQuality
        check(message == null)
        check(attachment == null)
        check(forwardExtra == null)
        check(!shareOriginal)
        check(listener == null)
    }
}

internal enum class FakeMessageType {
    Text,
    Photo,
    MultiPhoto,
    Video,
    Leverage,
}

internal enum class FakeWriteType {
    None,
    Connect,
    LeverageScheme,
}

internal class RecordingKakaoLinkSpecSender(
    private val result: Boolean,
) : KakaoLinkSpecSender {
    var roomId: Long? = null
    var message: String? = null
    var rawAttachment: String? = null
    var requestId: String? = null
    var sendCalls: Int = 0
    val rawAttachments = mutableListOf<String>()

    override fun send(
        roomId: Long,
        chatRoom: Any?,
        message: String,
        rawAttachment: String,
        requestId: String?,
    ): Boolean {
        this.roomId = roomId
        this.message = message
        this.rawAttachment = rawAttachment
        this.requestId = requestId
        sendCalls += 1
        rawAttachments += rawAttachment
        return result
    }
}

internal class RecordingKakaoLeverageAttachmentPatcher : KakaoLeverageAttachmentPatcher {
    var roomId: Long? = null
    var message: String? = null
    var rawAttachment: String? = null
    var requestId: String? = null

    override fun patchAsync(
        roomId: Long,
        message: String,
        rawAttachment: String,
        requestId: String?,
    ) {
        this.roomId = roomId
        this.message = message
        this.rawAttachment = rawAttachment
        this.requestId = requestId
    }
}

internal class RecordingKakaoChatLogCommitVerifier(
    private val result: Boolean,
    private vararg val additionalResults: Boolean,
    private val cleanupResult: Boolean = true,
) : KakaoChatLogCommitVerifier {
    var roomId: Long? = null
    var message: String? = null
    var minimumCreatedAt: Long? = null
    var minimumRowId: Long? = null
    var requestId: String? = null
    var rawAttachment: String? = null
    var latestRowRoomId: Long? = null
    var cleanupRoomId: Long? = null
    var cleanupMinimumCreatedAt: Long? = null
    var cleanupRequestId: String? = null
    var cleanupRawAttachment: String? = null
    val rawAttachments = mutableListOf<String?>()
    val cleanupRawAttachments = mutableListOf<String>()
    private var awaitCalls = 0

    override fun latestCommittedRowId(roomId: Long): Long {
        latestRowRoomId = roomId
        return 900L
    }

    override fun awaitCommitted(
        roomId: Long,
        message: String,
        minimumCreatedAt: Long,
        minimumRowId: Long,
        requestId: String?,
        rawAttachment: String?,
    ): Boolean {
        this.roomId = roomId
        this.message = message
        this.minimumCreatedAt = minimumCreatedAt
        this.minimumRowId = minimumRowId
        this.requestId = requestId
        this.rawAttachment = rawAttachment
        rawAttachments += rawAttachment
        val result =
            listOf(result, *additionalResults.toTypedArray()).getOrElse(awaitCalls) {
                additionalResults.lastOrNull() ?: result
            }
        awaitCalls += 1
        return result
    }

    override fun cleanupPendingKakaoLinkSendingLogs(
        roomId: Long,
        minimumCreatedAt: Long,
        requestId: String?,
        rawAttachment: String,
    ): Boolean {
        cleanupRoomId = roomId
        cleanupMinimumCreatedAt = minimumCreatedAt
        cleanupRequestId = requestId
        cleanupRawAttachment = rawAttachment
        cleanupRawAttachments += rawAttachment
        return cleanupResult
    }
}

internal class FakeChatSendingLogRequest(
    @JvmField val b: Any,
)

internal class FakeChatLog(
    private val roomId: Long,
    attachment: JSONObject,
) {
    var attachmentText: String = attachment.toString()
        private set

    fun getChatRoomId(): Long = roomId

    fun t(): JSONObject = JSONObject(attachmentText)

    fun f2(value: String) {
        attachmentText = value
    }
}

internal interface FakeListener : com.kakao.talk.manager.send.m

internal object FakeTextRequestRecorder {
    var chatRoom: Any? = null
    var sendingLog: Any? = null
    var writeType: FakeWriteType? = null
    var listener: Any? = null
    var shouldRetry: Boolean = true

    fun reset() {
        chatRoom = null
        sendingLog = null
        writeType = null
        listener = null
        shouldRetry = true
    }
}

internal class FakeTextRequestCompanion {
    companion object {
        @JvmField
        val f = FakeTextRequestCompanion()
    }

    fun u(
        chatRoom: FakeChatRoomModel,
        sendingLog: FakeTextSendingLog,
        writeType: FakeWriteType?,
        listener: FakeListener?,
        shouldRetry: Boolean,
    ) {
        FakeTextRequestRecorder.chatRoom = chatRoom
        FakeTextRequestRecorder.sendingLog = sendingLog
        FakeTextRequestRecorder.writeType = writeType
        FakeTextRequestRecorder.listener = listener
        FakeTextRequestRecorder.shouldRetry = shouldRetry
    }
}

internal class ModernTextRequestCompanion {
    companion object {
        @JvmField
        val f = ModernTextRequestCompanion()
    }

    fun u(
        chatRoom: FakeChatRoomModel,
        sendingLog: ModernFakeTextSendingLog,
        writeType: FakeWriteType?,
        listener: FakeListener?,
        shouldRetry: Boolean,
    ) {
        FakeTextRequestRecorder.chatRoom = chatRoom
        FakeTextRequestRecorder.sendingLog = sendingLog
        FakeTextRequestRecorder.writeType = writeType
        FakeTextRequestRecorder.listener = listener
        FakeTextRequestRecorder.shouldRetry = shouldRetry
    }
}

internal class MissingTextRequestCompanion {
    companion object {
        @JvmField
        val f = MissingTextRequestCompanion()
    }
}

internal class FakeOuterTextRequest {
    companion object {
        @JvmField
        val f = CompanionApi()
    }

    class CompanionApi {
        fun u(
            chatRoom: FakeChatRoomModel,
            sendingLog: FakeTextSendingLog,
            writeType: FakeWriteType?,
            listener: FakeListener?,
            shouldRetry: Boolean,
        ) {
            FakeTextRequestRecorder.chatRoom = chatRoom
            FakeTextRequestRecorder.sendingLog = sendingLog
            FakeTextRequestRecorder.writeType = writeType
            FakeTextRequestRecorder.listener = listener
            FakeTextRequestRecorder.shouldRetry = shouldRetry
        }
    }
}

internal class FakeTextSendingLog private constructor(
    private val roomId: Long,
    private val message: String,
    var messageType: FakeMessageType,
    val originClass: Class<*>?,
    val originTag: String?,
) {
    var G: String? = null
    var forwardExtra: String? = null
    var Z: Int = 0
    var V0: Long? = null

    fun getChatRoomId(): Long = roomId

    fun f0(): String = message

    fun H1(threadScope: Int) {
        Z = threadScope
    }

    fun J1(threadId: Long?) {
        V0 = threadId
    }

    class b(
        private val roomId: Long,
        private val messageType: FakeMessageType,
        @Suppress("UNUSED_PARAMETER") reserved: Int,
        @Suppress("UNUSED_PARAMETER") messageId: Long?,
        @Suppress("UNUSED_PARAMETER") needsUpload: Boolean,
    ) {
        constructor(
            chatRoom: FakeChatRoomModel,
            messageType: FakeMessageType,
            reserved: Int,
            messageId: Long?,
        ) : this(chatRoom.roomId, messageType, reserved, messageId, false)

        private var message: String = ""
        private var originClass: Class<*>? = null
        private var originTag: String? = null
        private var attachment: JSONObject? = null
        private var forwardExtra: JSONObject? = null

        fun j(message: String): b {
            this.message = message
            return this
        }

        fun l(
            sourceClass: Class<*>,
            tag: String,
        ): b {
            originClass = sourceClass
            originTag = tag
            return this
        }

        fun c(attachment: JSONObject): b {
            this.attachment = attachment
            return this
        }

        fun f(forwardExtra: JSONObject): b {
            this.forwardExtra = forwardExtra
            return this
        }

        fun b(): FakeTextSendingLog {
            check(messageType == FakeMessageType.Text || messageType == FakeMessageType.Leverage)
            return FakeTextSendingLog(roomId, message, messageType, originClass, originTag).also { log ->
                log.G = attachment?.toString()
                log.forwardExtra = forwardExtra?.toString()
            }
        }
    }
}

internal class ModernFakeTextSendingLog private constructor(
    private val roomId: Long,
    private val message: String,
    var messageType: FakeMessageType,
    val originClass: Class<*>?,
    val originTag: String?,
) {
    var G: String? = null
    var Z: Int = 0
    var V0: Long? = null

    fun getChatRoomId(): Long = roomId

    fun getMessage(): String = message

    fun H1(threadScope: Int) {
        Z = threadScope
    }

    fun J1(threadId: Long?) {
        V0 = threadId
    }

    class b(
        private val roomId: Long,
        private val messageType: FakeMessageType,
        @Suppress("UNUSED_PARAMETER") reserved: Int,
        @Suppress("UNUSED_PARAMETER") messageId: Long?,
        @Suppress("UNUSED_PARAMETER") needsUpload: Boolean,
    ) {
        constructor(
            chatRoom: FakeChatRoomModel,
            messageType: FakeMessageType,
            reserved: Int,
            messageId: Long?,
        ) : this(chatRoom.roomId, messageType, reserved, messageId, false)

        private var message: String = ""
        private var originClass: Class<*>? = null
        private var originTag: String? = null
        private var attachment: JSONObject? = null

        fun j(message: String): b {
            this.message = message
            return this
        }

        fun l(
            sourceClass: Class<*>,
            tag: String,
        ): b {
            originClass = sourceClass
            originTag = tag
            return this
        }

        fun c(attachment: JSONObject): b {
            this.attachment = attachment
            return this
        }

        fun b(): ModernFakeTextSendingLog {
            check(messageType == FakeMessageType.Text || messageType == FakeMessageType.Leverage)
            return ModernFakeTextSendingLog(roomId, message, messageType, originClass, originTag).also { log ->
                log.G = attachment?.toString()
            }
        }
    }
}

internal class RenamedThreadedEntryMediaSender(
    chatRoom: FakeChatRoom,
    private val threadId: Long?,
    private val sendWithThread: () -> Boolean,
    private val attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    companion object {
        val sentUris = mutableListOf<String>()
        var lastType: FakeMessageType? = null
        var lastWriteType: FakeWriteType? = null
        var lastShareOriginal: Boolean? = null
        var lastHighQuality: Boolean? = null

        fun reset() {
            sentUris.clear()
            lastType = null
            lastWriteType = null
            lastShareOriginal = null
            lastHighQuality = null
        }
    }

    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        check(threadId != null)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(mediaItem.path.isNotBlank())
        check(!suppressAnimation)
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

    @Suppress("UNUSED_PARAMETER")
    fun q(
        uris: List<Any>,
        type: FakeMessageType,
        message: String,
        attachment: JSONObject,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        onSuccess: kotlin.jvm.functions.Function1<Any?, Any?>,
        onFailure: kotlin.jvm.functions.Function1<Any?, Any?>,
    ) {
        sentUris += uris.map { uri -> uri.toString().removePrefix("uri:") }
        lastType = type
        lastWriteType = writeType
        lastShareOriginal = shareOriginal
        lastHighQuality = highQuality
        check(message.isEmpty())
        check(attachment.optString("callingPkg") == "com.kakao.talk")
        check(forwardExtra == null)
        check(writeType == FakeWriteType.Connect)
        check(!shareOriginal)
        check(highQuality)
        assertEquals("ok", onSuccess.invoke("ok"))
        assertEquals(null, onFailure.invoke("ignored"))
    }
}

internal class ModernChatMediaSender26_4_2(
    chatRoom: FakeChatRoom,
    private val threadId: Long?,
    private val attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    companion object {
        val sentUris = mutableListOf<String>()
        var constructorThreadId: Long? = null
        var lastType: FakeMessageType? = null
        var lastWriteType: FakeWriteType? = null
        var lastShareOriginal: Boolean? = null
        var lastHighQuality: Boolean? = null

        fun reset() {
            sentUris.clear()
            constructorThreadId = null
            lastType = null
            lastWriteType = null
            lastShareOriginal = null
            lastHighQuality = null
        }
    }

    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        constructorThreadId = threadId
        check(attachmentDecorator(JSONObject()) != null)
    }

    fun n(
        mediaItem: FakeMediaItem,
        suppressAnimation: Boolean,
    ) {
        check(mediaItem.path.isNotBlank())
        check(!suppressAnimation)
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
        sentUris += uris.map { uri -> uri.toString().removePrefix("uri:") }
        lastType = type
        lastWriteType = writeType
        lastShareOriginal = shareOriginal
        lastHighQuality = highQuality
        check(message == null)
        check(attachment == null)
        check(forwardExtra == null)
        check(writeType == FakeWriteType.None)
        check(!shareOriginal)
        check(highQuality)
        check(listener == null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun o(
        uris: List<Any>,
        type: FakeMessageType,
        message: String,
        attachment: JSONObject,
        forwardExtra: JSONObject?,
        writeType: FakeWriteType,
        shareOriginal: Boolean,
        highQuality: Boolean,
        onSuccess: kotlin.jvm.functions.Function1<Any?, Any?>,
        onFailure: kotlin.jvm.functions.Function1<Any?, Any?>,
    ) {
        sentUris += uris.map { uri -> uri.toString().removePrefix("uri:") }
        lastType = type
        lastWriteType = writeType
        lastShareOriginal = shareOriginal
        lastHighQuality = highQuality
        check(threadId != null)
        check(message.isEmpty())
        check(attachment.optString("callingPkg") == "com.kakao.talk")
        check(forwardExtra == null)
        check(writeType == FakeWriteType.Connect)
        check(!shareOriginal)
        check(highQuality)
        assertEquals("ok", onSuccess.invoke("ok"))
        assertEquals(null, onFailure.invoke("ignored"))
    }
}

internal class RenamedThreadedInjectMediaSender(
    chatRoom: FakeChatRoom,
    threadId: Long?,
    sendWithThread: () -> Boolean,
    attachmentDecorator: (JSONObject) -> JSONObject?,
) {
    init {
        check(chatRoom.hashCode() != 0 || chatRoom.hashCode() == 0)
        check(threadId == null || threadId >= 0L)
        check(!sendWithThread())
        check(attachmentDecorator(JSONObject()) != null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun z(
        sendingLog: Any,
        writeType: FakeWriteType,
        listener: FakeListener?,
    ) {
        check(sendingLog.hashCode() != Int.MIN_VALUE)
        check(writeType.name.isNotBlank())
        check(listener == null || listener.hashCode() != Int.MIN_VALUE)
    }
}

internal class FakeThreadedRequestCompanion {
    @Suppress("UNUSED_PARAMETER")
    fun u(
        chatRoom: FakeChatRoomModel,
        sendingLog: Any,
        writeType: FakeWriteType,
        listener: FakeListener?,
        shouldRetry: Boolean,
    ) {
        check(chatRoom.roomId >= 0L)
        check(sendingLog.hashCode() != Int.MIN_VALUE)
        check(writeType.name.isNotBlank())
        check(listener == null || listener.hashCode() != Int.MIN_VALUE)
        check(!shouldRetry || shouldRetry)
    }
}
