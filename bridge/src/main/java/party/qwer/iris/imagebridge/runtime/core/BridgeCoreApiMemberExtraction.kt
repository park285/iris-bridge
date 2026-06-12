package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONArray
import org.json.JSONObject
import party.qwer.iris.ImageBridgeProtocol
import party.qwer.iris.imagebridge.runtime.room.memberextract.ElementView
import party.qwer.iris.imagebridge.runtime.room.memberextract.PrimitiveValue

private const val MEMBER_EXTRACTION_UNAVAILABLE = "bridge core unavailable to evaluate member extraction"

internal data class MemberExtractionContainerData(
    val path: String,
    val containerType: String,
    val views: List<ElementView>,
)

internal data class MemberExtractionEvaluation(
    val sourcePath: String,
    val sourceClassName: String?,
    val members: List<ImageBridgeProtocol.ChatRoomMemberSnapshot>,
    val selectedPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan,
    val confidence: ImageBridgeProtocol.ChatRoomSnapshotConfidence,
    val confidenceScore: Int,
    val usedPreferredPlan: Boolean,
    val candidateGap: Int?,
)

internal fun BridgeCore.memberExtractionEvaluate(
    containers: List<MemberExtractionContainerData>,
    expectedMemberHints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>,
    preferredPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan?,
): MemberExtractionEvaluation? =
    memberExtractionEvaluate(
        containers = containers,
        expectedMemberHints = expectedMemberHints,
        preferredPlan = preferredPlan,
        loadCompatibleCore = ::bridgeCoreLoadCompatibleLibraryOnce,
        nativeEvaluate = BridgeCoreJniMemberField::nativeMemberExtractionEvaluate,
    )

internal fun BridgeCore.memberExtractionEvaluate(
    containers: List<MemberExtractionContainerData>,
    expectedMemberHints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>,
    preferredPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan?,
    loadCompatibleCore: () -> Boolean,
    nativeEvaluate: (String) -> String,
): MemberExtractionEvaluation? {
    if (!loadCompatibleCore()) {
        error(MEMBER_EXTRACTION_UNAVAILABLE)
    }
    val envelope =
        runCatching {
            JSONObject(nativeEvaluate(memberExtractionRequestJson(containers, expectedMemberHints, preferredPlan)))
        }.getOrElse { error ->
            bridgeCoreLogError("bridge-core member extraction threw", error)
            error(MEMBER_EXTRACTION_UNAVAILABLE)
        }
    if (!envelope.optBoolean("ok", false)) {
        throw IllegalArgumentException(
            envelope.optString("error").takeIf { it.isNotEmpty() }
                ?: "bridge core rejected member extraction request",
        )
    }
    if (!envelope.optBoolean("found", false)) {
        return null
    }
    return memberExtractionEvaluation(envelope.getJSONObject("snapshot"))
}

private fun memberExtractionRequestJson(
    containers: List<MemberExtractionContainerData>,
    expectedMemberHints: Collection<ImageBridgeProtocol.ChatRoomMemberHint>,
    preferredPlan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan?,
): String =
    JSONObject()
        .apply {
            put("expectedMembers", JSONArray(expectedMemberHints.map(::hintJson)))
            preferredPlan?.let { plan -> put("preferredPlan", planJson(plan)) }
            put("containers", JSONArray(containers.map(::containerJson)))
        }.toString()

private fun hintJson(hint: ImageBridgeProtocol.ChatRoomMemberHint): JSONObject =
    JSONObject().apply {
        put("userId", hint.userId)
        hint.nickname?.let { put("nickname", it) }
        hint.mentionUserId?.let { put("mentionUserId", it) }
    }

private fun planJson(plan: ImageBridgeProtocol.ChatRoomMemberExtractionPlan): JSONObject =
    JSONObject().apply {
        put("containerPath", plan.containerPath)
        plan.sourceClassName?.let { put("sourceClassName", it) }
        put("userIdPath", plan.userIdPath)
        put("nicknamePath", plan.nicknamePath)
        plan.rolePath?.let { put("rolePath", it) }
        plan.profileImagePath?.let { put("profileImagePath", it) }
        plan.mentionUserIdPath?.let { put("mentionUserIdPath", it) }
    }

private fun containerJson(container: MemberExtractionContainerData): JSONObject =
    JSONObject().apply {
        put("path", container.path)
        put("containerType", container.containerType)
        put("views", JSONArray(container.views.map(::viewJson)))
    }

private fun viewJson(view: ElementView): JSONObject =
    JSONObject().apply {
        put("className", view.className)
        put(
            "values",
            JSONArray(
                view.values.map { (path, value) ->
                    JSONArray().apply {
                        put(path)
                        when (value) {
                            is PrimitiveValue.LongValue -> put(value.value)
                            is PrimitiveValue.StringValue -> put(value.value)
                        }
                    }
                },
            ),
        )
    }

private fun memberExtractionEvaluation(snapshot: JSONObject): MemberExtractionEvaluation =
    MemberExtractionEvaluation(
        sourcePath = snapshot.getString("sourcePath"),
        sourceClassName = snapshot.stringOrNull("sourceClassName"),
        members = memberSnapshots(snapshot.getJSONArray("members")),
        selectedPlan = extractionPlan(snapshot.getJSONObject("selectedPlan")),
        confidence = snapshotConfidence(snapshot.getString("confidence")),
        confidenceScore = snapshot.getInt("confidenceScore"),
        usedPreferredPlan = snapshot.getBoolean("usedPreferredPlan"),
        candidateGap = if (snapshot.isNull("candidateGap")) null else snapshot.getInt("candidateGap"),
    )

private fun memberSnapshots(members: JSONArray): List<ImageBridgeProtocol.ChatRoomMemberSnapshot> =
    (0 until members.length()).map { index ->
        val member = members.getJSONObject(index)
        ImageBridgeProtocol.ChatRoomMemberSnapshot(
            userId = member.getLong("userId"),
            nickname = member.getString("nickname"),
            roleCode = if (member.isNull("roleCode")) null else member.getInt("roleCode"),
            profileImageUrl = member.stringOrNull("profileImageUrl"),
            mentionUserId = member.stringOrNull("mentionUserId"),
        )
    }

private fun extractionPlan(plan: JSONObject): ImageBridgeProtocol.ChatRoomMemberExtractionPlan =
    ImageBridgeProtocol.ChatRoomMemberExtractionPlan(
        containerPath = plan.getString("containerPath"),
        sourceClassName = plan.stringOrNull("sourceClassName"),
        userIdPath = plan.getString("userIdPath"),
        nicknamePath = plan.getString("nicknamePath"),
        rolePath = plan.stringOrNull("rolePath"),
        profileImagePath = plan.stringOrNull("profileImagePath"),
        mentionUserIdPath = plan.stringOrNull("mentionUserIdPath"),
        fingerprint = plan.getString("fingerprint"),
    )

private fun snapshotConfidence(label: String): ImageBridgeProtocol.ChatRoomSnapshotConfidence =
    when (label) {
        "HIGH" -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.HIGH
        "MEDIUM" -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.MEDIUM
        "LOW" -> ImageBridgeProtocol.ChatRoomSnapshotConfidence.LOW
        else -> error("bridge core returned unknown member extraction confidence: $label")
    }

private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
