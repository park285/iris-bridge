package party.qwer.iris.imagebridge.runtime.core

internal fun BridgeCore.encryptKakaoChatLogAttachment(
    encType: Int,
    plaintext: String,
    userId: Long,
): String =
    encryptKakaoChatLogAttachment(
        encType = encType,
        plaintext = plaintext,
        userId = userId,
        loadCompatibleCore = ::bridgeCoreLoadCompatibleLibraryOnce,
        nativeEncrypt = BridgeCoreJniKakaoLink::encryptKakaoChatLogAttachmentEnvelope,
    )

internal fun BridgeCore.encryptKakaoChatLogAttachment(
    encType: Int,
    plaintext: String,
    userId: Long,
    loadCompatibleCore: () -> Boolean,
    nativeEncrypt: (Int, String, Long) -> String,
): String =
    dispatchKakaoChatLogCrypto(
        unavailableMessage = "bridge core unavailable to encrypt Kakao chat log attachment",
        omittedMessage = "bridge core omitted encrypted Kakao chat log attachment",
        fieldName = "attachment",
        loadCompatibleCore = loadCompatibleCore,
    ) {
        nativeEncrypt(encType, plaintext, userId)
    }

internal fun BridgeCore.decryptKakaoChatLogAttachment(
    encType: Int,
    ciphertext: String,
    userId: Long,
): String =
    decryptKakaoChatLogAttachment(
        encType = encType,
        ciphertext = ciphertext,
        userId = userId,
        loadCompatibleCore = ::bridgeCoreLoadCompatibleLibraryOnce,
        nativeDecrypt = BridgeCoreJniKakaoLink::decryptKakaoChatLogAttachmentEnvelope,
    )

internal fun BridgeCore.decryptKakaoChatLogAttachment(
    encType: Int,
    ciphertext: String,
    userId: Long,
    loadCompatibleCore: () -> Boolean,
    nativeDecrypt: (Int, String, Long) -> String,
): String =
    dispatchKakaoChatLogCrypto(
        unavailableMessage = "bridge core unavailable to decrypt Kakao chat log attachment",
        omittedMessage = "bridge core omitted decrypted Kakao chat log attachment",
        fieldName = "plaintext",
        loadCompatibleCore = loadCompatibleCore,
    ) {
        nativeDecrypt(encType, ciphertext, userId)
    }

private fun dispatchKakaoChatLogCrypto(
    unavailableMessage: String,
    omittedMessage: String,
    fieldName: String,
    loadCompatibleCore: () -> Boolean,
    nativeValue: () -> String,
): String {
    if (!loadCompatibleCore()) {
        error(unavailableMessage)
    }
    val envelope =
        runCatching {
            BridgeCoreEnvelope.parse(nativeValue())
        }.getOrElse { error ->
            bridgeCoreLogError("bridge-core Kakao chat log crypto threw", error)
            error(unavailableMessage)
        }
    if (!envelope.isOk) {
        throw IllegalArgumentException(envelope.errorMessage ?: unavailableMessage)
    }
    return envelope.string(fieldName) ?: error(omittedMessage)
}
