@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package party.qwer.iris.imagebridge.runtime.kakao.memberfetch

import android.util.Log
import party.qwer.iris.imagebridge.runtime.kakao.DexClassScanner
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.KAKAO_CLASS_REGISTRY_TAG
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.discoverClass
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.hasSelfReturningAccessor
import party.qwer.iris.imagebridge.runtime.kakao.classregistry.isConcreteClass
import java.lang.reflect.Modifier

internal fun discoverKakaoMemberFetchAccess(classLoader: ClassLoader): KakaoMemberFetchAccess? =
    runCatching {
        val scanner = DexClassScanner(classLoader)
        val clientClass =
            discoverClass(
                classLoader = classLoader,
                scanner = scanner,
                lastKnownNames =
                    arrayOf(
                        "com.kakao.talk.core.loco.Loco",
                        "ry0.C1298d",
                        "Xp.d",
                    ),
                label = "MemberFetchClient",
                signatureMatcher = ::matchesMemberFetchFacade,
            )
        val singleton =
            resolveMemberFetchSingleton(clientClass)
                ?: error("member-fetch client singleton not found on ${clientClass.name}")
        val fetchMembersMethod =
            clientClass.methods.singleOrNull { method ->
                method.name == "i" &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0] == Long::class.javaPrimitiveType &&
                    List::class.java.isAssignableFrom(method.parameterTypes[1])
            } ?: error("member-fetch client#i(long, List) not found on ${clientClass.name}")
        val resultClass = discoverMemberFetchResultClass(classLoader, scanner)
        val unwrapValueMethod =
            resultClass.methods.singleOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == "e" &&
                    method.parameterCount == 1
            } ?: error("member-fetch result#e(Object) not found on ${resultClass.name}")
        val unwrapErrorMethod =
            resultClass.methods.singleOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == "d" &&
                    method.parameterCount == 1
            } ?: error("member-fetch result#d(Object) not found on ${resultClass.name}")
        KakaoMemberFetchAccess(
            clientSingleton = singleton,
            fetchMembersMethod = fetchMembersMethod,
            resultClass = resultClass,
            unwrapValueMethod = unwrapValueMethod,
            unwrapErrorMethod = unwrapErrorMethod,
        )
    }.getOrElse { error ->
        Log.w(KAKAO_CLASS_REGISTRY_TAG, "Kakao member-fetch discovery failed: ${error.message}")
        null
    }

private fun matchesMemberFetchFacade(clazz: Class<*>): Boolean {
    if (!isConcreteClass(clazz) || !hasSelfReturningAccessor(clazz)) {
        return false
    }
    return clazz.methods.any { method ->
        method.name == "i" &&
            method.parameterCount == 2 &&
            method.parameterTypes[0] == Long::class.javaPrimitiveType &&
            List::class.java.isAssignableFrom(method.parameterTypes[1])
    }
}

private fun resolveMemberFetchSingleton(clazz: Class<*>): Any? {
    clazz.declaredFields
        .filter { field -> Modifier.isStatic(field.modifiers) && field.type == clazz }
        .forEach { field ->
            runCatching {
                field.isAccessible = true
                return field.get(null)
            }
        }
    clazz.declaredFields
        .filter { field -> Modifier.isStatic(field.modifiers) && field.type != clazz }
        .forEach { holder ->
            runCatching {
                holder.isAccessible = true
                val holderValue = holder.get(null) ?: return@forEach
                holder.type.methods
                    .filter { method -> method.parameterCount == 0 && method.returnType == clazz }
                    .forEach { accessor ->
                        accessor.isAccessible = true
                        accessor.invoke(holderValue)?.let { return it }
                    }
            }
        }
    return null
}

private fun discoverMemberFetchResultClass(
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
