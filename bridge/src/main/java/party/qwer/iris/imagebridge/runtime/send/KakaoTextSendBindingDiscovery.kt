package party.qwer.iris.imagebridge.runtime.send

import android.content.Context
import party.qwer.iris.imagebridge.runtime.kakao.KakaoClassRegistry

internal fun discoverKakaoTextSendBinding(
    registry: KakaoClassRegistry,
    context: Context?,
    logInfo: (String, String) -> Unit,
    requestCompanionClassProvider: () -> Class<*>,
): KakaoTextSendBinding {
    val companionClass = requestCompanionClassProvider()
    val requestMethod = selectTextRequestMethod(companionClass, registry)
    val requestTarget = resolveRequestTarget(companionClass, requestMethod)
    val sendingLogClass = requestMethod.parameterTypes[1]
    val sendingLogFactories = discoverTextSendingLogFactories(registry, sendingLogClass, logInfo)
    logInfo(
        KAKAO_TEXT_SEND_TAG,
        "text send discovery companion=${companionClass.name} requestMethod=${requestMethod.toGenericString()} " +
            "sendingLogClass=${sendingLogClass.name}",
    )
    val loader = checkNotNull(registry.chatRoomClass.classLoader) { "Kakao chatRoom class loader unavailable" }
    val listener =
        createShareManagerSendListener(
            context = context,
            loader = loader,
            listenerClass = registry.listenerClass,
            logInfo = logInfo,
        )
    return KakaoTextSendBinding(
        requestMethod = requestMethod,
        requestTarget = requestTarget,
        sendingLogFactory = sendingLogFactories.text,
        leverageSendingLogFactory = sendingLogFactories.leverage,
        shareManagerTextInvoker =
            discoverShareManagerTextInvoker(
                context = context,
                chatRoomClass = registry.chatRoomClass,
                listenerClass = registry.listenerClass,
                logInfo = logInfo,
            ),
        writeType = selectTextWriteType(),
        leverageSchemeWriteType = selectLeverageSchemeWriteType(registry),
        listener = listener,
        kakaoLinkSpecSender =
            ReflectiveKakaoLinkSpecSender(
                loader = loader,
                listener = listener,
                logInfo = logInfo,
            ),
        leverageAttachmentPatcher =
            KakaoLeverageAttachmentDbPatcher(
                databasePath = kakaoTalkDatabasePath(registry.target.packageName),
                logInfo = logInfo,
            ),
        kakaoLinkCommitVerifier =
            KakaoChatLogDbCommitVerifier(
                databasePath = kakaoTalkDatabasePath(registry.target.packageName),
                logInfo = logInfo,
            ),
    )
}

private fun discoverTextSendingLogFactories(
    registry: KakaoClassRegistry,
    sendingLogClass: Class<*>,
    logInfo: (String, String) -> Unit,
): TextSendingLogFactories =
    TextSendingLogFactories(
        text =
            discoverSendingLogFactory(
                sendingLogClass = sendingLogClass,
                messageType = selectTextMessageType(registry),
                logInfo = logInfo,
                chatRoomClass = registry.chatRoomClass,
                origin = resolveTextSendingLogOrigin(registry.chatRoomClass.classLoader),
            ),
        leverage =
            discoverSendingLogFactory(
                sendingLogClass = sendingLogClass,
                messageType = selectLeverageMessageType(registry),
                logInfo = logInfo,
                chatRoomClass = registry.chatRoomClass,
                origin = resolveTextSendingLogOrigin(registry.chatRoomClass.classLoader),
            ),
    )

private data class TextSendingLogFactories(
    val text: KakaoSendingLogFactory,
    val leverage: KakaoSendingLogFactory,
)
