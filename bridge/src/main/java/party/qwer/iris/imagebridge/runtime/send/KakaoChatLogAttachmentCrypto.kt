package party.qwer.iris.imagebridge.runtime.send

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

// KakaoTalk chat log 호환 전용 AES-CBC이다. 새 암호화나 신뢰 경계에는 AEAD/HMAC 경로를 사용한다.
internal object KakaoChatLogAttachmentCrypto {
    fun encrypt(
        encType: Int,
        plaintext: String,
        userId: Long,
    ): String {
        val key = deriveAesKey(userId, encType)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(IV_BYTES))
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8)))
    }

    fun decrypt(
        encType: Int,
        ciphertext: String,
        userId: Long,
    ): String {
        val key = deriveAesKey(userId, encType)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(IV_BYTES))
        return String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8)
    }

    private fun deriveAesKey(
        userId: Long,
        encType: Int,
    ): ByteArray {
        val salt = generateSalt(userId, encType)
        return KakaoPkcs12KeyDerivation.deriveKey(KEY_BYTES, salt, iterations = 2, derivedKeySize = AES_KEY_SIZE)
    }

    private fun generateSalt(
        userId: Long,
        encType: Int,
    ): ByteArray {
        if (userId <= 0) return ByteArray(AES_BLOCK_SIZE)
        val prefix =
            when (encType) {
                0, 1 -> ""
                2, 7 -> "12"
                3 -> "24"
                4 -> "18"
                5 -> "30"
                6 -> "36"
                8 -> "48"
                9 -> "7"
                10 -> "35"
                11 -> "40"
                12 -> "17"
                13 -> "23"
                14 -> "29"
                15 -> "isabel"
                16 -> "kale"
                17 -> "sulli"
                18 -> "van"
                19 -> "merry"
                20 -> "kyle"
                21 -> "james"
                22 -> "maddux"
                23 -> "tony"
                24 -> "hayden"
                25 -> "paul"
                26 -> "elijah"
                27 -> "dorothy"
                28 -> "sally"
                29 -> "bran"
                31 -> "veil"
                else -> error("unsupported Kakao attachment encType=$encType")
            }
        val saltBytes = "$prefix$userId".toByteArray(StandardCharsets.UTF_8)
        val salt = ByteArray(AES_BLOCK_SIZE)
        System.arraycopy(saltBytes, 0, salt, 0, min(saltBytes.size, salt.size))
        return salt
    }

    private fun decodeHex(value: String): ByteArray {
        check(value.length % 2 == 0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private const val AES_BLOCK_SIZE = 16
    private const val AES_KEY_SIZE = 32
    private val KEY_BYTES = decodeHex("1608096f02172b0821210a1003030706")
    private val IV_BYTES = decodeHex("0f080100194725dc15f517e0e1150c35")
}
