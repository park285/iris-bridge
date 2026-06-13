package party.qwer.iris.imagebridge.runtime.server

import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.core.BridgeCore
import party.qwer.iris.imagebridge.runtime.core.memberProfilePayloadJson
import party.qwer.iris.imagebridge.runtime.core.memberProfileUserIds
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile

internal class BridgeMemberProfileActionHandler(
    private val memberProfileFetcher: ((Long, List<Long>) -> Map<Long, UpstreamMemberProfile>)?,
) {
    fun handle(request: ImageBridgeProtocol.ImageBridgeRequest): ImageBridgeProtocol.ImageBridgeResponse {
        val roomId = checkNotNull(request.roomId) { "roomId missing" }
        val fetcher = checkNotNull(memberProfileFetcher) { "member profile fetch unavailable" }
        val userIds = BridgeCore.memberProfileUserIds(request.memberIds, request.memberHints)
        val profiles = if (userIds.isEmpty()) emptyMap() else fetcher(roomId, userIds)
        return ImageBridgeProtocol.ImageBridgeResponse(
            status = ImageBridgeProtocol.STATUS_OK,
            payloadJson = BridgeCore.memberProfilePayloadJson(profiles.values),
        )
    }
}
