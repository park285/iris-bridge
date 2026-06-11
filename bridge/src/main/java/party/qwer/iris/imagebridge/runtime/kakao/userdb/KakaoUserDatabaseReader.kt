@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.userdb

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

internal class KakaoUserDatabaseReader(
    private val access: KakaoUserDatabaseAccess,
) {
    fun readNicknames(userIds: Collection<Long>): Map<Long, String> {
        if (userIds.isEmpty()) return emptyMap()
        val deduped = userIds.filter { it > 0L }.distinct()
        if (deduped.isEmpty()) return emptyMap()
        return runBlocking {
            withTimeoutOrNull(READ_TIMEOUT_MS) {
                deduped
                    .mapNotNull { userId ->
                        val userObj = getUserByIdSuspend(userId) ?: return@mapNotNull null
                        val nickname = extractNickname(userObj)?.trim().orEmpty()
                        if (nickname.isEmpty()) return@mapNotNull null
                        userId to nickname
                    }.toMap()
            } ?: emptyMap()
        }
    }

    private suspend fun getUserByIdSuspend(userId: Long): Any? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                val result =
                    access.getUserByIdV2Method
                        .apply { isAccessible = true }
                        .invoke(access.singleton, userId, cont)
                if (result !== COROUTINE_SUSPENDED && cont.isActive) {
                    cont.resume(result)
                }
            }.onFailure { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "getUserByIdV2($userId) failed: ${error.message}")
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }

    private fun extractNickname(userObj: Any): String? {
        for (name in NICKNAME_METHODS) {
            val method =
                userObj.javaClass.methods.firstOrNull { method ->
                    method.name == name && method.parameterCount == 0
                } ?: continue
            runCatching {
                return method.apply { isAccessible = true }.invoke(userObj) as? String
            }.onFailure { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "user nickname accessor $name failed: ${error.message}")
            }
        }
        for (name in NICKNAME_FIELDS) {
            val field =
                userObj.javaClass.declaredFields.firstOrNull { field ->
                    field.name == name
                } ?: continue
            runCatching {
                return field.apply { isAccessible = true }.get(userObj) as? String
            }.onFailure { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "user nickname field $name failed: ${error.message}")
            }
        }
        return null
    }

    private companion object {
        const val READ_TIMEOUT_MS = 2_000L
        val NICKNAME_METHODS = listOf("getNickName", "f", "nickname")
        val NICKNAME_FIELDS = listOf("nickname", "f")
    }
}
