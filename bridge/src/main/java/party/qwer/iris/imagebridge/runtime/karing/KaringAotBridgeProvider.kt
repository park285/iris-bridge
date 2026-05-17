package party.qwer.iris.imagebridge.runtime.karing

import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val SSO_HELPER_CLASS = "com.kakao.talk.widget.webview.SsoHelper"
private const val SSO_TYPE_CLASS = "com.kakao.talk.widget.webview.SsoType"
private const val KOTLIN_FUNCTION1_CLASS = "kotlin.jvm.functions.Function1"
private const val SSO_TYPE_KAKAO = "Kakao"
private const val KAKAO_SSO_PROBE_URL = "https://sharer.kakao.com"
private const val KAKAO_TGT_HEADER = "KA-TGT"
private const val OAUTH_HELPER_CLASS = "yP.d"
private const val HARDWARE_CLASS = "IZ.V"
private const val REFRESH_CALLER = "iris-karing"
private const val DEFAULT_TIMEOUT_MS = 8_000L

internal class KaringAotBridgeProvider(
    private val classLoader: ClassLoader,
    @Suppress("UNUSED_PARAMETER")
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    fun isAvailable(): Boolean = runCatching { resolveBinding() }.isSuccess

    fun availabilityError(): String? =
        runCatching { resolveBinding() }
            .exceptionOrNull()
            ?.let { error -> "${error::class.java.simpleName}: ${error.message ?: "no message"}" }

    fun payloadJson(): String {
        val binding = resolveBinding()
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

    private fun resolveBinding(): Binding {
        val ssoHelperClass = bindingStep("load SsoHelper") { Class.forName(SSO_HELPER_CLASS, false, classLoader) }
        val ssoTypeClass = bindingStep("load SsoType") { Class.forName(SSO_TYPE_CLASS, false, classLoader) }
        val function1Class = bindingStep("load Function1") { Class.forName(KOTLIN_FUNCTION1_CLASS, false, classLoader) }
        val oauthClass = bindingStep("load OAuthHelper") { Class.forName(OAUTH_HELPER_CLASS, false, classLoader) }
        val hardwareClass = bindingStep("load Hardware") { Class.forName(HARDWARE_CLASS, false, classLoader) }
        val ssoHelper =
            bindingStep("resolve SsoHelper singleton") {
                checkNotNull(
                    ssoHelperClass
                        .getDeclaredField("INSTANCE")
                        .apply { isAccessible = true }
                        .get(null),
                ) {
                    "karing sso helper unavailable"
                }
            }
        val ssoType =
            bindingStep("resolve SsoType") {
                checkNotNull(ssoTypeClass.getMethod("valueOf", String::class.java).invoke(null, SSO_TYPE_KAKAO)) {
                    "karing sso type unavailable"
                }
            }
        val oauthHelper =
            bindingStep("resolve OAuthHelper singleton") {
                checkNotNull(
                    oauthClass
                        .getDeclaredField("a")
                        .apply { isAccessible = true }
                        .get(null),
                ) {
                    "karing oauth helper unavailable"
                }
            }
        val hardware =
            bindingStep("resolve Hardware singleton") {
                checkNotNull(
                    hardwareClass
                        .getDeclaredField("a")
                        .apply { isAccessible = true }
                        .get(null),
                ) {
                    "karing hardware helper unavailable"
                }
            }
        val accessToken =
            bindingStep("resolve OAuthHelper access token") {
                oauthClass.getMethod("e").apply { isAccessible = true }
            }
        val refresh =
            bindingStep("resolve OAuthHelper refresh") {
                oauthClass.getMethod("u", String::class.java).apply { isAccessible = true }
            }
        val deviceId =
            bindingStep("resolve Hardware device id") {
                hardwareClass.getMethod("u").apply { isAccessible = true }
            }
        val getTgtIfNeed =
            bindingStep("resolve SsoHelper getTgtIfNeed") {
                ssoHelperClass.getMethod("getTgtIfNeed", ssoTypeClass, String::class.java, function1Class)
            }
        return Binding(
            oauthHelper = oauthHelper,
            hardware = hardware,
            refresh = { refresh.invoke(oauthHelper, REFRESH_CALLER) },
            accessToken = { accessToken.invoke(oauthHelper) as? String ?: "" },
            deviceId = { deviceId.invoke(hardware) as? String ?: "" },
            kaTgt = { requestKaTgt(ssoHelper, ssoType, getTgtIfNeed, function1Class) },
        )
    }

    private fun <T> bindingStep(
        name: String,
        block: () -> T,
    ): T =
        runCatching(block).getOrElse { error ->
            throw IllegalStateException("$name failed: ${error::class.java.simpleName}: ${error.message}", error)
        }

    private fun requestKaTgt(
        ssoHelper: Any,
        ssoType: Any,
        getTgtIfNeed: Method,
        function1Class: Class<*>,
    ): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String?>()
        val callback =
            Proxy.newProxyInstance(classLoader, arrayOf(function1Class)) { _, method, args ->
                if (method.name != "invoke") {
                    return@newProxyInstance null
                }
                val payload = args?.firstOrNull()
                result.set(extractKaTgt(payload))
                latch.countDown()
                kotlin.Unit
            }
        getTgtIfNeed.invoke(ssoHelper, ssoType, KAKAO_SSO_PROBE_URL, callback)
        check(latch.await(timeoutMs, TimeUnit.MILLISECONDS)) { "karing sso provider timed out" }
        return checkNotNull(result.get()) { "karing sso provider returned no KA-TGT" }
    }

    private fun extractKaTgt(payload: Any?): String? {
        val map = payload as? Map<*, *> ?: return null
        return map[KAKAO_TGT_HEADER] as? String
    }

    private data class Binding(
        val oauthHelper: Any,
        val hardware: Any,
        val refresh: () -> Unit,
        val accessToken: () -> String,
        val deviceId: () -> String,
        val kaTgt: () -> String,
    )
}
