package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
import org.json.JSONObject
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.kakao.memberfetch.UpstreamMemberProfile

private const val MEMBER_ENRICHMENT_UNAVAILABLE = "bridge core unavailable to enrich member snapshot"

internal data class MemberEnrichmentMergeResult(
    val members: List<ImageBridgeProtocol.ChatRoomMemberSnapshot>,
    val sourcePath: String?,
    val confidence: ImageBridgeProtocol.ChatRoomSnapshotConfidence,
    val confidenceScore: Int,
)

internal fun BridgeCore.memberEnrichmentMissingNicknames(
    members: List<ImageBridgeProtocol.ChatRoomMemberSnapshot>,
    expectedMemberHints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>,
    loadCompatibleCore: () -> Boolean = ::bridgeCoreLoadCompatibleLibraryOnce,
    nativeMissingNicknames: (String) -> String = BridgeCoreJniMemberField::nativeMemberEnrichmentMissingNicknames,
): List<Long> {
    if (members.isEmpty() && expectedMemberHints.isEmpty()) {
        return emptyList()
    }
    val envelope =
        memberEnrichmentEnvelope(loadCompatibleCore, nativeMissingNicknames) {
            JSONObject()
                .apply {
                    put("members", JSONArray(members.map(::memberNicknameJson)))
                    put("hints", JSONArray(expectedMemberHints.map(::hintJson)))
                }.toString()
        }
    val missing = envelope.getJSONArray("missingUserIds")
    return (0 until missing.length()).map(missing::getLong)
}

internal fun BridgeCore.memberEnrichmentMerge(
    snapshot: ImageBridgeProtocol.ChatRoomMembersSnapshot,
    upstreamProfiles: List<UpstreamMemberProfile>,
    loadCompatibleCore: () -> Boolean = ::bridgeCoreLoadCompatibleLibraryOnce,
    nativeMerge: (String) -> String = BridgeCoreJniMemberField::nativeMemberEnrichmentMerge,
): MemberEnrichmentMergeResult {
    if (upstreamProfiles.isEmpty()) {
        return MemberEnrichmentMergeResult(
            members = snapshot.members,
            sourcePath = snapshot.sourcePath,
            confidence = snapshot.confidence,
            confidenceScore = snapshot.confidenceScore,
        )
    }
    val envelope =
        memberEnrichmentEnvelope(loadCompatibleCore, nativeMerge) {
            JSONObject()
                .apply {
                    snapshot.sourcePath?.let { put("sourcePath", it) }
                    put("confidence", snapshot.confidence.name)
                    put("confidenceScore", snapshot.confidenceScore)
                    put("members", JSONArray(snapshot.members.map(::memberJson)))
                    put("upstreamProfiles", JSONArray(upstreamProfiles.map(::upstreamProfileJson)))
                }.toString()
        }
    return MemberEnrichmentMergeResult(
        members = memberSnapshots(envelope.getJSONArray("members")),
        sourcePath = envelope.stringOrNull("sourcePath"),
        confidence = snapshotConfidence(envelope.getString("confidence")),
        confidenceScore = envelope.getInt("confidenceScore"),
    )
}

private fun memberEnrichmentEnvelope(
    loadCompatibleCore: () -> Boolean,
    nativeValue: (String) -> String,
    requestJson: () -> String,
): JSONObject {
    if (!loadCompatibleCore()) {
        error(MEMBER_ENRICHMENT_UNAVAILABLE)
    }
    val envelope =
        runCatching {
            JSONObject(nativeValue(requestJson()))
        }.getOrElse { error ->
            bridgeCoreLogError("bridge-core member enrichment threw", error)
            error(MEMBER_ENRICHMENT_UNAVAILABLE)
        }
    if (!envelope.optBoolean("ok", false)) {
        throw IllegalArgumentException(
            envelope.optString("error").takeIf { it.isNotEmpty() }
                ?: "bridge core rejected member enrichment request",
        )
    }
    return envelope
}

private fun hintJson(hint: ImageBridgeProtocol.ChatRoomMemberHint): JSONObject =
    JSONObject().apply {
        put("userId", hint.userId)
        hint.nickname?.let { put("nickname", it) }
    }

private fun memberNicknameJson(member: ImageBridgeProtocol.ChatRoomMemberSnapshot): JSONObject =
    JSONObject().apply {
        put("userId", member.userId)
        put("nickname", member.nickname)
    }

private fun upstreamProfileJson(profile: UpstreamMemberProfile): JSONObject =
    JSONObject().apply {
        put("userId", profile.userId)
        put("nickname", profile.nickName)
        profile.profileImageUrl?.let { put("profileImageUrl", it) }
    }

private fun memberJson(member: ImageBridgeProtocol.ChatRoomMemberSnapshot): JSONObject =
    JSONObject().apply {
        put("userId", member.userId)
        put("nickname", member.nickname)
        member.roleCode?.let { put("roleCode", it) }
        member.profileImageUrl?.let { put("profileImageUrl", it) }
        member.mentionUserId?.let { put("mentionUserId", it) }
    }
