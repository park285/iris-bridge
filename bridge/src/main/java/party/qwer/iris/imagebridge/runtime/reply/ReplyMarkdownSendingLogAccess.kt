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

    fun readAttachmentSessionId(sendingLog: Any): String? = ReplySendingLogAttachmentAccess.readSessionId(sendingLog)

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
        // scope/threadId setter 메서드명은 KakaoTalk 버전마다 밀린다(26.4.2 J1/L1 → 26.5.2 L1/N1). 26.5.2에서
        // H1(int)가 multiUploadSequence setter로 새로 생겨 시그니처가 우연히 맞으면 scope가 엉뚱한 필드에 써진 채
        // 예외 없이 "성공"해 thread reply가 누락된다. 필드명 Z(scope)/V0(threadId)는 버전 간 안정적이라 직접 write한다.
        setThreadField(sendingLog, "Z", threadScope)
        setThreadField(sendingLog, "V0", java.lang.Long.valueOf(threadId))
    }

    private fun setThreadField(
        sendingLog: Any,
        fieldName: String,
        value: Any,
    ) {
        var cls: Class<*>? = sendingLog.javaClass
        while (cls != null) {
            try {
                cls.getDeclaredField(fieldName).apply { isAccessible = true }.set(sendingLog, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException(
            "ChatSendingLog thread field '$fieldName' absent in ${sendingLog.javaClass.name} hierarchy",
        )
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
