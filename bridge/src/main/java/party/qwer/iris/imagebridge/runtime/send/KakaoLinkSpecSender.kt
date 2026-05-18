package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.net.URLEncoder

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
            val spec = helperClass.getDeclaredMethod("b", String::class.java).apply { isAccessible = true }.invoke(null, query)
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
        val sendByExistingChatIdMethod =
            spec.javaClass.methods
                .firstOrNull { method ->
                    val types = method.parameterTypes
                    method.name == "c" &&
                        types.size == 1 &&
                        types[0] == Long::class.javaPrimitiveType
                }
                ?: error("KakaoLinkSpec existing chat id send method not found")
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

internal fun buildKakaoLinkV4EncodedQuery(rawAttachment: String): String {
    val attachment = JSONObject(rawAttachment)
    val platform = attachment.getJSONObject("P")
    val appVer = firstNonBlank(platform.optString("VA"), platform.optString("VI")) ?: "6.0.0"
    val appKey =
        firstNonBlank(attachment.optString("app_key"), attachment.optString("appKey"))
            ?: extractKakaoLinkAppKey(rawAttachment)
            ?: error("KakaoLink app key not found")
    val templateId =
        firstNonBlank(
            attachment.optString("template_id"),
            attachment.optString("templateId"),
            attachment.optJSONObject("K")?.optString("ti"),
            platform.optString("SDID"),
        )
            ?: error("KakaoLink template id not found")
    val templateArgs = explicitTemplateArgs(attachment)
    val queryParams =
        mutableListOf(
            "linkver" to "4.0",
            "appver" to appVer,
            "appkey" to appKey,
            "template_id" to templateId,
            "template_json" to attachment.toString(),
        )
    (templateArgs ?: inferTemplateArgs(attachment))?.let { resolvedArgs ->
        queryParams += "template_args" to resolvedArgs.toString()
    }
    return queryParams.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun describeThrowable(error: Throwable): String {
    val root =
        if (error is InvocationTargetException && error.targetException != null) {
            error.targetException
        } else {
            error
        }
    return "${root.javaClass.name}: ${root.message ?: ""}".trimEnd()
}
