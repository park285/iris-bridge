package party.qwer.iris.imagebridge.runtime.send

import org.json.JSONObject

internal fun kakaoLinkAttachmentsMatch(
    expectedRawAttachment: String,
    committedRawAttachment: String,
): Boolean =
    runCatching {
        val expected = JSONObject(expectedRawAttachment)
        val committed = JSONObject(committedRawAttachment)
        val expectedTemplateId = templateId(expected) ?: return@runCatching false
        if (expectedTemplateId != templateId(committed)) {
            return@runCatching false
        }
        val expectedTitles = itemTitles(expected)
        val committedTitles = itemTitles(committed)
        if (expectedTitles.isNotEmpty() || committedTitles.isNotEmpty()) {
            return@runCatching expectedTitles.isNotEmpty() && expectedTitles == committedTitles
        }
        headerAndIdentityMatch(expected, committed)
    }.getOrDefault(false)

internal fun kakaoLinkPendingCleanupAttachmentsMatch(
    expectedRawAttachment: String,
    pendingRawAttachment: String,
): Boolean =
    runCatching {
        val expected = JSONObject(expectedRawAttachment)
        val pending = JSONObject(pendingRawAttachment)
        val expectedTemplateId = templateId(expected) ?: return@runCatching false
        if (expectedTemplateId != templateId(pending)) {
            return@runCatching false
        }
        val expectedTitles = itemTitles(expected)
        val pendingTitles = itemTitles(pending)
        if (expectedTitles.isNotEmpty() || pendingTitles.isNotEmpty()) {
            return@runCatching expectedTitles.isNotEmpty() && expectedTitles == pendingTitles
        }
        val expectedHeader = headerTitle(expected)
        val pendingHeader = headerTitle(pending)
        expectedHeader != null && expectedHeader == pendingHeader && identityFieldsMatch(expected, pending)
    }.getOrDefault(false)

private fun templateId(attachment: JSONObject): String? =
    firstNonBlankAttachmentValue(
        attachment.optString("template_id"),
        attachment.optString("templateId"),
        attachment.optJSONObject("K")?.optString("ti"),
        attachment.optJSONObject("P")?.optString("SDID"),
        attachment.optString("ti"),
    )

private fun itemTitles(attachment: JSONObject): List<String> {
    val items = attachment.optJSONObject("C")?.optJSONArray("ITL") ?: return emptyList()
    val contentTitles =
        (0 until items.length())
            .mapNotNull { index ->
                items
                    .optJSONObject(index)
                    ?.optJSONObject("TD")
                    ?.optString("T")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
    if (contentTitles.isNotEmpty()) {
        return contentTitles
    }
    val args = attachment.optJSONObject("template_args") ?: attachment.optJSONObject("ta") ?: return emptyList()
    return (1..items.length())
        .mapNotNull { index ->
            args.optString("item${index}_title").trim().takeIf { it.isNotEmpty() }
        }
}

private fun headerTitle(attachment: JSONObject): String? =
    firstNonBlankAttachmentValue(
        attachment
            .optJSONObject("C")
            ?.optJSONObject("HD")
            ?.optJSONObject("TD")
            ?.optString("T"),
        attachment.optJSONObject("P")?.optString("ME"),
        attachment.optJSONObject("template_args")?.optString("alarm_title"),
        attachment.optJSONObject("template_args")?.optString("title"),
        attachment.optJSONObject("template_args")?.optString("stream_title"),
        attachment.optJSONObject("ta")?.optString("alarm_title"),
        attachment.optJSONObject("ta")?.optString("title"),
        attachment.optJSONObject("ta")?.optString("stream_title"),
    )

private fun headerAndIdentityMatch(
    expected: JSONObject,
    actual: JSONObject,
): Boolean {
    val expectedHeader = headerTitle(expected)
    val actualHeader = headerTitle(actual)
    return expectedHeader != null && expectedHeader == actualHeader && identityFieldsMatch(expected, actual)
}

private fun identityFieldsMatch(
    expected: JSONObject,
    actual: JSONObject,
): Boolean {
    val expectedIdentity = identityFields(expected)
    val actualIdentity = identityFields(actual)
    if (expectedIdentity.isEmpty() && actualIdentity.isEmpty()) {
        return true
    }
    return expectedIdentity.isNotEmpty() && expectedIdentity == actualIdentity
}

private fun identityFields(attachment: JSONObject): Map<String, String> =
    linkedMapOf<String, String>().apply {
        firstNonBlankArgumentValue(attachment, "stream_title", "item_title", "item1_title")
            ?.let { put("title", it) }
        firstNonBlankArgumentValue(
            attachment,
            "web_url",
            "mobile_web_url",
            "full_web_url",
            "full_mobile_web_url",
            "item_web_url",
            "item_mobile_web_url",
            "item_full_web_url",
            "item_full_mobile_web_url",
            "item1_web_url",
            "item1_mobile_web_url",
            "item1_full_web_url",
            "item1_full_mobile_web_url",
        )?.let { put("url", it) }
        firstNonBlankArgumentValue(attachment, "video_id", "item_video_id", "item1_video_id")
            ?.let { put("video", it) }
    }

private fun firstNonBlankArgumentValue(
    attachment: JSONObject,
    vararg keys: String,
): String? =
    firstNonBlankAttachmentValue(
        *keys
            .flatMap { key ->
                listOf(
                    attachment.optJSONObject("template_args")?.optString(key),
                    attachment.optJSONObject("ta")?.optString(key),
                    attachment.optString(key),
                )
            }.toTypedArray(),
    )

private fun firstNonBlankAttachmentValue(vararg values: String?): String? = values.firstOrNull { value -> !value.isNullOrBlank() }
