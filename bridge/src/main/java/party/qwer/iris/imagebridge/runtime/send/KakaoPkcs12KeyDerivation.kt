package party.qwer.iris.imagebridge.runtime.send

import java.security.MessageDigest
import kotlin.math.min

// KakaoTalk legacy PKCS#12 KDF 호환 전용이다. 인증/서명/토큰/무결성 검증에는 SHA-1을 쓰지 않는다.
internal object KakaoPkcs12KeyDerivation {
    fun deriveKey(
        passwordBytes: ByteArray,
        saltBytes: ByteArray,
        iterations: Int,
        derivedKeySize: Int,
    ): ByteArray {
        val passwordUtf16Be = ByteArray((passwordBytes.size + 1) * 2)
        passwordBytes.forEachIndexed { index, byte -> passwordUtf16Be[index * 2 + 1] = byte }
        val diversifier = ByteArray(KDF_BLOCK_SIZE) { 1 }
        val saltBlock = repeatToBlock(saltBytes, KDF_BLOCK_SIZE)
        val passwordBlock = repeatToBlock(passwordUtf16Be, KDF_BLOCK_SIZE)
        val inputBlock = saltBlock + passwordBlock
        val blockCount = (derivedKeySize + SHA1_DIGEST_SIZE - 1) / SHA1_DIGEST_SIZE
        val derivedKey = ByteArray(derivedKeySize)
        val adjustment = ByteArray(KDF_BLOCK_SIZE)

        repeat(blockCount) { blockIndex ->
            var digest = sha1Digest(diversifier, inputBlock)
            repeat(iterations - 1) {
                digest = sha1Digest(digest, EMPTY_BYTES)
            }
            adjustment.indices.forEach { index -> adjustment[index] = digest[index % digest.size] }
            repeat(inputBlock.size / KDF_BLOCK_SIZE) { chunkIndex ->
                pkcs16Adjust(inputBlock, chunkIndex * KDF_BLOCK_SIZE, adjustment)
            }
            val start = blockIndex * SHA1_DIGEST_SIZE
            val copied = min(derivedKeySize - start, digest.size)
            System.arraycopy(digest, 0, derivedKey, start, copied)
        }

        return derivedKey
    }

    private fun sha1Digest(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(first)
        digest.update(second)
        return digest.digest()
    }

    private fun repeatToBlock(
        input: ByteArray,
        blockSize: Int,
    ): ByteArray {
        if (input.isEmpty() || blockSize == 0) return EMPTY_BYTES
        val outputLength = blockSize * ((input.size + blockSize - 1) / blockSize)
        return ByteArray(outputLength) { index -> input[index % input.size] }
    }

    private fun pkcs16Adjust(
        value: ByteArray,
        offset: Int,
        adjustment: ByteArray,
    ) {
        val lastIndex = adjustment.size - 1
        var carry = unsigned(adjustment[lastIndex]) + unsigned(value[offset + lastIndex]) + 1
        value[offset + lastIndex] = carry.toByte()
        carry = carry ushr 8
        for (index in lastIndex - 1 downTo 0) {
            carry += unsigned(adjustment[index]) + unsigned(value[offset + index])
            value[offset + index] = carry.toByte()
            carry = carry ushr 8
        }
    }

    private fun unsigned(value: Byte): Int = value.toInt() and 0xff

    private const val KDF_BLOCK_SIZE = 64
    private const val SHA1_DIGEST_SIZE = 20
    private val EMPTY_BYTES = ByteArray(0)
}
