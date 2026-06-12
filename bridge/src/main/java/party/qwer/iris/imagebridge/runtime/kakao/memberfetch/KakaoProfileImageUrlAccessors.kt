@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG

internal fun profileDetailImageUrl(detail: Any): String? {
    val profileImage = invokeObject(detail, PROFILE_DETAIL_IMAGE_METHODS) ?: return null
    return invokeProfileImageString(profileImage, PROFILE_DETAIL_IMAGE_URL_METHODS)?.takeIf(String::isNotBlank)
}

private fun invokeObject(
    target: Any,
    names: List<String>,
): Any? {
    for (name in names) {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: continue
        runCatching {
            method.apply { isAccessible = true }.invoke(target)?.let { return it }
        }.onFailure { throwable ->
            Log.w(KAKAO_CLASS_REGISTRY_TAG, "profile detail object accessor $name failed: ${throwable.message}")
        }
    }
    return null
}

private fun invokeProfileImageString(
    target: Any,
    names: List<String>,
): String? {
    for (name in names) {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: continue
        runCatching {
            val value = method.apply { isAccessible = true }.invoke(target)
            if (value is String && value.isNotBlank()) {
                return value
            }
        }.onFailure { throwable ->
            Log.w(KAKAO_CLASS_REGISTRY_TAG, "profile detail image accessor $name failed: ${throwable.message}")
        }
    }
    return null
}

private val PROFILE_DETAIL_IMAGE_METHODS = listOf("getProfileImage", "v")
private val PROFILE_DETAIL_IMAGE_URL_METHODS =
    listOf(
        "getOriginalAnimatedUrl",
        "d",
        "getMediumAnimatedUrl",
        "a",
        "getOriginalUrl",
        "e",
        "getMediumUrl",
        "b",
        "getThumbnailUrl",
        "f",
    )
