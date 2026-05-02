package party.qwer.iris.imagebridge.runtime.room.memberextract

internal class MemberFieldSelector {
    fun selectUserIdPath(
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

    fun selectNicknamePath(
        views: List<ElementView>,
        userPath: String,
        expectedNicknames: Map<Long, String>,
    ): ScoredPath? =
        views
            .flatMap { view ->
                val userId = (view.values[userPath] as? PrimitiveValue.LongValue)?.value
                view.values.mapNotNull { (path, value) ->
                    val stringValue = (value as? PrimitiveValue.StringValue)?.value?.trim() ?: return@mapNotNull null
                    if (!looksLikeNickname(stringValue)) {
                        return@mapNotNull null
                    }
                    CandidateStringValue(path = path, value = stringValue, userId = userId)
                }
            }.groupBy(CandidateStringValue::path)
            .mapNotNull { (path, values) ->
                if (values.isEmpty()) {
                    return@mapNotNull null
                }
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

    fun selectProfileImagePath(views: List<ElementView>): ScoredPath? =
        views
            .flatMap { view ->
                view.values.mapNotNull { (path, value) ->
                    val stringValue = (value as? PrimitiveValue.StringValue)?.value?.trim() ?: return@mapNotNull null
                    if (!looksLikeProfileUrl(stringValue)) {
                        return@mapNotNull null
                    }
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

    fun selectRolePath(views: List<ElementView>): ScoredPath? =
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

    fun parseRoleCode(value: PrimitiveValue?): Int? =
        when (value) {
            is PrimitiveValue.LongValue -> value.value.toInt().takeIf { it in KNOWN_ROLE_CODES }
            is PrimitiveValue.StringValue ->
                when (value.value.trim().lowercase()) {
                    "owner", "master", "host" -> 1
                    "admin", "manager" -> 4
                    "bot" -> 8
                    "member", "normal" -> 2
                    else -> null
                }

            null -> null
        }

    fun looksLikeNickname(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_NICKNAME_LENGTH) {
            return false
        }
        if (looksLikeProfileUrl(value)) {
            return false
        }
        if (looksLikeInternalArtifactValue(value)) {
            return false
        }
        return true
    }

    fun looksLikeProfileUrl(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("http://") ||
            normalized.startsWith("https://") ||
            normalized.startsWith("content://") ||
            normalized.startsWith("file://") ||
            normalized.contains("/profile") ||
            normalized.contains(".png") ||
            normalized.contains(".jpg") ||
            normalized.contains(".jpeg") ||
            normalized.contains(".webp")
    }

    fun primitiveLongValue(value: PrimitiveValue?): Long? =
        when (value) {
            is PrimitiveValue.LongValue -> value.value
            is PrimitiveValue.StringValue -> value.value.trim().toLongOrNull()
            null -> null
        }

    fun pathHintScore(
        path: String,
        preferredTokens: Set<String>,
        discouragedTokens: Set<String>,
    ): Int {
        val normalized = path.lowercase()
        val preferred = preferredTokens.count { token -> normalized.contains(token) }
        val discouraged = discouragedTokens.count { token -> normalized.contains(token) }
        return preferred * 25 - discouraged * 15
    }

    private fun nicknameQualityScore(value: String): Int {
        val normalized = value.trim()
        var score = 0
        if (normalized.any { char -> char.code > 0x7f }) score += 30
        if (normalized.any { char -> char == ' ' || char == '[' || char == ']' }) score += 12
        return score
    }

    private fun genericLabelPenalty(value: String): Int {
        val normalized = value.trim().lowercase()
        return if (normalized in GENERIC_LABEL_VALUES) 220 else 0
    }

    private fun looksLikeInternalArtifactValue(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.length < 16) {
            return false
        }
        val lowercase = normalized.lowercase()
        val hasArtifactToken = INTERNAL_ARTIFACT_TOKENS.any { token -> lowercase.contains(token) }
        val asciiIdentifierLike = normalized.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        return asciiIdentifierLike && hasArtifactToken
    }
}
