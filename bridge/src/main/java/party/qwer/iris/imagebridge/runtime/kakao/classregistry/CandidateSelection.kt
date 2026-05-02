@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.classregistry

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun selectClassCandidate(
    label: String,
    candidates: List<Class<*>>,
    preferredNames: Set<String>,
): Class<*> {
    val uniqueCandidates = candidates.distinctBy { clazz -> clazz.name }
    check(uniqueCandidates.isNotEmpty()) {
        "$label not found by signature"
    }
    val preferredKnownConcrete =
        uniqueCandidates.filter { candidate ->
            candidate.name in preferredNames && isConcreteClass(candidate)
        }
    if (preferredKnownConcrete.size == 1) return preferredKnownConcrete.single()

    val preferredConcrete = uniqueCandidates.filter(::isConcreteClass)
    if (preferredConcrete.size == 1) return preferredConcrete.single()

    val preferredKnown = uniqueCandidates.filter { candidate -> candidate.name in preferredNames }
    if (preferredKnown.size == 1) return preferredKnown.single()

    val ambiguousCandidates =
        when {
            preferredKnownConcrete.isNotEmpty() -> preferredKnownConcrete
            preferredConcrete.isNotEmpty() -> preferredConcrete
            preferredKnown.isNotEmpty() -> preferredKnown
            else -> uniqueCandidates
        }
    check(ambiguousCandidates.size == 1) {
        "$label is ambiguous: ${ambiguousCandidates.joinToString { candidate -> candidate.name }}"
    }
    return ambiguousCandidates.single()
}

internal fun selectMethodCandidate(
    label: String,
    candidates: List<Method>,
    preferredNames: Set<String> = emptySet(),
): Method {
    val uniqueCandidates = candidates.distinctBy(::methodSignature)
    val preferredCandidates = uniqueCandidates.filter { method -> method.name in preferredNames }
    val narrowedCandidates = if (preferredCandidates.isNotEmpty()) preferredCandidates else uniqueCandidates
    return chooseUniqueCandidate(
        label = label,
        candidates = narrowedCandidates,
        preference = { method ->
            !Modifier.isAbstract(method.modifiers) && !method.isBridge && !method.isSynthetic
        },
        describe = ::methodSignature,
    )
}

internal fun selectFieldCandidate(
    label: String,
    candidates: List<Field>,
): Field =
    chooseUniqueCandidate(
        label = label,
        candidates = candidates.distinctBy { field -> "${field.declaringClass.name}.${field.name}:${field.type.name}" },
        preference = { field -> !field.isSynthetic },
        describe = { field -> "${field.declaringClass.name}.${field.name}:${field.type.name}" },
    )

private fun <T> chooseUniqueCandidate(
    label: String,
    candidates: List<T>,
    preference: (T) -> Boolean,
    describe: (T) -> String,
): T {
    check(candidates.isNotEmpty()) { "$label not found" }
    val preferred = candidates.filter(preference).ifEmpty { candidates }
    check(preferred.size == 1) {
        "$label is ambiguous: ${preferred.joinToString { candidate -> describe(candidate) }}"
    }
    return preferred.single()
}

private fun methodSignature(method: Method): String =
    method.parameterTypes.joinToString(
        prefix = "${method.declaringClass.name}.${method.name}(",
        postfix = "):${method.returnType.name}",
    ) { parameterType -> parameterType.name }
