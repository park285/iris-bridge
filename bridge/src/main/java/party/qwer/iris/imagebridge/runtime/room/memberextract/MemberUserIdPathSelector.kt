package party.qwer.iris.imagebridge.runtime.room.memberextract

internal fun selectUserIdPathFromViews(
    views: List<ElementView>,
    expectedMemberIds: Set<Long>,
): ScoredPath? =
    views
        .flatMap { view ->
            view.values.mapNotNull { (path, value) ->
                val longValue = primitiveLongValue(value)?.takeIf { it > 0L } ?: return@mapNotNull null
                path to longValue
            }
        }.groupBy({ it.first }, { it.second })
        .mapNotNull { (path, values) ->
            val distinctValues = values.distinct()
            val expectedHits = distinctValues.count { it in expectedMemberIds }
            if (values.size > 1 && distinctValues.size <= 1 && expectedHits == 0) {
                return@mapNotNull null
            }
            val largeValueBonus = values.count { it > Int.MAX_VALUE }
            ScoredPath(
                path = path,
                score =
                    expectedHits * 1_000 +
                        distinctValues.size * 80 +
                        values.size * 12 +
                        largeValueBonus * 5 +
                        pathHintScore(
                            path = path,
                            preferredTokens = setOf("user", "member", "id", "uid"),
                            discouragedTokens = setOf("chat", "room", "link"),
                        ),
            )
        }.maxByOrNull { scored -> scored.score }
