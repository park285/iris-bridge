@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.send

import android.net.Uri
import party.qwer.iris.imagebridge.runtime.BridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.NoopBridgeHookInstaller
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry
import party.qwer.iris.imagebridge.runtime.kakao.KakaoImageSendStrategy
import java.io.File

internal interface KakaoSendInvoker {
    fun sendSingle(
        chatRoom: Any,
        imagePath: String,
        threadId: Long?,
        threadScope: Int?,
    )

    fun sendMultiple(
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
    )

    fun sendThreaded(
        roomId: Long,
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long,
        threadScope: Int,
    )
}

internal class KakaoSendInvocationFactory(
    private val registry: KakaoClassRegistry,
    private val hookInstaller: BridgeHookInstaller = NoopBridgeHookInstaller,
    private val pathArgumentFactory: (String) -> Any = { path -> Uri.fromFile(File(path)) },
) : KakaoSendInvoker {
    private val senderFactory = ChatMediaSenderInstanceFactory(registry)
    private val shareManagerImageSender = KakaoShareManagerImageSender(registry)
    private val threadedEntryInvoker = ThreadedChatMediaEntryInvoker(registry, pathArgumentFactory)
    private val usesLegacyImageSend = registry.imageSendStrategy == KakaoImageSendStrategy.LEGACY_REFLECTION

    init {
        if (usesLegacyImageSend) {
            ThreadedImageXposedInjector.install(registry, hookInstaller)
            threadedEntryInvoker.warmUp()
        }
    }

    override fun sendSingle(
        chatRoom: Any,
        imagePath: String,
        threadId: Long?,
        threadScope: Int?,
    ) {
        require(imagePath.isNotBlank()) { "imagePath is blank" }
        sendPhotoList(chatRoom, listOf(imagePath), threadId, threadScope)
    }

    override fun sendMultiple(
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
    ) {
        require(imagePaths.isNotEmpty()) { "no image paths" }
        sendPhotoList(chatRoom, imagePaths, threadId, threadScope)
    }

    private fun sendPhotoList(
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long?,
        threadScope: Int?,
    ) {
        if (!usesLegacyImageSend) {
            if (threadId != null || threadScope != null) {
                error("threaded image send is not supported on ShareManager image path")
            }
            shareManagerImageSender.send(chatRoom, imagePaths, pathArgumentFactory)
            return
        }
        val sender = senderFactory.newSender(chatRoom, threadId, threadScope)
        val uris =
            ArrayList<Any>(imagePaths.size).apply {
                imagePaths.forEach { path -> add(pathArgumentFactory(path)) }
            }
        val type = if (imagePaths.size == 1) registry.photoType else registry.multiPhotoType
        registry.multiSendMethod.apply { isAccessible = true }.invoke(
            sender,
            uris,
            type,
            null,
            null,
            null,
            registry.writeTypeNone,
            false,
            true,
            null,
        )
    }

    override fun sendThreaded(
        roomId: Long,
        chatRoom: Any,
        imagePaths: List<String>,
        threadId: Long,
        threadScope: Int,
    ) {
        if (!usesLegacyImageSend) {
            shareManagerImageSender.send(chatRoom, imagePaths, pathArgumentFactory)
            return
        }
        ThreadedImageXposedInjector.withThreadContext(roomId, threadId, threadScope) {
            threadedEntryInvoker.invoke(
                sender = senderFactory.newSender(chatRoom, threadId, threadScope),
                imagePaths = imagePaths,
            )
        }
    }
}
