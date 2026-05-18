package party.qwer.iris.imagebridge.runtime.send

import org.json.JSONArray
import org.json.JSONObject

internal fun hasExplicitKakaoLinkTemplateArgs(rawAttachment: String): Boolean =
    runCatching {
        explicitTemplateArgs(JSONObject(rawAttachment)) != null
    }.getOrDefault(false)

internal fun hasResolvedIrisKakaoLinkTemplate(rawAttachment: String): Boolean =
    runCatching {
        isResolvedIrisKakaoLinkTemplate(JSONObject(rawAttachment))
    }.getOrDefault(false)

internal fun desiredTemplateId(attachment: JSONObject): String? =
    firstNonBlank(
        attachment.optString("template_id"),
        attachment.optString("templateId"),
        attachment.optJSONObject("K")?.optString("ti"),
        attachment.optJSONObject("P")?.optString("SDID"),
    )

internal fun desiredFirstWebUrl(attachment: JSONObject): String? =
    attachment
        .optJSONObject("C")
        ?.optJSONArray("ITL")
        ?.optJSONObject(0)
        ?.optJSONObject("L")
        ?.let { links -> firstNonBlank(links.optString("LPC"), links.optString("LMO")) }

internal fun explicitTemplateArgs(attachment: JSONObject): JSONObject? =
    attachment.optJSONObject("template_args")
        ?: attachment.optJSONObject("templateArgs")
        ?: attachment.optJSONObject("ta")

internal fun inferTemplateArgs(attachment: JSONObject): JSONObject? {
    val content = attachment.optJSONObject("C") ?: return null
    val args = JSONObject()
    putHeaderTemplateArgs(args, content)
    val items = content.optJSONArray("ITL") ?: return args.takeIf { it.length() > 0 }
    for (index in 0 until items.length()) {
        putInferredItemArgs(args, items, index)
    }
    args.put("visible_stream_count", items.length().coerceAtMost(4).toString())
    return args.takeIf { it.length() > 0 }
}

internal fun extractKakaoLinkAppKey(rawAttachment: String): String? =
    KAKAO_SCHEME_APP_KEY.find(rawAttachment)?.groupValues?.getOrNull(1)
        ?: APP_KEY_QUERY.find(rawAttachment)?.groupValues?.getOrNull(1)

internal fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { value -> !value.isNullOrBlank() }

internal fun formatKakaoLinkScheme(appKey: String): String = "kakao$appKey://kakaolink"

private fun isResolvedIrisKakaoLinkTemplate(attachment: JSONObject): Boolean {
    val platform = attachment.optJSONObject("P") ?: return false
    val content = attachment.optJSONObject("C") ?: return false
    val hasItems = content.optJSONArray("ITL")?.length()?.let { it > 0 } ?: false
    if (!hasItems) return false
    return platform.optString("SID").startsWith("iris_") ||
        platform.optString("SNM") == "hololive-bot"
}

private fun putHeaderTemplateArgs(
    args: JSONObject,
    content: JSONObject,
) {
    content.optJSONObject("HD")?.optJSONObject("TD")?.optString("T")?.takeIf { it.isNotBlank() }?.let { title ->
        args.put("alarm_title", title)
        args.put("header_title", title)
    }
}

private fun putInferredItemArgs(
    args: JSONObject,
    items: JSONArray,
    index: Int,
) {
    val item = items.optJSONObject(index)?.optJSONObject("TD") ?: return
    val oneBased = index + 1
    putInferredTitleArgs(args, item, index, oneBased)
    putInferredDescriptionArgs(args, item, index, oneBased)
    putInferredThumbnailArgs(args, items, index, oneBased)
    putInferredLinkArgs(args, items, index, oneBased)
}

private fun putInferredTitleArgs(
    args: JSONObject,
    item: JSONObject,
    index: Int,
    oneBased: Int,
) {
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
}

private fun putInferredDescriptionArgs(
    args: JSONObject,
    item: JSONObject,
    index: Int,
    oneBased: Int,
) {
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
}

private fun putInferredThumbnailArgs(
    args: JSONObject,
    items: JSONArray,
    index: Int,
    oneBased: Int,
) {
    items.optJSONObject(index)?.optJSONObject("TH")?.optString("THU")?.takeIf { it.isNotBlank() }?.let { thumbnail ->
        args.put("item${oneBased}_thumbnail", thumbnail)
        if (index == 0) {
            args.put("thumbnail", thumbnail)
        }
    }
}

private fun putInferredLinkArgs(
    args: JSONObject,
    items: JSONArray,
    index: Int,
    oneBased: Int,
) {
    items.optJSONObject(index)?.optJSONObject("L")?.let { links ->
        putInferredWebUrlArgs(args, links, index, oneBased)
        putInferredMobileWebUrlArgs(args, links, index, oneBased)
    }
}

private fun putInferredWebUrlArgs(
    args: JSONObject,
    links: JSONObject,
    index: Int,
    oneBased: Int,
) {
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
}

private fun putInferredMobileWebUrlArgs(
    args: JSONObject,
    links: JSONObject,
    index: Int,
    oneBased: Int,
) {
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

private val KAKAO_SCHEME_APP_KEY = Regex("""kakao([0-9a-fA-F]{16,64})://kakaolink""")
private val APP_KEY_QUERY = Regex("""(?:[?&]|\\b)app_key=([0-9a-fA-F]{16,64})""")
