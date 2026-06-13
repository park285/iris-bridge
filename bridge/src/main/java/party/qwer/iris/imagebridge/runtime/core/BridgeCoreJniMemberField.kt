package party.qwer.iris.imagebridge.runtime.core

internal object BridgeCoreJniMemberField {
    external fun nativeMemberExtractionEvaluate(requestJson: String): String

    external fun nativeMemberEnrichmentMissingNicknames(requestJson: String): String

    external fun nativeMemberEnrichmentMerge(requestJson: String): String
}
