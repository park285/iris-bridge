package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.os.Looper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Method

internal class KakaoMemberProfileFetcher(
    private val access: KakaoMemberFetchAccess,
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

    private companion object {
        const val MAX_MEMBER_IDS_PER_REQUEST = 500
        const val FETCH_TIMEOUT_MS = 5_000L
    }
}
