package party.qwer.iris.imagebridge.runtime

import org.json.JSONObject
import party.qwer.iris.imagebridge.runtime.reply.attachmentTextCandidate
import party.qwer.iris.imagebridge.runtime.reply.writeAttachmentField
import java.lang.reflect.Modifier

internal fun readRequestSendingLog(request: Any): Any? =
    runCatching {
        request.javaClass
            .getDeclaredField("b")
            .apply { isAccessible = true }
            .get(request)
    }.getOrNull()
        ?: request.javaClass.declaredFields
            .asSequence()
            .filterNot { field -> Modifier.isStatic(field.modifiers) }
            .mapNotNull { field ->
                runCatching {
                    field.isAccessible = true
                    field.get(request)?.takeIf(::looksLikeSendingLog)
                }.getOrNull()
            }.firstOrNull()

internal fun readLongNoArg(
    source: Any,
    methodName: String,
): Long? =
    runCatching {
        val value =
            source.javaClass
                .getMethod(methodName)
                .apply { isAccessible = true }
                .invoke(source)
        when (value) {
            is Long -> value
            is Number -> value.toLong()
            else -> value?.toString()?.toLongOrNull()
        }
    }.getOrNull()

internal fun readChatLogMessageText(chatLog: Any): String? =
    listOf("p", "h", "j", "h1")
        .asSequence()
        .mapNotNull { methodName ->
            runCatching {
                val value =
                    chatLog.javaClass
                        .getMethod(methodName)
                        .apply { isAccessible = true }
                        .invoke(chatLog)
                        ?.toString()
                value?.takeIf { it.isNotBlank() && attachmentTextCandidate(it) == null }
            }.getOrNull()
        }.firstOrNull()

internal fun readChatLogAttachmentText(chatLog: Any): String? =
    readChatLogAttachmentObject(chatLog)?.let { value ->
        when (value) {
            is JSONObject -> value.toString()
            is String -> value.takeIf { attachmentTextCandidate(it) != null }
            is CharSequence -> value.toString().takeIf { attachmentTextCandidate(it) != null }
            else -> attachmentTextCandidate(value)
        }
    } ?: readAttachmentTextFromFields(chatLog)

internal fun writeChatLogAttachmentText(
    chatLog: Any,
    attachmentText: String,
) {
    if (writeKnownChatLogAttachment(chatLog, attachmentText)) return
    if (writeChatLogAttachmentObject(chatLog, attachmentText)) return
    if (writeAttachmentTextToFields(chatLog, attachmentText)) return
    error("chatLog attachment field not writable")
}

private fun looksLikeSendingLog(value: Any): Boolean =
    runCatching {
        value.javaClass.getMethod("getChatRoomId")
        value.javaClass.getMethod("f0")
        true
    }.getOrDefault(false)

private fun readChatLogAttachmentObject(chatLog: Any): Any? =
    runCatching {
        chatLog.javaClass
            .getMethod("t")
            .apply { isAccessible = true }
            .invoke(chatLog)
    }.getOrNull()

private fun writeKnownChatLogAttachment(
    chatLog: Any,
    attachmentText: String,
): Boolean =
    runCatching {
        chatLog.javaClass
            .getMethod("f2", String::class.java)
            .apply { isAccessible = true }
            .invoke(chatLog, attachmentText)
        true
    }.getOrDefault(false)

private fun writeChatLogAttachmentObject(
    chatLog: Any,
    attachmentText: String,
): Boolean =
    runCatching {
        val current = readChatLogAttachmentObject(chatLog) as? JSONObject ?: return@runCatching false
        copyJsonObjectKeys(JSONObject(attachmentText), current)
        true
    }.getOrDefault(false)

private fun readAttachmentTextFromFields(source: Any): String? =
    source.javaClass.declaredFields
        .asSequence()
        .filterNot { field -> Modifier.isStatic(field.modifiers) }
        .mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                attachmentTextCandidate(field.get(source))
            }.getOrNull()
        }.firstOrNull()

private fun writeAttachmentTextToFields(
    source: Any,
    attachmentText: String,
): Boolean =
    source.javaClass.declaredFields
        .asSequence()
        .filterNot { field -> Modifier.isStatic(field.modifiers) }
        .any { field ->
            runCatching {
                field.isAccessible = true
                attachmentTextCandidate(field.get(source)) != null && writeAttachmentField(field, source, attachmentText)
            }.getOrDefault(false)
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
