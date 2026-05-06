package party.qwer.iris.imagebridge.runtime.reply

import org.json.JSONObject
import java.lang.reflect.Modifier

internal object ReplyMarkdownSendingLogAccess {
    fun readRoomId(sendingLog: Any): Long? =
        runCatching {
            val value =
                sendingLog.javaClass
                    .getMethod("getChatRoomId")
                    .apply { isAccessible = true }
                    .invoke(sendingLog)
            when (value) {
                is Long -> value
                is Number -> value.toLong()
                else -> value?.toString()?.toLongOrNull()
            }
        }.getOrNull()

    fun readMessageText(sendingLog: Any): String? =
        runCatching {
            sendingLog
                .javaClass
                .getMethod("f0")
                .apply { isAccessible = true }
                .invoke(sendingLog)
                ?.toString()
        }.getOrNull()

    fun readAttachmentSessionId(
        sendingLog: Any,
    ): String? =
        readAttachmentText(
            sendingLog,
        )?.let(::extractSessionId)

    fun readAttachmentText(sendingLog: Any): String? = readKnownAttachmentText(sendingLog) ?: readAttachmentTextFromFields(sendingLog)

    fun writeAttachmentText(
        sendingLog: Any,
        attachmentText: String,
    ) {
        if (writeKnownAttachmentText(sendingLog, attachmentText)) return
        if (writeAttachmentTextToFields(sendingLog, attachmentText)) return
        error("reply attachment field not writable")
    }

    fun writeThreadMetadata(
        sendingLog: Any,
        threadId: Long,
        threadScope: Int,
    ) {
        val sendingLogClass = sendingLog.javaClass
        val threadIdValue = java.lang.Long.valueOf(threadId)

        runCatching {
            sendingLogClass
                .getMethod("H1", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(sendingLog, threadScope)
        }.getOrElse {
            val scopeField = sendingLogClass.getDeclaredField("Z").apply { isAccessible = true }
            scopeField.setInt(sendingLog, threadScope)
        }

        runCatching {
            sendingLogClass
                .getMethod("J1", Long::class.javaObjectType)
                .apply { isAccessible = true }
                .invoke(sendingLog, threadIdValue)
        }.getOrElse {
            val threadField = sendingLogClass.getDeclaredField("V0").apply { isAccessible = true }
            threadField.set(sendingLog, threadIdValue)
        }
    }

    private fun readKnownAttachmentText(sendingLog: Any): String? =
        runCatching {
            val attachment =
                sendingLog
                    .javaClass
                    .getDeclaredField("G")
                    .apply { isAccessible = true }
                    .get(sendingLog)
            attachmentTextCandidate(attachment)
        }.getOrNull()

    private fun readAttachmentTextFromFields(source: Any): String? =
        source
            .javaClass
            .declaredFields
            .asSequence()
            .filterNot { field -> Modifier.isStatic(field.modifiers) }
            .mapNotNull { field ->
                runCatching {
                    field.isAccessible = true
                    attachmentTextCandidate(field.get(source))
                }.getOrNull()
            }.firstOrNull()

    private fun attachmentTextCandidate(value: Any?): String? =
        when (value) {
            null -> null
            is String -> value.takeIf(::isAttachmentText)
            is CharSequence -> value.toString().takeIf(::isAttachmentText)
            else -> stringFieldCandidate(value) ?: value.toString().takeIf(::isAttachmentText)
        }

    private fun writeKnownAttachmentText(
        sendingLog: Any,
        attachmentText: String,
    ): Boolean =
        runCatching {
            val field =
                sendingLog
                    .javaClass
                    .getDeclaredField("G")
                    .apply { isAccessible = true }
            writeAttachmentField(field, sendingLog, attachmentText)
        }.getOrDefault(false)

    private fun writeAttachmentTextToFields(
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
                    when {
                        attachmentTextCandidate(value) != null -> writeAttachmentField(field, source, attachmentText)
                        else -> false
                    }
                }.getOrDefault(false)
            }

    private fun writeAttachmentField(
        field: java.lang.reflect.Field,
        source: Any,
        attachmentText: String,
    ): Boolean {
        val type = field.type
        if (type.isAssignableFrom(String::class.java) || type == CharSequence::class.java || type == Any::class.java) {
            field.set(source, attachmentText)
            return true
        }
        val value = field.get(source) ?: return false
        return writeStringFieldCandidate(value, attachmentText)
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

    private fun isAttachmentText(value: String): Boolean = value.contains("irisSessionId") || value.contains("callingPkg") || value.contains("\"mentions\"")

    private fun extractSessionId(attachmentText: String): String? =
        runCatching {
            JSONObject(attachmentText).optString("irisSessionId").ifBlank { null }
        }.getOrNull()
}
