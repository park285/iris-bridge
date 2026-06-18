package party.qwer.iris.imagebridge.runtime.core

import org.json.JSONObject

internal object BridgeCoreJniMemberField {
    fun nativeMemberExtractionEvaluate(requestJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "member.extractionEvaluate",
            JSONObject().put("requestJson", requestJson),
        )

    fun nativeMemberEnrichmentMissingNicknames(requestJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "member.enrichmentMissingNicknames",
            JSONObject().put("requestJson", requestJson),
        )

    fun nativeMemberEnrichmentMerge(requestJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "member.enrichmentMerge",
            JSONObject().put("requestJson", requestJson),
        )

    fun nativeMemberProfileUserIds(requestJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "member.profileUserIds",
            JSONObject().put("requestJson", requestJson),
        )

    fun nativeMemberProfilePayload(requestJson: String): String =
        BridgeCoreJniDispatcher.envelope(
            "member.profilePayload",
            JSONObject().put("requestJson", requestJson),
        )
}
