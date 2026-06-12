package party.qwer.iris.imagebridge.runtime.reply

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

    fun readMessageText(sendingLog: Any): String? {
        val sendingLogClass = sendingLog.javaClass
        for (methodName in listOf("getMessage", "g0", "f0")) {
            val value =
                runCatching {
                    sendingLogClass
                        .getMethod(methodName)
                        .apply { isAccessible = true }
                        .invoke(sendingLog)
                }.getOrNull()
            if (value is CharSequence) return value.toString()
        }
        for (fieldName in listOf("message", "F")) {
            val value =
                runCatching {
                    sendingLogClass
                        .getDeclaredField(fieldName)
                        .apply { isAccessible = true }
                        .get(sendingLog)
                }.getOrNull()
            if (value is CharSequence) return value.toString()
        }
        return null
    }

    fun readAttachmentSessionId(
        sendingLog: Any,
    ): String? = ReplySendingLogAttachmentAccess.readSessionId(sendingLog)

    fun readAttachmentText(sendingLog: Any): String? = ReplySendingLogAttachmentAccess.readAttachmentText(sendingLog)

    fun writeAttachmentText(
        sendingLog: Any,
        attachmentText: String,
    ) = ReplySendingLogAttachmentAccess.writeAttachmentText(sendingLog, attachmentText)

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

    fun writeMessageType(
        sendingLog: Any,
        messageType: Any,
    ) {
        val sendingLogClass = sendingLog.javaClass
        runCatching {
            sendingLogClass
                .getDeclaredField("D")
                .apply { isAccessible = true }
                .set(sendingLog, messageType)
            return
        }
        val messageTypeClass = messageType.javaClass
        val field =
            sendingLogClass.declaredFields
                .firstOrNull { candidate -> messageTypeClass.isAssignableFrom(candidate.type) }
                ?: error("sendingLog message type field not found")
        field.apply { isAccessible = true }.set(sendingLog, messageType)
    }
}
