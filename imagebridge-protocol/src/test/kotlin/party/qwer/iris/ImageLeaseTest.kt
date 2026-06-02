package party.qwer.iris

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageLeaseTest {
    private val secret = "image-lease-signing-secret"

    // Golden fixture mirrored verbatim from the native contract test
    // (native/iris-runtime/tests/image_lease_contract_test.rs).
    private val goldenCanonicalJson =
        "{" +
            "\"version\":1," +
            "\"requestId\":\"req-7\"," +
            "\"roomId\":42," +
            "\"imageIndex\":0," +
            "\"canonicalPath\":\"/data/iris-tmp/reply-images/req-7/image-0\"," +
            "\"sha256Hex\":\"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"," +
            "\"byteLength\":1024," +
            "\"contentType\":\"image/png\"," +
            "\"lastModifiedEpochMs\":1700000000000," +
            "\"expiresAtEpochMs\":1700000060000," +
            "\"nonce\":\"nonce-abc\"" +
            "}"

    private val goldenSignature = "ea44895c336fedee9efe5322b6fefbf3d7ebbdc5b24effb9221b3e5feda052e4"

    private fun goldenPayload() =
        ImageLeasePayload(
            version = 1,
            requestId = "req-7",
            roomId = 42,
            imageIndex = 0,
            canonicalPath = "/data/iris-tmp/reply-images/req-7/image-0",
            sha256Hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            byteLength = 1024,
            contentType = "image/png",
            lastModifiedEpochMs = 1_700_000_000_000,
            expiresAtEpochMs = 1_700_000_060_000,
            nonce = "nonce-abc",
        )

    @Test
    fun `canonical json matches the cross-language golden`() {
        assertEquals(goldenCanonicalJson, ImageLease.canonicalJson(goldenPayload()))
    }

    @Test
    fun `canonical json escapes strings with json rules`() {
        val payload = goldenPayload().copy(canonicalPath = "/a/\"b\"\\c\n\t")
        val canonical = ImageLease.canonicalJson(payload)
        assertTrue(
            canonical.contains("\"canonicalPath\":\"/a/\\\"b\\\"\\\\c\\n\\t\""),
            "string fields must use standard JSON escaping: $canonical",
        )
    }

    @Test
    fun `signature matches the cross-language golden`() {
        assertEquals(goldenSignature, ImageLease.sign(secret, goldenPayload()))
    }

    @Test
    fun `issued lease verifies before and at expiry`() {
        val lease = ImageLease.issue(secret, goldenPayload())
        assertEquals(goldenSignature, lease.signature)
        assertTrue(ImageLease.verify(secret, lease, lease.payload.expiresAtEpochMs - 1).valid)
        assertTrue(ImageLease.verify(secret, lease, lease.payload.expiresAtEpochMs).valid)
    }

    @Test
    fun `expired lease is rejected`() {
        val lease = ImageLease.issue(secret, goldenPayload())
        val result = ImageLease.verify(secret, lease, lease.payload.expiresAtEpochMs + 1)
        assertFalse(result.valid)
        assertEquals(ImageLeaseVerification.EXPIRED, result)
    }

    @Test
    fun `tampered payload is rejected`() {
        val lease = ImageLease.issue(secret, goldenPayload())
        val tampered =
            lease.copy(
                payload = lease.payload.copy(canonicalPath = "/data/iris-tmp/reply-images/req-7/evil"),
            )
        val result = ImageLease.verify(secret, tampered, tampered.payload.expiresAtEpochMs - 1)
        assertFalse(result.valid)
        assertEquals(ImageLeaseVerification.SIGNATURE_MISMATCH, result)
    }

    @Test
    fun `tampered signature is rejected`() {
        val lease = ImageLease.issue(secret, goldenPayload())
        val tampered = lease.copy(signature = "0".repeat(goldenSignature.length))
        assertFalse(ImageLease.verify(secret, tampered, lease.payload.expiresAtEpochMs - 1).valid)
    }

    @Test
    fun `wrong secret is rejected`() {
        val lease = ImageLease.issue(secret, goldenPayload())
        assertFalse(ImageLease.verify("other-secret", lease, lease.payload.expiresAtEpochMs - 1).valid)
    }

    @Test
    fun `distinct nonces produce distinct signatures`() {
        val first = goldenPayload().copy(nonce = "nonce-1")
        val second = goldenPayload().copy(nonce = "nonce-2")
        assertFalse(ImageLease.sign(secret, first) == ImageLease.sign(secret, second))
    }
}
