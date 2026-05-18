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

internal fun kakaoLinkSpecSendAttachment(rawAttachment: String): String =
    runCatching {
        val attachment = JSONObject(rawAttachment)
        val appKey =
            firstNonBlank(attachment.optString("app_key"), attachment.optString("appKey"), attachment.optJSONObject("K")?.optString("ak"))
                ?: extractKakaoLinkAppKey(rawAttachment)
                ?: return@runCatching rawAttachment
        val templateId = desiredTemplateId(attachment) ?: return@runCatching rawAttachment
        val platform = attachment.optJSONObject("P")
        val appVer = firstNonBlank(platform?.optString("VA"), platform?.optString("VI")) ?: "6.0.0"
        JSONObject()
            .put("app_key", appKey)
            .put("template_id", templateId)
            .put(
                "P",
                JSONObject()
                    .put("VA", appVer)
                    .put("SDID", templateId)
                    .put(
                        "SL",
                        JSONObject()
                            .put("LCA", formatKakaoLinkScheme(appKey)),
                    ),
            )
            .put("C", kakaoLinkSpecTemplateJsonContent(attachment))
            .put("K", JSONObject().put("ti", templateId))
            .also { result ->
                explicitTemplateArgs(attachment)?.let { templateArgs ->
                    result.put("template_args", JSONObject(templateArgs.toString()))
                }
            }.toString()
    }.getOrDefault(rawAttachment)

internal fun kakaoLinkSpecCommitVerificationAttachment(rawAttachment: String): String = rawAttachment

internal fun kakaoLinkSpecPatchMatchAttachments(rawAttachment: String): List<String> = listOf(rawAttachment)

internal fun kakaoLinkDirectLeverageAttachment(rawAttachment: String): String {
    val attachment = JSONObject(rawAttachment)
    val platform = JSONObject(attachment.getJSONObject("P").toString())
    val content = JSONObject(attachment.getJSONObject("C").toString())
    val appVer = firstNonBlank(platform.optString("VA"), platform.optString("VI")) ?: "6.0.0"
    val appKey =
        firstNonBlank(attachment.optString("app_key"), attachment.optString("appKey"), attachment.optJSONObject("K")?.optString("ak"))
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
    val result =
        JSONObject()
            .put("P", platform)
            .put("C", content)
            .put(
                "K",
                JSONObject(attachment.optJSONObject("K")?.toString() ?: "{}")
                    .put("ak", appKey)
                    .put("av", appVer)
                    .put("ti", templateId)
                    .put("lv", "4.0"),
            )
    explicitTemplateArgs(attachment)?.let { templateArgs ->
        result.put("ta", JSONObject(templateArgs.toString()))
    }
    attachment.optJSONObject("extras")?.let { extras ->
        result.put("extras", JSONObject(extras.toString()))
    }
    return result.toString()
}

internal fun kakaoLinkDisplayPatchAttachment(
    committedAttachment: String?,
    rawAttachment: String,
): String =
    runCatching {
        if (committedAttachment.isNullOrBlank()) {
            return@runCatching rawAttachment
        }
        val committed = JSONObject(committedAttachment)
        val desired = JSONObject(rawAttachment)
        val desiredContent = desired.optJSONObject("C") ?: return@runCatching rawAttachment
        val desiredPlatform = desired.optJSONObject("P")
        val content = JSONObject(desiredContent.toString())
        stripDisplayStatuses(content)
        content.remove("BUL")
        if (!content.has("BUT")) {
            content.put("BUT", 0)
        }
        committed.put("C", content)
        desiredTemplateId(desired)?.let { templateId ->
            committed.optJSONObject("P")?.put("SDID", templateId)
            committed.optJSONObject("K")?.put("ti", templateId)
            patchReceiverSafetyTemplateId(committed, templateId)
        }
        desiredPlatform?.optString("ME")?.takeIf { it.isNotBlank() }?.let { title ->
            committed.optJSONObject("P")?.put("ME", title)
        }
        desiredFirstWebUrl(desired)?.let { url ->
            committed.optJSONObject("P")?.put("DID", url)
            committed.optJSONObject("P")?.put("L", JSONObject().put("LPC", url).put("LMO", url))
        }
        committed.toString()
    }.getOrDefault(rawAttachment)

private fun patchReceiverSafetyTemplateId(
    attachment: JSONObject,
    templateId: String,
) {
    val safetyLinks = attachment.optJSONObject("P")?.optJSONObject("SST")?.optJSONObject("L") ?: return
    for (key in RECEIVER_SAFETY_LINK_KEYS) {
        val link = safetyLinks.optString(key)
        if (link.isNotBlank()) {
            safetyLinks.put(
                key,
                TEMPLATE_ID_QUERY_PARAM.replace(link) { match -> "${match.groupValues[1]}$templateId" },
            )
        }
    }
}

private fun kakaoLinkSpecTemplateJsonContent(attachment: JSONObject): JSONObject {
    val content = attachment.optJSONObject("C")
    val title =
        firstNonBlank(
            content?.optJSONObject("HD")?.optJSONObject("TD")?.optString("T"),
            attachment.optJSONObject("P")?.optString("ME"),
        ) ?: ""
    val result =
        JSONObject()
            .put(
                "HD",
                JSONObject()
                    .put("TD", JSONObject().put("T", title)),
            )
    content?.optJSONArray("ITL")?.let { sourceItems ->
        val items = org.json.JSONArray()
        for (index in 0 until sourceItems.length()) {
            sourceItems.optJSONObject(index)?.let { item ->
                items.put(kakaoLinkSpecTemplateJsonItem(item))
            }
        }
        result.put("ITL", items)
    }
    return result
}

private fun kakaoLinkSpecTemplateJsonItem(item: JSONObject): JSONObject {
    val text = item.optJSONObject("TD")
    val thumbnail = item.optJSONObject("TH")
    val result = JSONObject()
    if (text != null) {
        result.put(
            "TD",
            JSONObject()
                .put("T", text.optString("T"))
                .put("D", text.optString("D")),
        )
    }
    if (thumbnail != null) {
        result.put(
            "TH",
            JSONObject()
                .put("THU", thumbnail.optString("THU"))
                .put("W", thumbnail.optInt("W", 200))
                .put("H", thumbnail.optInt("H", 200)),
        )
    }
    return result
}

private fun stripDisplayStatuses(content: JSONObject) {
    val items = content.optJSONArray("ITL") ?: return
    for (index in 0 until items.length()) {
        val text = items.optJSONObject(index)?.optJSONObject("TD") ?: continue
        val description = text.optString("D")
        val stripped =
            DISPLAY_STATUS_SUFFIXES.fold(description) { value, suffix ->
                value.removeSuffix(suffix)
            }
        if (stripped != description) {
            text.put("D", stripped)
        }
    }
}

internal fun hasExplicitKakaoLinkTemplateArgs(rawAttachment: String): Boolean =
    runCatching {
        explicitTemplateArgs(JSONObject(rawAttachment)) != null
    }.getOrDefault(false)

internal fun hasResolvedIrisKakaoLinkTemplate(rawAttachment: String): Boolean =
    runCatching {
        isResolvedIrisKakaoLinkTemplate(JSONObject(rawAttachment))
    }.getOrDefault(false)

private fun isResolvedIrisKakaoLinkTemplate(attachment: JSONObject): Boolean {
    val platform = attachment.optJSONObject("P") ?: return false
    val content = attachment.optJSONObject("C") ?: return false
    val hasItems = content.optJSONArray("ITL")?.length()?.let { it > 0 } ?: false
    if (!hasItems) return false
    return platform.optString("SID").startsWith("iris_") ||
        platform.optString("SNM") == "hololive-bot"
}

private fun desiredTemplateId(attachment: JSONObject): String? =
    firstNonBlank(
        attachment.optString("template_id"),
        attachment.optString("templateId"),
        attachment.optJSONObject("K")?.optString("ti"),
        attachment.optJSONObject("P")?.optString("SDID"),
    )

private fun desiredFirstWebUrl(attachment: JSONObject): String? =
    attachment
        .optJSONObject("C")
        ?.optJSONArray("ITL")
        ?.optJSONObject(0)
        ?.optJSONObject("L")
        ?.let { links -> firstNonBlank(links.optString("LPC"), links.optString("LMO")) }

private fun explicitTemplateArgs(attachment: JSONObject): JSONObject? =
    attachment.optJSONObject("template_args")
        ?: attachment.optJSONObject("templateArgs")
        ?: attachment.optJSONObject("ta")

private fun inferTemplateArgs(attachment: JSONObject): JSONObject? {
    val content = attachment.optJSONObject("C") ?: return null
    val args = JSONObject()
    content.optJSONObject("HD")?.optJSONObject("TD")?.optString("T")?.takeIf { it.isNotBlank() }?.let { title ->
        args.put("alarm_title", title)
        args.put("header_title", title)
    }
    val items = content.optJSONArray("ITL") ?: return args.takeIf { it.length() > 0 }
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index)?.optJSONObject("TD") ?: continue
        val oneBased = index + 1
        item.optString("T").takeIf { it.isNotBlank() }?.let { title ->
            args.put("item${oneBased}_title", title)
            if (index == 0) {
                args.put("stream_title", title)
                args.put("item_title", title)
                args.put("title", title)
            }
            args.put("item_title_$oneBased", title)
            args.put("title$oneBased", title)
        }
        item.optString("D").takeIf { it.isNotBlank() }?.let { description ->
            args.put("item${oneBased}_desc", description)
            if (index == 0) {
                args.put("stream_desc", description)
                args.put("item_desc", description)
                args.put("description", description)
            }
            args.put("item${oneBased}_description", description)
            args.put("item_description_$oneBased", description)
            args.put("description$oneBased", description)
            args.put("desc$oneBased", description)
        }
        items.optJSONObject(index)?.optJSONObject("TH")?.optString("THU")?.takeIf { it.isNotBlank() }?.let { thumbnail ->
            args.put("item${oneBased}_thumbnail", thumbnail)
            if (index == 0) {
                args.put("thumbnail", thumbnail)
            }
        }
        items.optJSONObject(index)?.optJSONObject("L")?.let { links ->
            links.optString("LPC").takeIf { it.isNotBlank() }?.let { webUrl ->
                args.put("item${oneBased}_full_web_url", webUrl)
                args.put("item${oneBased}_web_url", webUrl)
                if (index == 0) {
                    args.put("full_web_url", webUrl)
                    args.put("item_full_web_url", webUrl)
                    args.put("web_url", webUrl)
                    args.put("item_web_url", webUrl)
                    args.put("more_full_web_url", webUrl)
                    args.put("more_web_url", webUrl)
                }
            }
            links.optString("LMO").takeIf { it.isNotBlank() }?.let { mobileWebUrl ->
                args.put("item${oneBased}_full_mobile_web_url", mobileWebUrl)
                args.put("item${oneBased}_mobile_web_url", mobileWebUrl)
                if (index == 0) {
                    args.put("full_mobile_web_url", mobileWebUrl)
                    args.put("item_full_mobile_web_url", mobileWebUrl)
                    args.put("mobile_web_url", mobileWebUrl)
                    args.put("item_mobile_web_url", mobileWebUrl)
                    args.put("more_full_mobile_web_url", mobileWebUrl)
                    args.put("more_mobile_web_url", mobileWebUrl)
                }
            }
        }
    }
    args.put("visible_stream_count", items.length().coerceAtMost(4).toString())
    return args.takeIf { it.length() > 0 }
}

private fun extractKakaoLinkAppKey(rawAttachment: String): String? =
    KAKAO_SCHEME_APP_KEY.find(rawAttachment)?.groupValues?.getOrNull(1)
        ?: APP_KEY_QUERY.find(rawAttachment)?.groupValues?.getOrNull(1)

private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { value -> !value.isNullOrBlank() }

private fun formatKakaoLinkScheme(appKey: String): String = "kakao$appKey://kakaolink"

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

private val KAKAO_SCHEME_APP_KEY = Regex("""kakao([0-9a-fA-F]{16,64})://kakaolink""")
private val APP_KEY_QUERY = Regex("""(?:[?&]|\\b)app_key=([0-9a-fA-F]{16,64})""")
private val DISPLAY_STATUS_SUFFIXES = listOf(" · 예정", " · LIVE", " · 라이브", " · 종료", " · 대기")
private val RECEIVER_SAFETY_LINK_KEYS = arrayOf("LPC", "LMO", "LA", "LCA")
private val TEMPLATE_ID_QUERY_PARAM = Regex("""([?&]tid=)\d+""")
