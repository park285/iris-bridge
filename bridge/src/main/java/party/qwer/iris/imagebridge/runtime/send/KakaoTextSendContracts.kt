package party.qwer.iris.imagebridge.runtime.send

import java.lang.reflect.Method

internal const val KAKAO_TEXT_SEND_TAG = "IrisBridge"
internal const val SHARE_MANAGER_CLASS = "com.kakao.talk.manager.ShareManager"

internal data class KakaoTextSendCapability(
    val supported: Boolean,
    val ready: Boolean,
    val reason: String? = null,
)

internal interface KakaoTextSendInvoker {
    fun capability(): KakaoTextSendCapability

    fun send(
        roomId: Long,
        chatRoom: Any,
        message: String,
        markdown: Boolean,
        threadId: Long?,
        threadScope: Int?,
        mentionsJson: String?,
        attachmentJson: String?,
        requestId: String?,
    )
}

internal data class KakaoTextSendBinding(
    val requestMethod: Method,
    val requestTarget: Any?,
    val sendingLogFactory: KakaoSendingLogFactory,
    val leverageSendingLogFactory: KakaoSendingLogFactory,
    val shareManagerTextInvoker: KakaoShareManagerTextInvoker?,
    val writeType: Any?,
    val leverageSchemeWriteType: Any?,
    val listener: Any?,
    val kakaoLinkSpecSender: KakaoLinkSpecSender?,
    val leverageAttachmentPatcher: KakaoLeverageAttachmentPatcher?,
    val kakaoLinkCommitVerifier: KakaoChatLogCommitVerifier?,
) {
    fun invoke(
        chatRoom: Any,
        sendingLog: Any,
        writeTypeOverride: Any? = writeType,
    ) {
        requestMethod.apply { isAccessible = true }.invoke(
            requestTarget,
            chatRoom,
            sendingLog,
            writeTypeOverride,
            listener,
            false,
        )
    }
}
