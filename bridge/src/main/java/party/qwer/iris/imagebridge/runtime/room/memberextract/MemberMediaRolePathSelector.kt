package party.qwer.iris.imagebridge.runtime.room.memberextract

internal fun selectProfileImagePathFromViews(views: List<ElementView>): ScoredPath? =
    views
        .flatMap { view ->
            view.values.mapNotNull { (path, value) ->
                val stringValue = (value as? PrimitiveValue.StringValue)?.value?.trim() ?: return@mapNotNull null
                if (!looksLikeProfileUrl(stringValue)) return@mapNotNull null
                path to stringValue
            }
        }.groupBy({ it.first }, { it.second })
        .map { (path, values) ->
            ScoredPath(
                path = path,
                score =
                    values.size * 20 +
                        pathHintScore(
                            path = path,
                            preferredTokens = setOf("profile", "image", "avatar", "url"),
                            discouragedTokens = emptySet(),
                        ),
            )
        }.maxByOrNull { scored -> scored.score }

internal fun selectRolePathFromViews(views: List<ElementView>): ScoredPath? =
    views
        .flatMap { view ->
            view.values.mapNotNull { (path, value) -> parseRoleCode(value)?.let { path to it } }
        }.groupBy({ it.first }, { it.second })
        .map { (path, values) ->
            ScoredPath(
                path = path,
                score =
                    values.size * 20 +
                        values.distinct().size * 10 +
                        pathHintScore(
                            path = path,
                            preferredTokens = setOf("role", "type", "member"),
                            discouragedTokens = emptySet(),
                        ),
            )
        }.maxByOrNull { scored -> scored.score }
