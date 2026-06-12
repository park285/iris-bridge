package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.os.Looper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import java.lang.reflect.Method

internal class KakaoMemberProfileFetcher(
    private val access: KakaoMemberFetchAccess,
    private val profileDetailUpstream: ProfileDetailUpstream? = null,
) : MemberProfileUpstream {
    private val invoker = KakaoMemberFetchInvoker(access)
    private val parser = KakaoMemberProfileParser(access)

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
        val profiles =
            if (primaryProfiles.size == deduped.size || access.roomFetchMembersMethod == null) {
                primaryProfiles
            } else {
                val missingUserIds = deduped.filterNot(primaryProfiles::containsKey)
                primaryProfiles + fetchRoom(access.roomFetchMembersMethod, chatId, missingUserIds.toSet())
            }
        return enrichWithProfileDetails(chatId, profiles)
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
                    invoker.invokeFetchMembers(method, chatId, emptyList())
                }
            } ?: return emptyMap()
        return parser.parseProfiles(rawResult, wantedUserIds)
    }

    private fun fetchChunk(
        method: Method,
        chatId: Long,
        userIds: List<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        val rawResult =
            runBlocking {
                withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    invoker.invokeFetchMembers(method, chatId, userIds.toList())
                }
            } ?: return emptyMap()
        return parser.parseProfiles(rawResult, userIds.toSet())
    }

    private fun enrichWithProfileDetails(
        chatId: Long,
        profiles: Map<Long, UpstreamMemberProfile>,
    ): Map<Long, UpstreamMemberProfile> {
        val detailUpstream = profileDetailUpstream ?: return profiles
        if (profiles.isEmpty()) return profiles
        var missingAccessPermit = 0
        var attempted = 0
        var selected = 0
        val enriched =
            profiles.mapValues { (_, profile) ->
                if (profile.accessPermit.isNullOrBlank()) {
                    missingAccessPermit++
                    profile
                } else {
                    attempted++
                    val detail =
                        runCatching { detailUpstream.fetchProfileDetail(chatId, profile) }
                            .getOrElse { error ->
                                android.util.Log.w(KAKAO_CLASS_REGISTRY_TAG, "profile detail enrichment failed: ${rootMessage(error)}")
                                null
                            }
                    val detailUrl = detail?.profileImageUrl?.takeIf(String::isNotBlank)
                    if (detailUrl == null) {
                        profile
                    } else {
                        selected++
                        profile.copy(profileImageUrl = detailUrl)
                    }
                }
            }
        android.util.Log.i(
            KAKAO_CLASS_REGISTRY_TAG,
            "profile detail enrichment summary: profiles=${profiles.size} attempted=$attempted selected=$selected missingAccessPermit=$missingAccessPermit",
        )
        return enriched
    }

    private companion object {
        const val MAX_MEMBER_IDS_PER_REQUEST = 500
        const val FETCH_TIMEOUT_MS = 5_000L
    }
}
