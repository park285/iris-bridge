package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

internal class KakaoMemberFetchInvoker(
    private val access: KakaoMemberFetchAccess,
) {
    suspend fun invokeFetchMembers(
        method: Method,
        chatId: Long,
        userIds: List<Long>,
    ): Any? {
        method.isAccessible = true
        return when {
            method.isRequestedSuspendFetchMembersMethod() -> invokeSuspendRequestedMembers(method, chatId, userIds)
            method.isRoomSuspendFetchMembersMethod() -> invokeSuspendRoomMembers(method, chatId)
            else ->
                runCatching {
                    method.invoke(access.clientSingleton, chatId, userIds)
                }.getOrElse { error ->
                    Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile fetch failed: ${rootMessage(error)}")
                    null
                }
        }
    }

    private suspend fun invokeSuspendRequestedMembers(
        method: Method,
        chatId: Long,
        userIds: List<Long>,
    ): Any? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                val result =
                    method.invoke(
                        access.clientSingleton,
                        chatId,
                        userIds,
                        createContinuationArgument(method.parameterTypes[2], cont),
                    )
                if (!result.isCoroutineSuspendedMarker() && cont.isActive) {
                    cont.resume(result)
                }
            }.onFailure { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile suspend fetch failed: ${rootMessage(error)}")
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }

    private suspend fun invokeSuspendRoomMembers(
        method: Method,
        chatId: Long,
    ): Any? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                val result =
                    method.invoke(
                        access.clientSingleton,
                        chatId,
                        createContinuationArgument(method.parameterTypes[1], cont),
                    )
                if (!result.isCoroutineSuspendedMarker() && cont.isActive) {
                    cont.resume(result)
                }
            }.onFailure { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile suspend fetch failed: ${rootMessage(error)}")
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }
}

private fun createContinuationArgument(
    continuationType: Class<*>,
    continuation: CancellableContinuation<Any?>,
): Any {
    if (continuationType.isInstance(continuation)) {
        return continuation
    }
    val emptyContext = emptyCoroutineContextFor(continuationType)
    val handler =
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getContext" -> emptyContext
                "resumeWith" -> {
                    if (continuation.isActive) {
                        continuation.resume(extractResumeValue(args?.firstOrNull()))
                    }
                    null
                }
                "toString" -> "IrisMemberFetchContinuationProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
    return Proxy.newProxyInstance(continuationType.classLoader, arrayOf(continuationType), handler)
}

private fun Any?.isCoroutineSuspendedMarker(): Boolean =
    this === COROUTINE_SUSPENDED ||
        (
            this is Enum<*> &&
                javaClass.name == kotlinClassName("coroutines", "intrinsics", "CoroutineSingletons") &&
                name == "COROUTINE_SUSPENDED"
        )

private fun emptyCoroutineContextFor(continuationType: Class<*>): Any {
    val contextClass = Class.forName(kotlinClassName("coroutines", "EmptyCoroutineContext"), false, continuationType.classLoader)
    return contextClass
        .getDeclaredField("C")
        .apply { isAccessible = true }
        .get(null)
        ?: error("EmptyCoroutineContext.C is null")
}

private fun extractResumeValue(result: Any?): Any? {
    if (result?.javaClass?.name != kotlinClassName("Result\$Failure")) {
        return result
    }
    val exception =
        runCatching {
            result.javaClass
                .getDeclaredField("exception")
                .apply { isAccessible = true }
                .get(result)
        }.getOrNull()
    Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile suspend result failed: ${exception ?: result}")
    return null
}

internal fun rootMessage(error: Throwable): String {
    val root = (error as? InvocationTargetException)?.targetException ?: error
    val locations =
        root.stackTrace
            .take(6)
            .joinToString(separator = " <- ", prefix = " at ") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }.takeIf(String::isNotBlank)
            .orEmpty()
    return "${root.javaClass.name}: ${root.message.orEmpty()}$locations"
}
