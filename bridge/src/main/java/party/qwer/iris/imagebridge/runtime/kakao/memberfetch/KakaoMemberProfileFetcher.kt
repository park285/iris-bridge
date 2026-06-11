@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.os.Looper
import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG

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
        val profiles = linkedMapOf<Long, UpstreamMemberProfile>()
        deduped.chunked(MAX_MEMBER_IDS_PER_REQUEST).forEach { chunk ->
            profiles.putAll(fetchChunk(chatId, chunk))
        }
        return profiles
    }

    private fun fetchChunk(
        chatId: Long,
        userIds: List<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        val boxedIds = userIds.toList()
        val rawResult =
            access.fetchMembersMethod
                .apply {
                    isAccessible = true
                }.invoke(access.clientSingleton, chatId, boxedIds)
        access.unwrapErrorMethod
            .apply { isAccessible = true }
            .invoke(null, rawResult)
            ?.let { error("upstream member fetch failed: $it") }
        val response =
            access.unwrapValueMethod
                .apply { isAccessible = true }
                .invoke(null, rawResult)
                ?: return emptyMap()
        return extractMembers(response)
            .mapNotNull(::toProfile)
            .associateBy(UpstreamMemberProfile::userId)
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
        val userId = invokeLong(member, USER_ID_METHODS) ?: return null
        val nickName = invokeString(member, NICKNAME_METHODS)?.trim().orEmpty()
        if (nickName.isEmpty()) {
            return null
        }
        return UpstreamMemberProfile(
            userId = userId,
            nickName = nickName,
            profileImageUrl = invokeString(member, PROFILE_URL_METHODS)?.takeIf(String::isNotBlank),
        )
    }

    private fun invokeLong(
        target: Any,
        names: Set<String>,
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
        names: Set<String>,
    ): String? {
        for (name in names) {
            val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 } ?: continue
            runCatching {
                return method.apply { isAccessible = true }.invoke(target) as? String
            }.onFailure { throwable ->
                Log.w(KAKAO_CLASS_REGISTRY_TAG, "member profile string accessor $name failed: ${throwable.message}")
            }
        }
        return null
    }

    private companion object {
        const val MAX_MEMBER_IDS_PER_REQUEST = 500
        val USER_ID_METHODS = setOf("getUserId", "n")
        val NICKNAME_METHODS = setOf("getNickName", "f")
        val PROFILE_URL_METHODS = setOf("getProfileUrl", "j")
    }
}
