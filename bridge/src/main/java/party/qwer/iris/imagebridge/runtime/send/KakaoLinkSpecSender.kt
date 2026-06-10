package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.selectMethodBySignature
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import party.qwer.iris.imagebridge.runtime.core.buildKakaoLinkV4EncodedQuery as coreBuildKakaoLinkV4EncodedQuery

internal interface KakaoLinkSpecSender {
    fun send(
        roomId: Long,
        chatRoom: Any?,
        message: String,
        rawAttachment: String,
        requestId: String?,
    ): Boolean
}

internal class ReflectiveKakaoLinkSpecSender(
    private val loader: ClassLoader,
    private val listener: Any? = null,
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
) : KakaoLinkSpecSender {
    override fun send(
        roomId: Long,
        chatRoom: Any?,
        message: String,
        rawAttachment: String,
        requestId: String?,
    ): Boolean =
        runCatching {
            val query = buildKakaoLinkV4EncodedQuery(rawAttachment)
            val helperClass = Class.forName(KAKAO_LINK_HELPER_CLASS, false, loader)
            val spec =
                resolveKakaoLinkParserMethod(helperClass)
                    .apply { isAccessible = true }
                    .invoke(null, query)
            requireNotNull(spec) { "KakaoLinkSpec parser returned null" }
            val methodName = invokeKakaoLinkSpecSend(spec, roomId)
            logInfo(
                KAKAO_TEXT_SEND_TAG,
                "text send kakaolink spec invoked method=$methodName requestId=$requestId room=$roomId messageLength=${message.length}",
            )
            true
        }.onFailure { error ->
            logInfo(
                KAKAO_TEXT_SEND_TAG,
                "text send kakaolink spec failed requestId=$requestId room=$roomId " +
                    "error=${describeThrowable(error)}",
            )
        }.getOrDefault(false)

    private fun invokeKakaoLinkSpecSend(
        spec: Any,
        roomId: Long,
    ): String {
        val sendCandidates =
            spec.javaClass.methods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == Long::class.javaPrimitiveType
            }
        val sendByExistingChatIdMethod =
            listOf("c", "b")
                .mapNotNull { name -> sendCandidates.singleOrNull { method -> method.name == name } }
                .firstOrNull()
                ?: selectMethodBySignature(
                    label = "KakaoLinkSpec send on ${spec.javaClass.name}",
                    candidates = sendCandidates,
                )
        requireTruthySendResult(sendByExistingChatIdMethod.apply { isAccessible = true }.invoke(spec, roomId))
        return sendByExistingChatIdMethod.name
    }

    private fun requireTruthySendResult(result: Any?) {
        if (result is Boolean && !result) {
            error("KakaoLinkSpec send returned false")
        }
    }

    private companion object {
        private const val KAKAO_LINK_HELPER_CLASS = "com.kakao.talk.model.kakaolink.b"
    }
}

private fun resolveKakaoLinkParserMethod(helperClass: Class<*>): java.lang.reflect.Method {
    val parserCandidates =
        helperClass.methods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == String::class.java
        }
    for (name in listOf("c", "b")) {
        parserCandidates.singleOrNull { method -> method.name == name }?.let { return it }
    }
    return selectMethodBySignature(
        label = "KakaoLinkSpec parser on ${helperClass.name}",
        candidates = parserCandidates,
    )
}

internal fun buildKakaoLinkV4EncodedQuery(rawAttachment: String): String =
    BridgeCore.coreBuildKakaoLinkV4EncodedQuery(rawAttachment)
        ?: error("KakaoLink V4 query build failed")

private fun describeThrowable(error: Throwable): String {
    val root =
        if (error is InvocationTargetException && error.targetException != null) {
            error.targetException
        } else {
            error
        }
    return "${root.javaClass.name}: ${root.message ?: ""}".trimEnd()
}
