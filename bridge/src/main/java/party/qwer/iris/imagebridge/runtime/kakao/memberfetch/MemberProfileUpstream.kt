package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

internal data class UpstreamMemberProfile(
    val userId: Long,
    val nickName: String,
    val profileImageUrl: String?,
)

internal fun interface MemberProfileUpstream {
    fun fetchMemberProfiles(
        chatId: Long,
        userIds: Collection<Long>,
    ): Map<Long, UpstreamMemberProfile>
}
