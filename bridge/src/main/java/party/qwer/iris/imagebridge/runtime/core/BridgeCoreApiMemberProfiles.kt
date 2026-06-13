package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
import org.json.JSONObject
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile

private const val MEMBER_PROFILE_USER_IDS_UNAVAILABLE = "bridge core unavailable to normalize member profile user ids"
private const val MEMBER_PROFILE_PAYLOAD_UNAVAILABLE = "bridge core unavailable to build member profile payload"

internal fun BridgeCore.memberProfileUserIds(
    memberIds: List<Long>,
    memberHints: List<ImageBridgeProtocol.ChatRoomMemberHint>,
    loadCompatibleCore: () -> Boolean = ::bridgeCoreLoadCompatibleLibraryOnce,
    nativeUserIds: (String) -> String = BridgeCoreJniMemberField::nativeMemberProfileUserIds,
): List<Long> {
    if (memberIds.isEmpty() && memberHints.isEmpty()) {
        return emptyList()
    }
    if (!loadCompatibleCore()) {
        error(MEMBER_PROFILE_USER_IDS_UNAVAILABLE)
    }
    val envelope =
        runCatching {
            JSONObject(nativeUserIds(memberProfileUserIdsRequestJson(memberIds, memberHints)))
        }.getOrElse { error ->
            bridgeCoreLogError("bridge-core member profile user id policy threw", error)
            error(MEMBER_PROFILE_USER_IDS_UNAVAILABLE)
        }
    if (!envelope.optBoolean("ok", false)) {
        throw IllegalArgumentException(
            envelope.optString("error").takeIf { it.isNotEmpty() }
                ?: "bridge core rejected member profile user id request",
        )
    }
    val userIds = envelope.getJSONArray("userIds")
    return (0 until userIds.length()).map(userIds::getLong)
}

internal fun BridgeCore.memberProfilePayloadJson(
    profiles: Collection<UpstreamMemberProfile>,
    loadCompatibleCore: () -> Boolean = ::bridgeCoreLoadCompatibleLibraryOnce,
    nativePayload: (String) -> String = BridgeCoreJniMemberField::nativeMemberProfilePayload,
): String {
    if (profiles.isEmpty()) {
        return """{"members":[]}"""
    }
    if (!loadCompatibleCore()) {
        error(MEMBER_PROFILE_PAYLOAD_UNAVAILABLE)
    }
    val envelope =
        runCatching {
            JSONObject(nativePayload(memberProfilePayloadRequestJson(profiles)))
        }.getOrElse { error ->
            bridgeCoreLogError("bridge-core member profile payload policy threw", error)
            error(MEMBER_PROFILE_PAYLOAD_UNAVAILABLE)
        }
    if (!envelope.optBoolean("ok", false)) {
        throw IllegalArgumentException(
            envelope.optString("error").takeIf { it.isNotEmpty() }
                ?: "bridge core rejected member profile payload request",
        )
    }
    return envelope.getString("payloadJson")
}

private fun memberProfileUserIdsRequestJson(
    memberIds: List<Long>,
    memberHints: List<ImageBridgeProtocol.ChatRoomMemberHint>,
): String =
    JSONObject()
        .put("memberIds", JSONArray(memberIds))
        .put("memberHints", JSONArray(memberHints.map(::memberProfileHintJson)))
        .toString()

private fun memberProfileHintJson(hint: ImageBridgeProtocol.ChatRoomMemberHint): JSONObject = JSONObject().put("userId", hint.userId)

private fun memberProfilePayloadRequestJson(profiles: Collection<UpstreamMemberProfile>): String =
    JSONObject()
        .put("profiles", JSONArray(profiles.map(::memberProfileJson)))
        .toString()

private fun memberProfileJson(profile: UpstreamMemberProfile): JSONObject =
    JSONObject()
        .put("userId", profile.userId)
        .put("nickname", profile.nickName)
        .apply {
            profile.profileImageUrl?.let { url -> put("profileImageUrl", url) }
        }
