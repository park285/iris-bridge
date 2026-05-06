package party.qwer.iris.imagebridge.runtime.room.memberextract

internal fun selectNicknamePathFromViews(
    views: List<ElementView>,
    userPath: String,
    expectedNicknames: Map<Long, String>,
): ScoredPath? =
    views
        .flatMap { view ->
            val userId = (view.values[userPath] as? PrimitiveValue.LongValue)?.value
            view.values.mapNotNull { (path, value) ->
                val stringValue = (value as? PrimitiveValue.StringValue)?.value?.trim() ?: return@mapNotNull null
                if (!looksLikeNickname(stringValue)) return@mapNotNull null
                CandidateStringValue(path = path, value = stringValue, userId = userId)
            }
        }.groupBy(CandidateStringValue::path)
        .mapNotNull { (path, values) ->
            if (values.isEmpty()) return@mapNotNull null
            val distinctValues = values.map(CandidateStringValue::value).distinct()
            val expectedMatches = values.count { candidate -> candidate.userId?.let(expectedNicknames::get) == candidate.value }
            val labelPenalty = values.sumOf { candidate -> genericLabelPenalty(candidate.value) }
            val humanLikeBonus = values.sumOf { candidate -> nicknameQualityScore(candidate.value) }
            ScoredPath(
                path = path,
                score =
                    values.size * 40 +
                        distinctValues.size * 12 +
                        expectedMatches * 500 +
                        humanLikeBonus -
                        labelPenalty +
                        pathHintScore(
                            path = path,
                            preferredTokens = setOf("nickname", "nick", "name", "display"),
                            discouragedTokens = setOf("url", "image", "path", "id", "value", "label"),
                        ),
                genericPenalty = labelPenalty,
            )
        }.maxByOrNull { scored -> scored.score }
