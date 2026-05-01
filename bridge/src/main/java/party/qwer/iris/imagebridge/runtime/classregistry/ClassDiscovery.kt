@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime

import android.util.Log
import java.lang.reflect.Modifier

internal const val KAKAO_CLASS_REGISTRY_TAG = "IrisBridge"

internal fun stableClass(
    loader: ClassLoader,
    name: String,
): Class<*> = Class.forName(name, true, loader)

internal fun discoverClass(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
    lastKnownNames: Array<String>,
    label: String,
    signatureMatcher: (Class<*>) -> Boolean,
): Class<*> {
    val knownMatches =
        lastKnownNames.mapNotNull { name ->
            runCatching {
                Class.forName(name, false, classLoader)
            }.getOrNull()?.takeIf(signatureMatcher)
        }
    val knownConcrete = knownMatches.filter(::isConcreteClass).distinctBy { clazz -> clazz.name }
    if (knownConcrete.size == 1) {
        val selected = knownConcrete.single()
        Log.i(KAKAO_CLASS_REGISTRY_TAG, "$label found at known concrete name: ${selected.name}")
        return selected
    }
    for (name in lastKnownNames) {
        val clazz =
            runCatching {
                Class.forName(name, false, classLoader)
            }.getOrNull()
        if (clazz != null && signatureMatcher(clazz)) {
            Log.i(KAKAO_CLASS_REGISTRY_TAG, "$label matched known name candidate: $name")
        }
    }
    if (knownMatches.isEmpty()) {
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "$label not found at known names ${lastKnownNames.toList()}, starting DEX scan")
    } else {
        Log.w(
            KAKAO_CLASS_REGISTRY_TAG,
            "$label known-name candidates were insufficient ${knownMatches.map { candidate -> candidate.name }}, starting DEX scan",
        )
    }
    val scannedMatches = scanner.findAll(signatureMatcher)
    return selectClassCandidate(
        label = label,
        candidates = (knownMatches + scannedMatches).distinctBy { clazz -> clazz.name },
        preferredNames = lastKnownNames.toSet(),
    )
}

internal fun isConcreteClass(clazz: Class<*>): Boolean = !Modifier.isAbstract(clazz.modifiers) && !clazz.isInterface

internal fun hasEnumConstants(
    clazz: Class<*>,
    vararg names: String,
): Boolean {
    val constants = clazz.enumConstants ?: return false
    val constantNames = constants.map { (it as Enum<*>).name }.toSet()
    return names.all { it in constantNames }
}

internal fun hasSelfReturningAccessor(clazz: Class<*>): Boolean {
    val hasStaticSelfAccessor =
        clazz.methods.any { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                method.returnType == clazz
        }
    if (hasStaticSelfAccessor) return true
    return clazz.declaredFields.any { field ->
        Modifier.isStatic(field.modifiers) &&
            field.type != clazz &&
            field.type.methods.any { method ->
                method.parameterCount == 0 && method.returnType == clazz
            }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun requireEnumConstant(
    enumClass: Class<*>,
    name: String,
): Any =
    enumClass.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
        ?: error("enum constant $name not found in ${enumClass.name}")
