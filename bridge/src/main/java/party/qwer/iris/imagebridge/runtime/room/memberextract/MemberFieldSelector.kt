package party.qwer.iris.imagebridge.runtime.room.memberextract

internal class MemberFieldSelector {
    fun selectUserIdPath(
        views: List<ElementView>,
        expectedMemberIds: Set<Long>,
    ): ScoredPath? = selectUserIdPathFromViews(views, expectedMemberIds)

    fun selectNicknamePath(
        views: List<ElementView>,
        userPath: String,
        expectedNicknames: Map<Long, String>,
    ): ScoredPath? = selectNicknamePathFromViews(views, userPath, expectedNicknames)

    fun selectProfileImagePath(views: List<ElementView>): ScoredPath? = selectProfileImagePathFromViews(views)

    fun selectRolePath(views: List<ElementView>): ScoredPath? = selectRolePathFromViews(views)

    fun selectMentionUserIdPath(
        views: List<ElementView>,
        userPath: String,
        nicknamePath: String,
    ): ScoredPath? = selectMentionUserIdPathFromViews(views, userPath, nicknamePath)

    fun parseRoleCode(value: PrimitiveValue?): Int? =
        party.qwer.iris.imagebridge.runtime.room.memberextract
            .parseRoleCode(value)

    fun looksLikeNickname(value: String): Boolean =
        party.qwer.iris.imagebridge.runtime.room.memberextract
            .looksLikeNickname(value)

    fun looksLikeProfileUrl(value: String): Boolean =
        party.qwer.iris.imagebridge.runtime.room.memberextract
            .looksLikeProfileUrl(value)

    fun primitiveLongValue(value: PrimitiveValue?): Long? =
        party.qwer.iris.imagebridge.runtime.room.memberextract
            .primitiveLongValue(value)

    fun pathHintScore(
        path: String,
        preferredTokens: Set<String>,
        discouragedTokens: Set<String>,
    ): Int =
        party.qwer.iris.imagebridge.runtime.room.memberextract
            .pathHintScore(path, preferredTokens, discouragedTokens)

    fun looksLikeMentionUserIdValue(
        value: String,
        userId: Long?,
        nickname: String?,
    ): Boolean =
        party.qwer.iris.imagebridge.runtime.room.memberextract
            .looksLikeMentionUserIdValue(value, userId, nickname)
}
