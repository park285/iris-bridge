package party.qwer.iris.imagebridge.runtime.server

import org.json.JSONArray
import org.json.JSONObject
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile

internal class BridgeMemberProfileActionHandler(
    private val memberProfileFetcher: ((Long, List<Long>) -> Map<Long, UpstreamMemberProfile>)?,
) {
    fun handle(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val fetcher = checkNotNull(memberProfileFetcher) { "member profile fetch unavailable" }
        val userIds = request.profileUserIds()
        val profiles = if (userIds.isEmpty()) emptyMap() else fetcher(roomId, userIds)
        return ImageBridgeProtocol.ImageBridgeResponse(
            status = ImageBridgeProtocol.STATUS_OK,
            payloadJson = memberProfilePayloadJson(profiles.values),
        )
    }
}

private fun ImageBridgeProtocol.ImageBridgeRequest.profileUserIds(): List<Long> =
    memberIds
        .ifEmpty { memberHints.map { hint -> hint.userId } }
        .filter { userId -> userId > 0L }
        .distinct()

private fun memberProfilePayloadJson(profiles: Collection<UpstreamMemberProfile>): String =
    JSONObject()
        .put(
            "members",
            JSONArray().apply {
                profiles
                    .sortedBy(UpstreamMemberProfile::userId)
                    .forEach { profile ->
                        put(
                            JSONObject()
                                .put("userId", profile.userId)
                                .put("nickname", profile.nickName)
                                .apply {
                                    profile.profileImageUrl?.let { url -> put("profileImageUrl", url) }
                                },
                        )
                    }
            },
        ).toString()
