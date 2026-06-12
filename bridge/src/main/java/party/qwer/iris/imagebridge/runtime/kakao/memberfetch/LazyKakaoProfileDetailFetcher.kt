package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

internal class LazyKakaoProfileDetailFetcher(
    private val discover: () -> ProfileDetailUpstream?,
) : ProfileDetailUpstream {
    constructor(kakaoClassLoader: ClassLoader) : this({
        discoverKakaoProfileDetailAccess(kakaoClassLoader)?.let(::KakaoProfileDetailFetcher)
    })

    private val lock = Any()

    @Volatile
    private var delegate: ProfileDetailUpstream? = null

    override fun fetchProfileDetail(
        chatId: Long,
        profile: UpstreamMemberProfile,
    ): UpstreamProfileDetail? = currentDelegate()?.fetchProfileDetail(chatId, profile)

    private fun currentDelegate(): ProfileDetailUpstream? {
        delegate?.let { return it }
        return synchronized(lock) {
            delegate ?: discover()?.also { delegate = it }
        }
    }
}
