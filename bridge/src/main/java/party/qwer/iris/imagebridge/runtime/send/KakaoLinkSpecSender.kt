package party.qwer.iris.imagebridge.runtime.send

import android.util.Log
import org.json.JSONObject
import java.net.URLEncoder

internal interface KakaoLinkSpecSender {
    fun send(
        roomId: Long,
        message: String,
        rawAttachment: String,
        requestId: String?,
    ): Boolean
}

internal class ReflectiveKakaoLinkSpecSender(
    private val loader: ClassLoader,
    private val listener: Any?,
    private val logInfo: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
) : KakaoLinkSpecSender {
    override fun send(
        roomId: Long,
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
                    "error=${error.javaClass.name}: ${error.message}",
            )
        }.getOrDefault(false)

    private fun invokeKakaoLinkSpecSend(
        spec: Any,
        roomId: Long,
    ): String {
        val sendToReceiverMethod =
            spec.javaClass.methods
                .firstOrNull { method ->
                    val types = method.parameterTypes
                    method.name == "b" &&
                        types.size == 3 &&
                        types[0] == Long::class.javaPrimitiveType &&
                        types[1] == LongArray::class.java &&
                        (listener == null || types[2].isAssignableFrom(listener.javaClass))
                }
        if (sendToReceiverMethod != null) {
            sendToReceiverMethod.apply { isAccessible = true }.invoke(spec, roomId, null, listener)
            return sendToReceiverMethod.name
        }
        val sendByExistingChatIdMethod =
            spec.javaClass.methods
                .firstOrNull { method ->
                    val types = method.parameterTypes
                    method.name == "c" &&
                        types.size == 1 &&
                        types[0] == Long::class.javaPrimitiveType
                }
        if (sendByExistingChatIdMethod != null) {
            sendByExistingChatIdMethod.apply { isAccessible = true }.invoke(spec, roomId)
            return sendByExistingChatIdMethod.name
        }
        error("KakaoLinkSpec send method not found")
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
        )
    if (templateArgs == null) {
        queryParams += "template_json" to attachment.toString()
    }
    (templateArgs ?: inferTemplateArgs(attachment))?.let { templateArgs ->
        queryParams += "template_args" to templateArgs.toString()
    }
    return queryParams.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
}

internal fun hasExplicitKakaoLinkTemplateArgs(rawAttachment: String): Boolean =
    runCatching {
        explicitTemplateArgs(JSONObject(rawAttachment)) != null
    }.getOrDefault(false)

private fun explicitTemplateArgs(attachment: JSONObject): JSONObject? =
    attachment.optJSONObject("template_args")
        ?: attachment.optJSONObject("templateArgs")
        ?: attachment.optJSONObject("ta")

private fun inferTemplateArgs(attachment: JSONObject): JSONObject? {
    val content = attachment.optJSONObject("C") ?: return null
    val args = JSONObject()
    content.optJSONObject("HD")?.optJSONObject("TD")?.optString("T")?.takeIf { it.isNotBlank() }?.let { title ->
        args.put("title", title)
        args.put("header_title", title)
    }
    val items = content.optJSONArray("ITL") ?: return args.takeIf { it.length() > 0 }
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index)?.optJSONObject("TD") ?: continue
        val oneBased = index + 1
        item.optString("T").takeIf { it.isNotBlank() }?.let { title ->
            args.put("item${oneBased}_title", title)
            args.put("item_title_$oneBased", title)
            args.put("title$oneBased", title)
        }
        item.optString("D").takeIf { it.isNotBlank() }?.let { description ->
            args.put("item${oneBased}_description", description)
            args.put("item${oneBased}_desc", description)
            args.put("item_description_$oneBased", description)
            args.put("description$oneBased", description)
            args.put("desc$oneBased", description)
        }
    }
    return args.takeIf { it.length() > 0 }
}

private fun extractKakaoLinkAppKey(rawAttachment: String): String? =
    KAKAO_SCHEME_APP_KEY.find(rawAttachment)?.groupValues?.getOrNull(1)
        ?: APP_KEY_QUERY.find(rawAttachment)?.groupValues?.getOrNull(1)

private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { value -> !value.isNullOrBlank() }

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

private val KAKAO_SCHEME_APP_KEY = Regex("""kakao([0-9a-fA-F]{16,64})://kakaolink""")
private val APP_KEY_QUERY = Regex("""(?:[?&]|\\b)app_key=([0-9a-fA-F]{16,64})""")
