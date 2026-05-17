package party.qwer.iris.imagebridge.runtime.karing

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val KAKAO_SSO_PROBE_URL = "https://sharer.kakao.com"
private const val KAKAO_TGT_HEADER = "KA-TGT"

internal class KaringSsoTgtRequester(
    private val classLoader: ClassLoader,
    private val timeoutMs: Long,
) {
    fun request(
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
}
