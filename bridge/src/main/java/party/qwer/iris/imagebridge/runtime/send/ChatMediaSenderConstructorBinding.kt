@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.send

import java.lang.reflect.Constructor

internal fun senderConstructorShape(
    parameterTypes: Array<Class<*>>,
    function0Class: Class<*>,
    function1Class: Class<*>,
): SenderConstructorShape? =
    when {
        parameterTypes.size == 4 &&
            parameterTypes[2] == function0Class &&
            parameterTypes[3] == function1Class -> SenderConstructorShape.LegacyWithThreadFlag

        parameterTypes.size == 3 &&
            parameterTypes[2] == function1Class -> SenderConstructorShape.ModernThreadId

        else -> null
    }

internal fun selectConstructorBinding(
    candidates: List<SenderConstructorBinding>,
    ambiguousMessage: String,
): SenderConstructorBinding {
    val bestPriority = candidates.minOf { candidate -> candidate.shape.priority }
    val bestCandidates = candidates.filter { candidate -> candidate.shape.priority == bestPriority }
    check(bestCandidates.size == 1) {
        "$ambiguousMessage: ${constructorSignatures(bestCandidates)}"
    }
    return bestCandidates.single()
}

private fun constructorSignatures(candidates: List<SenderConstructorBinding>): String =
    candidates.joinToString { candidate ->
        candidate.constructor.parameterTypes.joinToString(
            prefix = "${candidate.constructor.declaringClass.name}(",
            postfix = ")",
        ) { parameterType -> parameterType.name }
    }

internal fun isThreadIdParameterType(parameterType: Class<*>): Boolean = parameterType == java.lang.Long::class.java || parameterType == java.lang.Long.TYPE

internal fun normalizedThreadIdArgument(
    parameterType: Class<*>,
    threadId: Long?,
): Any? =
    if (parameterType == java.lang.Long.TYPE) {
        threadId ?: 0L
    } else {
        threadId
    }

internal data class SenderConstructorBinding(
    val constructor: Constructor<*>,
    val shape: SenderConstructorShape,
)

internal enum class SenderConstructorShape(
    val priority: Int,
) {
    LegacyWithThreadFlag(0),
    ModernThreadId(1),
}
