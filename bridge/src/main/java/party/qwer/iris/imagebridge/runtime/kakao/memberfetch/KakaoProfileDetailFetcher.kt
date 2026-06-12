package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.os.Looper
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.createKakaoContinuationArgument
import party.qwer.iris.imagebridge.runtime.kakao.isKakaoCoroutineSuspendedMarker
import java.lang.reflect.Method
import kotlin.coroutines.resume

internal class KakaoProfileDetailFetcher(
    private val access: KakaoProfileDetailAccess,
) : ProfileDetailUpstream {
    override fun fetchProfileDetail(
        chatId: Long,
        profile: UpstreamMemberProfile,
    ): UpstreamProfileDetail? {
        require(chatId > 0L) { "chatId must be positive" }
        val accessPermit = profile.accessPermit?.takeIf(String::isNotBlank) ?: return null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            error("profile detail fetch must not run on the main thread")
        }
        val detail =
            fetchProfileDetailObject("refresh", access.refreshOtherProfileMethod, profile.userId, accessPermit, chatId)
                ?: fetchProfileDetailObject("other", access.otherProfileMethod, profile.userId, accessPermit, chatId)
                ?: return null
        val profileImageUrl = profileDetailImageUrl(detail)
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "profile detail selected URL kind: ${profileImageUrl.kindLabel()}")
        return UpstreamProfileDetail(profileImageUrl = profileImageUrl)
    }

    private fun fetchProfileDetailObject(
        label: String,
        method: Method?,
        userId: Long,
        accessPermit: String,
        chatId: Long,
    ): Any? {
        method ?: return null
        val result =
            runBlocking {
                withTimeoutOrNull(PROFILE_DETAIL_FETCH_TIMEOUT_MS) {
                    invokeOtherProfile(method, userId, accessPermit, chatId)
                }
            }
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "profile detail $label result class: ${result?.javaClass?.name ?: "null"}")
        return result
    }

    private suspend fun invokeOtherProfile(
        method: Method,
        userId: Long,
        accessPermit: String,
        chatId: Long,
    ): Any? =
        suspendCancellableCoroutine { cont ->
            runCatching {
                method.isAccessible = true
                val result =
                    method.invoke(
                        access.profileApi,
                        userId,
                        accessPermit,
                        java.lang.Long.valueOf(chatId),
                        createKakaoContinuationArgument(
                            method.parameterTypes[3],
                            cont,
                            proxyLabel = "IrisProfileDetailContinuationProxy",
                            failureLogPrefix = "profile detail",
                        ),
                    )
                if (!result.isKakaoCoroutineSuspendedMarker() && cont.isActive) {
                    cont.resume(result)
                }
            }.onFailure { error ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "profile detail fetch failed: ${rootMessage(error)}")
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }

    private companion object {
        const val PROFILE_DETAIL_FETCH_TIMEOUT_MS = 5_000L
    }
}

private fun String?.kindLabel(): String =
    when {
        isNullOrBlank() -> "none"
        contains(".gif", ignoreCase = true) -> "gif"
        contains(".webp", ignoreCase = true) -> "webp"
        contains(".png", ignoreCase = true) -> "png"
        contains(".jpg", ignoreCase = true) || contains(".jpeg", ignoreCase = true) -> "jpeg"
        else -> "other"
    }
