@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG

internal fun memberObjectToProfile(member: Any): UpstreamMemberProfile? {
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
        "Jr.i" -> JR_MEMBER_USER_ID_METHODS
        else -> DEFAULT_USER_ID_METHODS
    }

private fun nicknameMethodsFor(member: Any): List<String> =
    when (member.javaClass.name) {
        "cq.i" -> CQ_MEMBER_NICKNAME_METHODS
        "Qr.r" -> QR_MEMBER_NICKNAME_METHODS
        "Jr.i" -> JR_MEMBER_NICKNAME_METHODS
        else -> DEFAULT_NICKNAME_METHODS
    }

private fun profileUrlMethodsFor(member: Any): List<String> =
    when (member.javaClass.name) {
        "cq.i" -> CQ_MEMBER_PROFILE_URL_METHODS
        "Qr.r" -> QR_MEMBER_PROFILE_URL_METHODS
        "Jr.i" -> JR_MEMBER_PROFILE_URL_METHODS
        else -> DEFAULT_PROFILE_URL_METHODS
    }

private val CQ_MEMBER_USER_ID_METHODS = listOf("getUserId", "n")
private val CQ_MEMBER_NICKNAME_METHODS = listOf("getNickName", "f")
private val CQ_MEMBER_PROFILE_URL_METHODS = listOf("getProfileUrl", "j", "d", "g")
private val QR_MEMBER_USER_ID_METHODS = listOf("getUserId", "e")
private val QR_MEMBER_NICKNAME_METHODS = listOf("getNickName", "g")
private val QR_MEMBER_PROFILE_URL_METHODS = listOf("getProfileUrl", "i", "d", "h")
private val JR_MEMBER_USER_ID_METHODS = listOf("getUserId", "n")
private val JR_MEMBER_NICKNAME_METHODS = listOf("getNickName", "f")
private val JR_MEMBER_PROFILE_URL_METHODS = listOf("getProfileUrl", "j", "d", "g")
private val DEFAULT_USER_ID_METHODS = listOf("getUserId", "n", "e")
private val DEFAULT_NICKNAME_METHODS = listOf("getNickName", "f", "g")
private val DEFAULT_PROFILE_URL_METHODS = listOf("getProfileUrl", "j", "i", "d", "h", "g")
