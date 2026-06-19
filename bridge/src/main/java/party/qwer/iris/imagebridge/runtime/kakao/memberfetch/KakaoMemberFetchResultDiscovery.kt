package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.discoverClass
import java.lang.reflect.Modifier

internal fun discoverMemberFetchResultClass(
    classLoader: ClassLoader,
    scanner: DexClassScanner,
): Class<*> =
    discoverClass(
        classLoader = classLoader,
        scanner = scanner,
        lastKnownNames =
            arrayOf(
                "com.kakao.talk.core.loco.LocoResult",
                "com.kakao.talk.core.loco.f",
                "com.kakao.talk.core.loco.C52513f",
            ),
        label = "MemberFetchResult",
        signatureMatcher = ::matchesMemberFetchResult,
    )

private fun matchesMemberFetchResult(clazz: Class<*>): Boolean {
    val hasValueAccessor =
        clazz.methods.any { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name == "e" &&
                method.parameterCount == 1
        }
    val hasErrorAccessor =
        clazz.methods.any { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name == "d" &&
                method.parameterCount == 1
        }
    return hasValueAccessor && hasErrorAccessor
}
