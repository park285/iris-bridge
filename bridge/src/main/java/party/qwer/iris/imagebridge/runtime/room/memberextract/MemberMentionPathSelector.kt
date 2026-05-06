package party.qwer.iris.imagebridge.runtime.room.memberextract

internal fun selectMentionUserIdPathFromViews(
    views: List<ElementView>,
    userPath: String,
    nicknamePath: String,
): ScoredPath? =
    views
        .flatMap { view ->
            val userId = primitiveLongValue(view.values[userPath])
            val nickname = (view.values[nicknamePath] as? PrimitiveValue.StringValue)?.value?.trim()
            view.values.mapNotNull { (path, value) ->
                val stringValue = (value as? PrimitiveValue.StringValue)?.value?.trim() ?: return@mapNotNull null
                if (!looksLikeMentionUserIdPath(path) || !looksLikeMentionUserIdValue(stringValue, userId, nickname)) {
                    return@mapNotNull null
                }
                path to stringValue
            }
        }.groupBy({ it.first }, { it.second })
        .mapNotNull { (path, values) ->
            if (values.isEmpty()) return@mapNotNull null
            ScoredPath(
                path = path,
                score =
                    values.size * 80 +
                        values.distinct().size * 20 +
                        pathHintScore(
                            path = path,
                            preferredTokens = setOf("mention", "user", "id"),
                            discouragedTokens = setOf("nickname", "name", "profile", "url"),
                        ),
            )
        }.maxByOrNull { scored -> scored.score }

private fun looksLikeMentionUserIdPath(path: String): Boolean {
    val normalized = path.lowercase()
    return normalized.contains("mention") && normalized.contains("id")
}
