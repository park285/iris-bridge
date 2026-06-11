@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

internal class KakaoMemberProfileFetcher(
    private val access: KakaoMemberFetchAccess,
) : MemberProfileUpstream {
    override fun fetchMemberProfiles(
        chatId: Long,
        userIds: Collection<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        require(chatId > 0L) { "chatId must be positive" }
        if (userIds.isEmpty()) {
            return emptyMap()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            error("member profile fetch must not run on the main thread")
        }
        val deduped = userIds.filter { it > 0L }.distinct()
        if (deduped.isEmpty()) {
            return emptyMap()
        }
        val primaryProfiles = fetchWithMethod(access.fetchMembersMethod, chatId, deduped)
        if (primaryProfiles.size == deduped.size || access.roomFetchMembersMethod == null) {
            return primaryProfiles
        }
        val missingUserIds = deduped.filterNot(primaryProfiles::containsKey)
        return primaryProfiles + fetchRoom(access.roomFetchMembersMethod, chatId, missingUserIds.toSet())
    }

    private fun fetchWithMethod(
        method: Method,
        chatId: Long,
        deduped: List<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        if (method.isRoomSuspendFetchMembersMethod()) {
            return fetchRoom(method, chatId, deduped.toSet())
        }
        val profiles = linkedMapOf<Long, UpstreamMemberProfile>()
        deduped.chunked(MAX_MEMBER_IDS_PER_REQUEST).forEach { chunk ->
            profiles.putAll(fetchChunk(method, chatId, chunk))
        }
        return profiles
    }

    private fun fetchRoom(
        method: Method,
        chatId: Long,
        wantedUserIds: Set<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        val rawResult =
            runBlocking {
                withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    invokeFetchMembers(method, chatId, emptyList())
                }
            } ?: return emptyMap()
        return parseProfiles(rawResult, wantedUserIds)
    }

    private fun fetchChunk(
        method: Method,
        chatId: Long,
        userIds: List<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        val rawResult =
            runBlocking {
                withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    invokeFetchMembers(method, chatId, userIds.toList())
                }
            } ?: return emptyMap()
        return parseProfiles(rawResult, userIds.toSet())
    }

    private fun parseProfiles(
        rawResult: Any,
        wantedUserIds: Set<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        val resultPayload = unwrapLocoResultPayload(rawResult) ?: return emptyMap()
        access.unwrapErrorMethod
            .apply { isAccessible = true }
            .invoke(null, resultPayload)
            ?.let { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "upstream member fetch failed: $error")
                return emptyMap()
            }
        val response =
            access.unwrapValueMethod
                .apply { isAccessible = true }
                .invoke(null, resultPayload)
                ?: return emptyMap()
        return runCatching {
            extractMembers(response)
                .mapNotNull(::toProfile)
                .filter { profile -> profile.userId in wantedUserIds }
                .associateBy(UpstreamMemberProfile::userId)
        }.getOrElse { error ->
            Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile parse failed: ${rootMessage(error)}")
            emptyMap()
        }
    }

    private fun unwrapLocoResultPayload(rawResult: Any): Any? {
        if (!access.resultClass.isInstance(rawResult)) {
            return rawResult
        }
        val payloadMethod =
            rawResult.javaClass.methods.singleOrNull { method ->
                method.name == "j" &&
                    method.parameterCount == 0 &&
                    method.returnType == Any::class.java
            } ?: return rawResult
        return payloadMethod.apply { isAccessible = true }.invoke(rawResult)
    }

    private suspend fun invokeFetchMembers(
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

    private fun Method.isRoomSuspendFetchMembersMethod(): Boolean =
        parameterCount == 2 &&
            parameterTypes[1].isKotlinContinuationType()

    private fun Method.isRequestedSuspendFetchMembersMethod(): Boolean =
        parameterCount == 3 &&
            List::class.java.isAssignableFrom(parameterTypes[1]) &&
            parameterTypes[2].isKotlinContinuationType()

    private fun createContinuationArgument(
        continuationType: Class<*>,
        continuation: CancellableContinuation<Any?>,
    ): Any {
        if (continuationType.isInstance(continuation)) {
            return continuation
        }
        val emptyContext =
            emptyCoroutineContextFor(continuationType)
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

    private fun kotlinClassName(vararg segments: String): String = ("kotlin." + segments.joinToString("."))

    private fun rootMessage(error: Throwable): String {
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

    private fun extractMembers(response: Any): List<Any> {
        val listMethod =
            response.javaClass.methods.firstOrNull { method ->
                method.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(method.returnType)
            } ?: error("member fetch response has no member list accessor on ${response.javaClass.name}")
        @Suppress("UNCHECKED_CAST")
        return listMethod.apply { isAccessible = true }.invoke(response) as? List<Any> ?: emptyList()
    }

    private fun toProfile(member: Any): UpstreamMemberProfile? {
        val userId = invokeLong(member, userIdMethodsFor(member)) ?: return null
        val nickName = invokeString(member, nicknameMethodsFor(member))?.trim().orEmpty()
        if (nickName.isEmpty()) {
            return null
        }
        return UpstreamMemberProfile(
            userId = userId,
            nickName = nickName,
            profileImageUrl = invokeString(member, profileUrlMethodsFor(member))?.takeIf(String::isNotBlank),
        )
    }

    private fun invokeLong(
        target: Any,
        names: List<String>,
    ): Long? {
        for (name in names) {
            val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: continue
            runCatching {
                when (val value = method.apply { isAccessible = true }.invoke(target)) {
                    is Long -> return value
                    is Number -> return value.toLong()
                }
            }.onFailure { throwable ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile long accessor $name failed: ${throwable.message}")
            }
        }
        return null
    }

    private fun invokeString(
        target: Any,
        names: List<String>,
    ): String? {
        for (name in names) {
            val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: continue
            runCatching {
                unwrapStringValue(method.apply { isAccessible = true }.invoke(target))
                    ?.takeIf(String::isNotBlank)
                    ?.let { return it }
            }.onFailure { throwable ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile string accessor $name failed: ${throwable.message}")
            }
        }
        return null
    }

    private fun unwrapStringValue(value: Any?): String? {
        if (value is String) {
            return value
        }
        val optionalValue =
            value
                ?.javaClass
                ?.methods
                ?.firstOrNull { method ->
                    method.name == "a" &&
                        method.parameterCount == 0 &&
                        method.returnType == Any::class.java
                } ?: return null
        return runCatching {
            optionalValue.apply { isAccessible = true }.invoke(value) as? String
        }.getOrNull()
    }

    private fun userIdMethodsFor(member: Any): List<String> =
        when (member.javaClass.name) {
            "cq.i" -> CQ_MEMBER_USER_ID_METHODS
            "Qr.r" -> QR_MEMBER_USER_ID_METHODS
            else -> DEFAULT_USER_ID_METHODS
        }

    private fun nicknameMethodsFor(member: Any): List<String> =
        when (member.javaClass.name) {
            "cq.i" -> CQ_MEMBER_NICKNAME_METHODS
            "Qr.r" -> QR_MEMBER_NICKNAME_METHODS
            else -> DEFAULT_NICKNAME_METHODS
        }

    private fun profileUrlMethodsFor(member: Any): List<String> =
        when (member.javaClass.name) {
            "cq.i" -> CQ_MEMBER_PROFILE_URL_METHODS
            "Qr.r" -> QR_MEMBER_PROFILE_URL_METHODS
            else -> DEFAULT_PROFILE_URL_METHODS
        }

    private companion object {
        const val MAX_MEMBER_IDS_PER_REQUEST = 500
        const val FETCH_TIMEOUT_MS = 5_000L
        val CQ_MEMBER_USER_ID_METHODS = listOf("getUserId", "n")
        val CQ_MEMBER_NICKNAME_METHODS = listOf("getNickName", "f")
        val CQ_MEMBER_PROFILE_URL_METHODS = listOf("getProfileUrl", "j", "d", "g")
        val QR_MEMBER_USER_ID_METHODS = listOf("getUserId", "e")
        val QR_MEMBER_NICKNAME_METHODS = listOf("getNickName", "g")
        val QR_MEMBER_PROFILE_URL_METHODS = listOf("getProfileUrl", "i", "d", "h")
        val DEFAULT_USER_ID_METHODS = listOf("getUserId", "n", "e")
        val DEFAULT_NICKNAME_METHODS = listOf("getNickName", "f", "g")
        val DEFAULT_PROFILE_URL_METHODS = listOf("getProfileUrl", "j", "i", "d", "h", "g")
    }
}
