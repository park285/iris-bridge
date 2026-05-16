package party.qwer.iris.imagebridge.runtime.karing

import org.json.JSONObject
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val SSO_HELPER_CLASS = "com.kakao.talk.widget.webview.SsoHelper"
private const val SSO_TYPE_CLASS = "com.kakao.talk.widget.webview.SsoType"
private const val SSO_TYPE_KAKAO = "Kakao"
private const val KAKAO_E_HOME_URL = "https://e.kakao.com"
private const val KAKAO_TGT_HEADER = "KA-TGT"
private const val KAKAO_TGT_TIMEOUT_MS = 8_000L

internal class KaringAotBridgeProvider(
    private val classLoader: ClassLoader,
    private val timeoutMs: Long = KAKAO_TGT_TIMEOUT_MS,
) {
    fun isAvailable(): Boolean = runCatching { resolveBinding() }.isSuccess

    fun payloadJson(): String {
        val tgt = requestKaTgt()
        return JSONObject()
            .put("ka_tgt", tgt)
            .toString()
    }

    private fun requestKaTgt(): String {
        val binding = resolveBinding()
        val latch = CountDownLatch(1)
        val result = AtomicReference<String?>()
        val callback: (Any?) -> Unit =
            { payload: Any? ->
                result.set(extractKaTgt(payload))
                latch.countDown()
            }
        binding.getTgtIfNeed.invoke(binding.helper, binding.ssoType, KAKAO_E_HOME_URL, callback)
        check(latch.await(timeoutMs, TimeUnit.MILLISECONDS)) { "karing aot provider timed out" }
        return checkNotNull(result.get()) { "karing aot provider returned no KA-TGT" }
    }

    private fun resolveBinding(): Binding {
        val helperClass = Class.forName(SSO_HELPER_CLASS, false, classLoader)
        val ssoTypeClass = Class.forName(SSO_TYPE_CLASS, false, classLoader)
        val helper =
            checkNotNull(
                helperClass
                    .getDeclaredField("INSTANCE")
                    .apply { isAccessible = true }
                    .get(null),
            ) {
                "karing aot helper unavailable"
            }
        val ssoType =
            checkNotNull(ssoTypeClass.getMethod("valueOf", String::class.java).invoke(null, SSO_TYPE_KAKAO)) {
                "karing aot sso type unavailable"
            }
        val getTgtIfNeed = helperClass.getMethod("getTgtIfNeed", ssoTypeClass, String::class.java, Function1::class.java)
        return Binding(helper = helper, ssoType = ssoType, getTgtIfNeed = getTgtIfNeed)
    }

    private fun extractKaTgt(payload: Any?): String? {
        val map = payload as? Map<*, *> ?: return null
        return map[KAKAO_TGT_HEADER] as? String
    }

    private data class Binding(
        val helper: Any,
        val ssoType: Any,
        val getTgtIfNeed: Method,
    )
}
