package party.qwer.iris.imagebridge.runtime.send

import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.decryptKakaoChatLogAttachment
import party.qwer.iris.imagebridge.runtime.core.encryptKakaoChatLogAttachment

internal object KakaoChatLogAttachmentCrypto {
    fun encrypt(
        encType: Int,
        plaintext: String,
        userId: Long,
    ): String = BridgeCore.encryptKakaoChatLogAttachment(encType, plaintext, userId)

    fun decrypt(
        encType: Int,
        ciphertext: String,
        userId: Long,
    ): String = BridgeCore.decryptKakaoChatLogAttachment(encType, ciphertext, userId)
}
