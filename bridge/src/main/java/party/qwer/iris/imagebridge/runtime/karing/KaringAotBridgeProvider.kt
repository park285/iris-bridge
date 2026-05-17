package party.qwer.iris.imagebridge.runtime.karing

import org.json.JSONObject

private const val DEFAULT_TIMEOUT_MS = 8_000L

internal class KaringAotBridgeProvider(
    classLoader: ClassLoader,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val resolver = KaringAotBindingResolver(classLoader, timeoutMs)

    fun isAvailable(): Boolean = runCatching { resolver.resolve() }.isSuccess

    fun availabilityError(): String? =
        runCatching { resolver.resolve() }
            .exceptionOrNull()
            ?.let { error -> "${error::class.java.simpleName}: ${error.message ?: "no message"}" }

    fun payloadJson(): String {
        val binding = resolver.resolve()
        binding.refresh()
        val accessToken = binding.accessToken()
        val deviceId = binding.deviceId()
        check(accessToken.isNotBlank()) { "karing aot access token unavailable" }
        check(deviceId.isNotBlank()) { "karing aot device id unavailable" }
        val aot =
            JSONObject()
                .put("access_token", accessToken)
                .put("d_id", deviceId)
        return JSONObject()
            .put("aot", aot)
            .put("ka_tgt", binding.kaTgt())
            .toString()
    }
}
