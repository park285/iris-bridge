package party.qwer.iris.imagebridge.runtime.send

internal fun sleepBeforeKakaoLinkRetry(
    sleeper: (Long) -> Unit,
    delayMs: Long,
    requestId: String?,
    roomId: Long,
    interruptedMessage: String,
    logInfo: (String, String) -> Unit,
): Boolean {
    try {
        sleeper(delayMs)
        return true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        logInfo(KAKAO_TEXT_SEND_TAG, "$interruptedMessage requestId=$requestId room=$roomId")
        return false
    }
}
