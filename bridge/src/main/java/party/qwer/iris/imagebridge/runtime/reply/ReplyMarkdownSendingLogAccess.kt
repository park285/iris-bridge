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

    private fun readAttachmentText(sendingLog: Any): String? = readKnownAttachmentText(sendingLog) ?: readAttachmentTextFromFields(sendingLog)

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

    private fun isAttachmentText(value: String): Boolean = value.contains("irisSessionId") || value.contains("callingPkg")

    private fun extractSessionId(attachmentText: String): String? =
        runCatching {
            JSONObject(attachmentText).optString("irisSessionId").ifBlank { null }
        }.getOrNull()
}
