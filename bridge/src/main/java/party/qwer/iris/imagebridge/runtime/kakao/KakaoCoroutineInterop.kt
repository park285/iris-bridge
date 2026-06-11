@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao

import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

internal fun Class<*>.isKotlinContinuationType(): Boolean = name == kotlinClassName("coroutines", "Continuation")

internal fun kotlinClassName(vararg segments: String): String = "kotlin." + segments.joinToString(".")

internal fun createKakaoContinuationArgument(
    continuationType: Class<*>,
    continuation: CancellableContinuation<Any?>,
    proxyLabel: String,
    failureLogPrefix: String,
): Any {
    if (!continuationType.isInterface && continuationType.isInstance(continuation)) {
        return continuation
    }
    val emptyContext = emptyCoroutineContextFor(continuationType)
    val handler =
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getContext" -> emptyContext
                "resumeWith" -> {
                    if (continuation.isActive) {
                        continuation.resume(extractKakaoResumeValue(args?.firstOrNull(), failureLogPrefix))
                    }
                    null
                }
                "toString" -> proxyLabel
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
    return Proxy.newProxyInstance(continuationType.classLoader, arrayOf(continuationType), handler)
}

internal fun Any?.isKakaoCoroutineSuspendedMarker(): Boolean =
    this === COROUTINE_SUSPENDED ||
        (
            this is Enum<*> &&
                javaClass.name == kotlinClassName("coroutines", "intrinsics", "CoroutineSingletons") &&
                name == "COROUTINE_SUSPENDED"
        )

private fun emptyCoroutineContextFor(continuationType: Class<*>): Any {
    val contextClass = Class.forName(kotlinClassName("coroutines", "EmptyCoroutineContext"), false, continuationType.classLoader)
    return listOf("C", "INSTANCE")
        .firstNotNullOfOrNull { fieldName ->
            runCatching {
                contextClass
                    .getDeclaredField(fieldName)
                    .apply { isAccessible = true }
                    .get(null)
            }.getOrNull()
        } ?: error("EmptyCoroutineContext singleton is null")
}

private fun extractKakaoResumeValue(
    result: Any?,
    failureLogPrefix: String,
): Any? {
    val value = result.unwrapBoxedKotlinResults()
    if (value?.javaClass?.name != kotlinClassName("Result\$Failure")) {
        return value
    }
    val exception =
        runCatching {
            value.javaClass
                .getDeclaredField("exception")
                .apply { isAccessible = true }
                .get(value)
        }.getOrNull()
    Log.w(KAKAO_CLASS_REGISTRY_TAG, "$failureLogPrefix suspend result failed: ${exception ?: value}")
    return null
}

private fun Any?.unwrapBoxedKotlinResults(): Any? {
    var value = this
    repeat(MAX_RESULT_UNWRAP_DEPTH) {
        if (value?.javaClass?.name != kotlinClassName("Result")) {
            return value
        }
        val unboxed =
            runCatching {
                value
                    ?.javaClass
                    ?.getDeclaredMethod("unbox-impl")
                    ?.apply { isAccessible = true }
                    ?.invoke(value)
            }.getOrDefault(value)
        if (unboxed === value) {
            return value
        }
        value = unboxed
    }
    return value
}

private const val MAX_RESULT_UNWRAP_DEPTH = 4
