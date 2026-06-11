package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.createKakaoContinuationArgument
import party.qwer.iris.imagebridge.runtime.kakao.isKakaoCoroutineSuspendedMarker
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
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
                        createKakaoContinuationArgument(
                            method.parameterTypes[2],
                            cont,
                            proxyLabel = "IrisMemberFetchContinuationProxy",
                            failureLogPrefix = "member profile",
                        ),
                    )
                if (!result.isKakaoCoroutineSuspendedMarker() && cont.isActive) {
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
                        createKakaoContinuationArgument(
                            method.parameterTypes[1],
                            cont,
                            proxyLabel = "IrisMemberFetchContinuationProxy",
                            failureLogPrefix = "member profile",
                        ),
                    )
                if (!result.isKakaoCoroutineSuspendedMarker() && cont.isActive) {
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
