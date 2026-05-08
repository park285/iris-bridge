package party.qwer.iris.imagebridge.runtime.room.memberextract

internal fun parseRoleCode(value: PrimitiveValue?): Int? =
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

internal fun looksLikeNickname(value: String): Boolean {
    if (value.isBlank() || value.length > MAX_NICKNAME_LENGTH) return false
    if (looksLikeProfileUrl(value)) return false
    if (looksLikeInternalArtifactValue(value)) return false
    if (looksLikeSystemNotice(value)) return false
    return true
}

internal fun looksLikeProfileUrl(value: String): Boolean {
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

internal fun primitiveLongValue(value: PrimitiveValue?): Long? =
    when (value) {
        is PrimitiveValue.LongValue -> value.value
        is PrimitiveValue.StringValue -> value.value.trim().toLongOrNull()
        null -> null
    }

internal fun pathHintScore(
    path: String,
    preferredTokens: Set<String>,
    discouragedTokens: Set<String>,
): Int {
    val normalized = path.lowercase()
    val preferred = preferredTokens.count { token -> normalized.contains(token) }
    val discouraged = discouragedTokens.count { token -> normalized.contains(token) }
    return preferred * 25 - discouraged * 15
}

internal fun looksLikeMentionUserIdValue(
    value: String,
    userId: Long?,
    nickname: String?,
): Boolean {
    val normalized = value.trim()
    if (normalized.isBlank() || normalized.length > MAX_MENTION_USER_ID_LENGTH) return false
    if (normalized.toLongOrNull() != null || userId?.toString() == normalized) return false
    if (nickname?.trim()?.takeIf(String::isNotEmpty) == normalized) return false
    if (looksLikeProfileUrl(normalized)) return false
    return true
}

internal fun nicknameQualityScore(value: String): Int {
    val normalized = value.trim()
    var score = 0
    if (normalized.any { char -> char.code > 0x7f }) score += 30
    if (normalized.any { char -> char == ' ' || char == '[' || char == ']' }) score += 12
    return score
}

internal fun genericLabelPenalty(value: String): Int {
    val normalized = value.trim().lowercase()
    return if (normalized in GENERIC_LABEL_VALUES) 220 else 0
}

private fun looksLikeInternalArtifactValue(value: String): Boolean {
    val normalized = value.trim()
    val lowercase = normalized.lowercase()
    if (lowercase in INTERNAL_ARTIFACT_TOKENS) return true
    if (normalized.length < 16) return false
    val hasArtifactToken = INTERNAL_ARTIFACT_TOKENS.any { token -> lowercase.contains(token) }
    val asciiIdentifierLike = normalized.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    return asciiIdentifierLike && hasArtifactToken
}

private fun looksLikeSystemNotice(value: String): Boolean {
    val normalized = value.trim()
    return SYSTEM_NOTICE_PATTERNS.any { pattern -> pattern.matches(normalized) }
}

private val SYSTEM_NOTICE_PATTERNS =
    listOf(
        Regex("""Welcome to ['‘’"].+?['‘’"]\.?""", RegexOption.IGNORE_CASE),
    )
