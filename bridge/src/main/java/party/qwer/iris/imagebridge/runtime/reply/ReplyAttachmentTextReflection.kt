package party.qwer.iris.imagebridge.runtime.reply

import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.looksLikeReplyAttachmentText
import party.qwer.iris.imagebridge.runtime.core.replyAttachmentSessionId
import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal fun attachmentTextCandidate(value: Any?): String? =
    when (value) {
        null -> null
        is String -> value.takeIf(::isAttachmentText)
        is CharSequence -> value.toString().takeIf(::isAttachmentText)
        else -> stringFieldCandidate(value) ?: value.toString().takeIf(::isAttachmentText)
    }

internal fun writeAttachmentField(
    field: Field,
    source: Any,
    attachmentText: String,
): Boolean {
    val type = field.type
    if (type.isAssignableFrom(String::class.java) || type == CharSequence::class.java || type == Any::class.java) {
        field.set(source, attachmentText)
        return true
    }
    val value = field.get(source) ?: return false
    if (value is JSONObject) {
        copyJsonObjectKeys(JSONObject(attachmentText), value)
        return true
    }
    return writeStringFieldCandidate(value, attachmentText)
}

private fun copyJsonObjectKeys(
    source: JSONObject,
    target: JSONObject,
) {
    val keys = source.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        target.put(key, source.get(key))
    }
}

private fun stringFieldCandidate(source: Any): String? =
    source
        .javaClass
        .declaredFields
        .asSequence()
        .filterNot { field -> Modifier.isStatic(field.modifiers) }
        .mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                when (val value = field.get(source)) {
                    is String -> value
                    is CharSequence -> value.toString()
                    else -> null
                }
            }.getOrNull()
        }.firstOrNull(::isAttachmentText)

private fun writeStringFieldCandidate(
    source: Any,
    attachmentText: String,
): Boolean =
    source
        .javaClass
        .declaredFields
        .asSequence()
        .filterNot { field -> Modifier.isStatic(field.modifiers) }
        .any { field ->
            runCatching {
                field.isAccessible = true
                val value = field.get(source)
                val text =
                    when (value) {
                        is String -> value
                        is CharSequence -> value.toString()
                        else -> null
                    }
                if (text?.let(::isAttachmentText) == true) {
                    field.set(source, attachmentText)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        }

private fun isAttachmentText(value: String): Boolean = BridgeCore.looksLikeReplyAttachmentText(value)

internal fun extractAttachmentSessionId(attachmentText: String): String? = BridgeCore.replyAttachmentSessionId(attachmentText)
