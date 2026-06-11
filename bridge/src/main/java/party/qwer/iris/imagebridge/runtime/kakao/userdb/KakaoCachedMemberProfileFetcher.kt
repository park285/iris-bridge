package party.qwer.iris.imagebridge.runtime.kakao.userdb

import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.MemberProfileUpstream
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile

internal class KakaoCachedMemberProfileFetcher(
    private val baseFetcher: MemberProfileUpstream,
    private val userDbReader: KakaoUserDatabaseReader,
) : MemberProfileUpstream {
    override fun fetchMemberProfiles(
        chatId: Long,
        userIds: Collection<Long>,
    ): Map<Long, UpstreamMemberProfile> {
        require(chatId > 0L) { "chatId must be positive" }
        if (userIds.isEmpty()) return emptyMap()
        val deduped = userIds.filter { it > 0L }.distinct()
        if (deduped.isEmpty()) return emptyMap()

        baseFetcher.fetchMemberProfiles(chatId, deduped)

        return userDbReader
            .readNicknames(deduped)
            .map { (userId, nickName) ->
                userId to UpstreamMemberProfile(userId = userId, nickName = nickName, profileImageUrl = null)
            }.toMap()
    }
}
