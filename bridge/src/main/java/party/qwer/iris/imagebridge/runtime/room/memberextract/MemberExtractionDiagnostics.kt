package party.qwer.iris.imagebridge.runtime.room.memberextract

import android.util.Log

internal class MemberExtractionDiagnostics(
    private val candidateCollector: MemberCandidateCollector,
) {
    fun logMissingCandidateDiagnostics(
        roomId: Long,
        containers: List<ContainerCandidate>,
        expectedMemberIds: Set<Long>,
        expectedNicknames: Map<Long, String>,
    ) {
        val summary =
            containers
                .take(MAX_DEBUG_CONTAINER_SUMMARY)
                .joinToString(separator = " || ") { container -> debugSummary(container) }
        Log.w(
            TAG,
            "missing member candidates roomId=$roomId expectedMemberCount=${expectedMemberIds.size} expectedNicknameCount=${expectedNicknames.size} containers=${containers.size} summary=$summary",
        )
    }

    private fun debugSummary(container: ContainerCandidate): String {
        val views = candidateCollector.views(container).take(MAX_DEBUG_VIEW_COUNT)
        val classes = views.map(ElementView::className).distinct().take(MAX_DEBUG_PATHS)
        val paths = views.flatMap { view -> view.values.keys }.distinct().take(MAX_DEBUG_PATHS)
        val samples =
            views
                .flatMap { view ->
                    view.values.mapNotNull { (path, value) ->
                        redactedDiagnosticSample(path, value)
                    }
                }.distinct()
                .take(MAX_DEBUG_SAMPLE_COUNT)
        return "path=${container.path} type=${candidateCollector.typeLabel(container)} classes=$classes paths=$paths samples=$samples"
    }
}

internal fun redactedDiagnosticSample(
    path: String,
    value: PrimitiveValue,
): String =
    when (value) {
        is PrimitiveValue.StringValue -> "$path=<string:${value.value.length} chars>"
        is PrimitiveValue.LongValue -> "$path=<long>"
    }
