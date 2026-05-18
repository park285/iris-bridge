package party.qwer.iris.imagebridge.runtime.send

import org.json.JSONArray
import org.json.JSONObject

internal fun kakaoLinkSpecSendAttachment(rawAttachment: String): String =
    runCatching {
        val attachment = JSONObject(rawAttachment)
        val appKey =
            firstNonBlank(
                attachment.optString("app_key"),
                attachment.optString("appKey"),
                attachment.optJSONObject("K")?.optString("ak"),
            )
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
                    .put("SL", JSONObject().put("LCA", formatKakaoLinkScheme(appKey))),
            ).put("C", kakaoLinkSpecTemplateJsonContent(attachment))
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
        firstNonBlank(
            attachment.optString("app_key"),
            attachment.optString("appKey"),
            attachment.optJSONObject("K")?.optString("ak"),
        )
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
    val result = JSONObject().put("HD", JSONObject().put("TD", JSONObject().put("T", title)))
    content?.optJSONArray("ITL")?.let { sourceItems ->
        val items = JSONArray()
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
        result.put("TD", JSONObject().put("T", text.optString("T")).put("D", text.optString("D")))
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

private val DISPLAY_STATUS_SUFFIXES = listOf(" · 예정", " · LIVE", " · 라이브", " · 종료", " · 대기")
private val RECEIVER_SAFETY_LINK_KEYS = arrayOf("LPC", "LMO", "LA", "LCA")
private val TEMPLATE_ID_QUERY_PARAM = Regex("""([?&]tid=)\d+""")
