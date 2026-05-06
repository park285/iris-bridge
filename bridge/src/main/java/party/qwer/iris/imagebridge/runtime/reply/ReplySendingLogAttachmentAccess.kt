package party.qwer.iris.imagebridge.runtime.reply

import java.lang.reflect.Modifier

internal object ReplySendingLogAttachmentAccess {
    fun readSessionId(sendingLog: Any): String? = readAttachmentText(sendingLog)?.let(::extractAttachmentSessionId)

    fun readAttachmentText(sendingLog: Any): String? = readKnownAttachmentText(sendingLog) ?: readAttachmentTextFromFields(sendingLog)

    fun writeAttachmentText(
        sendingLog: Any,
        attachmentText: String,
    ) {
        if (writeKnownAttachmentText(sendingLog, attachmentText)) return
        if (writeAttachmentTextToFields(sendingLog, attachmentText)) return
        error("reply attachment field not writable")
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
                    attachmentTextCandidate(value) != null && writeAttachmentField(field, source, attachmentText)
                }.getOrDefault(false)
            }
}
